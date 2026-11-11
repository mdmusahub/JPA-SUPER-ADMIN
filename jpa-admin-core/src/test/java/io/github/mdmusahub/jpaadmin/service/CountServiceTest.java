package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.testmodel.Gadget;
import io.github.mdmusahub.jpaadmin.metadata.testmodel.Widget;
import io.github.mdmusahub.jpaadmin.testsupport.TestPersistence;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * The count service against a real database (Hibernate over H2), with an entity manager that counts the queries it
 * is asked for, so the cache can be proven and not just described: one query per entity per window, none inside the
 * window, and a failure that leaves the page working.
 */
class CountServiceTest {

    private static final int WIDGETS = 3;
    private static final int GADGETS = 2;

    private static AdminEntity widget;
    private static AdminEntity gadget;

    private final AtomicInteger queries = new AtomicInteger();
    private Class<?> failFor;
    private EntityManager entityManager;
    private MutableClock clock;
    private CountService counts;
    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger serviceLog;

    /** A clock the test moves by hand, so no test ever waits (TEST-6). */
    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-10T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        void goBackSeconds(long seconds) {
            now = now.minusSeconds(seconds);
        }
    }

    @BeforeAll
    static void fillTheDatabase() {
        EntityManager setup = TestPersistence.entityManager();
        setup.getTransaction().begin();
        setup.createQuery("delete from Widget").executeUpdate();
        setup.createQuery("delete from Gadget").executeUpdate();
        for (int i = 0; i < WIDGETS; i++) {
            Widget row = new Widget();
            row.setName("Widget " + i);
            setup.persist(row);
        }
        for (int i = 0; i < GADGETS; i++) {
            Gadget row = new Gadget();
            row.setName("Gadget " + i);
            setup.persist(row);
        }
        setup.getTransaction().commit();
        setup.close();
        widget = entity(Widget.class, "widget");
        gadget = entity(Gadget.class, "gadget");
    }

    private static AdminEntity entity(Class<?> type, String urlName) {
        return new AdminEntity(type, type.getSimpleName(), urlName, type.getSimpleName(), "Entities", "table", "id",
                null, List.of());
    }

    @BeforeEach
    void countTheQueries() {
        entityManager = TestPersistence.countingEntityManager(TestPersistence.entityManager(), queries,
                () -> failFor);
        clock = new MutableClock();
        counts = CountService.over(entityManager, clock);
        serviceLog = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(CountService.class);
        logged = new ListAppender<>();
        logged.start();
        serviceLog.addAppender(logged);
    }

    @AfterEach
    void closeTheEntityManager() {
        serviceLog.detachAppender(logged);
    }

    private List<String> warnings() {
        return logged.list.stream().filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    // ---- the counts themselves --------------------------------------------------------------------------------

    @Test
    void itCountsTheRowsOfEachEntity() {
        assertThat(counts.count(widget)).isEqualTo(OptionalLong.of(WIDGETS));
        assertThat(counts.count(gadget)).isEqualTo(OptionalLong.of(GADGETS));
    }

    @Test
    void itCountsSeveralEntitiesInTheOrderItWasGivenThem() {
        Map<String, OptionalLong> all = counts.countAll(List.of(widget, gadget));

        assertThat(all).containsExactly(Map.entry("widget", OptionalLong.of(WIDGETS)),
                Map.entry("gadget", OptionalLong.of(GADGETS)));
        assertThat(queries).hasValue(2);
    }

    // ---- the cache --------------------------------------------------------------------------------------------

    @Test
    void oneCountCostsOneQueryAndEveryRepeatInsideTheWindowCostsNone() {
        counts.count(widget);
        int afterTheFirst = queries.get();
        for (int i = 0; i < 10; i++) {
            assertThat(counts.count(widget)).isEqualTo(OptionalLong.of(WIDGETS));
        }

        assertThat(afterTheFirst).isEqualTo(1);
        assertThat(queries).as("ten more sidebars, no more queries").hasValue(1);
    }

    @Test
    void aWholeSidebarCostsOneQueryPerEntityAndTheNextPageInsideTheWindowCostsNothing() {
        counts.countAll(List.of(widget, gadget));
        counts.countAll(List.of(widget, gadget));
        counts.countAll(List.of(widget, gadget));

        assertThat(queries).hasValue(2);
    }

    @Test
    void afterTheWindowTheCountIsReadAgain() {
        counts.count(widget);
        clock.advanceSeconds(CountService.TTL_SECONDS - 1);
        counts.count(widget);
        assertThat(queries).as("still inside the window").hasValue(1);

        clock.advanceSeconds(1);
        counts.count(widget);

        assertThat(queries).hasValue(2);
    }

    @Test
    void theWindowIsShortSoAnewRecordShowsUpQuickly() {
        assertThat(CountService.TTL_SECONDS).isEqualTo(5);
    }

    @Test
    void aClockThatJumpsBackwardsDoesNotFreezeTheCountForever() {
        counts.count(widget);
        clock.goBackSeconds(3600);
        counts.count(widget);

        assertThat(queries).hasValue(2);
    }

    @Test
    void clearingTheCacheMakesTheNextPageAskAgain() {
        counts.count(widget);
        counts.clear();
        counts.count(widget);

        assertThat(queries).hasValue(2);
    }

    // ---- failures ---------------------------------------------------------------------------------------------

    @Test
    void aCountThatFailsIsUnavailableAndTheOtherEntitiesAreStillCounted() {
        failFor = Widget.class;

        Map<String, OptionalLong> all = counts.countAll(List.of(widget, gadget));

        assertThat(all.get("widget")).isEqualTo(OptionalLong.empty());
        assertThat(all.get("gadget")).isEqualTo(OptionalLong.of(GADGETS));
    }

    @Test
    void aCountThatFailsNeverThrowsAtThePage() {
        failFor = Widget.class;

        assertThatCode(() -> counts.count(widget)).doesNotThrowAnyException();
        assertThatCode(() -> counts.countAll(List.of(widget, gadget))).doesNotThrowAnyException();
    }

    @Test
    void aFailureIsLoggedOncePerWindowWithTheCauseAndWithoutAStackTrace() {
        failFor = Widget.class;

        counts.count(widget);
        counts.count(widget);
        counts.count(widget);

        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0)).contains(Widget.class.getName()).contains("the table is not there")
                .contains("unavailable");
        assertThat(logged.list.stream().filter(event -> event.getLevel() == Level.WARN))
                .allSatisfy(event -> assertThat(event.getThrowableProxy())
                        .as("a stack trace on every page would fill the host's log").isNull());
    }

    @Test
    void aFailureIsNotRememberedForeverEither() {
        failFor = Widget.class;
        counts.count(widget);
        failFor = null;
        clock.advanceSeconds(CountService.TTL_SECONDS);

        assertThat(counts.count(widget)).isEqualTo(OptionalLong.of(WIDGETS));
    }

    // ---- no factory at all ------------------------------------------------------------------------------------

    @Test
    void theUnavailableServiceCountsNothingAndAsksNoQuestions() {
        CountService nothing = CountService.unavailable(clock);

        assertThat(nothing.count(widget)).isEqualTo(OptionalLong.empty());
        assertThat(nothing.countAll(List.of(widget, gadget)).values()).containsOnly(OptionalLong.empty());
        assertThat(queries).hasValue(0);
        assertThat(warnings()).isEmpty();
    }

    // ---- the rule itself --------------------------------------------------------------------------------------

    @Test
    void theReadsRunInAReadOnlyTransaction() throws NoSuchMethodException {
        for (Method method : List.of(CountService.class.getMethod("count", AdminEntity.class),
                CountService.class.getMethod("countAll", Iterable.class))) {
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertThat(transactional).as(method.getName()).isNotNull();
            assertThat(transactional.readOnly()).as(method.getName()).isTrue();
        }
    }
}
