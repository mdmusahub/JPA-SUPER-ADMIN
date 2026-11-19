package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.StatsTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.TestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.statsmodel.StatsEntities;
import io.github.mdmusahub.jpaadmin.web.view.ChartSeries;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/**
 * The chart statistics against a real database (Hibernate over H2) with a clock the test moves by hand (TEST-6) and an
 * entity manager that counts the queries it is asked for (Phase 10, D-101, D-102): daily buckets for a known set of
 * rows at every edge of every range, the range whitelist, the number of queries per request, the 5-second cache, the
 * time zone rule, the row cap and its note, and a table that cannot be read.
 *
 * <p>"Today" is 2026-11-10 (12:00 UTC) unless a test says otherwise.
 */
class StatsServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    /** The entities that have a creation field in the chart model, in registry order (see {@code StatsEntities}). */
    private static final List<String> CHART_ENTITIES = List.of("beacon", "fallback", "journal", "relic", "stub", "voucher");

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-10T12:00:00Z");
        private ZoneId zone = ZoneOffset.UTC;

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId other) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }

    private final AtomicInteger queries = new AtomicInteger();
    private Class<?> failFor;
    private MutableClock clock;
    private AdminEntityRegistry registry;
    private StatsService stats;
    private EntityManager entityManager;
    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger serviceLog;

    @BeforeEach
    void startFromAnEmptyDatabase() {
        StatsTestPersistence.clear();
        RecordingStatementInspector.clear();
        failFor = null;
        queries.set(0);
        clock = new MutableClock();
        registry = StatsTestPersistence.registry();
        entityManager = TestPersistence.countingEntityManager(StatsTestPersistence.entityManager(), queries, () -> failFor);
        stats = StatsService.over(CreationDates.over(entityManager), registry, clock);
        serviceLog = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(StatsService.class);
        logged = new ListAppender<>();
        logged.start();
        serviceLog.addAppender(logged);
    }

    @AfterEach
    void closeEverything() {
        serviceLog.detachAppender(logged);
        entityManager.close();
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private static StatsEntities.Journal journal(LocalDateTime at) {
        StatsEntities.Journal row = new StatsEntities.Journal();
        row.title = "entry";
        row.createdAt = at;
        return row;
    }

    private static LocalDateTime at(String isoDateTime) {
        return LocalDateTime.parse(isoDateTime);
    }

    private static int index(ChartSeries series, String entity) {
        for (int i = 0; i < series.entities().size(); i++) {
            if (series.entities().get(i).name().equals(entity)) {
                return i;
            }
        }
        throw new AssertionError("no such entity in the series: " + entity);
    }

    private static ChartSeries.Entity entity(ChartSeries series, String name) {
        return series.entities().get(index(series, name));
    }

    /** The counts of one entity, oldest day first. */
    private static List<Long> column(ChartSeries series, String entity) {
        int i = index(series, entity);
        return series.days().stream().map(day -> day.counts().get(i)).toList();
    }

    private static long countOn(ChartSeries series, String entity, String date) {
        int i = index(series, entity);
        return series.days().stream().filter(day -> day.date().equals(date)).findFirst().orElseThrow().counts().get(i);
    }

    private List<String> warnings() {
        return logged.list.stream().filter(event -> event.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /** One row on each edge of each range, for the entity that has a {@code LocalDateTime}. */
    private void seedTheEdges() {
        StatsTestPersistence.persist(
                journal(at("2026-11-10T00:00:00")), journal(at("2026-11-10T23:59:59")),   // today: 2
                journal(at("2026-11-09T12:00:00")),                                       // yesterday
                journal(at("2026-11-04T00:00:00")),                                       // first day of 7
                journal(at("2026-11-03T23:59:59")),                                       // just outside 7
                journal(at("2026-10-12T00:00:00")),                                       // first day of 30
                journal(at("2026-10-11T23:59:59")),                                       // just outside 30
                journal(at("2026-08-13T00:00:00")),                                       // first day of 90
                journal(at("2026-08-12T23:59:59")),                                       // just outside 90
                journal(at("2026-11-11T00:00:00")),                                       // tomorrow: not yet
                journal(null));                                                           // no date at all
    }

    // ---- which entities ---------------------------------------------------------------------------------------------

    @Test
    void onlyEntitiesWithAnAcceptableCreationFieldAreInTheChart() {
        ChartSeries series = stats.series(null);

        assertThat(series.entities().stream().map(ChartSeries.Entity::name)).containsExactlyElementsOf(CHART_ENTITIES);
        assertThat(stats.hasChart()).isTrue();
    }

    @Test
    void theEntitiesLeftOutAreNamedByTheirLabels() {
        assertThat(stats.leftOut()).containsExactly("Masked", "Plain", "Veiled");
    }

    @Test
    void aHiddenEntityIsNotInTheChartEvenWithAPerfectField() {
        StatsEntities.Shadow shadow = new StatsEntities.Shadow();
        shadow.createdAt = at("2026-11-10T10:00:00");
        StatsTestPersistence.persist(shadow);
        RecordingStatementInspector.clear();

        ChartSeries series = stats.series(null);

        assertThat(series.entities().stream().map(ChartSeries.Entity::name)).doesNotContain("shadow");
        assertThat(stats.leftOut()).doesNotContain("Shadow");
        assertThat(RecordingStatementInspector.statements()).noneMatch(sql -> sql.toLowerCase().contains("shadow"));
    }

    @Test
    void anEntityWhoseOnlyCreationFieldIsSensitiveOrHiddenIsNeverQueried() {
        stats.series(null);

        List<String> selects = RecordingStatementInspector.selects().stream().map(String::toLowerCase).toList();
        assertThat(selects).noneMatch(sql -> sql.contains("masked") || sql.contains("veiled"));
        assertThat(selects).anyMatch(sql -> sql.contains("fallback"));
    }

    @Test
    void aSensitiveFirstChoiceMakesTheNextNameTheCreationField() {
        StatsEntities.Fallback row = new StatsEntities.Fallback();
        row.createdAt = at("2026-11-10T09:00:00");
        row.creationDate = TODAY.minusDays(2);
        StatsTestPersistence.persist(row);

        ChartSeries series = stats.series("7");

        assertThat(countOn(series, "fallback", "2026-11-08")).isEqualTo(1);
        assertThat(countOn(series, "fallback", "2026-11-10")).isZero();
        assertThat(RecordingStatementInspector.selects()).noneMatch(sql -> sql.toLowerCase().contains("createdat")
                && sql.toLowerCase().contains("fallback"));
    }

    @Test
    void aConsoleWithoutAnyCreationFieldHasNoChart() {
        List<AdminEntity> noField = registry.all().stream().filter(entity -> entity.creationField().isEmpty()).toList();
        StatsService none = StatsService.over(CreationDates.over(entityManager), new AdminEntityRegistry(noField,
                List.of()), clock);

        assertThat(none.hasChart()).isFalse();
        assertThat(none.series(null).entities()).isEmpty();
        assertThat(none.series(null).days()).hasSize(30).allSatisfy(day -> assertThat(day.counts()).isEmpty());
        assertThat(queries).hasValue(0);
    }

    @Test
    void anEmptyConsoleHasNoChartAndCostsNoQuery() {
        StatsService empty = StatsService.unavailable(AdminEntityRegistry.empty(), clock);

        assertThat(empty.hasChart()).isFalse();
        assertThat(empty.leftOut()).isEmpty();
        assertThat(empty.series("7").entities()).isEmpty();
    }

    // ---- the buckets ------------------------------------------------------------------------------------------------

    @Test
    void sevenDaysCountTheSevenLastDaysIncludingTodayAndNothingOutsideThem() {
        seedTheEdges();

        ChartSeries series = stats.series("7");

        assertThat(series.range()).isEqualTo(7);
        assertThat(series.days()).hasSize(7);
        assertThat(series.days().get(0).date()).isEqualTo("2026-11-04");
        assertThat(series.days().get(6).date()).isEqualTo("2026-11-10");
        assertThat(column(series, "journal")).containsExactly(1L, 0L, 0L, 0L, 0L, 1L, 2L);
        assertThat(entity(series, "journal").total()).isEqualTo(4);
        assertThat(series.notes()).isEmpty();
    }

    @Test
    void thirtyDaysStartTwentyNineDaysBeforeToday() {
        seedTheEdges();

        ChartSeries series = stats.series("30");

        assertThat(series.days()).hasSize(30);
        assertThat(series.days().get(0).date()).isEqualTo("2026-10-12");
        assertThat(countOn(series, "journal", "2026-10-12")).isEqualTo(1);
        assertThat(countOn(series, "journal", "2026-11-03")).isEqualTo(1);
        assertThat(entity(series, "journal").total()).isEqualTo(6);
    }

    @Test
    void ninetyDaysStartEightyNineDaysBeforeToday() {
        seedTheEdges();

        ChartSeries series = stats.series("90");

        assertThat(series.days()).hasSize(90);
        assertThat(series.days().get(0).date()).isEqualTo("2026-08-13");
        assertThat(countOn(series, "journal", "2026-08-13")).isEqualTo(1);
        assertThat(countOn(series, "journal", "2026-10-11")).isEqualTo(1);
        assertThat(entity(series, "journal").total()).isEqualTo(8);
    }

    @Test
    void everyDayOfTheRangeIsThereOldestFirstOnceEach_evenWithNoRecord() {
        ChartSeries series = stats.series("90");

        List<String> expected = new ArrayList<>();
        for (int i = 89; i >= 0; i--) {
            expected.add(TODAY.minusDays(i).toString());
        }
        assertThat(series.days().stream().map(ChartSeries.Day::date)).containsExactlyElementsOf(expected);
        assertThat(series.entities()).allSatisfy(entity -> {
            assertThat(entity.available()).isTrue();
            assertThat(entity.total()).isZero();
        });
    }

    @Test
    void theThreeRangesAreCutFromTheSameData_theShorterIsTheTailOfTheLonger() {
        seedTheEdges();

        List<Long> seven = column(stats.series("7"), "journal");
        List<Long> thirty = column(stats.series("30"), "journal");
        List<Long> ninety = column(stats.series("90"), "journal");

        assertThat(thirty.subList(23, 30)).isEqualTo(seven);
        assertThat(ninety.subList(60, 90)).isEqualTo(thirty);
    }

    @Test
    void eachAcceptedTypeOfCreationFieldIsBucketedByDay() {
        EntityManager em = StatsTestPersistence.entityManager();
        try {
            em.getTransaction().begin();
            StatsEntities.Voucher voucher = new StatsEntities.Voucher();
            voucher.creationDate = TODAY.minusDays(1);
            StatsEntities.Beacon beacon = new StatsEntities.Beacon();
            beacon.createdOn = Instant.parse("2026-11-08T10:00:00Z");
            StatsEntities.Stub stub = new StatsEntities.Stub();
            stub.createTime = OffsetDateTime.parse("2026-11-07T10:00:00Z");
            StatsEntities.Relic relic = new StatsEntities.Relic();
            relic.createdDateTime = ZonedDateTime.parse("2026-11-06T10:00:00Z");
            em.persist(voucher);
            em.persist(beacon);
            em.persist(stub);
            em.persist(relic);
            em.getTransaction().commit();
        } finally {
            em.close();
        }

        ChartSeries series = stats.series("7");

        assertThat(countOn(series, "voucher", "2026-11-09")).isEqualTo(1);
        assertThat(countOn(series, "beacon", "2026-11-08")).isEqualTo(1);
        assertThat(countOn(series, "stub", "2026-11-07")).isEqualTo(1);
        assertThat(countOn(series, "relic", "2026-11-06")).isEqualTo(1);
    }

    @Test
    void eachEntityIsCountedSeparately() {
        StatsTestPersistence.persist(journal(at("2026-11-10T08:00:00")), journal(at("2026-11-10T09:00:00")));
        StatsEntities.Voucher voucher = new StatsEntities.Voucher();
        voucher.creationDate = TODAY;
        StatsTestPersistence.persist(voucher);

        ChartSeries series = stats.series("7");

        assertThat(countOn(series, "journal", "2026-11-10")).isEqualTo(2);
        assertThat(countOn(series, "voucher", "2026-11-10")).isEqualTo(1);
        assertThat(countOn(series, "beacon", "2026-11-10")).isZero();
    }

    // ---- the range whitelist ----------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"7", "30", "90"})
    void anAllowedRangeIsUsedWithoutANote(String range) {
        ChartSeries series = stats.series(range);

        assertThat(series.range()).isEqualTo(Integer.parseInt(range));
        assertThat(series.notes()).isEmpty();
    }

    @Test
    void noRangeIsThirtyDaysWithoutANote() {
        ChartSeries series = stats.series(null);

        assertThat(series.range()).isEqualTo(30);
        assertThat(series.notes()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"45", "0", "-7", "1", "8", "91", "365", "abc", "", " ", " 7", "7 ", "07", "7.0", "7,30",
            "007", "+7", "0x7", "7;30", "30%", "seven", "99999999999999999999", "<script>alert(1)</script>",
            "' or 1=1 --"})
    void anyOtherRangeFallsBackToThirtyAndTheChartSaysSo(String range) {
        ChartSeries series = stats.series(range);

        assertThat(series.range()).isEqualTo(30);
        assertThat(series.days()).hasSize(30);
        assertThat(series.notes()).hasSize(1);
        assertThat(series.notes().get(0)).as("one fixed sentence, whatever was typed: the text is never repeated")
                .isEqualTo("That range is not available, so the last 30 days are shown. Choose 7, 30 or 90 days.");
    }

    // ---- the cost: queries and the cache ----------------------------------------------------------------------------

    @Test
    void oneLoadCostsExactlyOneQueryPerChartEntityWhateverTheRange() {
        for (String range : List.of("7", "30", "90")) {
            stats.clear();
            queries.set(0);

            stats.series(range);

            assertThat(queries).as("range " + range).hasValue(CHART_ENTITIES.size());
        }
    }

    @Test
    void theQueriesAreRealSelectsOfTheCreationColumnOnly() {
        seedTheEdges();
        RecordingStatementInspector.clear();

        stats.series("30");

        List<String> selects = RecordingStatementInspector.selects().stream().map(String::toLowerCase).toList();
        assertThat(selects).hasSize(CHART_ENTITIES.size());
        String forJournal = selects.stream().filter(sql -> sql.contains("from journal")).findFirst().orElseThrow();
        assertThat(forJournal).startsWith("select ").contains("createdat").doesNotContain("title")
                .doesNotContain("count(").doesNotContain("*");
        assertThat(forJournal.substring(0, forJournal.indexOf(" from "))).doesNotContain("id");
        assertThat(forJournal).contains("order by").contains("?");
    }

    @Test
    void aRepeatInsideTheWindowRunsNoQueryAtAll() {
        ChartSeries first = stats.series("30");
        queries.set(0);
        RecordingStatementInspector.clear();

        ChartSeries again = stats.series("30");
        clock.advanceSeconds(StatsService.TTL_SECONDS - 1);
        ChartSeries almostLater = stats.series("30");

        assertThat(queries).hasValue(0);
        assertThat(RecordingStatementInspector.statements()).isEmpty();
        assertThat(again).isEqualTo(first);
        assertThat(almostLater).isEqualTo(first);
    }

    @Test
    void switchingTheRangeInsideTheWindowRunsNoQuery() {
        stats.series("7");
        queries.set(0);

        stats.series("30");
        stats.series("90");
        stats.series("7");

        assertThat(queries).hasValue(0);
    }

    @Test
    void afterTheWindowEveryChartEntityIsReadAgainOnceAndNewRecordsShow() {
        ChartSeries before = stats.series("7");
        StatsTestPersistence.persist(journal(at("2026-11-10T11:00:00")));
        queries.set(0);

        assertThat(countOn(stats.series("7"), "journal", "2026-11-10")).as("still the cached answer").isZero();
        clock.advanceSeconds(StatsService.TTL_SECONDS);
        ChartSeries after = stats.series("7");

        assertThat(queries).hasValue(CHART_ENTITIES.size());
        assertThat(countOn(before, "journal", "2026-11-10")).isZero();
        assertThat(countOn(after, "journal", "2026-11-10")).isEqualTo(1);
    }

    @Test
    void aNewDayAsksAgainEvenInsideTheWindow() {
        stats.series("7");
        queries.set(0);

        clock.advanceSeconds(60 * 60 * 13);   // 01:00 the next day, long after the window as well
        stats.series("7");
        assertThat(queries).hasValue(CHART_ENTITIES.size());
    }

    @Test
    void aClockThatJumpsBackwardsDoesNotKeepAnAnswerForever() {
        stats.series("7");
        queries.set(0);

        clock.advanceSeconds(-3600);
        stats.series("7");

        assertThat(queries).hasValue(CHART_ENTITIES.size());
    }

    @Test
    void clearForgetsEverythingCached() {
        stats.series("7");
        queries.set(0);

        stats.clear();
        stats.series("7");

        assertThat(queries).hasValue(CHART_ENTITIES.size());
    }

    // ---- a table that cannot be read ---------------------------------------------------------------------------------

    @Test
    void aFailingEntityIsUnavailableAndTheOthersStillShow() {
        StatsTestPersistence.persist(journal(at("2026-11-10T08:00:00")));
        StatsEntities.Voucher voucher = new StatsEntities.Voucher();
        voucher.creationDate = TODAY;
        StatsTestPersistence.persist(voucher);
        failFor = StatsEntities.Journal.class;

        ChartSeries series = stats.series("7");

        ChartSeries.Entity broken = entity(series, "journal");
        assertThat(broken.available()).isFalse();
        assertThat(broken.total()).isZero();
        assertThat(column(series, "journal")).containsOnly(0L);
        assertThat(entity(series, "voucher").available()).isTrue();
        assertThat(countOn(series, "voucher", "2026-11-10")).isEqualTo(1);
        assertThat(series.entities()).hasSize(CHART_ENTITIES.size());
        assertThat(series.notes()).anySatisfy(note -> assertThat(note).contains("Journal").contains("could not be read"));
    }

    @Test
    void aFailureIsLoggedOncePerWindowWithoutAStackTraceOrAnyDetailOfTheStatement() {
        failFor = StatsEntities.Journal.class;

        stats.series("7");
        stats.series("30");
        stats.series("90");

        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0)).contains("StatsEntities$Journal").contains("unavailable")
                .doesNotContain("\tat ").doesNotContain("select ");
        assertThat(logged.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        assertThat(queries).as("the failed read is cached too, so a broken table is not asked again").hasValue(
                CHART_ENTITIES.size());

        clock.advanceSeconds(StatsService.TTL_SECONDS);
        stats.series("7");

        assertThat(warnings()).as("one line per window").hasSize(2);
    }

    @Test
    void aTableThatComesBackShowsAgainAfterTheWindow() {
        failFor = StatsEntities.Journal.class;
        assertThat(entity(stats.series("7"), "journal").available()).isFalse();

        failFor = null;
        clock.advanceSeconds(StatsService.TTL_SECONDS);

        assertThat(entity(stats.series("7"), "journal").available()).isTrue();
    }

    @Test
    void aConsoleWithoutAnEntityManagerShowsEveryChartEntityAsUnavailable() {
        StatsService broken = StatsService.unavailable(registry, clock);

        ChartSeries series = broken.series("7");

        assertThat(series.entities()).hasSize(CHART_ENTITIES.size()).noneMatch(ChartSeries.Entity::available);
        assertThat(series.days()).hasSize(7);
    }

    // ---- the row cap ------------------------------------------------------------------------------------------------

    private StatsService withCap(int cap) {
        return StatsService.over(CreationDates.over(entityManager), registry, clock, cap);
    }

    @Test
    void theCapIsFiftyThousand() {
        assertThat(StatsService.ROW_CAP).isEqualTo(50_000);
    }

    @Test
    void aWindowWithinTheCapIsNeverMarked_evenAtExactlyTheCap() {
        StatsTestPersistence.persist(journal(at("2026-11-10T01:00:00")), journal(at("2026-11-09T01:00:00")),
                journal(at("2026-11-08T01:00:00")), journal(at("2026-11-07T01:00:00")), journal(at("2026-11-06T01:00:00")));

        ChartSeries series = withCap(5).series("7");

        assertThat(entity(series, "journal").capped()).isFalse();
        assertThat(entity(series, "journal").total()).isEqualTo(5);
        assertThat(series.notes()).isEmpty();
    }

    @Test
    void moreRowsThanTheCapAreCountedFromTheNewestEndAndSaidSo() {
        for (int day = 0; day < 6; day++) {
            StatsTestPersistence.persist(journal(TODAY.minusDays(day).atTime(8, 0)), journal(TODAY.minusDays(day)
                    .atTime(9, 0)));
        }

        ChartSeries series = withCap(5).series("7");

        ChartSeries.Entity journal = entity(series, "journal");
        assertThat(journal.capped()).isTrue();
        assertThat(journal.total()).as("only the newest five rows are counted").isEqualTo(5);
        assertThat(countOn(series, "journal", "2026-11-10")).isEqualTo(2);
        assertThat(countOn(series, "journal", "2026-11-09")).isEqualTo(2);
        assertThat(countOn(series, "journal", "2026-11-08")).as("the oldest day reached is partial").isEqualTo(1);
        assertThat(countOn(series, "journal", "2026-11-07")).isZero();
        assertThat(series.notes()).hasSize(1);
        assertThat(series.notes().get(0)).contains("Journal").contains("up to and including 2026-11-08")
                .contains("incomplete").contains("more than 5 records").contains("90 days");
    }

    @Test
    void theNoteNamesTheCapWithAThousandsSeparatorAtTheRealCap() {
        assertThat(String.format(java.util.Locale.ROOT, "%,d", StatsService.ROW_CAP)).isEqualTo("50,000");
    }

    @Test
    void aShorterRangeThatLiesInTheCompletePartIsNotMarked() {
        StatsTestPersistence.persist(journal(at("2026-11-10T08:00:00")), journal(at("2026-11-10T09:00:00")),
                journal(at("2026-11-10T10:00:00")), journal(at("2026-09-10T08:00:00")), journal(at("2026-09-09T08:00:00")),
                journal(at("2026-09-08T08:00:00")));
        StatsService capped = withCap(4);

        ChartSeries seven = capped.series("7");
        ChartSeries thirty = capped.series("30");
        ChartSeries ninety = capped.series("90");

        assertThat(entity(seven, "journal").capped()).isFalse();
        assertThat(entity(thirty, "journal").capped()).isFalse();
        assertThat(entity(ninety, "journal").capped()).isTrue();
        assertThat(seven.notes()).isEmpty();
        assertThat(thirty.notes()).isEmpty();
        assertThat(ninety.notes()).hasSize(1);
        assertThat(entity(seven, "journal").total()).isEqualTo(3);
    }

    // ---- time -------------------------------------------------------------------------------------------------------

    @Test
    void aMomentIsAssignedToTheDayOfTheClocksZone() {
        StatsTestPersistence.persist(beaconAt("2026-11-09T20:00:00Z"));

        clock.zone = ZoneOffset.UTC;
        assertThat(countOn(stats.series("7"), "beacon", "2026-11-09")).isEqualTo(1);

        stats.clear();
        clock.zone = ZoneId.of("Asia/Kolkata");
        ChartSeries kolkata = stats.series("7");
        assertThat(countOn(kolkata, "beacon", "2026-11-10")).as("01:30 on the 10th in Kolkata").isEqualTo(1);
        assertThat(countOn(kolkata, "beacon", "2026-11-09")).isZero();

        stats.clear();
        clock.zone = ZoneId.of("America/Los_Angeles");
        ChartSeries losAngeles = stats.series("7");
        assertThat(countOn(losAngeles, "beacon", "2026-11-09")).as("12:00 on the 9th in Los Angeles").isEqualTo(1);
    }

    @Test
    void todayIsTodayInTheClocksZone() {
        clock.now = Instant.parse("2026-11-10T20:30:00Z");
        clock.zone = ZoneId.of("Asia/Kolkata");   // 02:00 on the 11th
        StatsTestPersistence.persist(beaconAt("2026-11-10T20:00:00Z"));

        ChartSeries series = stats.series("7");

        assertThat(series.days().get(6).date()).isEqualTo("2026-11-11");
        assertThat(countOn(series, "beacon", "2026-11-11")).isEqualTo(1);
    }

    @Test
    void aLocalDateTimeIsACalendarValueAndIsNotMovedByTheZone() {
        StatsTestPersistence.persist(journal(at("2026-11-09T23:30:00")));
        clock.zone = ZoneId.of("Pacific/Kiritimati");   // +14

        ChartSeries series = stats.series("7");

        assertThat(countOn(series, "journal", "2026-11-09")).isEqualTo(1);
    }

    @Test
    void offsetAndZonedValuesAreConvertedToTheClocksZone() {
        EntityManager em = StatsTestPersistence.entityManager();
        try {
            em.getTransaction().begin();
            StatsEntities.Stub stub = new StatsEntities.Stub();
            stub.createTime = OffsetDateTime.parse("2026-11-09T22:30:00-05:00");   // 03:30 UTC on the 10th
            StatsEntities.Relic relic = new StatsEntities.Relic();
            relic.createdDateTime = ZonedDateTime.parse("2026-11-09T22:30:00-05:00[America/New_York]");
            em.persist(stub);
            em.persist(relic);
            em.getTransaction().commit();
        } finally {
            em.close();
        }

        clock.zone = ZoneOffset.UTC;
        ChartSeries utc = stats.series("7");
        assertThat(countOn(utc, "stub", "2026-11-10")).isEqualTo(1);
        assertThat(countOn(utc, "relic", "2026-11-10")).isEqualTo(1);

        stats.clear();
        clock.zone = ZoneId.of("America/Los_Angeles");
        ChartSeries losAngeles = stats.series("7");
        assertThat(countOn(losAngeles, "stub", "2026-11-09")).isEqualTo(1);
        assertThat(countOn(losAngeles, "relic", "2026-11-09")).isEqualTo(1);
    }

    private static StatsEntities.Beacon beaconAt(String instant) {
        StatsEntities.Beacon beacon = new StatsEntities.Beacon();
        beacon.createdOn = Instant.parse(instant);
        return beacon;
    }
}
