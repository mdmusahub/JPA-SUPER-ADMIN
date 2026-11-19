package io.github.mdmusahub.jpaadmin.testsupport;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.statsmodel.StatsEntities;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.List;

/**
 * A fourth real JPA setup, for the chart tests: its own persistence unit ({@code stats-test}), its own in-memory database and
 * its own neutral entities ({@link StatsEntities}), so the rows the chart tests write never change a count another test
 * class asserts. Every statement is recorded by {@link RecordingStatementInspector}.
 */
public final class StatsTestPersistence {

    private static final EntityManagerFactory FACTORY = Persistence.createEntityManagerFactory("stats-test");

    private StatsTestPersistence() {
    }

    public static EntityManagerFactory factory() {
        return FACTORY;
    }

    public static EntityManager entityManager() {
        return FACTORY.createEntityManager();
    }

    /** The registry the console would build from the chart model (the hidden entity is not in it). */
    public static AdminEntityRegistry registry() {
        return EntityScanner.scan(FACTORY.getMetamodel(), DiscoverySettings.defaults());
    }

    /** Removes every row of every chart entity. */
    public static void clear() {
        EntityManager em = entityManager();
        try {
            em.getTransaction().begin();
            for (Class<?> type : List.of(StatsEntities.Journal.class, StatsEntities.Beacon.class,
                    StatsEntities.Voucher.class, StatsEntities.Stub.class, StatsEntities.Relic.class,
                    StatsEntities.Plain.class, StatsEntities.Masked.class, StatsEntities.Fallback.class,
                    StatsEntities.Veiled.class, StatsEntities.Shadow.class)) {
                em.createQuery("delete from " + type.getSimpleName()).executeUpdate();
            }
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    /** Stores the rows in one transaction. */
    public static void persist(Object... rows) {
        EntityManager em = entityManager();
        try {
            em.getTransaction().begin();
            for (Object row : rows) {
                em.persist(row);
            }
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }
}
