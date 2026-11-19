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
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 12 against a real Hibernate and a real database: a hidden column is neither selected nor joined (D-113), its
 * cells are empty, sort, filter and search keep working on it, and the second check refuses a sensitive name or the
 * last column even when the query was not built by the parser. The SQL is recorded, so the tests do not take the code's
 * word for what is selected.
 */
class ListQueryServiceHiddenTest {

    private static final int PARCELS = 30;
    private static final AdminEntityRegistry REGISTRY = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
            DiscoverySettings.defaults());
    private static final AdminEntity PARCEL = REGISTRY.find("parcel").orElseThrow();
    private static final List<String> EVERY_COLUMN = List.of("id", "reference", "notes", "weight", "pieces", "fragile",
            "status", "shippedOn", "createdAt", "depot", "courier", "crate", "vault");

    private EntityManager entityManager;
    private ListQueryService service;

    @BeforeEach
    void seed() {
        ListTestPersistence.empty();
        ListTestPersistence.inTransaction(em -> {
            Depot north = depot(em, "North hub");
            Depot south = depot(em, "South hub");
            Courier courier = new Courier();
            courier.setBadgeNumber("BADGE-SECRET-1");
            courier.setCallSign("Falcon");
            em.persist(courier);
            Crate crate = new Crate();
            crate.setNumber(42);
            em.persist(crate);
            for (int i = 1; i <= PARCELS; i++) {
                Parcel parcel = new Parcel();
                parcel.setReference(String.format("PKG-%03d", i));
                parcel.setNotes("Handle with care #" + i);
                parcel.setWeight(new BigDecimal("1.5").multiply(BigDecimal.valueOf(i)));
                parcel.setPieces(i % 7);
                parcel.setStatus(ParcelStatus.values()[i % 4]);
                parcel.setDepot(i % 2 == 0 ? north : south);
                parcel.setCourier(i % 2 == 0 ? courier : null);
                parcel.setCrate(i % 5 == 0 ? crate : null);
                parcel.setAccessToken("token-secret-" + i);
                parcel.setMemo("memo-secret-" + i);
                parcel.setInternalCode("internal-" + i);
                em.persist(parcel);
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

    private static Depot depot(EntityManager em, String name) {
        Depot depot = new Depot();
        depot.setName(name);
        depot.setCity("Leeds");
        depot.setApiKey("DEPOT-KEY-SECRET");
        em.persist(depot);
        return depot;
    }

    private ListPage list(ListQuery query) {
        return service.list(PARCEL, query);
    }

    private static ListQuery hiding(String... keys) {
        return new ListQuery("", null, null, 0, 100, List.of(), List.of(keys));
    }

    private static ListQuery sortedHiding(String sort, String dir, String... keys) {
        return new ListQuery("", sort, dir, 0, 100, List.of(), List.of(keys));
    }

    private static int index(ListPage page, String key) {
        for (int i = 0; i < page.columns().size(); i++) {
            if (page.columns().get(i).key().equals(key)) {
                return i;
            }
        }
        throw new AssertionError("no column " + key);
    }

    private static List<String> parcelSelects() {
        return RecordingStatementInspector.statements().stream()
                .filter(sql -> sql.toLowerCase(Locale.ROOT).contains(" from parcel "))
                .filter(sql -> sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")).toList();
    }

    /** The select list of the page query (the second parcel select; the first is the count). */
    private static String selectList() {
        String page = parcelSelects().get(1).toLowerCase(Locale.ROOT);
        return page.substring(0, page.indexOf(" from "));
    }

    private static String pageSql() {
        return parcelSelects().get(1).toLowerCase(Locale.ROOT);
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            n++;
        }
        return n;
    }

    // ---- what is selected -------------------------------------------------------------------------------------

    @Test
    void aHiddenColumnIsNotInTheSelectList() {
        list(hiding("notes", "weight", "pieces"));

        assertThat(selectList()).doesNotContain(".notes").doesNotContain(".weight").doesNotContain(".pieces");
        assertThat(selectList()).as("the others are still selected").contains(".reference").contains(".status");
    }

    @Test
    void withNothingHiddenEveryColumnIsSelectedAsBefore() {
        list(hiding());

        assertThat(selectList()).contains(".notes").contains(".weight").contains(".pieces").contains(".reference");
    }

    @Test
    void theCellsOfAHiddenColumnAreEmptyAndEveryOtherCellIsUnchanged() {
        ListPage plain = list(hiding());
        ListPage hidden = list(hiding("notes", "depot"));

        assertThat(hidden.rows()).hasSameSizeAs(plain.rows());
        int notes = index(hidden, "notes");
        int depot = index(hidden, "depot");
        for (int r = 0; r < plain.rows().size(); r++) {
            assertThat(hidden.rows().get(r).id()).isEqualTo(plain.rows().get(r).id());
            for (int c = 0; c < hidden.columns().size(); c++) {
                Cell expected = c == notes || c == depot ? Cell.empty() : plain.rows().get(r).cells().get(c);
                assertThat(hidden.rows().get(r).cells().get(c)).as("row %d column %d", r, c).isEqualTo(expected);
            }
        }
    }

    @Test
    void aHiddenValueNeverReachesThePage() {
        ListPage page = list(hiding("notes", "depot", "reference"));

        assertThat(String.valueOf(page.rows())).doesNotContain("Handle with care").doesNotContain("North hub")
                .doesNotContain("South hub").doesNotContain("PKG-0");
    }

    @Test
    void theHiddenColumnsStayInThePagesColumnListSoTheTemplateCanSkipThem() {
        ListPage page = list(hiding("notes"));

        assertThat(page.columns()).extracting(ListColumn::key).contains("notes");
        assertThat(page.query().hidden()).containsExactly("notes");
    }

    // ---- joins ------------------------------------------------------------------------------------------------

    @Test
    void aPageWithNoHiddenColumnJoinsOncePerRelationColumn() {
        list(hiding());

        assertThat(count(pageSql(), " left join ")).as("depot, courier, crate and vault").isEqualTo(4);
    }

    @Test
    void hidingRelationColumnsRemovesTheirJoins() {
        list(hiding("depot", "courier"));
        assertThat(count(pageSql(), " left join ")).isEqualTo(2);
        RecordingStatementInspector.clear();

        list(hiding("depot", "courier", "crate", "vault"));
        assertThat(count(pageSql(), " left join ")).as("no relation column is shown, so none is joined").isZero();
        assertThat(selectList()).doesNotContain("callsign").doesNotContain(".name").doesNotContain(".number");
    }

    @Test
    void theCountNeverHadAJoinAndStillHasNone() {
        list(hiding("depot"));

        assertThat(parcelSelects().get(0).toLowerCase(Locale.ROOT)).doesNotContain(" join ");
    }

    @Test
    void aHiddenRelationColumnHasNoRelationIdSoNothingLinksToItsTarget() {
        ListPage page = list(hiding("depot", "courier"));
        int depot = index(page, "depot");
        int crate = index(page, "crate");

        assertThat(page.relationIds()).isNotEmpty().allSatisfy(ids -> assertThat(ids.get(depot)).isNull());
        assertThat(page.relationIds()).as("a shown relation keeps its id").anySatisfy(ids -> assertThat(ids.get(crate)).isNotNull());
    }

    @Test
    void hidingColumnsCostsNoExtraStatement() {
        list(hiding("notes", "depot", "courier"));

        assertThat(parcelSelects()).as("a count and a page").hasSize(2);
        assertThat(RecordingStatementInspector.statements()).as("and the choices of the two relation filters, as before")
                .hasSize(4);
    }

    // ---- sort, filter and search stay conditions ---------------------------------------------------------------

    @Test
    void aHiddenColumnThatIsTheSortKeyStillSortsAndIsOnlyInTheOrderBy() {
        ListPage ascending = list(sortedHiding("weight", "asc", "weight"));

        assertThat(ascending.rows().stream().map(row -> Integer.valueOf(row.id())).toList()).isSorted();
        assertThat(selectList()).as("sorted by but not shown, so not selected").doesNotContain(".weight");
        assertThat(pageSql()).as("the order by names it").contains("order by").containsPattern("order by[^,]*weight");
        RecordingStatementInspector.clear();

        ListPage descending = list(sortedHiding("weight", "desc", "weight"));

        assertThat(descending.rows().get(0).id()).isEqualTo(ascending.rows().get(PARCELS - 1).id());
        assertThat(descending.query().sort()).isEqualTo("weight");
    }

    @Test
    void aHiddenRelationColumnThatIsTheSortKeyKeepsItsJoinForTheOrderByOnly() {
        list(hiding());
        int allJoins = count(pageSql(), " left join ");
        RecordingStatementInspector.clear();

        list(sortedHiding(null, null, "depot"));
        assertThat(count(pageSql(), " left join ")).as("unsorted: the hidden depot is not joined").isEqualTo(allJoins - 1);
        RecordingStatementInspector.clear();

        ListPage sorted = list(sortedHiding("depot", "asc", "depot"));

        assertThat(count(pageSql(), " left join ")).as("sorted by it: its join stays for the order by").isEqualTo(allJoins);
        assertThat(selectList()).as("but nothing of it is selected").doesNotContain(".name");
        List<Integer> ids = sorted.rows().stream().map(row -> Integer.valueOf(row.id())).toList();
        assertThat(ids.subList(0, PARCELS / 2)).as("the north hub (even parcels) first, in id order").allMatch(id -> id % 2 == 0)
                .isSorted();
        assertThat(ids.subList(PARCELS / 2, PARCELS)).allMatch(id -> id % 2 == 1).isSorted();
    }

    @Test
    void aFilterOnAHiddenColumnStillFilters() {
        ListQuery plain = new ListQuery("", null, null, 0, 100, List.of(), List.of());
        Map<String, List<String>> params = new HashMap<>();
        params.put("filter.status", List.of("LOST"));
        List<ActiveFilter> filters = service.parseFilters(PARCEL, params).filters();
        assertThat(filters).hasSize(1);

        long expected = list(plain.withParsedFilters(filters)).total();
        RecordingStatementInspector.clear();
        ListPage hidden = list(plain.withParsedFilters(filters).withParsedHidden(List.of("status")));

        assertThat(expected).isPositive().isLessThan(PARCELS);
        assertThat(hidden.total()).isEqualTo(expected);
        assertThat(hidden.query().filters()).hasSize(1);
        assertThat(selectList()).doesNotContain(".status");
    }

    @Test
    void theSearchStillLooksInAHiddenColumn() {
        ListQuery plain = new ListQuery("care #7", null, null, 0, 100, List.of(), List.of());

        long expected = list(plain).total();
        ListPage hidden = list(plain.withParsedHidden(List.of("notes")));

        assertThat(expected).isEqualTo(1);
        assertThat(hidden.total()).as("a condition of the query, not display").isEqualTo(expected);
        assertThat(hidden.rows().get(0).cells().get(index(hidden, "notes")).type()).isEqualTo(Cell.Type.EMPTY);
    }

    @Test
    void thePagingAndTheTotalDoNotDependOnWhatIsHidden() {
        ListPage plain = list(new ListQuery("", null, null, 1, 10, List.of(), List.of()));
        ListPage hidden = list(new ListQuery("", null, null, 1, 10, List.of(), List.of("notes", "depot")));

        assertThat(hidden.total()).isEqualTo(plain.total());
        assertThat(hidden.query().page()).isEqualTo(1);
        assertThat(hidden.rows()).extracting(ListView.Row::id).containsExactlyElementsOf(
                plain.rows().stream().map(ListView.Row::id).toList());
    }

    // ---- the second check -------------------------------------------------------------------------------------

    @Test
    void aQueryBuiltByHandCannotHideASensitiveColumnAnUnknownNameOrAnAttributeThatIsNotAColumn() {
        ListPage page = list(hiding("accessToken", "memo", "internalCode", "nothing", "weight"));

        assertThat(page.query().hidden()).containsExactly("weight");
        assertThat(String.valueOf(page.rows())).doesNotContain("token-secret").doesNotContain("memo-secret")
                .doesNotContain("internal-");
        assertThat(page.rows().get(0).cells().get(index(page, "accessToken"))).isEqualTo(Cell.masked());
        assertThat(page.rows().get(0).cells().get(index(page, "memo"))).as("hiding cannot turn a masked column off")
                .isEqualTo(Cell.masked());
    }

    @Test
    void aQueryBuiltByHandCannotHideTheLastColumnThatCanStay() {
        ListPage page = list(hiding(EVERY_COLUMN.toArray(new String[0])));

        assertThat(page.query().hidden()).isEmpty();
        assertThat(page.rows().get(0).cells().get(index(page, "reference")).type()).isNotEqualTo(Cell.Type.EMPTY);
        assertThat(selectList()).contains(".reference").contains(".notes");
    }

    @Test
    void allButOneColumnHiddenLeavesThatColumnShown() {
        List<String> all = new ArrayList<>(EVERY_COLUMN);
        all.remove("reference");

        ListPage page = list(hiding(all.toArray(new String[0])));

        assertThat(page.query().hidden()).hasSize(EVERY_COLUMN.size() - 1).doesNotContain("reference");
        assertThat(page.rows().get(0).cells().get(index(page, "reference")).display()).isEqualTo("PKG-001");
        assertThat(count(pageSql(), " left join ")).isZero();
    }

    @Test
    void theIdentifierCanBeHiddenAndTheRowStillHasItsId() {
        ListPage page = list(hiding("id"));

        assertThat(page.rows().get(0).id()).as("the row id is not a column").isNotBlank();
        assertThat(page.rows().get(0).cells().get(index(page, "id")).type()).isEqualTo(Cell.Type.EMPTY);
        assertThat(page.rows().get(0).cells().get(index(page, "reference")).display()).isEqualTo("PKG-001");
    }

    @Test
    void aPlanWithNothingHiddenIsTheSamePageAsBefore() {
        ListPage page = list(ListQuery.defaults());

        assertThat(page.query().hidden()).isEmpty();
        assertThat(page.rows()).hasSize(25);
    }
}
