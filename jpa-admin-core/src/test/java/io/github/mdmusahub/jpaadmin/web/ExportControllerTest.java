package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
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
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Shielded;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

/**
 * The export route and the export button of the list (Phase 11): the headers, the file name, the cap, the refusals, the
 * link and what it carries, and the dashboard flag. The controller is called directly against a real database; the route
 * itself (session, GET only, CSRF) is proved over the real chain in the sample application.
 */
class ExportControllerTest {

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
    private static final int PARCELS = 60;

    private static AdminEntityRegistry registry;
    private static EntityManager entityManager;
    private static ConsoleNavigation navigation;
    private static ListQueryService lists;

    @BeforeAll
    static void wire() {
        ListTestPersistence.empty();
        ListTestPersistence.inTransaction(em -> {
            Depot depot = new Depot();
            depot.setName("Zürich depot");
            em.persist(depot);
            for (int i = 1; i <= PARCELS; i++) {
                Parcel parcel = new Parcel();
                parcel.setReference("REF-" + String.format("%02d", i));
                parcel.setStatus(ParcelStatus.values()[i % 4]);
                parcel.setPieces(i % 5);
                parcel.setDepot(depot);
                parcel.setAccessToken(SECRET + i);
                parcel.setMemo("memo " + SECRET + i);
                em.persist(parcel);
            }
            Shielded shielded = new Shielded();
            shielded.setTitle("not for export");
            em.persist(shielded);
        });
        registry = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(), DiscoverySettings.defaults());
        entityManager = ListTestPersistence.entityManager();
        navigation = new ConsoleNavigation(registry, CountService.over(entityManager, Clock.systemUTC()), true);
        lists = ListQueryService.over(entityManager, registry);
    }

    private static ExportService exports(ExportSettings settings) {
        return ExportService.over(lists, registry, settings, CLOCK);
    }

    private static ExportController controller(ExportSettings settings) {
        return new ExportController(navigation, lists, exports(settings));
    }

    private static EntityController listController(ExportSettings settings) {
        ConsoleChromeFactory chromes = new ConsoleChromeFactory(BASE, new ConsoleSettings("Back office", "#0d9f6e"), TOKENS,
                navigation);
        return new EntityController(chromes, new ConsoleViews(Page::new), navigation, lists, BASE, exports(settings));
    }

    private static MockHttpServletRequest signedIn(String... parameters) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal((Principal) () -> "admin");
        for (int i = 0; i < parameters.length; i += 2) {
            request.addParameter(parameters[i], parameters[i + 1]);
        }
        return request;
    }

    private static MockHttpServletResponse export(ExportController controller, String entity, String q, String sort, String dir,
                                                  MockHttpServletRequest request) throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.export(entity, q, sort, dir, request, response);
        return response;
    }

    private static MockHttpServletResponse export(String entity) throws IOException {
        return export(controller(ExportSettings.defaults()), entity, null, null, null, signedIn());
    }

    private static List<List<String>> rows(MockHttpServletResponse response) throws IOException {
        return Csv.parse(response.getContentAsString(StandardCharsets.UTF_8));
    }

    private static ListView listOf(EntityController controller, String entity, MockHttpServletRequest request, String q,
                                   String page, String size) {
        ModelAndView result = controller.entity(entity, q, null, null, page, size, request);
        return (ListView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE);
    }

    private static PageChrome chromeOf(EntityController controller, String entity, MockHttpServletRequest request) {
        return (PageChrome) controller.entity(entity, null, null, null, null, null, request).getModel().get(PageChrome.ATTRIBUTE);
    }

    private static int status(ResponseStatusException refusal) {
        return refusal.getStatusCode().value();
    }

    // ---- the response ---------------------------------------------------------------------------------------------------

    @Test
    void theResponseIsAUtf8CsvAttachmentWithAGeneratedFileNameAndTheSafetyHeaders() throws IOException {
        MockHttpServletResponse response = export("parcel");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualToIgnoringCase("text/csv;charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"parcel-2026-11-19.csv\"");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.isCommitted()).as("flushed at the end").isTrue();
    }

    @Test
    void theBodyIsTheHeaderAndEveryRecordAndNothingSensitive() throws IOException {
        MockHttpServletResponse response = export("parcel");
        List<List<String>> table = rows(response);

        assertThat(table).hasSize(1 + PARCELS);
        assertThat(table.get(0)).contains("ID", "Reference", "Status", "Depot").noneMatch(label -> label.toLowerCase(Locale.ROOT)
                .contains("token") || label.toLowerCase(Locale.ROOT).contains("memo"));
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain(SECRET, "memo ");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("Zürich depot");
        assertThat(response.getContentAsByteArray()).as("real UTF-8 bytes, not the platform's").contains(0xC3, 0xBC);
    }

    @Test
    void theBodyEndsWithTheLineEndingOfTheLastRowAndHasNoExtraRowForANote() throws IOException {
        MockHttpServletResponse response = export("parcel");

        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).endsWith("\r\n").doesNotEndWith("\r\n\r\n");
        assertThat(rows(response)).allSatisfy(row -> assertThat(row).hasSize(rows(response).get(0).size()));
    }

    @Test
    void aFileThatIsNotCutHasNoTruncationHeaders() throws IOException {
        MockHttpServletResponse response = export("parcel");

        assertThat(response.getHeader(ExportController.TRUNCATED_HEADER)).isNull();
        assertThat(response.getHeader(ExportController.ROW_LIMIT_HEADER)).isNull();
    }

    @Test
    void aFileCutAtTheCapSaysSoInTwoHeadersAndHasNoExtraRow() throws IOException {
        MockHttpServletResponse response = export(controller(new ExportSettings(true, 25, false)), "parcel", null, null, null,
                signedIn());

        assertThat(response.getHeader("X-Export-Truncated")).isEqualTo("true");
        assertThat(response.getHeader("X-Export-Row-Limit")).isEqualTo("25");
        assertThat(rows(response)).as("the header and 25 records, no note row").hasSize(26);
    }

    @Test
    void theByteOrderMarkIsOffByDefaultAndOnWhenAsked() throws IOException {
        byte[] plain = export("parcel").getContentAsByteArray();
        byte[] marked = export(controller(new ExportSettings(true, 100, true)), "parcel", null, null, null, signedIn())
                .getContentAsByteArray();

        assertThat(plain[0]).isEqualTo((byte) 'I');
        assertThat(marked[0]).isEqualTo((byte) 0xEF);
        assertThat(marked[1]).isEqualTo((byte) 0xBB);
        assertThat(marked[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    void aHeadRequestGetsTheHeadersAndReadsNoRows() throws IOException {
        MockHttpServletRequest request = signedIn();
        request.setMethod("HEAD");

        MockHttpServletResponse response = export(controller(ExportSettings.defaults()), "parcel", null, null, null, request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"parcel-2026-11-19.csv\"");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void nothingIsPutInTheSession() throws IOException {
        MockHttpServletRequest request = signedIn();

        export(controller(new ExportSettings(true, 25, false)), "parcel", null, null, null, request);

        assertThat(request.getSession(false)).as("no session was made for the notice").isNull();
    }

    // ---- the query is the list's ------------------------------------------------------------------------------------------

    @Test
    void searchFilterAndSortAreTheListsAndPageAndSizeAreIgnored() throws IOException {
        MockHttpServletRequest request = signedIn("filter.status", "DELIVERED", "page", "3", "size", "10");

        List<List<String>> table = rows(export(controller(ExportSettings.defaults()), "parcel", "REF-", "reference", "desc",
                request));

        int status = table.get(0).indexOf("Status");
        int reference = table.get(0).indexOf("Reference");
        assertThat(table.stream().skip(1).map(row -> row.get(status))).isNotEmpty().containsOnly("DELIVERED");
        assertThat(table.stream().skip(1).map(row -> row.get(reference)).toList()).isSortedAccordingTo(
                java.util.Comparator.reverseOrder());
        assertThat(table.size() - 1).isEqualTo(PARCELS / 4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"accessToken", "memo", "internalCode", "picture", "dimensions", "items", "no-such-column"})
    void aSortOnASensitiveHiddenOrUnknownKeyIsIgnoredAndLeaksNothing(String key) throws IOException {
        MockHttpServletResponse sorted = export(controller(ExportSettings.defaults()), "parcel", null, key, "desc", signedIn());

        assertThat(rows(sorted)).hasSize(1 + PARCELS);
        assertThat(sorted.getContentAsString(StandardCharsets.UTF_8)).doesNotContain(SECRET);
        assertThat(rows(sorted).stream().skip(1).map(row -> row.get(0)).toList()).as("in identifier order")
                .isSortedAccordingTo(java.util.Comparator.comparingLong(Long::parseLong));
    }

    @Test
    void aFilterOnASensitiveOrHiddenFieldIsIgnored() throws IOException {
        MockHttpServletRequest request = signedIn("filter.accessToken", SECRET + "1", "filter.internalCode", "x", "filter.nope", "y");

        MockHttpServletResponse response = export(controller(ExportSettings.defaults()), "parcel", null, null, null, request);

        assertThat(rows(response)).hasSize(1 + PARCELS);
    }

    // ---- refusals ---------------------------------------------------------------------------------------------------------------

    @Test
    void anUnknownAndAHiddenEntityAreTheSameNotFound() {
        ExportController controller = controller(ExportSettings.defaults());

        assertThatThrownBy(() -> export(controller, "nothing-like-this", null, null, null, signedIn()))
                .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> assertThat(status(refusal)).isEqualTo(404));
        assertThatThrownBy(() -> export(controller, "vault", null, null, null, signedIn()))
                .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                    assertThat(status(refusal)).isEqualTo(404);
                    assertThat(refusal.getReason()).as("no text that could differ").isNull();
                });
    }

    @Test
    void anEntityThatIsNotExportableIsForbiddenAndWritesNothing() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller(ExportSettings.defaults()).export("shielded", null, null, null, signedIn(), response))
                .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> assertThat(status(refusal)).isEqualTo(403));
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Disposition")).isNull();
    }

    @Test
    void theGlobalSwitchForbidsEveryKnownEntityAndStillHidesTheUnknown() {
        ExportController off = controller(new ExportSettings(false, 50_000, false));

        assertThatThrownBy(() -> export(off, "parcel", null, null, null, signedIn()))
                .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> assertThat(status(refusal)).isEqualTo(403));
        assertThatThrownBy(() -> export(off, "nothing-like-this", null, null, null, signedIn()))
                .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> assertThat(status(refusal)).isEqualTo(404));
    }

    @Test
    void aFailureReadingTheFirstPageLeavesTheResponseUntouchedForTheConsolesErrorPage() {
        ListQueryService broken = new ListQueryService(entityManager, registry) {
            @Override
            public List<List<String>> exportRows(io.github.mdmusahub.jpaadmin.service.ExportPlan plan, int offset, int limit) {
                throw new IllegalStateException("the database is down");
            }
        };
        ExportController controller = new ExportController(navigation, broken,
                ExportService.over(broken, registry, ExportSettings.defaults(), CLOCK));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.export("parcel", null, null, null, signedIn(), response))
                .isInstanceOf(IllegalStateException.class);
        assertThat(response.isCommitted()).isFalse();
        assertThat(response.getHeader("Content-Disposition")).as("an error page is not a download").isNull();
        assertThat(response.getContentType()).isNull();
    }

    // ---- the route's mapping --------------------------------------------------------------------------------------------

    @Test
    void theRouteIsGetOnlyAndNamedExportCsvUnderTheEntity() throws NoSuchMethodException {
        var method = ExportController.class.getMethod("export", String.class, String.class, String.class, String.class,
                HttpServletRequest.class, HttpServletResponse.class);

        assertThat(method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value()[0])
                .endsWith("/export.csv").startsWith("/{entity:");
        assertThat(method.getAnnotations()).extracting(a -> a.annotationType().getSimpleName())
                .doesNotContain("PostMapping", "RequestMapping", "PutMapping", "DeleteMapping", "PatchMapping");
    }

    // ---- the button on the list -----------------------------------------------------------------------------------------

    @Test
    void theListOffersTheExportLinkWithTheCurrentSearchAndNoPage() {
        ListView view = listOf(listController(ExportSettings.defaults()), "parcel", signedIn(), "REF-1", "1", "10");

        assertThat(view.actions().canExport()).isTrue();
        assertThat(view.actions().exportHref()).isEqualTo("/admin/parcel/export.csv?q=REF-1");
    }

    @Test
    void theExportLinkOfAPlainListIsTheBareRoute() {
        ListView view = listOf(listController(ExportSettings.defaults()), "parcel", signedIn(), null, null, null);

        assertThat(view.actions().exportHref()).isEqualTo("/admin/parcel/export.csv");
    }

    @Test
    void theExportLinkCarriesFiltersAndSortAndLeavesOutPageAndSize() {
        EntityController controller = listController(ExportSettings.defaults());
        MockHttpServletRequest request = signedIn("filter.status", "DELIVERED", "filter.pieces.min", "2");

        ModelAndView result = controller.entity("parcel", "ref", "reference", "desc", "1", "10", request);
        String href = ((ListView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE)).actions().exportHref();

        assertThat(href).startsWith("/admin/parcel/export.csv?q=ref&filter.pieces.min=2&filter.status=DELIVERED&sort=reference&dir=desc");
        assertThat(href).doesNotContain("page=", "size=");
    }

    @ParameterizedTest
    @ValueSource(strings = {"two words", "a/b", "say \"hi\"", "it's", "a&b=c", "100%", "x#y", "plus+sign", "http://x", "ünï 東京", "<script>"})
    void theExportLinkEncodesEveryAwkwardSearchValueAndStaysUnderTheBasePath(String text) {
        ListView view = listOf(listController(ExportSettings.defaults()), "parcel", signedIn(), text, null, null);
        String href = view.actions().exportHref();

        assertThat(href).startsWith("/admin/parcel/export.csv?q=");
        String query = href.substring(href.indexOf('?') + 1);
        assertThat(query).doesNotContain(" ", "\"", "'", "<", ">", "#").doesNotContain("&");
        assertThat(java.net.URLDecoder.decode(query.substring(2), StandardCharsets.UTF_8)).isEqualTo(text);
    }

    @Test
    void theExportLinkOfACustomBasePathIsUnderThatBasePath() {
        BasePath custom = BasePath.of("/console-x");
        ConsoleChromeFactory chromes = new ConsoleChromeFactory(custom, new ConsoleSettings("Back office", "#0d9f6e"), TOKENS,
                navigation);
        EntityController controller = new EntityController(chromes, new ConsoleViews(Page::new), navigation, lists, custom,
                exports(ExportSettings.defaults()));

        ListView view = listOf(controller, "parcel", signedIn(), null, null, null);

        assertThat(view.actions().exportHref()).isEqualTo("/console-x/parcel/export.csv");
    }

    @Test
    void noExportLinkWhenTheGlobalSwitchIsOff() {
        ListView view = listOf(listController(new ExportSettings(false, 50_000, false)), "parcel", signedIn(), null, null, null);

        assertThat(view.actions().canExport()).isFalse();
        assertThat(view.actions().exportHref()).isNull();
        assertThat(view.actions().canCreate()).as("the other actions stay").isTrue();
    }

    @Test
    void noExportLinkForAnEntityThatIsNotExportable() {
        ListView view = listOf(listController(ExportSettings.defaults()), "shielded", signedIn(), null, null, null);

        assertThat(view.actions().canExport()).isFalse();
        assertThat(view.actions().exportHref()).isNull();
        assertThat(view.actions().canCreate()).as("the other actions stay").isTrue();
    }

    @Test
    void aListBuiltWithTheOldConstructorOffersNoExport() {
        ConsoleChromeFactory chromes = new ConsoleChromeFactory(BASE, new ConsoleSettings("Back office", "#0d9f6e"), TOKENS,
                navigation);
        EntityController old = new EntityController(chromes, new ConsoleViews(Page::new), navigation, lists, BASE);

        assertThat(listOf(old, "parcel", signedIn(), null, null, null).actions().canExport()).isFalse();
    }

    // ---- the notice about the cap (D-110) ---------------------------------------------------------------------------------

    @Test
    void aListWhoseExportWouldBeCutSaysSoOnThePage() {
        PageChrome chrome = chromeOf(listController(new ExportSettings(true, 50, false)), "parcel", signedIn());

        assertThat(chrome.flash()).extracting(PageChrome.FlashMessage::text)
                .contains("Export is limited to the first 50 of 60 matching records. Narrow the search or the filters to export the rest.");
        assertThat(chrome.flash()).filteredOn(m -> m.text().startsWith("Export is limited"))
                .allSatisfy(m -> assertThat(m.level()).isEqualTo(PageChrome.FlashMessage.Level.INFO));
    }

    @Test
    void thereIsNoNoticeWhenEverythingFitsNotEvenAtTheExactCap() {
        assertThat(chromeOf(listController(new ExportSettings(true, PARCELS, false)), "parcel", signedIn()).flash())
                .noneMatch(m -> m.text().startsWith("Export is limited"));
        assertThat(chromeOf(listController(ExportSettings.defaults()), "parcel", signedIn()).flash())
                .noneMatch(m -> m.text().startsWith("Export is limited"));
    }

    @Test
    void thereIsNoNoticeWhenTheExportIsNotOffered() {
        assertThat(chromeOf(listController(new ExportSettings(false, 5, false)), "parcel", signedIn()).flash())
                .noneMatch(m -> m.text().startsWith("Export is limited"));
    }

    @Test
    void theNoticeCountsOnlyTheMatchingRecords() {
        EntityController controller = listController(new ExportSettings(true, 10, false));

        PageChrome chrome = (PageChrome) controller.entity("parcel", null, null, null, null, null,
                signedIn("filter.status", "DELIVERED")).getModel().get(PageChrome.ATTRIBUTE);

        assertThat(chrome.flash()).extracting(PageChrome.FlashMessage::text)
                .contains("Export is limited to the first 10 of 15 matching records. Narrow the search or the filters to export the rest.");
    }

    // ---- the dashboard flag ---------------------------------------------------------------------------------------------

    @Test
    void theDashboardFlagIsTheSwitchAndTheEntitysOwnOption() {
        DashboardView on = navigation.dashboard();
        DashboardView off = new ConsoleNavigation(registry, CountService.over(entityManager, Clock.systemUTC()), false).dashboard();
        DashboardView oldConstructor = new ConsoleNavigation(registry, CountService.over(entityManager, Clock.systemUTC())).dashboard();

        assertThat(card(on, "parcel").permissions().export()).isTrue();
        assertThat(card(on, "shielded").permissions().export()).isFalse();
        assertThat(card(off, "parcel").permissions().export()).isFalse();
        assertThat(card(oldConstructor, "parcel").permissions().export()).isFalse();
    }

    private static DashboardView.EntityCard card(DashboardView view, String urlName) {
        AdminEntity entity = registry.find(urlName).orElseThrow();
        return view.groups().stream().flatMap(group -> group.entities().stream())
                .filter(card -> card.entity().equals(entity.urlName())).findFirst().orElseThrow();
    }
}
