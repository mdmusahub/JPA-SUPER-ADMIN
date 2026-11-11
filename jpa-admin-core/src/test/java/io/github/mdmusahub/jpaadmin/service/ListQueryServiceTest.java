package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Courier;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Crate;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Dimensions;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelItem;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Stamp;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Vault;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Phase 4 against a real Hibernate and a real database: one page of rows, the total, the paging state, the cells,
 * the search, the sort, and what the SQL really selects. The neutral {@code listmodel} entities carry every case: a
 * sensitive field by name and by annotation, a hidden field, a collection, binary data, an embedded value and four
 * relations with four different labels.
 *
 * <p>The SQL of every query is recorded, so these tests do not take the code's word for what it selects: a sensitive
 * column is shown to be absent from the statement, the number of statements is counted, and the search text is shown
 * to be a bound parameter and not part of the SQL.
 */
class ListQueryServiceTest {

    /** 55 parcels: pages of 25 are 25, 25 and 5, and pages of 10 end with a partial page of 5. */
    private static final int PARCELS = 55;
    private static final String SECRET_WORD = "hunter2-searchword";

    private static final AdminEntityRegistry REGISTRY = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
            DiscoverySettings.defaults());
    private static final AdminEntity PARCEL = REGISTRY.find("parcel").orElseThrow();

    private final List<Parcel> seeded = new ArrayList<>();
    private EntityManager entityManager;
    private ListQueryService service;

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
                parcel.setReference(String.format("PKG-%03d", i));
                parcel.setNotes(notes(i));
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
                parcel.setAccessToken(SECRET_WORD + "-" + i);
                parcel.setMemo("memo-secret-" + i);
                parcel.setInternalCode("internal-" + i);
                parcel.setPicture(new byte[] {1, 2, 3});
                Dimensions size = new Dimensions();
                size.setWidth(i);
                size.setHeight(2 * i);
                parcel.setDimensions(size);
                em.persist(parcel);
                ParcelItem item = new ParcelItem();
                item.setDescription("item of " + i);
                item.setParcel(parcel);
                em.persist(item);
                seeded.add(parcel);
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

    /** Special texts at fixed rows: syntax characters, unicode, a long text with line breaks. */
    private static String notes(int i) {
        return switch (i) {
            case 7 -> "see http://x for details";
            case 8 -> "100% sure";
            case 9 -> "a_b";
            case 11 -> "it's \"quoted\"";
            case 12 -> "wow! really";
            case 13 -> "C:\\temp\\file";
            case 14 -> "Zürich café 東京";
            case 15 -> "line one\nline two\tTabbed";
            case 20 -> "LongText " + "word ".repeat(80) + "end";
            case 21 -> "x".repeat(200) + "UNBROKEN";
            default -> "Handle with care #" + i;
        };
    }

    private ListPage list(ListQuery query) {
        return service.list(PARCEL, query);
    }

    private ListPage list(String q, String sort, String dir, String page, String size) {
        return list(ListQuery.parse(PARCEL, q, sort, dir, page, size));
    }

    private static List<Long> ids(ListPage page) {
        return page.rows().stream().map(row -> Long.valueOf(row.id())).toList();
    }

    private static Cell cell(ListPage page, ListView.Row row, String key) {
        for (int i = 0; i < page.columns().size(); i++) {
            if (page.columns().get(i).key().equals(key)) {
                return row.cells().get(i);
            }
        }
        throw new AssertionError("no column " + key);
    }

    private Cell cellOfParcel(int number, String key) {
        ListPage page = list(null, null, null, null, "100");
        return cell(page, page.rows().get(number - 1), key);
    }

    // ---- paging -----------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {10, 25, 50, 100})
    void everyPageSizeReturnsThatManyRowsOrFewerAndTheRightTotal(int size) {
        ListPage page = list(null, null, null, null, String.valueOf(size));

        assertThat(page.rows()).hasSize(Math.min(size, PARCELS));
        assertThat(page.total()).isEqualTo(PARCELS);
        assertThat(page.query().size()).isEqualTo(size);
        assertThat(page.query().page()).isZero();
    }

    @Test
    void thePagesFollowEachOtherWithoutGapOrOverlapAndTheLastOneIsPartial() {
        List<Long> all = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            ListPage listed = list(null, null, null, String.valueOf(page), "25");
            all.addAll(ids(listed));
            assertThat(listed.query().page()).isEqualTo(page);
        }

        assertThat(list(null, null, null, "2", "25").rows()).as("the last page holds what is left").hasSize(5);
        assertThat(all).hasSize(PARCELS).doesNotHaveDuplicates().isSorted();
    }

    @Test
    void thePagesOfTenEndWithAPageOfFive() {
        assertThat(list(null, null, null, "4", "10").rows()).as("page 4 is the fifth full page").hasSize(10);
        ListPage last = list(null, null, null, "5", "10");

        assertThat(last.rows()).hasSize(5);
        assertThat(last.query().page()).isEqualTo(5);
        assertThat(list(null, null, null, "6", "10").rows()).as("page 6 does not exist, so it is page 5").hasSize(5);
        assertThat(list(null, null, null, "6", "10").query().page()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"99", "3", "1000000", "2147483647", "99999999999999999999", "1234567890123456789"})
    void aPagePastTheEndIsTheLastPageAndNotAnError(String requested) {
        ListPage page = list(null, null, null, requested, "25");

        assertThat(page.query().page()).as("clamped to the last page").isEqualTo(2);
        assertThat(page.rows()).hasSize(5);
        assertThat(page.total()).isEqualTo(PARCELS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "abc", "", " ", "1.5", "NaN", "0x1", "1e3", "-99999999999999999999", "%00", "¹"})
    void aPageThatIsNotAWholeNumberIsTheFirstPage(String requested) {
        ListPage page = list(null, null, null, requested, "25");

        assertThat(page.query().page()).isZero();
        assertThat(page.rows()).hasSize(25);
    }

    @Test
    void theRowsAreInIdentifierOrderWhenNothingIsSorted() {
        ListPage page = list(null, null, null, null, "100");

        assertThat(ids(page)).isSorted().hasSize(PARCELS);
        assertThat(page.query().sort()).isNull();
    }

    @Test
    void anEntityWithNoRecordsHasAnEmptyFirstPageAndATotalOfZero() {
        ListPage page = service.list(REGISTRY.find("stamp").orElseThrow(),
                ListQuery.parse(REGISTRY.find("stamp").orElseThrow(), null, null, null, "7", "10"));

        assertThat(page.total()).isZero();
        assertThat(page.rows()).isEmpty();
        assertThat(page.query().page()).as("a page past an empty list is the first page").isZero();
        assertThat(page.columns()).isNotEmpty();
    }

    @Test
    void anEntityWithATextIdentifierShowsItAsTheRowId() {
        ListTestPersistence.inTransaction(em -> {
            Stamp stamp = new Stamp();
            stamp.setCode("A-1/x y");
            stamp.setCaption("first");
            em.persist(stamp);
        });
        AdminEntity stamps = REGISTRY.find("stamp").orElseThrow();

        ListPage page = service.list(stamps, ListQuery.defaults());

        assertThat(page.rows()).singleElement().satisfies(row -> assertThat(row.id()).isEqualTo("A-1/x y"));
        assertThat(service.list(stamps, ListQuery.parse(stamps, "a-1", null, null, null, null)).total())
                .as("a text identifier is searchable").isEqualTo(1);
    }

    @Test
    void theTotalCountsEveryMatchingRecordNotOnlyThoseOnThePage() {
        assertThat(list("PKG-00", null, null, null, "10").total()).isEqualTo(9);
        assertThat(list("PKG-00", null, null, null, "10").rows()).hasSize(9);
        assertThat(list("PKG-", null, null, null, "10").total()).isEqualTo(PARCELS);
        assertThat(list("PKG-", null, null, null, "10").rows()).hasSize(10);
    }

    @Test
    void aSearchThatMatchesNothingGivesAnEmptyPageAndAZeroTotal() {
        ListPage page = list("nothing like this", null, null, "3", "25");

        assertThat(page.total()).isZero();
        assertThat(page.rows()).isEmpty();
        assertThat(page.query().search()).isEqualTo("nothing like this");
    }

    // ---- columns and cells ------------------------------------------------------------------------------------

    @Test
    void theColumnsAreTheVisibleOnesAndEveryRowHasOneCellPerColumn() {
        ListPage page = list(null, null, null, null, "10");

        assertThat(page.columns().stream().map(ListColumn::key)).doesNotContain("internalCode", "items", "picture",
                "dimensions");
        assertThat(page.rows()).allSatisfy(row -> assertThat(row.cells()).hasSameSizeAs(page.columns()));
    }

    @Test
    void valuesAreFormattedAsDisplayTextNotAsJavaObjects() {
        // Parcel 4: status 4 % 4 = 0 -> PENDING; 4 % 3 != 0 -> not fragile; weight 6 (the column keeps two decimals); pieces 4.
        assertThat(cellOfParcel(4, "reference")).isEqualTo(Cell.text("PKG-004"));
        assertThat(cellOfParcel(4, "weight")).isEqualTo(new Cell(Cell.Type.NUMBER, "6.00", Cell.Tone.NONE, null, null));
        assertThat(cellOfParcel(4, "pieces")).isEqualTo(new Cell(Cell.Type.NUMBER, "4", Cell.Tone.NONE, null, null));
        assertThat(cellOfParcel(4, "fragile")).isEqualTo(new Cell(Cell.Type.BOOLEAN, "No", Cell.Tone.NONE, null, null));
        assertThat(cellOfParcel(6, "fragile")).isEqualTo(new Cell(Cell.Type.BOOLEAN, "Yes", Cell.Tone.GREEN, null, null));
        assertThat(cellOfParcel(4, "status")).isEqualTo(new Cell(Cell.Type.ENUM, "Pending", Cell.Tone.AMBER, null, null));
        assertThat(cellOfParcel(5, "status")).isEqualTo(new Cell(Cell.Type.ENUM, "In transit", Cell.Tone.GREY, null, null));
        assertThat(cellOfParcel(6, "status")).isEqualTo(new Cell(Cell.Type.ENUM, "Delivered", Cell.Tone.GREEN, null, null));
        assertThat(cellOfParcel(7, "status")).isEqualTo(new Cell(Cell.Type.ENUM, "Lost", Cell.Tone.GREY, null, null));
        assertThat(cellOfParcel(1, "shippedOn")).isEqualTo(new Cell(Cell.Type.DATE, "2026-01-06", Cell.Tone.NONE, null, null));
        assertThat(cellOfParcel(1, "createdAt").display()).matches("2026-03-01 \\d\\d:\\d\\d");
        assertThat(cellOfParcel(1, "createdAt").title()).startsWith("2026-03-01T");
        assertThat(cellOfParcel(4, "id").display()).isEqualTo(String.valueOf(list(null, null, null, null, "100").rows().get(3).id()));
    }

    @Test
    void aLongTextIsCutOnOneLineAndTheFullTextIsTheHoverText() {
        Cell cut = cellOfParcel(20, "notes");

        assertThat(cut.type()).isEqualTo(Cell.Type.TEXT);
        assertThat(cut.display()).hasSize(CellFormatter.MAX_DISPLAY + 1).endsWith("…").startsWith("LongText word word");
        assertThat(cut.title()).startsWith("LongText word word").endsWith("end").doesNotContain("\n");
    }

    @Test
    void aLongWordWithoutSpacesIsCutToo() {
        Cell cut = cellOfParcel(21, "notes");

        assertThat(cut.display()).hasSize(CellFormatter.MAX_DISPLAY + 1);
        assertThat(cut.title()).endsWith("UNBROKEN");
    }

    @Test
    void lineBreaksAndTabsInATextBecomeSpacesSoARowStaysOnOneLine() {
        assertThat(cellOfParcel(15, "notes").display()).isEqualTo("line one line two Tabbed");
    }

    @Test
    void unicodeTextIsShownAsItIs() {
        assertThat(cellOfParcel(14, "notes").display()).isEqualTo("Zürich café 東京");
    }

    @Test
    void aMissingValueIsAnEmptyCell() {
        ListTestPersistence.inTransaction(em -> {
            Parcel bare = new Parcel();
            em.persist(bare);
        });
        ListPage page = list(null, null, null, null, "100");
        ListView.Row bare = page.rows().get(page.rows().size() - 1);

        assertThat(cell(page, bare, "reference")).isEqualTo(Cell.empty());
        assertThat(cell(page, bare, "status")).isEqualTo(Cell.empty());
        assertThat(cell(page, bare, "shippedOn")).isEqualTo(Cell.empty());
        assertThat(cell(page, bare, "depot")).isEqualTo(Cell.empty());
        assertThat(cell(page, bare, "accessToken")).as("a masked cell does not say whether there is a value")
                .isEqualTo(Cell.masked());
    }

    // ---- relations --------------------------------------------------------------------------------------------

    @Test
    void aRelationShowsTheLabelOfItsTarget() {
        // Parcel 2 is even: depot "North hub", courier with call sign "Falcon", no crate; parcel 5: crate.
        Cell depot = cellOfParcel(2, "depot");

        assertThat(depot.type()).isEqualTo(Cell.Type.RELATION);
        assertThat(depot.display()).isEqualTo("North hub");
        assertThat(depot.href()).as("no link until a detail page exists (Phase 7)").isNull();
        assertThat(cellOfParcel(1, "depot").display()).isEqualTo("South hub");
    }

    @Test
    void theLabelIsTheFirstTextAttributeThatIsNotSensitiveWhenNoPreferredNameExists() {
        assertThat(cellOfParcel(2, "courier").display()).isEqualTo("Falcon");
        assertThat(cellOfParcel(1, "courier")).isEqualTo(Cell.empty());
    }

    @Test
    void aTargetWithoutTextShowsItsIdentifier() {
        Cell crate = cellOfParcel(5, "crate");

        assertThat(crate.type()).isEqualTo(Cell.Type.RELATION);
        assertThat(crate.display()).startsWith("#").matches("#\\d+");
    }

    @Test
    void aTargetHiddenFromTheConsoleShowsOnlyItsIdentifierNeverItsName() {
        Cell vault = cellOfParcel(11, "vault");
        ListPage page = list(null, null, null, null, "100");

        assertThat(vault.display()).matches("#\\d+");
        assertThat(String.valueOf(page.rows())).doesNotContain("Deep vault name");
        assertThat(RecordingStatementInspector.selects()).noneMatch(sql -> sql.toLowerCase(Locale.ROOT).contains("deep vault"));
    }

    @Test
    void aSensitiveAttributeOfATargetIsNeverTheLabelNorSelected() {
        list(null, null, null, null, "100");
        String sql = String.join("\n", RecordingStatementInspector.selects()).toLowerCase(Locale.ROOT);

        assertThat(sql).doesNotContain("apikey").doesNotContain("badgenumber");
        assertThat(String.valueOf(list(null, null, null, null, "100").rows())).doesNotContain("DEPOT-KEY-SECRET",
                "BADGE-SECRET-1");
    }

    @Test
    void aPageCostsExactlyTwoQueriesWhateverTheRowsAndRelationColumns() {
        list(null, null, null, null, "100");

        assertThat(RecordingStatementInspector.statements()).as("a count and a page, nothing else (ARCH-6)").hasSize(2);
        assertThat(RecordingStatementInspector.selects()).hasSize(2);
        RecordingStatementInspector.clear();

        list("pkg", "weight", "desc", "1", "10");

        assertThat(RecordingStatementInspector.statements()).hasSize(2);
    }

    @Test
    void anEmptyListCostsOnlyTheCount() {
        service.list(REGISTRY.find("stamp").orElseThrow(), ListQuery.defaults());

        assertThat(RecordingStatementInspector.statements()).hasSize(1);
    }

    @Test
    void aCollectionIsNeitherReadNorShown() {
        ListPage page = list(null, null, null, null, "100");
        String sql = String.join("\n", RecordingStatementInspector.statements()).toLowerCase(Locale.ROOT);

        assertThat(page.columns().stream().map(ListColumn::key)).doesNotContain("items");
        assertThat(sql).doesNotContain("parcelitem");
        assertThat(String.valueOf(page.rows())).doesNotContain("item of ");
    }

    // ---- sensitive and hidden ---------------------------------------------------------------------------------

    @Test
    void aSensitiveColumnIsMaskedInEveryRowWithNoValue() {
        ListPage page = list(null, null, null, null, "100");

        for (String key : List.of("accessToken", "memo")) {
            assertThat(page.columns()).anySatisfy(column -> {
                assertThat(column.key()).isEqualTo(key);
                assertThat(column.type()).isEqualTo(Cell.Type.MASKED);
            });
            assertThat(page.rows()).allSatisfy(row -> assertThat(cell(page, row, key)).isEqualTo(Cell.masked()));
        }
        assertThat(String.valueOf(page)).doesNotContain(SECRET_WORD, "memo-secret", "internal-");
    }

    @Test
    void aSensitiveHiddenOrBinaryAttributeIsNotInTheSqlThatSelectsRows() {
        list(null, null, null, null, "100");
        String sql = String.join("\n", RecordingStatementInspector.statements()).toLowerCase(Locale.ROOT);

        assertThat(sql).as("sensitive by name").doesNotContain("accesstoken")
                .as("sensitive by annotation").doesNotContain("memo")
                .as("hidden").doesNotContain("internalcode")
                .as("binary").doesNotContain("picture")
                .as("embedded").doesNotContain("width").doesNotContain("height")
                .as("the columns that are shown are selected").contains("reference").contains("weight");
    }

    // ---- search -----------------------------------------------------------------------------------------------

    @Test
    void theSearchIgnoresCase() {
        for (String text : List.of("pkg-007", "PKG-007", "Pkg-007", "  pkg-007  ")) {
            ListPage page = list(text, null, null, null, null);

            assertThat(page.total()).as(text).isEqualTo(1);
            assertThat(cell(page, page.rows().get(0), "reference").display()).isEqualTo("PKG-007");
        }
    }

    @Test
    void theSearchMatchesAnywhereInAnyTextAttribute() {
        assertThat(list("care #3", null, null, null, "100").total()).as("in the notes").isGreaterThan(5);
        assertThat(list("G-05", null, null, null, "100").total()).as("inside the reference: 050 to 055").isEqualTo(6);
    }

    @Test
    void aSensitiveValueCanNeverBeFoundBySearch() {
        // Every access token holds this word; if the field were searched, all 55 parcels would match.
        for (String text : List.of("hunter2", SECRET_WORD, SECRET_WORD + "-7", "memo-secret", "memo-secret-3")) {
            assertThat(list(text, null, null, null, "100").total()).as(text).isZero();
        }
        assertThat(RecordingStatementInspector.statements()).allSatisfy(sql ->
                assertThat(sql.toLowerCase(Locale.ROOT)).doesNotContain("accesstoken").doesNotContain("memo"));
    }

    @Test
    void aHiddenFieldIsNotSearched() {
        assertThat(list("internal-", null, null, null, "100").total()).isZero();
        assertThat(list("internal-12", null, null, null, "100").total()).isZero();
    }

    @Test
    void relationsNumbersAndOtherKindsAreNotSearched() {
        assertThat(list("North hub", null, null, null, "100").total()).as("a relation's label").isZero();
        assertThat(list("Falcon", null, null, null, "100").total()).isZero();
        assertThat(list("7.5", null, null, null, "100").total()).as("a number").isZero();
        assertThat(list("delivered", null, null, null, "100").total()).as("an enum").isZero();
        assertThat(list("2026-01", null, null, null, "100").total()).as("a date").isZero();
    }

    @Test
    void anEntityWithNothingSearchableMatchesNothingInsteadOfEverything() {
        ListTestPersistence.inTransaction(em -> {
            Crate crate = new Crate();
            crate.setNumber(7);
            em.persist(crate);
        });
        AdminEntity crates = REGISTRY.find("crate").orElseThrow();

        assertThat(service.list(crates, ListQuery.parse(crates, "7", null, null, null, null)).total()).isZero();
        assertThat(service.list(crates, ListQuery.parse(crates, null, null, null, null, null)).total()).isPositive();
    }

    @Test
    void textThatLooksLikeSyntaxIsMatchedLiterally() {
        assertThat(ids(list("http://x", null, null, null, null))).containsExactly(idOfParcel(7));
        assertThat(ids(list("100%", null, null, null, null))).as("a percent sign is not a wildcard")
                .containsExactly(idOfParcel(8));
        assertThat(ids(list("%", null, null, null, null))).containsExactly(idOfParcel(8));
        assertThat(ids(list("a_b", null, null, null, null))).as("an underscore is not a wildcard")
                .containsExactly(idOfParcel(9));
        assertThat(ids(list("_", null, null, null, null))).containsExactly(idOfParcel(9));
        assertThat(ids(list("it's", null, null, null, null))).containsExactly(idOfParcel(11));
        assertThat(ids(list("\"quoted\"", null, null, null, null))).containsExactly(idOfParcel(11));
        assertThat(ids(list("wow!", null, null, null, null))).as("the escape character is literal too")
                .containsExactly(idOfParcel(12));
        assertThat(ids(list("!", null, null, null, null))).containsExactly(idOfParcel(12));
        assertThat(ids(list("C:\\temp", null, null, null, null))).containsExactly(idOfParcel(13));
        assertThat(ids(list("\\", null, null, null, null))).containsExactly(idOfParcel(13));
        assertThat(ids(list("ZÜRICH", null, null, null, null))).as("unicode, any case").containsExactly(idOfParcel(14));
        assertThat(ids(list("東京", null, null, null, null))).containsExactly(idOfParcel(14));
    }

    @Test
    void aWildcardLookalikeMatchesOnlyWhatItLiterallySpells() {
        assertThat(list("%%", null, null, null, null).total()).isZero();
        assertThat(list("__", null, null, null, null).total()).isZero();
        assertThat(list("_%", null, null, null, null).total()).isZero();
        assertThat(list("PKG-0_1", null, null, null, null).total()).isZero();
        assertThat(list("PKG%007", null, null, null, null).total()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"x' or '1'='1", "'; drop table parcel; --", "\" or 1=1 --", "1' union select * from depot --",
            "%' or '%'='", "x') or ('1'='1", "\\' or 1=1"})
    void anInjectionAttemptFindsNothingAndNeverReachesTheSql(String attempt) {
        ListPage page = list(attempt, null, null, null, "100");

        assertThat(page.total()).isZero();
        assertThat(page.rows()).isEmpty();
        assertThat(RecordingStatementInspector.statements()).as("the text is bound, so it is not in any statement")
                .allSatisfy(sql -> assertThat(sql).doesNotContain(attempt).doesNotContain("1'='1").doesNotContain("drop table")
                        .contains("?"));
        assertThat(ListTestPersistence.entityManager().createQuery("select count(p) from Parcel p", Long.class)
                .getSingleResult()).isEqualTo(PARCELS);
    }

    @Test
    void aVeryLongSearchIsCutAndStillWorks() {
        ListPage page = list("PKG-007" + "x".repeat(5000), null, null, null, null);

        assertThat(page.query().search()).hasSize(ListQuery.MAX_SEARCH_LENGTH);
        assertThat(page.total()).as("the cut text no longer matches").isZero();
        assertThat(list("pkg-007" + " ".repeat(5000) + "tail", null, null, null, null).query().search())
                .as("white space does not push the real text out").hasSizeLessThanOrEqualTo(ListQuery.MAX_SEARCH_LENGTH);
    }

    @Test
    void aLongSearchThatStartsWithARealWordFindsIt() {
        ListPage page = list("PKG-007" + " ".repeat(40), null, null, null, null);

        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void aSearchSurvivesPagingAndSorting() {
        ListPage second = list("PKG-00", "weight", "desc", "1", "10");

        assertThat(second.total()).isEqualTo(9);
        assertThat(second.query().page()).as("only one page of 9, so page 1 is clamped to 0").isZero();
        assertThat(ids(second)).hasSize(9);
    }

    // ---- sort -------------------------------------------------------------------------------------------------

    private static Comparator<Parcel> byIdThen(Comparator<Parcel> primary) {
        return primary.thenComparing(parcel -> parcel.getId());
    }

    private List<Long> expected(Comparator<Parcel> comparator) {
        return seeded.stream().sorted(comparator).map(Parcel::getId).toList();
    }

    private List<Long> sortedIds(String key, String dir) {
        List<Long> all = new ArrayList<>();
        for (int page = 0; page < 6; page++) {
            all.addAll(ids(list(null, key, dir, String.valueOf(page), "10")));
        }
        return all;
    }

    @Test
    void everySortableColumnSortsAscendingAndDescendingWithTheIdentifierAsTieBreaker() {
        record Case(String key, Comparator<Parcel> ascending) {
        }
        List<Case> cases = List.of(
                new Case("reference", Comparator.comparing(Parcel::getReference)),
                new Case("notes", Comparator.comparing(Parcel::getNotes)),
                new Case("weight", Comparator.comparing(Parcel::getWeight)),
                new Case("pieces", Comparator.comparingInt(Parcel::getPieces)),
                new Case("fragile", Comparator.comparing(Parcel::isFragile)),
                new Case("status", Comparator.comparing(parcel -> parcel.getStatus().name())),
                new Case("shippedOn", Comparator.comparing(Parcel::getShippedOn)),
                new Case("createdAt", Comparator.comparing(Parcel::getCreatedAt)));

        for (Case sort : cases) {
            assertThat(sortedIds(sort.key(), "asc")).as(sort.key() + " asc")
                    .isEqualTo(expected(byIdThen(sort.ascending())));
            // Descending reverses the column, but equal values still come in identifier order.
            assertThat(sortedIds(sort.key(), "desc")).as(sort.key() + " desc")
                    .isEqualTo(expected(sort.ascending().reversed().thenComparing(Parcel::getId)));
        }
    }

    @Test
    void theIdentifierColumnSortsBothWays() {
        assertThat(sortedIds("id", "asc")).isEqualTo(expected(Comparator.comparing(Parcel::getId)));
        assertThat(sortedIds("id", "desc")).isEqualTo(expected(Comparator.comparing(Parcel::getId).reversed()));
    }

    @Test
    void sortingByAColumnWithManyEqualValuesPagesWithoutRepeatsOrGaps() {
        // 55 parcels share only seven values of "pieces": without the tie-breaker the database may order equal rows
        // differently on each page, which repeats some rows and drops others.
        List<Long> pages = new ArrayList<>();
        for (int page = 0; page < 6; page++) {
            pages.addAll(ids(list(null, "pieces", "asc", String.valueOf(page), "10")));
        }

        assertThat(pages).hasSize(PARCELS).doesNotHaveDuplicates();
        assertThat(pages).isEqualTo(expected(byIdThen(Comparator.comparingInt(Parcel::getPieces))));
    }

    @Test
    void theSqlOrdersBySortColumnThenByIdentifier() {
        list(null, "pieces", "desc", null, null);
        String page = RecordingStatementInspector.selects().get(1).toLowerCase(Locale.ROOT);

        // Hibernate writes the order as positions in the select list; position 1 is the identifier.
        assertThat(page).contains("order by " + selectPosition(page, ".pieces") + " desc,1 ");
        RecordingStatementInspector.clear();

        list(null, "pieces", "asc", null, null);
        assertThat(RecordingStatementInspector.selects().get(1).toLowerCase(Locale.ROOT))
                .contains("order by " + selectPosition(RecordingStatementInspector.selects().get(1).toLowerCase(Locale.ROOT),
                        ".pieces") + ",1 ");
        RecordingStatementInspector.clear();

        list(null, null, null, null, null);
        assertThat(RecordingStatementInspector.selects().get(1).toLowerCase(Locale.ROOT)).contains("order by 1 ")
                .doesNotContain("order by 1,");
    }

    /** The 1-based position of the select item that ends with {@code suffix}. */
    private static int selectPosition(String sql, String suffix) {
        String items = sql.substring("select ".length(), sql.indexOf(" from "));
        String[] columns = items.split(",");
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].endsWith(suffix)) {
                return i + 1;
            }
        }
        throw new AssertionError("no select item " + suffix + " in " + sql);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nosuch", "accessToken", "memo", "internalCode", "depot", "items", "picture", "dimensions",
            "reference;drop table parcel", "' or 1=1 --"})
    void anUnsortableKeyIsIgnoredAndTheListIsInIdentifierOrder(String key) {
        ListPage page = list(null, key, "desc", null, "100");

        assertThat(page.query().sort()).isNull();
        assertThat(ids(page)).isSorted();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accessToken", "memo", "internalCode", "depot", "items", "picture", "nosuch"})
    void theServiceDropsAnUnsortableKeyEvenWhenTheQueryWasNotBuiltByTheParser(String key) {
        // The record can be constructed directly; the service must not trust it (SEC-2).
        ListPage page = list(new ListQuery("", key, "asc", 0, 100));

        assertThat(page.query().sort()).isNull();
        assertThat(ids(page)).isSorted();
        assertThat(RecordingStatementInspector.selects().get(1).toLowerCase(Locale.ROOT)).doesNotContain("order by "
                + "p1_0." + key.toLowerCase(Locale.ROOT));
    }

    @Test
    void anInvalidDirectionIsAscendingAndOneWithoutAKeyIsDropped() {
        assertThat(list(null, "pieces", "sideways", null, null).query().dir()).isEqualTo("asc");
        assertThat(list(null, null, "desc", null, null).query().dir()).isNull();
    }

    @Test
    void sortingAndSearchingTogetherSortsOnlyTheMatches() {
        ListPage page = list("PKG-00", "weight", "desc", null, null);

        assertThat(page.total()).isEqualTo(9);
        assertThat(page.rows().stream().map(row -> cell(page, row, "weight").display()).map(BigDecimal::new))
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    // ---- misc -------------------------------------------------------------------------------------------------

    @Test
    void aServiceWithoutAnEntityManagerRefusesInsteadOfGuessing() {
        assertThatThrownBy(() -> ListQueryService.unavailable().list(PARCEL, ListQuery.defaults()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theRowsOfAPageAreDisplayValuesOnly() {
        ListPage page = list(null, null, null, null, "10");

        for (ListView.Row row : page.rows()) {
            assertThat(row.id()).isInstanceOf(String.class);
            assertThat(row.cells()).allSatisfy(cell -> assertThat(cell).isInstanceOf(Cell.class));
        }
        assertThat(String.valueOf(page)).doesNotContain("testsupport.listmodel");
    }

    private Long idOfParcel(int number) {
        return seeded.get(number - 1).getId();
    }
}
