package io.github.mdmusahub.jpaadmin.perf;

import io.github.mdmusahub.jpaadmin.engines.EngineDatabase;
import jakarta.persistence.EntityManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The performance measurements on a real PostgreSQL in a container (Phase 14a, D-131), and what a trigram index does to the search
 * (the index advice of the Phase 14 scope). Runs only with the Maven profile {@code perf}. Without Docker it is skipped with the
 * reason on a laptop and fails when {@code JPA_ADMIN_REQUIRE_ENGINES} is true, like the engine tests.
 */
@EnabledIfSystemProperty(named = PerfSwitch.PROPERTY, matches = "true", disabledReason = PerfSwitch.OFF)
class PostgreSqlPerfTest extends AbstractPerfMeasurement {

    @Override
    EngineDatabase newDatabase() {
        return EngineDatabase.postgres();
    }

    /** One trigram index per searched text column: the list's search is an OR over all three, so one index would not be used. */
    @Override
    void afterTheCommonMeasurements() throws Exception {
        try (EntityManager em = factory.createEntityManager()) {
            long started = System.nanoTime();
            em.getTransaction().begin();
            em.unwrap(org.hibernate.Session.class).doWork(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("create extension if not exists pg_trgm");
                    for (String column : List.of("title", "details", "code")) {
                        statement.execute("create index perfitem_" + column + "_trgm on PerfItem using gin (lower(" + column
                                + ") gin_trgm_ops)");
                    }
                    statement.execute("analyze PerfItem");
                }
            });
            em.getTransaction().commit();
            fact(String.format(java.util.Locale.ROOT, "three pg_trgm GIN indexes (lower(title), lower(details), lower(code)) built in %.1f s",
                    (System.nanoTime() - started) / 1e9));
            String like = "select count(i) from PerfItem i where lower(i.title) like :text escape '!'";
            measure("with trigram indexes: count where lower(title) like '%zebra%' (rare)", () ->
                    em.createQuery(like, Long.class).setParameter("text", "%zebra%").getSingleResult());
            measure("with trigram indexes: list, first page, search 'zebra' (rare)", () -> {
                var lists = io.github.mdmusahub.jpaadmin.service.ListQueryService.over(em, registry);
                lists.list(item, lists.parse(item, "zebra", null, null, "0", "25"));
            });
            measure("with trigram indexes: list, first page, search 'item' (every row)", () -> {
                var lists = io.github.mdmusahub.jpaadmin.service.ListQueryService.over(em, registry);
                lists.list(item, lists.parse(item, "item", null, null, "0", "25"));
            });
        }
    }
}
