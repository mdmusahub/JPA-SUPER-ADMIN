package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Courier;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Crate;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Ledger;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Vault;
import io.github.mdmusahub.jpaadmin.web.view.ListView.Filters.Definition.Kind;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Phase 6 against a real Hibernate and a real database: what every kind of filter does to the rows and the total, how
 * filters combine with the search, the sort and the paging, which SQL they cost, and what the filter panel may list.
 * The expected rows are computed here from the seeded data, never copied from the output.
 */
class ListQueryServiceFilterTest {

    private static final int PARCELS = 40;
    private static final AdminEntityRegistry REGISTRY = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
            DiscoverySettings.defaults());
    private static final AdminEntity PARCEL = REGISTRY.find("parcel").orElseThrow();
    private static final AdminEntity LEDGER = REGISTRY.find("ledger").orElseThrow();

    private final List<Parcel> seeded = new ArrayList<>();
    private final List<Ledger> ledgers = new ArrayList<>();
    private long northId;
    private long southId;
    private long falconId;
    private EntityManager entityManager;
    private ListQueryService service;

    @BeforeEach
    void seed() {
        ListTestPersistence.empty();
        seeded.clear();
        ledgers.clear();
        ListTestPersistence.inTransaction(em -> {
            Depot north = depot(em, "North hub", "Leeds");
            Depot south = depot(em, "South hub", "Bath");
            depot(em, "Alpha hub", "Ayr");
            depot(em, null, "Nowhere");
            northId = north.getId();
            southId = south.getId();
            Courier courier = new Courier();
            courier.setBadgeNumber("BADGE-SECRET-1");
            courier.setCallSign("Falcon");
            em.persist(courier);
            falconId = courier.getId();
            Crate crate = new Crate();
            crate.setNumber(42);
            em.persist(crate);
            Vault vault = new Vault();
            vault.setName("Deep vault name");
            em.persist(vault);
            for (int i = 1; i <= PARCELS; i++) {
                Parcel parcel = new Parcel();
                parcel.setReference(String.format("FIL-%03d", i));
                parcel.setNotes(i == 7 ? "100% sure" : i == 9 ? "a_b" : i % 5 == 0 ? "Handle with CARE" : "plain");
                parcel.setWeight(new BigDecimal("1.5").multiply(BigDecimal.valueOf(i)));
                parcel.setPieces(i % 7);
                parcel.setFragile(i % 3 == 0);
                parcel.setStatus(ParcelStatus.values()[i % 4]);
                parcel.setShippedOn(LocalDate.of(2026, 1, 1).plusDays(i));
                parcel.setCreatedAt(LocalDateTime.of(2026, 3, 1, 0, 0).plusHours(6L * i));
                parcel.setDepot(i % 2 == 0 ? north : south);
                parcel.setCourier(i % 2 == 0 ? courier : null);
                parcel.setCrate(i % 5 == 0 ? crate : null);
                parcel.setVault(i % 11 == 0 ? vault : null);
                parcel.setAccessToken("token-secret-" + i);
                parcel.setMemo("memo-secret-" + i);
                parcel.setInternalCode("internal-" + i);
                em.persist(parcel);
                seeded.add(parcel);
            }
            for (int k = 1; k <= 6; k++) {
                ledgers.add(ledger(em, k));
            }
        });
        entityManager = ListTestPersistence.entityManager();
        service = ListQueryService.over(entityManager, REGISTRY);
        RecordingStatementInspector.clear();
    }

    @AfterEach
    void close() {
        entityManager.close();
    }

    private static Depot depot(EntityManager em, String name, String city) {
        Depot depot = new Depot();
        depot.setName(name);
        depot.setCity(city);
        depot.setApiKey("DEPOT-KEY-SECRET");
        em.persist(depot);
        return depot;
    }

    /** Rows 1 to 5 sit at noon on 2026-05-0k; row 6 sits at midnight on 2026-05-05, the edge of a day. */
    private static Ledger ledger(EntityManager em, int k) {
        Ledger ledger = new Ledger();
        ledger.setLabel("ledger " + k);
        Instant at = k == 6 ? Instant.parse("2026-05-05T00:00:00Z") : Instant.parse("2026-05-0" + k + "T12:00:00Z");
        if (k <= 5) {
            ledger.setAmount(k * 100L);
            ledger.setLevel((short) k);
            ledger.setRatio(k * 1.5);
            ledger.setTilt(k * 0.5f);
            ledger.setBig(BigInteger.TEN.pow(k));
        }
        ledger.setSeenAt(at);
        ledger.setOpenedAt(at.atOffset(ZoneOffset.UTC));
        ledger.setClosedAt(at.atZone(ZoneOffset.UTC));
        ledger.setOldDate(java.util.Date.from(at));
        ledger.setStamped(java.sql.Timestamp.from(at));
        ledger.setPlainDate(java.sql.Date.valueOf(LocalDate.of(2026, 5, k == 6 ? 5 : k)));
        em.persist(ledger);
        return ledger;
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static Map<String, List<String>> params(String... pairs) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            params.computeIfAbsent(pairs[i], key -> new ArrayList<>()).add(pairs[i + 1]);
        }
        return params;
    }

    private ListPage run(AdminEntity entity, String q, String sort, String dir, String page, String size, String... pairs) {
        FilterParse parsed = service.parseFilters(entity, params(pairs));
        return service.list(entity, service.parse(entity, q, sort, dir, page, size).withParsedFilters(parsed.filters()));
    }

    private ListPage run(String... pairs) {
        return run(PARCEL, null, null, null, null, "100", pairs);
    }

    private static List<Long> ids(ListPage page) {
        return page.rows().stream().map(row -> Long.valueOf(row.id())).toList();
    }

    private List<Long> expected(Predicate<Parcel> test) {
        return seeded.stream().filter(test).map(Parcel::getId).sorted().toList();
    }

    private List<Long> ledgerIds(int... ks) {
        List<Long> ids = new ArrayList<>();
        for (int k : ks) {
            ids.add(ledgers.get(k - 1).getId());
        }
        return ids.stream().sorted().toList();
    }

    private ListPage runLedger(String... pairs) {
        return run(LEDGER, null, null, null, null, "100", pairs);
    }

    private static int number(Parcel parcel) {
        return Integer.parseInt(parcel.getReference().substring(4));
    }

    // ---- each kind --------------------------------------------------------------------------------------------

    @Test
    void aTextFilterIsACaseInsensitiveContains() {
        assertThat(ids(run("filter.reference", "fil-01"))).isEqualTo(expected(p -> p.getReference().contains("FIL-01")));
        assertThat(ids(run("filter.notes", "care"))).isEqualTo(expected(p -> p.getNotes().equals("Handle with CARE")))
                .hasSize(8);
        assertThat(run("filter.reference", "zzz").total()).isZero();
    }

    @Test
    void aTextFilterTakesPercentUnderscoreAndTheEscapeCharacterLiterally() {
        assertThat(ids(run("filter.notes", "%"))).isEqualTo(expected(p -> p.getNotes().equals("100% sure")));
        assertThat(ids(run("filter.notes", "100%"))).hasSize(1);
        assertThat(ids(run("filter.notes", "_"))).isEqualTo(expected(p -> p.getNotes().equals("a_b")));
        assertThat(run("filter.notes", "p_ain").total()).as("an underscore is not 'any one character'").isZero();
        assertThat(run("filter.notes", "%%").total()).isZero();
        assertThat(run("filter.notes", "!").total()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "IN_TRANSIT", "DELIVERED", "LOST"})
    void anEnumFilterMatchesExactlyThatConstant(String constant) {
        assertThat(ids(run("filter.status", constant))).isEqualTo(expected(p -> p.getStatus().name().equals(constant)))
                .isNotEmpty();
    }

    @Test
    void aBooleanFilterSplitsTheRowsIntoTwoAndAnyIsAll() {
        assertThat(ids(run("filter.fragile", "true"))).isEqualTo(expected(Parcel::isFragile));
        assertThat(ids(run("filter.fragile", "false"))).isEqualTo(expected(p -> !p.isFragile()));
        assertThat(run("filter.fragile", "any").total()).isEqualTo(PARCELS);
    }

    @Test
    void aRelationFilterMatchesTheChosenRecordByItsIdentifier() {
        assertThat(ids(run("filter.depot", String.valueOf(northId)))).isEqualTo(expected(p -> number(p) % 2 == 0));
        assertThat(ids(run("filter.depot", String.valueOf(southId)))).isEqualTo(expected(p -> number(p) % 2 == 1));
        assertThat(ids(run("filter.courier", String.valueOf(falconId)))).isEqualTo(expected(p -> number(p) % 2 == 0));
    }

    @Test
    void aNumberRangeIsInclusiveAtBothEndsAndEitherBoundMayBeLeftOut() {
        assertThat(ids(run("filter.weight.min", "30", "filter.weight.max", "45")))
                .isEqualTo(expected(p -> p.getWeight().compareTo(new BigDecimal("30")) >= 0
                        && p.getWeight().compareTo(new BigDecimal("45")) <= 0)).hasSize(11);
        assertThat(ids(run("filter.weight.min", "58.5"))).isEqualTo(expected(p -> number(p) >= 39)).hasSize(2);
        assertThat(ids(run("filter.weight.max", "3"))).isEqualTo(expected(p -> number(p) <= 2));
        assertThat(ids(run("filter.weight.min", "4.5", "filter.weight.max", "4.5"))).as("equal bounds: an exact match")
                .isEqualTo(expected(p -> number(p) == 3));
    }

    @Test
    void aWholeNumberFieldRoundsAFractionalBoundInwards() {
        // pieces is i % 7, 0 to 6. min 2.5 means 3 and up, max 5.9 means 5 and down.
        assertThat(ids(run("filter.pieces.min", "2.5", "filter.pieces.max", "5.9")))
                .isEqualTo(expected(p -> p.getPieces() >= 3 && p.getPieces() <= 5));
        assertThat(ids(run("filter.pieces.min", "-3"))).hasSize(PARCELS);
    }

    @Test
    void aDateRangeOnADateIsInclusiveAtBothEnds() {
        LocalDate from = LocalDate.of(2026, 1, 10);
        LocalDate to = LocalDate.of(2026, 1, 20);

        assertThat(ids(run("filter.shippedOn.from", "2026-01-10", "filter.shippedOn.to", "2026-01-20")))
                .isEqualTo(expected(p -> !p.getShippedOn().isBefore(from) && !p.getShippedOn().isAfter(to))).hasSize(11);
        assertThat(ids(run("filter.shippedOn.from", "2026-02-05"))).isEqualTo(expected(p -> number(p) >= 35));
        assertThat(ids(run("filter.shippedOn.to", "2026-01-03"))).isEqualTo(expected(p -> number(p) <= 2));
    }

    @Test
    void aDateRangeOnADateTimeStartsAtTheFirstDaysMidnightAndIncludesTheWholeLastDay() {
        // createdAt is 2026-03-01T00:00 plus 6 hours per row: row 4 is exactly 2026-03-02T00:00, row 11 is 2026-03-03T18:00,
        // row 12 is exactly 2026-03-04T00:00 and must not be in a range that ends on the 3rd.
        assertThat(ids(run("filter.createdAt.from", "2026-03-02", "filter.createdAt.to", "2026-03-03")))
                .isEqualTo(expected(p -> number(p) >= 4 && number(p) <= 11)).hasSize(8);
        assertThat(ids(run("filter.createdAt.from", "2026-03-04"))).isEqualTo(expected(p -> number(p) >= 12));
    }

    @Test
    void everyNumberTypeIsComparedInItsOwnType() {
        assertThat(ids(runLedger("filter.amount.min", "200", "filter.amount.max", "400"))).isEqualTo(ledgerIds(2, 3, 4));
        assertThat(ids(runLedger("filter.level.min", "2.5"))).as("a short, rounded up").isEqualTo(ledgerIds(3, 4, 5));
        assertThat(ids(runLedger("filter.ratio.min", "3", "filter.ratio.max", "6"))).as("a double").isEqualTo(ledgerIds(2, 3, 4));
        assertThat(ids(runLedger("filter.tilt.min", "1", "filter.tilt.max", "2"))).as("a float").isEqualTo(ledgerIds(2, 3, 4));
        assertThat(ids(runLedger("filter.big.min", "100", "filter.big.max", "10000"))).as("a big integer")
                .isEqualTo(ledgerIds(2, 3, 4));
    }

    @ParameterizedTest
    @ValueSource(strings = {"seenAt", "openedAt", "closedAt", "oldDate", "stamped", "plainDate"})
    void everyDateTypeIsComparedInItsOwnTypeAndTheLastDayIsWhole(String key) {
        String from = "filter." + key + ".from";
        String to = "filter." + key + ".to";

        assertThat(ids(runLedger(from, "2026-05-02", to, "2026-05-04"))).as(key).isEqualTo(ledgerIds(2, 3, 4));
        assertThat(ids(runLedger(to, "2026-05-02"))).as(key + ": the whole last day, noon included").isEqualTo(ledgerIds(1, 2));
        assertThat(ids(runLedger(from, "2026-05-05"))).as(key + ": from the first moment of the day, midnight included")
                .isEqualTo(ledgerIds(5, 6));
        assertThat(ids(runLedger(to, "2026-05-04"))).as(key + ": the next day's midnight is not in the range")
                .isEqualTo(ledgerIds(1, 2, 3, 4));
    }

    // ---- combining --------------------------------------------------------------------------------------------

    @Test
    void filtersCombineWithEachOtherAsAnAnd() {
        List<Long> expected = expected(p -> p.getStatus() == ParcelStatus.PENDING && p.isFragile()
                && p.getWeight().compareTo(new BigDecimal("20")) >= 0);

        ListPage page = run("filter.status", "PENDING", "filter.fragile", "true", "filter.weight.min", "20");

        assertThat(ids(page)).isEqualTo(expected).isNotEmpty();
        assertThat(page.total()).isEqualTo(expected.size());
        assertThat(page.query().filters()).hasSize(3);
    }

    @Test
    void filtersCombineWithTheSearchTheSortAndThePaging() {
        List<Long> collected = new ArrayList<>();
        long total = -1;
        for (int page = 0; page < 3; page++) {
            ListPage listed = run(PARCEL, "FIL-", "weight", "desc", String.valueOf(page), "10", "filter.fragile", "false");
            total = listed.total();
            collected.addAll(ids(listed));
        }

        List<Long> expectedAll = seeded.stream().filter(p -> !p.isFragile())
                .sorted(java.util.Comparator.comparing(Parcel::getWeight).reversed()).map(Parcel::getId).toList();
        assertThat(total).isEqualTo(expectedAll.size());
        assertThat(collected).as("paged, sorted, no gap and no repeat").isEqualTo(expectedAll);
        assertThat(run(PARCEL, "FIL-00", null, null, null, "100", "filter.status", "DELIVERED").total())
                .as("the search narrows the filtered rows").isEqualTo(seeded.stream()
                        .filter(p -> p.getStatus() == ParcelStatus.DELIVERED && number(p) < 10).count());
    }

    @Test
    void theTotalAndTheRowsAgreeForEveryCombination() {
        String[][] sets = {{"filter.fragile", "true"}, {"filter.status", "LOST", "filter.depot", String.valueOf(northId)},
                {"filter.weight.min", "10", "filter.weight.max", "50", "filter.fragile", "false"},
                {"filter.createdAt.from", "2026-03-02", "filter.notes", "plain"}, {"filter.courier", String.valueOf(falconId)}};
        for (String[] set : sets) {
            long summed = 0;
            long total = 0;
            for (int page = 0; page < 6; page++) {
                ListPage listed = run(PARCEL, null, null, null, String.valueOf(page), "10", set);
                total = listed.total();
                if (listed.query().page() == page) {
                    summed += listed.rows().size();
                }
            }
            assertThat(summed).as(String.join(" ", set)).isEqualTo(total);
        }
    }

    @Test
    void aPagePastTheEndOfAFilteredListIsItsLastPage() {
        ListPage page = run(PARCEL, null, null, null, "99", "10", "filter.fragile", "true");

        assertThat(page.total()).isEqualTo(13);
        assertThat(page.query().page()).isEqualTo(1);
        assertThat(page.rows()).hasSize(3);
    }

    @Test
    void aFilterThatMatchesNothingGivesAnEmptyPageAndAZeroTotal() {
        ListPage page = run("filter.status", "LOST", "filter.weight.max", "1");

        assertThat(page.total()).isZero();
        assertThat(page.rows()).isEmpty();
        assertThat(page.query().filters()).hasSize(2);
    }

    // ---- what a filter may never touch -----------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"accessToken", "memo", "internalCode", "crate", "vault", "items", "picture", "dimensions", "id", "nosuch",
            "reference;drop table parcel"})
    void aFilterOnASensitiveHiddenOrUnknownFieldBuiltByHandIsDroppedAndNeverReachesTheDatabase(String key) {
        for (Kind kind : Kind.values()) {
            RecordingStatementInspector.clear();
            ActiveFilter filter = kind == Kind.NUMBER_RANGE ? ActiveFilter.range(key, kind, "1", "2")
                    : kind == Kind.DATE_RANGE ? ActiveFilter.range(key, kind, "2026-01-01", "2026-12-31")
                    : ActiveFilter.of(key, kind, kind == Kind.BOOLEAN ? "true" : kind == Kind.ENUM ? "LOST" : "1");

            ListPage page = service.list(PARCEL, ListQuery.defaults().withFilters(List.of(filter)).withSize(100));

            assertThat(page.query().filters()).as(key + " " + kind).isEmpty();
            assertThat(page.total()).as(key + " " + kind + ": every row, as if there were no filter").isEqualTo(PARCELS);
            assertThat(String.join("\n", RecordingStatementInspector.statements()).toLowerCase(Locale.ROOT))
                    .doesNotContain("access_token", "memo", "internal_code", "drop table");
        }
    }

    @Test
    void aFilterOfTheWrongKindForItsFieldIsDropped() {
        ListPage page = service.list(PARCEL, ListQuery.defaults().withFilters(List.of(
                ActiveFilter.of("reference", Kind.ENUM, "LOST"), ActiveFilter.range("status", Kind.NUMBER_RANGE, "1", "2"),
                ActiveFilter.of("fragile", Kind.TEXT, "x"))).withSize(100));

        assertThat(page.query().filters()).isEmpty();
        assertThat(page.total()).isEqualTo(PARCELS);
    }

    @Test
    void aRelationChoiceThatIsNotOneOfTheTargetsRecordsIsIgnoredAndSaid() {
        for (String choice : new String[] {"999999", "abc", "-1", "0", "1 or 1=1", String.valueOf(northId) + "0"}) {
            ListPage page = run("filter.depot", choice);

            assertThat(page.total()).as(choice).isEqualTo(PARCELS);
            assertThat(page.query().filters()).as(choice).isEmpty();
            assertThat(page.notes()).as(choice).containsExactly(
                    new FilterNote("The Depot filter was ignored: that choice is not available.", false));
        }
    }

    @Test
    void aRelationFilterOnATargetWithNoLabelOrAHiddenTargetDoesNotExist() {
        ListPage page = run("filter.crate", "1", "filter.vault", "1");

        assertThat(page.total()).isEqualTo(PARCELS);
        assertThat(page.controls().stream().map(FilterControl::key)).doesNotContain("crate", "vault");
    }

    // ---- the controls -----------------------------------------------------------------------------------------

    @Test
    void theControlsAreTheFilterableFieldsInColumnOrderWithTheirKinds() {
        ListPage page = run();

        assertThat(page.controls().stream().map(FilterControl::key)).containsExactly("courier", "createdAt", "depot", "fragile",
                "notes", "pieces", "reference", "shippedOn", "status", "weight");
        assertThat(page.controls()).filteredOn(c -> c.key().equals("status")).singleElement().satisfies(control -> {
            assertThat(control.kind()).isEqualTo(Kind.ENUM);
            assertThat(control.options().stream().map(o -> o.value() + "=" + o.label())).containsExactly("PENDING=Pending",
                    "IN_TRANSIT=In transit", "DELIVERED=Delivered", "LOST=Lost");
        });
        assertThat(page.controls()).filteredOn(c -> c.key().equals("fragile")).singleElement().satisfies(control ->
                assertThat(control.options().stream().map(o -> o.value() + "=" + o.label())).containsExactly("true=Yes", "false=No"));
    }

    @Test
    void aRelationControlListsTheTargetsLabelsInLabelOrderWithMissingLabelsLast() {
        ListPage page = run();

        FilterControl depot = page.controls().stream().filter(c -> c.key().equals("depot")).findFirst().orElseThrow();
        assertThat(depot.kind()).isEqualTo(Kind.RELATION);
        assertThat(depot.options()).hasSize(4);
        assertThat(depot.options().stream().map(o -> o.label())).startsWith("Alpha hub", "North hub", "South hub");
        assertThat(depot.options().get(3).label()).as("a depot with no name is shown by its identifier, last").matches("#\\d+");
        assertThat(depot.options().stream().map(o -> o.value())).contains(String.valueOf(northId), String.valueOf(southId));
        FilterControl courier = page.controls().stream().filter(c -> c.key().equals("courier")).findFirst().orElseThrow();
        assertThat(courier.options()).singleElement().satisfies(o -> {
            assertThat(o.label()).isEqualTo("Falcon");
            assertThat(o.value()).isEqualTo(String.valueOf(falconId));
        });
    }

    @Test
    void noControlEverListsASensitiveValueOrAHiddenEntitysRow() {
        String everything = String.valueOf(run().controls()) + String.valueOf(run("filter.depot", String.valueOf(northId)).controls());

        assertThat(everything).doesNotContain("Deep vault name", "DEPOT-KEY-SECRET", "BADGE-SECRET-1", "token-secret", "memo-secret",
                "internal-", "Leeds", "Bath");
    }

    @Test
    void aRelationControlsStatementReadsOnlyTheTargetsIdentifierAndLabel() {
        run();
        List<String> statements = RecordingStatementInspector.statements().stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();

        String depotChoices = statements.stream().filter(s -> s.contains(" from depot ")).findFirst().orElseThrow();
        assertThat(depotChoices).startsWith("select d1_0.id,d1_0.name from depot d1_0 order by").contains("fetch first ? rows only");
        String courierChoices = statements.stream().filter(s -> s.contains(" from courier ")).findFirst().orElseThrow();
        assertThat(courierChoices).startsWith("select c1_0.id,c1_0.callsign from courier c1_0");
        assertThat(String.join("\n", statements)).doesNotContain("api_key", "badge_number", "city", " from vault", " from crate");
    }

    // ---- what it costs ----------------------------------------------------------------------------------------

    @Test
    void filtersDoNotAddStatementsToTheCountAndThePageOnlyTheChoicesOfTheRelationFiltersDo() {
        run();
        int plain = RecordingStatementInspector.statements().size();
        RecordingStatementInspector.clear();

        ListPage page = run("filter.status", "PENDING", "filter.fragile", "true", "filter.weight.min", "1",
                "filter.depot", String.valueOf(northId), "filter.createdAt.from", "2026-03-01", "filter.notes", "plain");
        assertThat(page.total()).as("rows 12, 24 and 36").isEqualTo(3);

        assertThat(plain).as("2 for the parcels and one per relation filter").isEqualTo(4);
        assertThat(RecordingStatementInspector.statements()).hasSize(4);
        assertThat(RecordingStatementInspector.selects()).hasSize(4);
    }

    @Test
    void aRelationFilterAddsNoJoinToTheCountOrThePage() {
        run();
        List<String> plain = parcelSelects();
        RecordingStatementInspector.clear();

        run("filter.depot", String.valueOf(northId), "filter.courier", String.valueOf(falconId));
        List<String> filtered = parcelSelects();

        assertThat(joins(filtered.get(1))).as("the page keeps its four joins").isEqualTo(joins(plain.get(1))).isEqualTo(4);
        assertThat(joins(filtered.get(0))).as("the count has none, the foreign key column is compared").isZero();
        assertThat(filtered.get(0)).contains("depot_id=?", "courier_id=?");
    }

    @Test
    void everyFilterValueIsABoundParameterNeverPartOfTheSql() {
        String hostile = "x' or '1'='1";
        run("filter.reference", hostile, "filter.notes", "'; drop table parcel; --", "filter.status", "LOST",
                "filter.weight.min", "1.5", "filter.shippedOn.from", "2026-01-02", "filter.depot", String.valueOf(northId));

        assertThat(RecordingStatementInspector.statements()).isNotEmpty().allSatisfy(sql -> {
            String lower = sql.toLowerCase(Locale.ROOT);
            assertThat(lower).doesNotContain("1'='1", "drop table", "x' or", "2026-01-02", "1.5");
            assertThat(lower).doesNotContain("'lost'");
        });
        assertThat(ListTestPersistence.entityManager().createQuery("select count(p) from Parcel p", Long.class).getSingleResult())
                .isEqualTo((long) PARCELS);
    }

    @Test
    void theRowsAreDisplayValuesAfterTheSessionIsCleared() {
        ListPage page = run("filter.depot", String.valueOf(northId));
        entityManager.clear();

        assertThat(page.rows()).isNotEmpty().allSatisfy(row -> assertThat(String.valueOf(row.cells())).doesNotContain("Parcel@"));
    }

    // ---- the bound --------------------------------------------------------------------------------------------

    @Test
    void aTargetWithExactlyTheBoundOfRecordsStillGetsItsFilter() {
        addDepots(ListQueryService.MAX_RELATION_OPTIONS - 4);

        ListPage page = run();

        FilterControl depot = page.controls().stream().filter(c -> c.key().equals("depot")).findFirst().orElseThrow();
        assertThat(depot.options()).hasSize(ListQueryService.MAX_RELATION_OPTIONS);
        assertThat(page.notes()).isEmpty();
        assertThat(ids(run("filter.depot", String.valueOf(northId)))).isNotEmpty();
    }

    @Test
    void aTargetWithMoreThanTheBoundGetsNoFilterAndTheListIsNeverSilentlyCutShort() {
        addDepots(ListQueryService.MAX_RELATION_OPTIONS - 3);

        ListPage page = run("filter.depot", String.valueOf(northId), "filter.status", "LOST");

        assertThat(page.controls().stream().map(FilterControl::key)).as("no depot dropdown, no truncated one").doesNotContain("depot")
                .contains("courier", "status");
        assertThat(page.notes()).containsExactly(new FilterNote(
                "The Depot filter is not available: it would have more than 200 choices.", false));
        assertThat(page.query().filters().stream().map(ActiveFilter::key)).containsExactly("status");
        assertThat(page.total()).as("the depot filter was ignored, the status filter was not")
                .isEqualTo(expected(p -> p.getStatus() == ParcelStatus.LOST).size());
        assertThat(RecordingStatementInspector.statements().stream().map(s -> s.toLowerCase(Locale.ROOT)).filter(s -> s.contains(" from depot ")).findFirst().orElseThrow())
                .as("one more row than the bound is read to find out").contains("fetch first ? rows only");
    }

    private void addDepots(int count) {
        ListTestPersistence.inTransaction(em -> {
            for (int i = 0; i < count; i++) {
                depot(em, "Extra " + i, "Town");
            }
        });
        RecordingStatementInspector.clear();
    }

    private static List<String> parcelSelects() {
        return RecordingStatementInspector.selects().stream()
                .filter(sql -> sql.toLowerCase(Locale.ROOT).contains(" from parcel ")).map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    private static int joins(String sql) {
        int count = 0;
        for (int at = sql.indexOf(" join "); at >= 0; at = sql.indexOf(" join ", at + 1)) {
            count++;
        }
        return count;
    }

    @Test
    void theZonedAndOffsetTypesRoundTripWithoutTheServerTimeZoneMattering() {
        // The day boundaries are UTC, like the cells. A different default zone must not move a row across a day.
        java.util.TimeZone before = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Kiritimati")); // UTC+14
            assertThat(ids(runLedger("filter.closedAt.from", "2026-05-02", "filter.closedAt.to", "2026-05-04")))
                    .isEqualTo(ledgerIds(2, 3, 4));
            assertThat(ids(runLedger("filter.openedAt.to", "2026-05-04"))).isEqualTo(ledgerIds(1, 2, 3, 4));
        } finally {
            java.util.TimeZone.setDefault(before);
        }
    }
}
