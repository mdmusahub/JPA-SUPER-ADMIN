package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.Csv;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Courier;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Crate;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Dimensions;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Pass;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelItem;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Vault;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 11 against a real Hibernate and a real database: the file that comes out of {@link ExportService} for the neutral
 * list model, which has a field of every kind, a sensitive field by name and by annotation, a hidden field, a collection,
 * binary data, an embedded value and four relations. Like the list tests, these tests do not take the code's word for what is
 * selected: the SQL of every statement is recorded.
 */
class ExportServiceTest {

    private static final int PARCELS = 55;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC);
    private static final String SECRET = "hunter2-searchword";

    private static final AdminEntityRegistry REGISTRY = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
            DiscoverySettings.defaults());
    private static final AdminEntity PARCEL = REGISTRY.find("parcel").orElseThrow();

    private final List<Parcel> seeded = new ArrayList<>();
    private EntityManager entityManager;
    private Spy lists;

    /** The list's query service that remembers every page the export asks for. */
    private static class Spy extends ListQueryService {
        final List<int[]> pages = new ArrayList<>();

        Spy(EntityManager entityManager, AdminEntityRegistry registry) {
            super(entityManager, registry);
        }

        @Override
        public List<List<String>> exportRows(ExportPlan plan, int offset, int limit) {
            pages.add(new int[] {offset, limit});
            return super.exportRows(plan, offset, limit);
        }
    }

    @BeforeEach
    void seed() {
        ListTestPersistence.empty();
        seeded.clear();
        ListTestPersistence.inTransaction(em -> {
            Depot north = depot(em, "North hub", "Leeds");
            Depot south = depot(em, "South hub", "Bath");
            Courier courier = new Courier();
            courier.setBadgeNumber("BADGE-SECRET-1");
            courier.setCallSign("Falcon");
            em.persist(courier);
            Crate crate = new Crate();
            crate.setNumber(42);
            em.persist(crate);
            Vault vault = new Vault();
            vault.setName("Deep vault name");
            em.persist(vault);
            for (int i = 1; i <= PARCELS; i++) {
                Parcel parcel = new Parcel();
                parcel.setReference(reference(i));
                parcel.setNotes("Handle with care #" + i);
                parcel.setWeight(new BigDecimal("1.5").multiply(BigDecimal.valueOf(i)));
                parcel.setPieces(i % 7);
                parcel.setFragile(i % 3 == 0);
                parcel.setStatus(ParcelStatus.values()[i % 4]);
                parcel.setShippedOn(LocalDate.of(2026, 1, 1).plusDays((i * 5L) % 40));
                parcel.setCreatedAt(LocalDateTime.of(2026, 3, 1, 8, 0).plusMinutes(i * 37L % 500));
                parcel.setDepot(i % 2 == 0 ? north : south);
                parcel.setCourier(i % 2 == 0 ? courier : null);
                parcel.setCrate(i % 5 == 0 ? crate : null);
                parcel.setVault(i % 11 == 0 ? vault : null);
                parcel.setAccessToken(SECRET + "-" + i);
                parcel.setMemo("memo-secret-" + i);
                parcel.setInternalCode("internal-" + i);
                parcel.setPicture(new byte[] {1, 2, 3});
                Dimensions size = new Dimensions();
                size.setWidth(7_700_000 + i);
                size.setHeight(7_800_000 + i);
                parcel.setDimensions(size);
                special(parcel, i);
                em.persist(parcel);
                ParcelItem item = new ParcelItem();
                item.setDescription("item-secret-of-" + i);
                item.setParcel(parcel);
                em.persist(item);
                seeded.add(parcel);
            }
        });
        entityManager = ListTestPersistence.entityManager();
        lists = new Spy(entityManager, REGISTRY);
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

    /** Each of the six formula characters leads the reference of one parcel. */
    private static String reference(int i) {
        return switch (i) {
            case 1 -> "=1+1";
            case 2 -> "+49 30";
            case 3 -> "-dash";
            case 4 -> "@home";
            case 5 -> "\tTabbed";
            case 6 -> "\rReturned";
            default -> String.format("PKG-%03d", i);
        };
    }

    private static void special(Parcel parcel, int i) {
        switch (i) {
            case 1 -> parcel.setWeight(new BigDecimal("-2.50"));
            case 7 -> parcel.setNotes("a,b \"quoted\"\nsecond line");
            case 8 -> parcel.setNotes(null);
            case 9 -> parcel.setWeight(null);
            case 10 -> parcel.setStatus(null);
            case 12 -> parcel.setShippedOn(null);
            case 13 -> parcel.setNotes("Zürich café 東京");
            default -> { }
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------------

    private ExportService service(ExportSettings settings, int pageSize) {
        return ExportService.withPageSize(lists, REGISTRY, settings, CLOCK, pageSize);
    }

    private ExportService service() {
        return service(ExportSettings.defaults(), 7);
    }

    private ListQuery query(AdminEntity entity, String q, String sort, String dir, String... filterPairs) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        for (int i = 0; i < filterPairs.length; i += 2) {
            params.computeIfAbsent(filterPairs[i], key -> new ArrayList<>()).add(filterPairs[i + 1]);
        }
        return lists.parse(entity, q, sort, dir, null, null).withParsedFilters(lists.parseFilters(entity, params).filters());
    }

    private static byte[] bytes(ExportService.Export export) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Writer out = new OutputStreamWriter(bytes, StandardCharsets.UTF_8);
        export.writeTo(out);
        out.flush();
        return bytes.toByteArray();
    }

    private String file(ExportService service, AdminEntity entity, ListQuery query) throws IOException {
        return new String(bytes(service.open(entity, query)), StandardCharsets.UTF_8);
    }

    private String file(AdminEntity entity, ListQuery query) throws IOException {
        return file(service(), entity, query);
    }

    private List<List<String>> table(AdminEntity entity, ListQuery query) throws IOException {
        return Csv.parse(file(entity, query));
    }

    private List<List<String>> parcels() throws IOException {
        return table(PARCEL, ListQuery.defaults());
    }

    private static int column(List<List<String>> table, String header) {
        int at = table.get(0).indexOf(header);
        assertThat(at).as("a column headed '%s' in %s", header, table.get(0)).isGreaterThanOrEqualTo(0);
        return at;
    }

    private static List<String> values(List<List<String>> table, String header) {
        int at = column(table, header);
        return table.stream().skip(1).map(row -> row.get(at)).toList();
    }

    private static String normalised(String sql) {
        return sql.toLowerCase(Locale.ROOT).replace("_", "");
    }

    // ---- the header: the list's visible columns, in the list's order, without the masked ones -----------------------

    @Test
    void theHeaderIsTheListsColumnsInTheListsOrderWithoutTheMaskedOnes() throws IOException {
        List<String> expected = ListColumns.of(PARCEL, REGISTRY).stream().filter(column -> column.type() != Cell.Type.MASKED)
                .map(ListColumn::label).toList();

        List<List<String>> table = parcels();

        assertThat(table.get(0)).isEqualTo(expected);
        assertThat(table.get(0)).contains("ID", "Reference", "Notes", "Weight", "Status", "Depot", "Courier", "Crate", "Vault");
        assertThat(table).hasSize(1 + PARCELS);
        assertThat(table).allSatisfy(row -> assertThat(row).hasSameSizeAs(table.get(0)));
    }

    @Test
    void aSensitiveHiddenCollectionBinaryOrEmbeddedAttributeIsNotInTheHeader() throws IOException {
        String header = String.join("|", parcels().get(0)).toLowerCase(Locale.ROOT);

        assertThat(header).doesNotContain("token", "memo", "internal", "items", "picture", "dimensions", "width", "height");
    }

    @Test
    void neitherTheFileNorTheStatementsHoldASensitiveHiddenCollectionBinaryOrEmbeddedValue() throws IOException {
        String text = file(PARCEL, ListQuery.defaults());

        assertThat(text).doesNotContain(SECRET, "memo-secret", "internal-", "item-secret", "BADGE-SECRET", "DEPOT-KEY-SECRET",
                "Deep vault name");
        assertThat(text).as("the embedded width and height").doesNotContain("77000", "78000");
        String selected = RecordingStatementInspector.selects().stream().map(ExportServiceTest::normalised)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(selected).doesNotContain("accesstoken", "memo", "internalcode", "picture", "width", "height",
                "apikey", "badgenumber", "description");
    }

    // ---- values: every column kind ------------------------------------------------------------------------------------------

    @Test
    void everyKindOfValueIsWrittenInItsMachineReadableForm() throws IOException {
        List<List<String>> table = parcels();
        int row = values(table, "Reference").indexOf("PKG-020") + 1; // parcel 20: i % 2 == 0, i % 5 == 0, i % 4 == 0

        Parcel p = seeded.get(19);
        assertThat(table.get(row).get(column(table, "ID"))).isEqualTo(String.valueOf(p.getId()));
        assertThat(table.get(row).get(column(table, "Weight"))).as("plain decimal").isEqualTo("30.00");
        assertThat(table.get(row).get(column(table, "Pieces"))).isEqualTo("6");
        assertThat(table.get(row).get(column(table, "Fragile"))).as("boolean").isEqualTo("false");
        assertThat(table.get(row).get(column(table, "Status"))).as("enum constant, not the list's wording").isEqualTo("PENDING");
        assertThat(table.get(row).get(column(table, "Shipped on"))).as("ISO date").isEqualTo(p.getShippedOn().toString());
        assertThat(table.get(row).get(column(table, "Created at"))).as("ISO date-time, no zone, no conversion")
                .isEqualTo(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(p.getCreatedAt()));
    }

    @Test
    void anEnumIsTheConstantNameEvenWhenTheListHumanisesIt() throws IOException {
        assertThat(values(parcels(), "Status")).contains("IN_TRANSIT").doesNotContain("In transit");
    }

    @Test
    void aBooleanIsTrueOrFalse() throws IOException {
        assertThat(values(parcels(), "Fragile")).containsOnly("true", "false");
    }

    @Test
    void nullsAreEmptyCells() throws IOException {
        List<List<String>> table = parcels();

        assertThat(values(table, "Weight")).filteredOn(String::isEmpty).hasSize(1);
        assertThat(values(table, "Notes")).filteredOn(String::isEmpty).hasSize(1);
        assertThat(values(table, "Status")).filteredOn(String::isEmpty).hasSize(1);
        assertThat(values(table, "Shipped on")).filteredOn(String::isEmpty).hasSize(1);
        assertThat(values(table, "Courier")).filteredOn(String::isEmpty).hasSize(28)
                .as("the 28 odd-numbered parcels have no courier");
    }

    @Test
    void textIsCompleteAndSurvivesQuotingUnicodeAndLineBreaks() throws IOException {
        List<String> notes = values(parcels(), "Notes");

        assertThat(notes).contains("a,b \"quoted\"\nsecond line", "Zürich café 東京", "Handle with care #2");
    }

    // ---- relations -------------------------------------------------------------------------------------------------------------

    @Test
    void aRelationIsTheLabelAsTheListShowsItOrElseThePlainIdWithoutAHash() throws IOException {
        List<List<String>> table = parcels();
        Long crateId = seeded.get(4).getCrate().getId();
        Long vaultId = seeded.get(10).getVault().getId();

        assertThat(values(table, "Depot")).containsOnly("North hub", "South hub");
        assertThat(values(table, "Courier")).containsOnly("Falcon", "");
        assertThat(values(table, "Crate")).as("the target has no label: its id").containsOnly(String.valueOf(crateId), "");
        assertThat(values(table, "Vault")).as("the target is hidden: its id, never its name").containsOnly(String.valueOf(vaultId), "");
        assertThat(values(table, "Crate")).noneMatch(value -> value.startsWith("#"));
        assertThat(values(table, "Vault")).noneMatch(value -> value.startsWith("#"));
    }

    @Test
    void relationLabelsComeFromTheListsJoinNotFromAQueryPerRow() throws IOException {
        RecordingStatementInspector.clear();

        file(service(ExportSettings.defaults(), 100), PARCEL, ListQuery.defaults());

        // one count, one small statement for each of the two filterable relations (as on the list page), one page of 55 rows
        assertThat(RecordingStatementInspector.selects()).hasSize(1 + 2 + 1);
    }

    // ---- the formula guard, end to end (SEC-10) -------------------------------------------------------------------------

    @Test
    void eachOfTheSixLeadingCharactersIsGuardedInTheFile() throws IOException {
        List<String> references = values(parcels(), "Reference");

        assertThat(references).contains("'=1+1", "'+49 30", "'-dash", "'@home", "'\tTabbed", "'\rReturned");
        assertThat(references).noneMatch(value -> !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0);
    }

    @Test
    void aNegativeNumberArrivesAsGuardedText() throws IOException {
        assertThat(values(parcels(), "Weight")).contains("'-2.50");
    }

    // ---- search, filters and sort are the list's -----------------------------------------------------------------------

    @Test
    void theSearchIsRespected() throws IOException {
        List<List<String>> table = table(PARCEL, query(PARCEL, "pkg-02", null, null));

        assertThat(values(table, "Reference")).isNotEmpty().allSatisfy(reference -> assertThat(reference).startsWith("PKG-02"));
        assertThat(table.size() - 1).isEqualTo((int) seeded.stream().filter(p -> p.getReference().toLowerCase(Locale.ROOT)
                .contains("pkg-02") || String.valueOf(p.getNotes()).toLowerCase(Locale.ROOT).contains("pkg-02")).count());
    }

    @Test
    void aSearchThatMatchesNothingGivesTheHeaderAndNoRows() throws IOException {
        List<List<String>> table = table(PARCEL, query(PARCEL, "no-such-text-anywhere", null, null));

        assertThat(table).hasSize(1);
        assertThat(table.get(0)).contains("Reference");
    }

    @Test
    void aFilterIsRespectedAndCombinesWithTheSearch() throws IOException {
        List<List<String>> filtered = table(PARCEL, query(PARCEL, null, null, null, "filter.status", "DELIVERED"));
        List<List<String>> both = table(PARCEL, query(PARCEL, "care", null, null, "filter.status", "DELIVERED",
                "filter.fragile", "true"));

        assertThat(values(filtered, "Status")).isNotEmpty().containsOnly("DELIVERED");
        assertThat(filtered.size() - 1).isEqualTo((int) seeded.stream().filter(p -> p.getStatus() == ParcelStatus.DELIVERED).count());
        assertThat(values(both, "Status")).containsOnly("DELIVERED");
        assertThat(values(both, "Fragile")).containsOnly("true");
    }

    @Test
    void aRelationFilterIsRespected() throws IOException {
        Long south = seeded.get(0).getDepot().getId();
        List<List<String>> table = table(PARCEL, query(PARCEL, null, null, null, "filter.depot", String.valueOf(south)));

        assertThat(values(table, "Depot")).isNotEmpty().containsOnly("South hub");
    }

    @Test
    void theSortIsRespectedInBothDirectionsWithTheIdAsTheTieBreaker() throws IOException {
        List<String> ascending = values(table(PARCEL, query(PARCEL, null, "pieces", "asc")), "Pieces");
        List<String> descending = values(table(PARCEL, query(PARCEL, null, "pieces", "desc")), "Pieces");

        assertThat(ascending.stream().map(Integer::valueOf).toList()).isSorted();
        assertThat(descending.stream().map(Integer::valueOf).toList()).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        List<List<String>> byDepot = table(PARCEL, query(PARCEL, null, "depot", "asc"));
        assertThat(values(byDepot, "Depot")).isSorted();
    }

    @Test
    void nullsComeLastInBothDirectionsAsOnTheList() throws IOException {
        assertThat(values(table(PARCEL, query(PARCEL, null, "weight", "asc")), "Weight")).last().isEqualTo("");
        assertThat(values(table(PARCEL, query(PARCEL, null, "weight", "desc")), "Weight")).last().isEqualTo("");
    }

    @Test
    void anUnknownHiddenOrSensitiveSortKeyIsIgnoredLikeOnTheList() throws IOException {
        List<String> unsorted = values(parcels(), "ID");

        for (String key : new String[] {"nope", "accessToken", "memo", "internalCode", "picture", "dimensions", "items"}) {
            assertThat(values(table(PARCEL, query(PARCEL, null, key, "desc")), "ID")).as(key).isEqualTo(unsorted);
        }
    }

    @Test
    void aQueryBuiltByHandWithASensitiveSortOrAnUnknownFilterNeverReachesTheDatabase() throws IOException {
        ListQuery bad = new ListQuery("", "accessToken", "desc", 0, 25);

        assertThat(lists.exportPlan(PARCEL, bad).query().sort()).isNull();
        assertThat(file(PARCEL, bad)).doesNotContain(SECRET);
        assertThat(RecordingStatementInspector.selects().stream().map(ExportServiceTest::normalised))
                .noneMatch(sql -> sql.contains("accesstoken"));
    }

    @Test
    void unknownHiddenAndSensitiveFilterNamesAreIgnored() throws IOException {
        ListQuery query = query(PARCEL, null, null, null, "filter.nope", "x", "filter.accessToken", SECRET,
                "filter.internalCode", "internal-1", "filter.memo", "memo", "filter.items", "1");

        assertThat(query.filters()).isEmpty();
        assertThat(table(PARCEL, query)).hasSize(1 + PARCELS);
    }

    @Test
    void thePageAndSizeOfTheListQueryMeanNothingToAnExport() throws IOException {
        ListQuery paged = lists.parse(PARCEL, null, null, null, "3", "10");

        assertThat(table(PARCEL, paged)).hasSize(1 + PARCELS);
    }

    // ---- memory bound and cost ------------------------------------------------------------------------------------------

    @Test
    void aTableLargerThanOnePageIsReadInPagesOfTheConfiguredSize() throws IOException {
        List<List<String>> table = table(PARCEL, ListQuery.defaults());

        assertThat(table).hasSize(1 + PARCELS);
        assertThat(lists.pages).extracting(page -> page[0]).containsExactly(0, 7, 14, 21, 28, 35, 42, 49);
        assertThat(lists.pages).extracting(page -> page[1]).containsExactly(7, 7, 7, 7, 7, 7, 7, 6);
        assertThat(values(table, "ID").stream().map(Long::valueOf).toList()).as("no row twice, none missing, in order")
                .isEqualTo(seeded.stream().map(Parcel::getId).sorted().toList());
    }

    @Test
    void everyPageStatementIsCutWithAnOffsetAndALimit() throws IOException {
        file(PARCEL, ListQuery.defaults());

        List<String> selects = RecordingStatementInspector.selects().stream().map(sql -> sql.toLowerCase(Locale.ROOT)).toList();
        List<String> pages = selects.stream().filter(sql -> !sql.startsWith("select count")).toList();
        assertThat(pages).isNotEmpty().allSatisfy(sql -> assertThat(sql).containsAnyOf("fetch first", "limit"));
    }

    @Test
    void theNumberOfStatementsFollowsThePagesNotTheRowsOrTheRelationColumns() throws IOException {
        file(PARCEL, ListQuery.defaults());

        // 1 count + 2 relation choice lists (as on the list page) + ceil(55 / 7) = 8 pages. Four relation columns, no query per row.
        assertThat(RecordingStatementInspector.selects()).hasSize(1 + 2 + 8);
        assertThat(RecordingStatementInspector.statements()).as("only selects").hasSize(11);
    }

    @Test
    void noEntityAndNoCollectionIsEverLoaded() throws IOException {
        file(PARCEL, ListQuery.defaults());

        assertThat(entityManager.unwrap(Session.class).getStatistics().getEntityCount()).isZero();
        assertThat(entityManager.unwrap(Session.class).getStatistics().getCollectionCount()).isZero();
    }

    @Test
    void anEmptyTableGivesTheHeaderOnlyAndReadsNoPage() throws IOException {
        ListTestPersistence.empty();
        lists.pages.clear();

        List<List<String>> table = parcels();

        assertThat(table).hasSize(1);
        assertThat(lists.pages).isEmpty();
    }

    @Test
    void aTableThatShrinksWhileTheFileIsWrittenEndsWithoutAnErrorOrAnEndlessLoop() throws IOException {
        ExportService.Export export = service().open(PARCEL, ListQuery.defaults());
        ListTestPersistence.inTransaction(em -> em.createQuery("delete from ParcelItem").executeUpdate());
        ListTestPersistence.inTransaction(em -> em.createQuery("delete from Parcel").executeUpdate());

        List<List<String>> table = Csv.parse(new String(bytes(export), StandardCharsets.UTF_8));

        assertThat(table.size()).isEqualTo(1 + 7); // the first page was read before; the rest is gone
    }

    // ---- the cap -------------------------------------------------------------------------------------------------------------

    @Test
    void aLoweredCapCutsTheFileAndSaysSo() throws IOException {
        ExportService capped = service(new ExportSettings(true, 10, false), 7);

        ExportService.Export export = capped.open(PARCEL, ListQuery.defaults());
        List<List<String>> table = Csv.parse(new String(bytes(export), StandardCharsets.UTF_8));

        assertThat(table).hasSize(1 + 10);
        assertThat(export.truncated()).isTrue();
        assertThat(export.rowLimit()).isEqualTo(10);
        assertThat(export.matching()).isEqualTo(PARCELS);
        assertThat(export.rows()).isEqualTo(10);
        assertThat(lists.pages).extracting(page -> page[1]).containsExactly(7, 3);
    }

    @Test
    void theCutKeepsTheListsOrderSoItIsTheFirstRowsOfTheList() throws IOException {
        ExportService capped = service(new ExportSettings(true, 5, false), 7);

        List<List<String>> table = Csv.parse(file(capped, PARCEL, query(PARCEL, null, "pieces", "desc")));

        assertThat(values(table, "Pieces")).hasSize(5).containsOnly("6");
    }

    @Test
    void aCapEqualToTheMatchingRowsIsNotTruncated() throws IOException {
        ExportService.Export exact = service(new ExportSettings(true, PARCELS, false), 7).open(PARCEL, ListQuery.defaults());
        ExportService.Export oneLess = service(new ExportSettings(true, PARCELS - 1, false), 7).open(PARCEL, ListQuery.defaults());

        assertThat(exact.truncated()).isFalse();
        assertThat(exact.rows()).isEqualTo(PARCELS);
        assertThat(oneLess.truncated()).isTrue();
        assertThat(oneLess.rows()).isEqualTo(PARCELS - 1);
    }

    @Test
    void theCapAppliesToTheMatchingRowsAfterTheSearchAndFilters() throws IOException {
        ListQuery delivered = query(PARCEL, null, null, null, "filter.status", "DELIVERED");
        long matching = seeded.stream().filter(p -> p.getStatus() == ParcelStatus.DELIVERED).count();

        ExportService.Export export = service(new ExportSettings(true, (int) matching, false), 7).open(PARCEL, delivered);

        assertThat(export.matching()).isEqualTo(matching);
        assertThat(export.truncated()).as("everything that matches fits").isFalse();
    }

    @Test
    void aSettingAboveTheCeilingStillCutsAtTheCeiling() {
        assertThat(service(new ExportSettings(true, 1_000_000, false), 7).cap()).isEqualTo(50_000);
        assertThat(service(new ExportSettings(true, 123, false), 7).cap()).isEqualTo(123);
    }

    // ---- the byte order mark, the file name ---------------------------------------------------------------------------

    @Test
    void thereIsNoByteOrderMarkByDefault() throws IOException {
        byte[] bytes = bytes(service().open(PARCEL, ListQuery.defaults()));

        assertThat(bytes[0]).isEqualTo((byte) 'I');
        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotStartWith("﻿");
    }

    @Test
    void theByteOrderMarkIsEfBbBfWhenTheSettingAsksForIt() throws IOException {
        byte[] bytes = bytes(service(new ExportSettings(true, 100, true), 7).open(PARCEL, ListQuery.defaults()));

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(bytes, 3, 2, StandardCharsets.UTF_8)).isEqualTo("ID");
    }

    @Test
    void theFileNameIsTheEntityKeyAndTheClocksDateAndNothingElse() {
        String name = service().open(PARCEL, query(PARCEL, "anything the visitor typed; rm -rf /", null, null)).fileName();

        assertThat(name).isEqualTo("parcel-2026-11-19.csv");
        assertThat(name).matches(Pattern.compile("[a-z0-9-]+-\\d{4}-\\d{2}-\\d{2}\\.csv"));
        assertThat(name.chars()).allMatch(c -> c < 128);
    }

    @Test
    void theFileNameFollowsTheInjectedClock() {
        Clock later = Clock.fixed(Instant.parse("2027-01-02T23:59:59Z"), ZoneOffset.UTC);

        String name = ExportService.withPageSize(lists, REGISTRY, ExportSettings.defaults(), later, 7)
                .open(PARCEL, ListQuery.defaults()).fileName();

        assertThat(name).isEqualTo("parcel-2027-01-02.csv");
    }

    // ---- SEC-11: the switches are enforced in the service -----------------------------------------------------------

    @Test
    void anEntityThatIsNotTheRegistrysIsTheSameAsAnUnknownOne() {
        ExportService empty = ExportService.withPageSize(lists, AdminEntityRegistry.empty(), ExportSettings.defaults(), CLOCK, 7);

        assertThatThrownBy(() -> empty.open(PARCEL, ListQuery.defaults())).isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void anEntityMarkedNotExportableIsRefusedEvenThoughItIsInTheConsole() {
        AdminEntity shielded = REGISTRY.find("shielded").orElseThrow();

        assertThat(service().offers(shielded)).isFalse();
        assertThatThrownBy(() -> service().open(shielded, ListQuery.defaults())).isInstanceOf(ActionRefusedException.class);
    }

    @Test
    void aRefusedExportNeverReachesTheDatabase() {
        AdminEntity shielded = REGISTRY.find("shielded").orElseThrow();
        RecordingStatementInspector.clear();

        assertThatThrownBy(() -> service().open(shielded, ListQuery.defaults())).isInstanceOf(ActionRefusedException.class);
        assertThatThrownBy(() -> service(ExportSettings.off(), 7).open(PARCEL, ListQuery.defaults()))
                .isInstanceOf(ActionRefusedException.class);

        assertThat(RecordingStatementInspector.statements()).isEmpty();
    }

    @Test
    void theGlobalSwitchRefusesEveryKnownEntity() {
        ExportService off = service(new ExportSettings(false, 50_000, false), 7);

        assertThat(off.offers(PARCEL)).isFalse();
        assertThatThrownBy(() -> off.open(PARCEL, ListQuery.defaults())).isInstanceOf(ActionRefusedException.class);
    }

    @Test
    void anUnknownEntityIsStillNotFoundWhenTheGlobalSwitchIsOff() {
        ExportService off = ExportService.withPageSize(lists, AdminEntityRegistry.empty(), ExportSettings.off(), CLOCK, 7);

        assertThatThrownBy(() -> off.open(PARCEL, ListQuery.defaults())).isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void aReadOnlyEntityCanBeExported() {
        AdminEntity archive = REGISTRY.find("archive").orElseThrow();

        assertThat(service().offers(archive)).isTrue();
    }

    @Test
    void theNoneServiceOffersNothingAndRefusesEverything() {
        ExportService none = ExportService.none();

        assertThat(none.offers(PARCEL)).isFalse();
        assertThatThrownBy(() -> none.open(PARCEL, ListQuery.defaults())).isInstanceOf(RecordNotFoundException.class);
    }

    // ---- an identifier that is a sensitive field ---------------------------------------------------------------------------

    @Test
    void aSensitiveIdentifierIsNeverSelectedNorWritten() throws IOException {
        ListTestPersistence.inTransaction(em -> {
            Pass pass = new Pass();
            pass.setAccessToken(987654321L);
            pass.setHolder("alice");
            em.persist(pass);
        });
        AdminEntity entity = REGISTRY.find("pass").orElseThrow();
        RecordingStatementInspector.clear();

        String text = file(entity, ListQuery.defaults());

        assertThat(Csv.parse(text)).containsExactly(List.of("Holder"), List.of("alice"));
        assertThat(text).doesNotContain("987654321");
        String selectList = normalised(RecordingStatementInspector.selects().get(RecordingStatementInspector.selects().size() - 1));
        assertThat(selectList.substring(0, selectList.indexOf(" from "))).doesNotContain("accesstoken");
    }

    // ---- hidden columns (Phase 12, D-113): the export follows the list's visible columns -------------------------------------

    private static ListQuery hiding(String... keys) {
        return new ListQuery("", null, null, 0, 25, List.of(), List.of(keys));
    }

    /** The headers of the columns the list shows for {@code query}: not masked, not hidden, in list order. */
    private List<String> listedHeaders(ListQuery query) {
        ListPage page = lists.list(PARCEL, query);
        return page.columns().stream()
                .filter(column -> column.type() != Cell.Type.MASKED && !page.query().hidden().contains(column.key()))
                .map(ListColumn::label).toList();
    }

    @Test
    void theFilesHeaderIsExactlyTheColumnsTheListShowsWhenSomeAreHidden() throws IOException {
        ListQuery query = hiding("weight", "depot", "notes");

        List<List<String>> table = table(PARCEL, query);

        assertThat(table.get(0)).containsExactlyElementsOf(listedHeaders(query));
        assertThat(table.get(0)).doesNotContain("Weight", "Depot", "Notes").contains("Reference", "ID", "Courier", "Status");
        assertThat(table).as("the same records, fewer columns").hasSize(1 + PARCELS);
        assertThat(table).allSatisfy(row -> assertThat(row).hasSameSizeAs(table.get(0)));
    }

    @Test
    void theRowsOfTheFileAreTheRowsOfTheListWithoutTheHiddenColumns() throws IOException {
        ListQuery query = hiding("weight", "status", "createdAt");
        List<List<String>> plain = table(PARCEL, hiding());

        List<List<String>> hidden = table(PARCEL, query);

        assertThat(hidden.get(0)).hasSize(plain.get(0).size() - 3);
        for (String header : hidden.get(0)) {
            assertThat(values(hidden, header)).as("the %s column is unchanged", header).isEqualTo(values(plain, header));
        }
    }

    @Test
    void aHiddenColumnIsNeitherSelectedNorJoinedByTheExport() throws IOException {
        file(PARCEL, hiding("weight", "notes", "depot", "courier"));
        String page = normalised(RecordingStatementInspector.selects().get(RecordingStatementInspector.selects().size() - 1));
        String selectList = page.substring(0, page.indexOf(" from "));

        assertThat(selectList).doesNotContain(".weight", ".notes", "callsign", ".name");
        assertThat(selectList).contains(".reference");
        assertThat(page.split(" left join ").length - 1).as("only crate and vault are still joined").isEqualTo(2);
    }

    @Test
    void aHiddenValueIsNotInTheFile() throws IOException {
        String text = file(PARCEL, hiding("notes", "depot", "reference"));

        assertThat(text).doesNotContain("Handle with care", "North hub", "South hub", "PKG-0");
    }

    @Test
    void theIdentifierCanBeHiddenAndTheFileLosesItsIdColumn() throws IOException {
        List<List<String>> table = table(PARCEL, hiding("id"));

        assertThat(table.get(0)).doesNotContain("ID").contains("Reference");
        assertThat(table).hasSize(1 + PARCELS);
        assertThat(lists.exportPlan(PARCEL, hiding("id")).columns()).extracting(ListColumn::key).doesNotContain("id");
    }

    @Test
    void aSensitiveNameBuiltIntoTheQueryChangesNothingAndStaysOutOfTheFile() throws IOException {
        String plain = file(PARCEL, hiding());

        String text = file(PARCEL, hiding("accessToken", "memo", "internalCode", "nothing"));

        assertThat(text).isEqualTo(plain);
        assertThat(text).doesNotContain(SECRET, "memo-secret", "internal-");
    }

    @Test
    void allButOneColumnHiddenLeavesAOneColumnFile() throws IOException {
        List<String> all = new ArrayList<>(List.of("id", "reference", "notes", "weight", "pieces", "fragile", "status",
                "shippedOn", "createdAt", "depot", "courier", "crate", "vault"));
        all.remove("pieces");

        List<List<String>> table = table(PARCEL, hiding(all.toArray(new String[0])));

        assertThat(table.get(0)).containsExactly("Pieces");
        assertThat(table).hasSize(1 + PARCELS);
    }

    @Test
    void aQueryThatHidesEveryColumnIsRefusedByTheSecondCheckAndTheFileHasEveryColumn() throws IOException {
        List<List<String>> plain = table(PARCEL, hiding());

        List<List<String>> table = table(PARCEL, hiding("id", "reference", "notes", "weight", "pieces", "fragile", "status",
                "shippedOn", "createdAt", "depot", "courier", "crate", "vault"));

        assertThat(table.get(0)).isEqualTo(plain.get(0));
    }

    @Test
    void hidingColumnsKeepsTheSearchTheFiltersAndTheSortOfTheFile() throws IOException {
        ListQuery query = lists.parse(PARCEL, "care #1", "pieces", "desc", null, null).withParsedHidden(List.of("pieces"));

        List<List<String>> table = table(PARCEL, query);

        assertThat(table.get(0)).doesNotContain("Pieces");
        assertThat(values(table, "Reference")).as("parcels 1 and 10 to 19 match, except 13 whose notes were replaced").hasSize(10);
        assertThat(values(table, "Reference")).contains("PKG-010");
    }

    // ---- failure ----------------------------------------------------------------------------------------------------------------

    @Test
    void aFailureReadingTheFirstPageHappensInOpenBeforeAnyByteIsWritten() {
        ListQueryService broken = new Spy(entityManager, REGISTRY) {
            @Override
            public List<List<String>> exportRows(ExportPlan plan, int offset, int limit) {
                throw new IllegalStateException("database is down " + SECRET);
            }
        };

        assertThatThrownBy(() -> ExportService.withPageSize(broken, REGISTRY, ExportSettings.defaults(), CLOCK, 7)
                .open(PARCEL, ListQuery.defaults())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFailureAfterTheFirstPageIsRethrownSoTheFileIsNeverTakenForWhole() throws IOException {
        ListQueryService failing = new Spy(entityManager, REGISTRY) {
            @Override
            public List<List<String>> exportRows(ExportPlan plan, int offset, int limit) {
                if (offset > 0) {
                    throw new IllegalStateException("database is down " + SECRET);
                }
                return super.exportRows(plan, offset, limit);
            }
        };
        ExportService.Export export = ExportService.withPageSize(failing, REGISTRY, ExportSettings.defaults(), CLOCK, 7)
                .open(PARCEL, ListQuery.defaults());
        StringBuilder written = new StringBuilder();
        Writer out = new Writer() {
            @Override
            public void write(char[] buffer, int offset, int length) {
                written.append(buffer, offset, length);
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        assertThatThrownBy(() -> export.writeTo(out)).isInstanceOf(IllegalStateException.class);
        assertThat(Csv.parse(written.toString())).as("the header and the first page").hasSize(1 + 7);
    }
}
