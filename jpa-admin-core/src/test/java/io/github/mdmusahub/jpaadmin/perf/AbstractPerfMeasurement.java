package io.github.mdmusahub.jpaadmin.perf;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.engines.EngineDatabase;
import io.github.mdmusahub.jpaadmin.engines.EngineGate;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.service.CreationDates;
import io.github.mdmusahub.jpaadmin.service.ExportService;
import io.github.mdmusahub.jpaadmin.service.ExportSettings;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.testsupport.PinnedTestZone;
import io.github.mdmusahub.jpaadmin.testsupport.perfmodel.PerfItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * The performance measurements (Phase 14a, D-131): a large synthetic data set and what the console's own queries cost on it.
 * <b>Not tests of speed and never part of a normal build</b>: the concrete classes are excluded from the default surefire run, refuse
 * to run without the system property {@code jpa.admin.perf=true}, and are switched on only by the Maven profile {@code perf}
 * ({@code mvn -B -pl jpa-admin-core -Pperf test}). Each measurement still has an explicit upper bound ({@link PerfBounds}) that flags
 * a regression.
 *
 * <p>The data: 200,000 {@link PerfItem} rows (three text columns, three to-one relations, a creation moment spread over 400 days)
 * and three tables of 2,000 rows. {@code -Djpa.admin.perf.rows=...} sets another size (for trying the profile out; the recorded
 * numbers are for 200,000).
 *
 * <p>Each measurement is run twice to warm up, then {@value #RUNS} times; the median, the fastest and the slowest are kept. The
 * results of a class are written to {@code target/perf-report-<engine>.md} for {@code docs/logs/phase-14a-performance.md}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractPerfMeasurement {

    static final int RUNS = 7;
    private static final int WARMUPS = 2;
    private static final int OWNERS = 2_000;
    private static final int BATCH = 5_000;
    private static final int PAGE_SIZE = 25;
    private static final LocalDateTime ANCHOR = LocalDateTime.of(2026, 11, 22, 12, 0);
    private static final int CAP = 50_000;

    private static final String[] WORDS = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel", "india", "juliet",
        "kilo", "lima", "mike", "november", "oscar", "papa", "quebec", "romeo", "sierra", "tango", "uniform", "victor", "whiskey",
        "xray", "yankee", "amber", "birch", "cedar", "dune", "ember", "fjord", "grove", "harbor", "iris", "jade", "knoll", "lagoon",
        "meadow", "north", "orchard"};

    /** A piece of work that may throw. */
    @FunctionalInterface
    interface Work {
        void run() throws Exception;
    }

    /** One measurement: times in nanoseconds, the bound in milliseconds. */
    record Result(String name, long medianNanos, long minNanos, long maxNanos, long boundMillis, String note) {
        double medianMillis() {
            return medianNanos / 1_000_000.0;
        }
    }

    private EngineDatabase database;
    private EngineGate.Decision gate = new EngineGate.Decision(EngineGate.Verdict.RUN, "");
    private String engineLabel;
    protected EntityManagerFactory factory;
    protected AdminEntityRegistry registry;
    protected AdminEntity item;
    protected int rows;
    private final List<Result> results = new ArrayList<>();
    private final List<String> facts = new ArrayList<>();

    abstract EngineDatabase newDatabase();

    /** What this engine adds to the list of measurements (for example an index), after the common ones. */
    void afterTheCommonMeasurements() throws Exception {
    }

    String engine() {
        return database.name();
    }

    // ---- the engine and the data, once per class ---------------------------------------------------------------------------

    @BeforeAll
    void startAndLoad() throws Exception {
        database = newDatabase();
        gate = database.needsDocker()
                ? EngineGate.decide(database.name(), database.testClass().replace("EngineTest", "PerfTest"),
                        EngineGate.dockerAvailable(), EngineGate.required())
                : new EngineGate.Decision(EngineGate.Verdict.RUN, "");
        if (gate.verdict() == EngineGate.Verdict.FAIL) {
            EngineGate.apply(gate);
        }
        if (gate.verdict() == EngineGate.Verdict.SKIP) {
            System.err.println(gate.reason());
            return;
        }
        PinnedTestZone.requirePinned(); // pinned once for the whole run, never changed here (see PinnedTestZone and EngineTestBase.ZONE)
        rows = Integer.getInteger("jpa.admin.perf.rows", 200_000);
        database.start();
        engineLabel = database.version();
        Map<String, Object> properties = new java.util.HashMap<>(database.properties());
        String url = (String) properties.get("jakarta.persistence.jdbc.url");
        if (url.startsWith("jdbc:h2:")) {
            // H2 keeps the result of the last identical query on unchanged tables and hands it out again (OPTIMIZE_REUSE_RESULTS, on by
            // default), so a repeated run would measure a cache, not the query. The measurements switch it off. PostgreSQL and MySQL have no
            // such cache (their warm page cache is part of the numbers, and said so in the report).
            properties.put("jakarta.persistence.jdbc.url", "jdbc:h2:mem:perf-h2;DB_CLOSE_DELAY=-1;OPTIMIZE_REUSE_RESULTS=FALSE");
        }
        if (Boolean.getBoolean("jpa.admin.perf.sql")) {
            properties.put("hibernate.show_sql", "true"); // statements only, with ? for every value: no data is printed
        }
        factory = Persistence.createEntityManagerFactory("perf-test", properties);
        registry = EntityScanner.scan(factory.getMetamodel(), DiscoverySettings.defaults());
        item = registry.all().stream().filter(entity -> entity.type() == PerfItem.class).findFirst().orElseThrow();
        long started = System.nanoTime();
        load();
        facts.add(String.format(Locale.ROOT, "loaded %,d items and 3 x %,d relation targets with JDBC batches of %,d in %.1f s",
                rows, OWNERS, BATCH, (System.nanoTime() - started) / 1e9));
    }

    @AfterAll
    void report() throws IOException {
        try {
            if (database != null && !results.isEmpty()) {
                afterTheCommonMeasurementsSafely();
                write();
            }
        } finally {
            try {
                if (factory != null) {
                    factory.close();
                }
            } finally {
                if (database != null) {
                    database.close();
                }
            }
        }
    }

    private void afterTheCommonMeasurementsSafely() {
        // Engine specific measurements run here, after the common ones, so their numbers are in the same report.
        try {
            afterTheCommonMeasurements();
        } catch (Exception failure) {
            facts.add("the engine specific measurements failed: " + failure.getClass().getSimpleName());
            throw new AssertionError("[" + engine() + "] engine specific measurements failed", failure);
        }
    }

    @BeforeEach
    void skipWithoutDocker() {
        EngineGate.apply(gate);
    }

    // ---- loading ------------------------------------------------------------------------------------------------------------

    private void load() throws Exception {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            em.unwrap(org.hibernate.Session.class).doWork(connection -> {
                for (String table : List.of("PerfOwner", "PerfGroup", "PerfPlace")) {
                    try (PreparedStatement insert = connection.prepareStatement("insert into " + table + " (name) values (?)")) {
                        for (int i = 1; i <= OWNERS; i++) {
                            insert.setString(1, table.substring(4) + " " + String.format("%04d", i));
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }
                long[] owners = ids(connection, "PerfOwner");
                long[] teams = ids(connection, "PerfGroup");
                long[] places = ids(connection, "PerfPlace");
                String[] statuses = {"OPEN", "ACTIVE", "DONE", "ARCHIVED"};
                try (PreparedStatement insert = connection.prepareStatement("insert into PerfItem (title, details, code, amount, status, "
                        + "createdAt, owner_id, team_id, place_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (int i = 0; i < rows; i++) {
                        insert.setString(1, "item " + i + " " + (i % 1000 == 7 ? "zebra" : WORDS[i % WORDS.length]));
                        insert.setString(2, details(i));
                        insert.setString(3, "C" + i);
                        insert.setBigDecimal(4, BigDecimal.valueOf(i % 10_000, 2));
                        insert.setString(5, statuses[i % 4]);
                        insert.setTimestamp(6, Timestamp.valueOf(ANCHOR.minusSeconds((long) (i * 172.8 * 200_000 / rows))));
                        insert.setLong(7, owners[(int) ((i * 7919L) % OWNERS)]);
                        insert.setLong(8, teams[(int) ((i * 104_729L) % OWNERS)]);
                        insert.setLong(9, places[(int) ((i * 1_299_709L) % OWNERS)]);
                        insert.addBatch();
                        if ((i + 1) % BATCH == 0) {
                            insert.executeBatch();
                        }
                    }
                    insert.executeBatch();
                }
            });
            em.getTransaction().commit();
        }
        // The planner needs statistics on a table that was just filled; every database has its own word for it.
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            em.unwrap(org.hibernate.Session.class).doWork(connection -> {
                try (Statement statement = connection.createStatement()) {
                    for (String table : List.of("PerfItem", "PerfOwner", "PerfGroup", "PerfPlace")) {
                        statement.execute(analyze(table));
                    }
                }
            });
            em.getTransaction().commit();
        }
    }

    /** The statement that refreshes the planner's statistics for {@code table}. */
    String analyze(String table) {
        return "analyze " + table;
    }

    private static long[] ids(java.sql.Connection connection, String table) throws java.sql.SQLException {
        List<Long> found = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select id from " + table + " order by id")) {
            while (result.next()) {
                found.add(result.getLong(1));
            }
        }
        return found.stream().mapToLong(Long::longValue).toArray();
    }

    private static String details(int i) {
        StringBuilder text = new StringBuilder(320);
        for (int k = 0; k < 40; k++) {
            text.append(WORDS[(int) ((i * 31L + k * 7L) % WORDS.length)]).append(' ');
        }
        return text.toString().trim();
    }

    // ---- measuring ------------------------------------------------------------------------------------------------------------

    /** Runs {@code work} {@value #WARMUPS} times to warm up, then {@value #RUNS} times, keeps the median, and checks the bound. */
    protected Result measure(String name, Work work) throws Exception {
        return measure(name, "", work);
    }

    protected Result measure(String name, String note, Work work) throws Exception {
        for (int i = 0; i < WARMUPS; i++) {
            work.run();
        }
        long[] times = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long started = System.nanoTime();
            work.run();
            times[i] = System.nanoTime() - started;
        }
        Arrays.sort(times);
        long bound = PerfBounds.of(engine(), name);
        Result result = new Result(name, times[RUNS / 2], times[0], times[RUNS - 1], bound, note);
        results.add(result);
        assertThat(result.medianMillis()).as("[%s] %s: the median of %d runs is over its bound of %d ms (a regression, or a slower machine)",
                engineLabel, name, RUNS, bound).isLessThanOrEqualTo((double) bound);
        return result;
    }

    protected void fact(String text) {
        facts.add(text);
    }

    private ListQueryService lists(EntityManager em) {
        return ListQueryService.over(em, registry);
    }

    private ListPage page(EntityManager em, String search, int page, List<String> hidden) {
        ListQueryService lists = lists(em);
        ListQuery query = lists.parse(item, search, null, null, String.valueOf(page), String.valueOf(PAGE_SIZE))
                .withParsedHidden(hidden);
        return lists.list(item, query);
    }

    private ListPage sortedPage(EntityManager em, String key, int page) {
        ListQueryService lists = lists(em);
        return lists.list(item, lists.parse(item, null, key, "asc", String.valueOf(page), String.valueOf(PAGE_SIZE)));
    }

    // ---- the list: first, middle and last page, with and without a search --------------------------------------------------------

    @Test
    void theListPageFirstMiddleAndLastWithNoSearch() throws Exception {
        try (EntityManager em = factory.createEntityManager()) {
            int pages = (rows + PAGE_SIZE - 1) / PAGE_SIZE;
            measure("list, first page, no search", () -> page(em, null, 0, List.of()));
            measure("list, middle page, no search", () -> page(em, null, pages / 2, List.of()));
            measure("list, last page, no search", () -> page(em, null, pages - 1, List.of()));
            assertThat(page(em, null, 0, List.of()).total()).isEqualTo(rows);
        }
    }

    @Test
    void theListPageSortedByAColumnThatHasNoIndex() throws Exception {
        try (EntityManager em = factory.createEntityManager()) {
            int pages = (rows + PAGE_SIZE - 1) / PAGE_SIZE;
            measure("list, first page, sorted by title (no index)", () -> sortedPage(em, "title", 0));
            measure("list, last page, sorted by title (no index)", () -> sortedPage(em, "title", pages - 1));
            measure("list, first page, sorted by a relation's label (owner)", () -> sortedPage(em, "owner", 0));
        }
    }

    @Test
    void theListPageFirstMiddleAndLastWithARareSearchWord() throws Exception {
        try (EntityManager em = factory.createEntityManager()) {
            long matches = page(em, "zebra", 0, List.of()).total();
            int pages = (int) ((matches + PAGE_SIZE - 1) / PAGE_SIZE);
            String note = matches + " matching rows";
            measure("list, first page, search 'zebra' (rare)", note, () -> page(em, "zebra", 0, List.of()));
            measure("list, middle page, search 'zebra' (rare)", note, () -> page(em, "zebra", pages / 2, List.of()));
            measure("list, last page, search 'zebra' (rare)", note, () -> page(em, "zebra", pages - 1, List.of()));
            assertThat(matches).isEqualTo(rows / 1000);
        }
    }

    @Test
    void theListPageFirstMiddleAndLastWithAWordInEveryRow() throws Exception {
        try (EntityManager em = factory.createEntityManager()) {
            int pages = (rows + PAGE_SIZE - 1) / PAGE_SIZE;
            String note = "every row matches";
            measure("list, first page, search 'item' (every row)", note, () -> page(em, "item", 0, List.of()));
            measure("list, middle page, search 'item' (every row)", note, () -> page(em, "item", pages / 2, List.of()));
            measure("list, last page, search 'item' (every row)", note, () -> page(em, "item", pages - 1, List.of()));
        }
    }

    // ---- the search on one text column and the count --------------------------------------------------------------------------

    @Test
    void theSearchOnOneTextColumnAndCountStar() throws Exception {
        try (EntityManager em = factory.createEntityManager()) {
            String like = "select count(i) from PerfItem i where lower(i.title) like :text escape '!'";
            measure("search: count where lower(title) like '%zebra%' (rare)", () ->
                    em.createQuery(like, Long.class).setParameter("text", "%zebra%").getSingleResult());
            measure("search: count where lower(title) like '%item%' (every row)", () ->
                    em.createQuery(like, Long.class).setParameter("text", "%item%").getSingleResult());
            measure("count(*) of the whole table", () -> em.createQuery("select count(i) from PerfItem i", Long.class).getSingleResult());
        }
    }

    // ---- the relation join cost ------------------------------------------------------------------------------------------------

    @Test
    void theCostOfOneTwoAndThreeRelationColumnsOnAPage() throws Exception {
        try (EntityManager em = factory.createEntityManager()) {
            int pages = (rows + PAGE_SIZE - 1) / PAGE_SIZE;
            List<List<String>> hidden = List.of(List.of("owner", "team", "place"), List.of("team", "place"), List.of("place"), List.of());
            for (int shown = 0; shown <= 3; shown++) {
                List<String> hide = hidden.get(shown);
                String label = shown + " relation column" + (shown == 1 ? "" : "s") + " shown";
                measure("relations: " + label + ", first page", () -> page(em, null, 0, hide));
                measure("relations: " + label + ", middle page", () -> page(em, null, pages / 2, hide));
            }
        }
    }

    // ---- the chart ----------------------------------------------------------------------------------------------------------------

    @Test
    void theChartReadUnderTheCapAndAtTheCapAndTheGroupByAlternative() throws Exception {
        LocalDate last = ANCHOR.toLocalDate();
        ZoneId zone = ZoneOffset.UTC;
        try (EntityManager em = factory.createEntityManager()) {
            CreationDates dates = CreationDates.over(em);
            CreationDates.Days under = dates.read(item, last.minusDays(89), last, zone, CAP);
            CreationDates.Days over = dates.read(item, last.minusDays(399), last, zone, CAP);
            long underRows = under.perDay().values().stream().mapToLong(Long::longValue).sum();
            long overRows = over.perDay().values().stream().mapToLong(Long::longValue).sum();
            fact(String.format(Locale.ROOT, "chart under the cap: %,d rows in 90 days, capped=%s; at the cap: %,d rows read of %,d "
                    + "in the 400 day window, capped=%s, incomplete through %s", underRows, under.capped(), overRows, rows,
                    over.capped(), over.incompleteThrough()));
            measure("chart: read the creation column, 90 days (under the 50,000 cap)", underRows + " rows read",
                    () -> dates.read(item, last.minusDays(89), last, zone, CAP));
            measure("chart: read the creation column, 400 days (200,000 rows, cut at the 50,000 cap)", overRows + " rows read",
                    () -> dates.read(item, last.minusDays(399), last, zone, CAP));
            assertThat(under.capped()).isFalse();
            assertThat(over.capped()).isEqualTo(rows > CAP);
            assertThat(overRows).isEqualTo(Math.min(rows, CAP));
            // The alternative that the cap recommendation weighs: let the database count per day (one row per day comes back).
            String grouped = "select cast(i.createdAt as LocalDate), count(i) from PerfItem i where i.createdAt >= :from "
                    + "and i.createdAt < :to group by cast(i.createdAt as LocalDate)";
            measure("chart alternative: group by day in the database, 90 days", () ->
                    em.createQuery(grouped, Object[].class).setParameter("from", last.minusDays(89).atStartOfDay())
                            .setParameter("to", last.plusDays(1).atStartOfDay()).getResultList());
            measure("chart alternative: group by day in the database, 400 days", () ->
                    em.createQuery(grouped, Object[].class).setParameter("from", last.minusDays(399).atStartOfDay())
                            .setParameter("to", last.plusDays(1).atStartOfDay()).getResultList());
        }
    }

    // ---- the export ---------------------------------------------------------------------------------------------------------------

    @Test
    void theExportOfFiftyThousandRows() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);
        try (EntityManager em = factory.createEntityManager()) {
            ExportService exports = ExportService.over(lists(em), registry, new ExportSettings(true, CAP, false), clock);
            ExportService.Export probe = exports.open(item, ListQuery.defaults());
            fact(String.format(Locale.ROOT, "export: %,d rows written, truncated=%s, %,d rows match", probe.rows(), probe.truncated(),
                    probe.matching()));
            measure("export: 50,000 rows (a count and 100 pages of 500)", probe.rows() + " rows", () -> {
                ExportService.Export export = exports.open(item, ListQuery.defaults());
                try (Writer out = Writer.nullWriter()) {
                    export.writeTo(out);
                }
            });
            ListQueryService lists = lists(em);
            io.github.mdmusahub.jpaadmin.service.ExportPlan plan = lists.exportPlan(item, ListQuery.defaults());
            measure("export: the first page of 500 rows (offset 0)", () -> lists.exportRows(plan, 0, 500));
            measure("export: the last page of 500 rows (offset 49,500)", () -> lists.exportRows(plan, CAP - 500, 500));
        }
    }

    // ---- the report ---------------------------------------------------------------------------------------------------------------

    private void write() throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("## ").append(engineLabel).append("\n\n");
        text.append("- machine: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append(", ")
                .append(Runtime.getRuntime().availableProcessors()).append(" logical CPUs, JVM maximum heap ")
                .append(Runtime.getRuntime().maxMemory() / (1024 * 1024)).append(" MB\n");
        text.append("- JDK: ").append(System.getProperty("java.vendor")).append(' ').append(System.getProperty("java.version")).append('\n');
        text.append("- rows: ").append(String.format(Locale.ROOT, "%,d", rows)).append(", page size ").append(PAGE_SIZE)
                .append(", each measurement: ").append(WARMUPS).append(" warm-up runs then ").append(RUNS).append(" timed runs\n");
        for (String fact : facts) {
            text.append("- ").append(fact).append('\n');
        }
        text.append("\n| Measurement | Median ms | Min ms | Max ms | Bound ms | Note |\n|---|---:|---:|---:|---:|---|\n");
        for (Result result : results) {
            text.append(String.format(Locale.ROOT, "| %s | %.1f | %.1f | %.1f | %s | %s |%n", result.name(), result.medianMillis(),
                    result.minNanos() / 1e6, result.maxNanos() / 1e6,
                    result.boundMillis() == Long.MAX_VALUE ? "none yet" : String.valueOf(result.boundMillis()), result.note()));
        }
        Path folder = Path.of(System.getProperty("basedir", ".")).resolve("target");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("perf-report-" + engine().toLowerCase(Locale.ROOT) + ".md"), text, StandardCharsets.UTF_8);
        Map<String, String> csv = new java.util.LinkedHashMap<>();
        results.forEach(result -> csv.put(result.name(), String.format(Locale.ROOT, "%.1f", result.medianMillis())));
        Files.writeString(folder.resolve("perf-report-" + engine().toLowerCase(Locale.ROOT) + ".csv"),
                csv.entrySet().stream().map(entry -> engine() + " | " + entry.getKey() + ";" + entry.getValue())
                        .collect(java.util.stream.Collectors.joining("\n")), StandardCharsets.UTF_8);
    }
}
