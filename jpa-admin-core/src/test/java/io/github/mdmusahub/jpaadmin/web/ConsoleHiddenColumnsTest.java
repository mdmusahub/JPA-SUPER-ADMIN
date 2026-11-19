package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.service.ExportService;
import io.github.mdmusahub.jpaadmin.service.ExportSettings;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.testsupport.Csv;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

/**
 * The {@code hide} parameter through the list and export controllers (Phase 12, D-112, D-113), against a real database:
 * which names count, what happens to a hostile request, the notice, and that the export follows the list. The
 * controllers are called directly; the route and the session are proved over the real chain in the sample application.
 */
class ConsoleHiddenColumnsTest {

    private record Page(String template, Locale locale) implements View {
        @Override
        public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
            throw new UnsupportedOperationException("the test double renders nothing");
        }
    }

    private static final BasePath BASE = BasePath.of("/admin");
    private static final CsrfTokens TOKENS = request -> new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", "token-123");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC);
    private static final String SECRET = "needle-secret-token";
    private static final List<String> EVERY_COLUMN = List.of("id", "reference", "notes", "weight", "pieces", "fragile",
            "status", "shippedOn", "createdAt", "depot", "courier", "crate", "vault");

    private static EntityController listController;
    private static ExportController exportController;

    @BeforeAll
    static void wire() {
        ListTestPersistence.empty();
        ListTestPersistence.inTransaction(em -> {
            Depot depot = new Depot();
            depot.setName("Zürich depot");
            em.persist(depot);
            for (int i = 1; i <= 30; i++) {
                Parcel parcel = new Parcel();
                parcel.setReference("REF-" + String.format("%02d", i));
                parcel.setNotes("notes " + i);
                parcel.setStatus(ParcelStatus.values()[i % 4]);
                parcel.setPieces(i % 5);
                parcel.setDepot(depot);
                parcel.setAccessToken(SECRET + i);
                parcel.setMemo("memo " + SECRET + i);
                parcel.setInternalCode("internal-" + i);
                em.persist(parcel);
            }
        });
        AdminEntityRegistry registry = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
                DiscoverySettings.defaults());
        EntityManager entityManager = ListTestPersistence.entityManager();
        ConsoleNavigation navigation = new ConsoleNavigation(registry, CountService.over(entityManager, Clock.systemUTC()), true);
        ListQueryService lists = ListQueryService.over(entityManager, registry);
        ExportService exports = ExportService.over(lists, registry, ExportSettings.defaults(), CLOCK);
        ConsoleChromeFactory chromes = new ConsoleChromeFactory(BASE, new ConsoleSettings("Back office", "#0d9f6e"), TOKENS,
                navigation);
        listController = new EntityController(chromes, new ConsoleViews(Page::new), navigation, lists, BASE, exports);
        exportController = new ExportController(navigation, lists, exports);
    }

    private static MockHttpServletRequest request(String... hides) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal((Principal) () -> "admin");
        for (String hide : hides) {
            request.addParameter("hide", hide);
        }
        return request;
    }

    private static ModelAndView open(MockHttpServletRequest request) {
        return listController.entity("parcel", null, null, null, null, null, request);
    }

    private static ListView viewOf(ModelAndView result) {
        return (ListView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE);
    }

    private static PageChrome chromeOf(ModelAndView result) {
        return (PageChrome) result.getModel().get(PageChrome.ATTRIBUTE);
    }

    private static List<String> hiddenKeys(ListView view) {
        return view.columns().stream().filter(column -> !column.visible()).map(ListView.Column::key).toList();
    }

    private static List<List<String>> exported(MockHttpServletRequest request) throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        exportController.export("parcel", null, null, null, request, response);
        return Csv.parse(response.getContentAsString(StandardCharsets.UTF_8));
    }

    // ---- the list ---------------------------------------------------------------------------------------------

    @Test
    void aNamedColumnIsHiddenAndTheOthersAreShown() {
        ListView view = viewOf(open(request("pieces")));

        assertThat(hiddenKeys(view)).containsExactly("pieces");
        assertThat(view.rows()).hasSize(25);
        assertThat(view.rows().get(0).cells().get(view.columns().stream().map(ListView.Column::key).toList().indexOf("pieces")))
                .isEqualTo(Cell.empty());
    }

    @Test
    void severalNamesAreAcceptedRepeatedAndComeOutInColumnOrder() {
        ListView view = viewOf(open(request("status", "pieces", "status", "reference")));

        assertThat(hiddenKeys(view)).containsExactly("pieces", "reference", "status");
    }

    @Test
    void aSensitiveOrHiddenAttributeNameChangesNothingAndSaysNothing() {
        ModelAndView result = open(request("accessToken", "memo", "internalCode", "nothing"));

        assertThat(hiddenKeys(viewOf(result))).isEmpty();
        assertThat(chromeOf(result).flash()).as("an ignored name is silent, so a sensitive one cannot be told from an unknown one")
                .isEmpty();
        assertThat(viewOf(result).columns()).filteredOn(column -> column.type() == Cell.Type.MASKED)
                .extracting(ListView.Column::visible).containsOnly(true);
    }

    @Test
    void aSensitiveNameBesideAValidOneIsIgnoredAndTheValidOneStays() {
        ModelAndView result = open(request("memo", "weight", "accessToken"));

        assertThat(hiddenKeys(viewOf(result))).containsExactly("weight");
    }

    @Test
    void noSensitiveValueAndNoHiddenAttributeValueIsInThePageWhateverIsAskedFor() {
        ModelAndView result = open(request("accessToken", "memo", "internalCode", "notes", "reference"));

        String everything = String.valueOf(viewOf(result)) + chromeOf(result);
        assertThat(everything).doesNotContain(SECRET, "memo needle", "internal-").doesNotContain("REF-0").doesNotContain("notes 1");
    }

    @Test
    void aRequestThatHidesEveryColumnIsIgnoredAndThePageSaysSo() {
        ModelAndView result = open(request(EVERY_COLUMN.toArray(new String[0])));

        assertThat(hiddenKeys(viewOf(result))).as("every column stays").isEmpty();
        assertThat(chromeOf(result).flash()).containsExactly(ListViewFactory.everyColumnHiddenNotice());
        assertThat(viewOf(result).rows()).hasSize(25);
    }

    @Test
    void everyColumnPlusJunkIsStillIgnoredAsAWholeWithTheNotice() {
        List<String> asked = new ArrayList<>(EVERY_COLUMN);
        asked.addAll(List.of("accessToken", "memo", "internalCode", "nothing"));

        ModelAndView result = open(request(asked.toArray(new String[0])));

        assertThat(hiddenKeys(viewOf(result))).isEmpty();
        assertThat(chromeOf(result).flash()).hasSize(1);
    }

    @Test
    void allButOneColumnHiddenIsAllowedAndHasNoNotice() {
        List<String> asked = new ArrayList<>(EVERY_COLUMN);
        asked.remove("reference");

        ModelAndView result = open(request(asked.toArray(new String[0])));

        assertThat(hiddenKeys(viewOf(result))).hasSize(EVERY_COLUMN.size() - 1).doesNotContain("reference");
        assertThat(chromeOf(result).flash()).isEmpty();
    }

    @Test
    void aHostileRequestNeverBreaksThePage() {
        List<String> asked = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            asked.add("junk" + i);
        }
        asked.add("x".repeat(5_000));
        asked.add("weight,pieces");
        asked.add("");
        asked.add("pieces\u0000");
        asked.add("<script>alert(1)</script>");
        asked.add("'; drop table parcel; --");

        ModelAndView result = open(request(asked.toArray(new String[0])));

        assertThat(hiddenKeys(viewOf(result))).isEmpty();
        assertThat(viewOf(result).rows()).hasSize(25);
        assertThat(chromeOf(result).flash()).isEmpty();
    }

    @Test
    void aCommaJoinedValueIsOneNameThatIsNotAColumn() {
        assertThat(hiddenKeys(viewOf(open(request("weight,pieces"))))).isEmpty();
    }

    @Test
    void theLinksOfThePageKeepTheHiddenColumnsAndTheExportLinkToo() {
        ListView view = viewOf(open(request("weight", "pieces")));

        assertThat(view.actions().exportHref()).isEqualTo("/admin/parcel/export.csv?hide=pieces&hide=weight");
        assertThat(view.paging().sizes()).allSatisfy(size -> assertThat(size.href()).contains("hide=pieces&hide=weight"));
        assertThat(view.columns()).filteredOn(ListView.Column::sortable)
                .allSatisfy(column -> assertThat(column.sortHref()).contains("hide=pieces&hide=weight"));
    }

    // ---- the export follows the list --------------------------------------------------------------------------

    @Test
    void theExportHasExactlyTheColumnsTheListShows() throws IOException {
        MockHttpServletRequest asked = request("weight", "pieces", "notes", "accessToken");
        ListView view = viewOf(open(asked));

        List<List<String>> table = exported(asked);

        List<String> shown = view.columns().stream().filter(column -> column.visible() && column.type() != Cell.Type.MASKED)
                .map(ListView.Column::label).toList();
        assertThat(table.get(0)).containsExactlyElementsOf(shown);
        assertThat(table.get(0)).doesNotContain("Weight", "Pieces", "Notes", "Access token", "Memo");
        assertThat(table).hasSize(1 + 30);
    }

    @Test
    void theExportOfAHideThatNamesASensitiveColumnIsTheExportWithoutIt() throws IOException {
        assertThat(exported(request("accessToken", "memo", "internalCode"))).isEqualTo(exported(request()));
    }

    @Test
    void theExportOfARequestThatHidesEveryColumnHasEveryColumn() throws IOException {
        assertThat(exported(request(EVERY_COLUMN.toArray(new String[0])))).isEqualTo(exported(request()));
    }

    @Test
    void theExportHoldsNoHiddenValueAndNoSensitiveValue() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        exportController.export("parcel", null, null, null, request("notes", "depot", "accessToken"), response);
        String text = response.getContentAsString(StandardCharsets.UTF_8);

        assertThat(text).doesNotContain("notes 1", "Zürich depot", SECRET, "memo needle", "internal-");
        assertThat(text).startsWith("ID,");
    }
}
