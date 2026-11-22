package io.github.mdmusahub.jpaadmin.engines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.service.CreationDates;
import io.github.mdmusahub.jpaadmin.service.DetailQueryService;
import io.github.mdmusahub.jpaadmin.service.DetailRecord;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.testsupport.enginemodel.Essay;
import io.github.mdmusahub.jpaadmin.testsupport.enginemodel.Tome;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Ledger;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Ticket;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What the three engine classes extend: every shared scenario. This last layer holds the questions only a real database can
 * answer differently: the chart's day buckets and cap, long and large text, the numbers and moments a column keeps, and a
 * UUID identifier (Phase 14a, D-128).
 */
public abstract class AbstractEngineScenarios extends EngineRecordScenarios {

    // ---- the chart query ----------------------------------------------------------------------------------------------------

    /** Forty essays, one every seven hours from 2026-11-10 00:00 UTC, so the days differ between UTC and Asia/Kolkata. */
    private List<Instant> seedEssays() {
        rowsWillChange();
        List<Instant> moments = new ArrayList<>();
        EnginePersistence.inTransaction(factory, em -> {
            for (int i = 0; i < 40; i++) {
                Essay essay = new Essay();
                essay.setTitle("Essay " + i);
                essay.setCreatedAt(Instant.parse("2026-11-10T00:00:00Z").plus(Duration.ofHours(7L * i)));
                moments.add(essay.getCreatedAt());
                em.persist(essay);
            }
        });
        return moments;
    }

    private static Map<LocalDate, Long> perDay(List<Instant> moments, ZoneId zone) {
        Map<LocalDate, Long> days = new HashMap<>();
        moments.forEach(moment -> days.merge(moment.atZone(zone).toLocalDate(), 1L, Long::sum));
        return days;
    }

    @Test
    void theChartCountsEachRecordInTheDayOfTheZoneItIsReadIn() {
        List<Instant> moments = seedEssays();
        AdminEntity essay = entity("essay");
        CreationDates dates = CreationDates.over(entityManager);
        LocalDate first = LocalDate.of(2026, 11, 8);
        LocalDate last = LocalDate.of(2026, 11, 30);

        for (String zone : List.of("UTC", "Asia/Kolkata", "America/Los_Angeles")) {
            CreationDates.Days days = dates.read(essay, first, last, ZoneId.of(zone), 50_000);

            assertThat(days.perDay()).as("the days in " + zone).isEqualTo(perDay(moments, ZoneId.of(zone)));
            assertThat(days.capped()).isFalse();
        }
        assertThat(perDay(moments, ZoneId.of("UTC"))).as("the zones really do disagree on this data")
                .isNotEqualTo(perDay(moments, ZoneId.of("Asia/Kolkata")));
    }

    @Test
    void theChartReadsOnlyTheNewestRowsPastTheCapAndSaysWhereItIsIncomplete() {
        List<Instant> moments = seedEssays();
        AdminEntity essay = entity("essay");
        ZoneId zone = ZoneId.of("UTC");

        CreationDates.Days days = CreationDates.over(entityManager).read(essay, LocalDate.of(2026, 11, 8),
                LocalDate.of(2026, 11, 30), zone, 10);

        List<Instant> newest = moments.stream().sorted(Comparator.reverseOrder()).limit(10).toList();
        assertThat(days.capped()).isTrue();
        assertThat(days.perDay()).as("only the newest ten are counted").isEqualTo(perDay(newest, zone));
        assertThat(days.incompleteThrough()).isEqualTo(newest.get(9).atZone(zone).toLocalDate());
    }

    @Test
    void aCreationMomentThatIsACalendarValueIsNotMovedByTheZone() {
        AdminEntity parcel = parcelEntity();

        for (String zone : List.of("UTC", "Asia/Kolkata", "America/Los_Angeles")) {
            CreationDates.Days days = CreationDates.over(entityManager).read(parcel, LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 1), ZoneId.of(zone), 50_000);

            assertThat(days.perDay()).as(zone).isEqualTo(Map.of(LocalDate.of(2026, 3, 1), (long) PARCELS));
        }
    }

    // ---- long and large text -------------------------------------------------------------------------------------------------

    private static String filler(int length, String ending) {
        return "lorem ipsum ".repeat(length / 12 + 1).substring(0, length - ending.length()) + ending;
    }

    private void seedLongTexts() {
        rowsWillChange();
        EnginePersistence.inTransaction(factory, em -> {
            for (int i = 0; i < 3; i++) {
                Essay essay = new Essay();
                essay.setTitle("Long " + i);
                essay.setHeadline(filler(4_000, i == 0 ? " headline-NEEDLE" : " headline-hay"));
                essay.setSummary(filler(20_000, i == 0 ? " summary-NEEDLE" : " summary-hay"));
                essay.setCreatedAt(Instant.parse("2026-11-10T00:00:00Z"));
                em.persist(essay);
            }
        });
    }

    private void seedTomes() {
        rowsWillChange();
        EnginePersistence.inTransaction(factory, em -> {
            for (int i = 0; i < 3; i++) {
                Tome tome = new Tome();
                tome.setTitle("Tome " + i);
                tome.setBody(filler(100_000, i == 0 ? " body-NEEDLE" : " body-hay"));
                em.persist(tome);
            }
        });
    }

    @Test
    void aSearchFindsATextAtTheEndOfAColumnOf4000AndOf20000Characters() {
        seedLongTexts();
        AdminEntity essay = entity("essay");

        for (String needle : List.of("HEADLINE-needle", "summary-NEEDLE")) {
            assertThat(lists.list(essay, lists.parse(essay, needle, null, null, null, null)).total()).as(needle).isEqualTo(1);
        }
        assertThat(lists.list(essay, lists.parse(essay, "no-such-needle", null, null, null, null)).total()).isZero();
    }

    @Test
    void aVeryLongTextIsCutInTheListAndTheListSortsByEachLongColumn() {
        seedLongTexts();
        AdminEntity essay = entity("essay");

        for (String sort : List.of("title", "headline", "summary")) {
            ListPage page = lists.list(essay, lists.parse(essay, null, sort, "asc", null, null));

            assertThat(page.total()).as("sorted by " + sort).isEqualTo(3);
            for (var row : page.rows()) {
                assertThat(row.cells()).allSatisfy(cell -> assertThat(cell.display() == null ? 0 : cell.display().length())
                        .as("a cell never carries a whole 20,000 character text").isLessThan(1_000));
            }
        }
    }

    @Test
    void aListOfALargeObjectTextColumnShowsItsRowsCutOnEveryDatabase() {
        seedTomes();
        AdminEntity tome = entity("tome");

        ListPage page = lists.list(tome, lists.parse(tome, null, null, null, null, null));

        assertThat(page.total()).isEqualTo(3);
        for (var row : page.rows()) {
            assertThat(row.cells()).allSatisfy(cell -> assertThat(cell.display() == null ? 0 : cell.display().length())
                    .as("a cell never carries a whole 100,000 character text").isLessThan(1_000));
        }
    }

    /**
     * A known limit on every database, found by the first engine run (Phase 14a): Hibernate 7 checks the type of the argument of
     * {@code lower(...)} before it asks the database, and a {@code @Lob String} is a CLOB, not a string. A search on an entity
     * with such an attribute therefore fails (the console's error page) on H2, PostgreSQL and MySQL alike; the list without a
     * search works. This test says what happens today; it is not an endorsement (see the Phase 14a report for the options).
     */
    @Test
    void aSearchOnAnEntityWithALargeObjectTextColumnFailsOnEveryDatabaseToday() {
        seedTomes();
        AdminEntity tome = entity("tome");

        assertThatThrownBy(() -> lists.list(tome, lists.parse(tome, "BODY-needle", null, null, null, null)))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("lower()");
        assertThat(lists.list(tome, lists.parse(tome, null, null, null, null, null)).total())
                .as("the same list without a search still works").isEqualTo(3);
    }

    /**
     * Whether a sort by a {@code @Lob} text column orders by the text. H2 and MySQL do. PostgreSQL stores such a column as an
     * {@code oid} (a reference to a large object), so {@code order by} orders by that number, which is the creation order, and
     * not by the text; the list still answers, the order is just not alphabetical.
     */
    protected abstract boolean aLargeObjectColumnSortsByItsText();

    @Test
    void aSortByALargeObjectColumnFollowsTheTextOnlyWhereTheDatabaseKeepsItAsText() {
        rowsWillChange();
        EnginePersistence.inTransaction(factory, em -> {
            for (String first : List.of("c", "a", "b")) {
                Tome tome = new Tome();
                tome.setTitle("Tome " + first);
                tome.setBody(first + " first letter decides");
                em.persist(tome);
            }
        });
        AdminEntity tome = entity("tome");

        ListPage page = lists.list(tome, lists.parse(tome, null, "body", "asc", null, null));
        List<String> titles = new ArrayList<>();
        for (var row : page.rows()) {
            titles.add(cell(page, row, "title").display());
        }

        assertThat(titles).as("every record is there, in the order this database gives")
                .isEqualTo(aLargeObjectColumnSortsByItsText() ? List.of("Tome a", "Tome b", "Tome c")
                        : List.of("Tome c", "Tome a", "Tome b"));
    }

    /**
     * Whether this engine returns a moment stored with an offset ({@code OffsetDateTime}, {@code ZonedDateTime}) in UTC. H2 keeps the
     * offset it was given; PostgreSQL and MySQL keep the instant and return it in UTC. The instant is the same either way; only the
     * wall-clock text the page shows differs.
     */
    protected abstract boolean momentsWithAnOffsetComeBackInUtc();

    // ---- numbers and moments a column keeps -----------------------------------------------------------------------------------

    private Long seedLedger() {
        rowsWillChange();
        Long[] id = new Long[1];
        EnginePersistence.inTransaction(factory, em -> {
            Ledger ledger = new Ledger();
            ledger.setLabel("one");
            ledger.setAmount(9_007_199_254_740_993L);
            ledger.setLevel((short) 32_000);
            ledger.setRatio(0.1);
            ledger.setTilt(1.1f);
            ledger.setBig(new BigInteger("12345678901234567890"));
            ledger.setSeenAt(Instant.parse("2026-03-04T05:06:07.123456Z"));
            ledger.setOpenedAt(OffsetDateTime.parse("2026-03-04T05:06:07.123456+05:30"));
            ledger.setClosedAt(ZonedDateTime.of(2026, 3, 4, 5, 6, 7, 0, ZoneId.of("Asia/Kolkata")));
            ledger.setOldDate(new java.util.Date(Instant.parse("2026-03-04T05:06:07.123Z").toEpochMilli()));
            ledger.setStamped(java.sql.Timestamp.from(Instant.parse("2026-03-04T05:06:07.123456Z")));
            ledger.setPlainDate(java.sql.Date.valueOf("2026-03-04"));
            em.persist(ledger);
            em.flush();
            id[0] = ledger.getId();
        });
        return id[0];
    }

    @Test
    void everyNumberAndMomentComesBackAsItWentIn() {
        Long id = seedLedger();

        Ledger back = read(em -> em.find(Ledger.class, id));

        assertThat(back.getAmount()).as("a long beyond the precision of a double").isEqualTo(9_007_199_254_740_993L);
        assertThat(back.getLevel()).isEqualTo((short) 32_000);
        assertThat(back.getRatio()).isEqualTo(0.1);
        assertThat(back.getTilt()).isEqualTo(1.1f);
        assertThat(back.getBig()).isEqualTo(new BigInteger("12345678901234567890"));
        assertThat(back.getSeenAt()).as("an instant keeps its microseconds").isEqualTo(Instant.parse("2026-03-04T05:06:07.123456Z"));
        assertThat(back.getOpenedAt().toInstant()).as("an offset time is the same instant, whatever offset it is read in")
                .isEqualTo(Instant.parse("2026-03-04T05:06:07.123456+05:30"));
        assertThat(back.getClosedAt().toInstant()).isEqualTo(Instant.parse("2026-03-03T23:36:07Z"));
        assertThat(back.getOldDate().getTime()).isEqualTo(Instant.parse("2026-03-04T05:06:07.123Z").toEpochMilli());
        assertThat(back.getStamped().toInstant()).isEqualTo(Instant.parse("2026-03-04T05:06:07.123456Z"));
        assertThat(back.getPlainDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 4));
    }

    @Test
    void theDetailPageShowsTheSameTextForTheSameMomentOnEveryDatabase() {
        Long id = seedLedger();
        DetailRecord record = DetailQueryService.over(entityManager, registry).find(entity("ledger"), String.valueOf(id)).orElseThrow();

        Map<String, String> shown = new java.util.LinkedHashMap<>();
        record.items().forEach(item -> shown.put(item.label(), item.cell().display()));

        assertThat(shown).containsEntry("Amount", "9007199254740993").containsEntry("Level", "32000")
                .containsEntry("Big", "12345678901234567890");
        assertThat(shown.get("Seen at")).as("an instant is shown in UTC, to the minute").isEqualTo("2026-03-04 05:06");
        String wall = momentsWithAnOffsetComeBackInUtc() ? "2026-03-03 23:36" : "2026-03-04 05:06";
        assertThat(shown.get("Opened at")).as("an offset time: the same instant, shown in the offset the database returned")
                .isEqualTo(wall);
        assertThat(shown.get("Closed at")).as("a zoned time: the same instant, shown in the offset the database returned")
                .isEqualTo(wall);
        assertThat(shown.get("Old date")).isEqualTo("2026-03-04 05:06");
        assertThat(shown.get("Stamped")).isEqualTo("2026-03-04 05:06");
        assertThat(shown.get("Plain date")).isEqualTo("2026-03-04");
    }

    @Test
    void aFilterOnADoubleAndALongRangeSelectsTheSameRowsOnEveryDatabase() {
        seedLedger();
        AdminEntity ledger = entity("ledger");

        assertThat(lists.list(ledger, lists.parse(ledger, null, null, null, null, null).withParsedFilters(
                lists.parseFilters(ledger, Map.of("filter.ratio.min", List.of("0.05"), "filter.ratio.max", List.of("0.15"))).filters()))
                .total()).isEqualTo(1);
        assertThat(lists.list(ledger, lists.parse(ledger, null, null, null, null, null).withParsedFilters(
                lists.parseFilters(ledger, Map.of("filter.amount.min", List.of("9007199254740993"))).filters())).total())
                .as("a long that a double cannot hold exactly").isEqualTo(1);
        assertThat(lists.list(ledger, lists.parse(ledger, null, null, null, null, null).withParsedFilters(
                lists.parseFilters(ledger, Map.of("filter.amount.min", List.of("9007199254740994"))).filters())).total()).isZero();
    }

    // ---- an identifier that is a UUID ----------------------------------------------------------------------------------------

    @Test
    void aListOfRecordsWithAUuidIdentifierPagesWithoutRepeatsOrGapsWhenTheSortColumnTies() {
        rowsWillChange();
        List<UUID> all = new ArrayList<>();
        EnginePersistence.inTransaction(factory, em -> {
            for (int i = 0; i < 30; i++) {
                Ticket ticket = new Ticket();
                ticket.setId(UUID.nameUUIDFromBytes(("ticket-" + i).getBytes()));
                ticket.setSubject("the same subject");
                em.persist(ticket);
                all.add(ticket.getId());
            }
        });
        AdminEntity ticket = entity("ticket");

        List<String> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            seen.addAll(lists.list(ticket, lists.parse(ticket, null, "subject", "asc", String.valueOf(page), "10")).rows().stream()
                    .map(row -> row.id()).toList());
        }

        assertThat(seen).hasSize(30).doesNotHaveDuplicates();
        assertThat(seen).containsExactlyInAnyOrderElementsOf(all.stream().map(UUID::toString).toList());
        assertThat(DetailQueryService.over(entityManager, registry).find(ticket, all.get(7).toString())).isPresent();
    }
}
