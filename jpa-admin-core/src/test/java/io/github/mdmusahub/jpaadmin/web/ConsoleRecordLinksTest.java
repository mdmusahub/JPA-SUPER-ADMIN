package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Anchor;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Archive;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Crate;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Pass;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Stamp;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Vault;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

/**
 * The links on the list page (Phase 7, D-65 and D-70): the identifier cell leads to the record, a relation cell leads to
 * the related record exactly when the target is in the console, bulk delete is offered exactly when the service would
 * accept it, and none of it costs a statement. Built by the real controller, query service and factory over a real
 * database.
 */
class ConsoleRecordLinksTest {

    private record Page(String template, Locale locale) implements View {
        @Override
        public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
            throw new UnsupportedOperationException("the test double renders nothing");
        }
    }

    private static final BasePath BASE = BasePath.of("/admin");
    private static final CsrfTokens TOKENS = request -> new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", "token-123");
    private static final List<String> AWKWARD = List.of("a b", "x&y=z", "100%", "café", "q?x#y", "with+plus", "it's",
            "\"quote\"", "<b>bold</b>", "http://x", "semi;colon", "tilde~", "star*", "a/b", ".", "..");

    private EntityController controller;
    private Long depotId;
    private Long crateId;
    private Long vaultId;
    private Long parcelId;
    private Long emptyParcelId;

    @BeforeEach
    void wire() {
        ListTestPersistence.empty();
        ListTestPersistence.inTransaction(em -> {
            Depot depot = new Depot();
            depot.setName("Central <b>depot</b>");
            em.persist(depot);
            Crate crate = new Crate();
            crate.setNumber(7);
            em.persist(crate);
            Vault vault = new Vault();
            vault.setName("Hidden vault");
            em.persist(vault);
            Parcel parcel = new Parcel();
            parcel.setReference("REF-1");
            parcel.setDepot(depot);
            parcel.setCrate(crate);
            parcel.setVault(vault);
            em.persist(parcel);
            Parcel bare = new Parcel();
            bare.setReference("REF-2");
            em.persist(bare);
            for (String code : AWKWARD) {
                Stamp stamp = new Stamp();
                stamp.setCode(code);
                stamp.setCaption("caption of " + code);
                em.persist(stamp);
            }
            Pass pass = new Pass();
            pass.setAccessToken(1234567L);
            pass.setHolder("Someone");
            em.persist(pass);
            Archive archive = new Archive();
            archive.setTitle("Old");
            em.persist(archive);
            Anchor anchor = new Anchor();
            anchor.setTitle("Fixed");
            em.persist(anchor);
            em.flush();
            depotId = depot.getId();
            crateId = crate.getId();
            vaultId = vault.getId();
            parcelId = parcel.getId();
            emptyParcelId = bare.getId();
        });
        AdminEntityRegistry registry = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
                DiscoverySettings.defaults());
        EntityManager entityManager = ListTestPersistence.entityManager();
        ConsoleNavigation navigation = new ConsoleNavigation(registry, CountService.over(entityManager, Clock.systemUTC()));
        ConsoleChromeFactory chromes = new ConsoleChromeFactory(BASE, new ConsoleSettings("Back office", "#0d9f6e"),
                TOKENS, navigation);
        controller = new EntityController(chromes, new ConsoleViews(Page::new), navigation,
                ListQueryService.over(entityManager, registry), BASE);
    }

    private ListView open(String entity, String... parameters) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal((Principal) () -> "admin");
        for (int i = 0; i + 1 < parameters.length; i += 2) {
            request.addParameter(parameters[i], parameters[i + 1]);
        }
        ModelAndView result = controller.entity(entity, request.getParameter("q"), request.getParameter("sort"),
                request.getParameter("dir"), request.getParameter("page"), request.getParameter("size"), request);
        return (ListView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE);
    }

    private static int indexOf(ListView view, String key) {
        for (int i = 0; i < view.columns().size(); i++) {
            if (view.columns().get(i).key().equals(key)) {
                return i;
            }
        }
        throw new AssertionError(key);
    }

    private static Cell cell(ListView view, ListView.Row row, String key) {
        return row.cells().get(indexOf(view, key));
    }

    private static ListView.Row rowWithReference(ListView view, String reference) {
        int at = indexOf(view, "reference");
        return view.rows().stream().filter(row -> row.cells().get(at).display().equals(reference)).findFirst().orElseThrow();
    }

    // ---- the identifier cell ---------------------------------------------------------------------------------------------

    @Test
    void theIdentifierCellOfEveryRowLinksToTheRecordsDetailPage() {
        ListView view = open("parcel");

        assertThat(view.rows()).isNotEmpty().allSatisfy(row ->
                assertThat(cell(view, row, "id").href()).isEqualTo("/admin/parcel/" + row.id()));
    }

    @Test
    void theIdentifierLinkCarriesTheListStateSoTheDetailPageCanLeadBack() {
        ListView view = open("parcel", "q", "REF", "sort", "reference", "dir", "desc", "size", "10");

        assertThat(cell(view, view.rows().get(0), "id").href())
                .isEqualTo("/admin/parcel/" + view.rows().get(0).id() + "?q=REF&sort=reference&dir=desc&size=10");
    }

    @Test
    void anIdentifierThatIsMaskedHasNoLinkNoCheckboxAndNoBulkDelete() {
        ListView view = open("pass");

        assertThat(view.rows()).hasSize(1);
        Cell id = view.rows().get(0).cells().get(indexOf(view, "accessToken"));
        assertThat(id).isEqualTo(Cell.masked());
        assertThat(view.actions().canBulkDelete()).isFalse();
        assertThat(view.actions().bulkDeleteHref()).isNull();
        assertThat(view.toString()).doesNotContain("1234567/delete");
    }

    @Test
    void aTextIdentifierIsPercentEncodedInTheLinkAndReadsBackAsTheSameText() {
        ListView view = open("stamp", "size", "100");

        for (ListView.Row row : view.rows()) {
            String href = cell(view, row, "code").href();
            if (row.id().contains("/") || row.id().equals(".") || row.id().equals("..")) {
                assertThat(href).as("an id that cannot be an address segment has no link: " + row.id()).isNull();
                continue;
            }
            assertThat(href).as(row.id()).startsWith("/admin/stamp/").doesNotContain(" ", "'", "\"", "<", ">", "&", "#", "//");
            String segment = href.substring("/admin/stamp/".length());
            segment = segment.substring(0, segment.indexOf('?')); // the list state follows the segment
            assertThat(segment).as(row.id()).doesNotContain("/", "?");
            assertThat(URLDecoder.decode(segment, StandardCharsets.UTF_8)).as(row.id()).isEqualTo(row.id());
            new Cell(Cell.Type.TEXT, "x", Cell.Tone.NONE, href, null); // the link rule of the contract
        }
        assertThat(view.rows()).hasSize(AWKWARD.size());
    }

    // ---- relation cells -----------------------------------------------------------------------------------------------------

    @Test
    void aRelationCellLinksToTheRelatedRecordWhenItsTargetIsInTheConsole() {
        ListView view = open("parcel");
        ListView.Row row = rowWithReference(view, "REF-1");

        assertThat(cell(view, row, "depot").href()).isEqualTo("/admin/depot/" + depotId);
        assertThat(cell(view, row, "depot").display()).isEqualTo("Central <b>depot</b>");
        assertThat(cell(view, row, "crate").href()).as("a target without a label is still a record to open")
                .isEqualTo("/admin/crate/" + crateId);
        assertThat(cell(view, row, "crate").display()).isEqualTo("#" + crateId);
    }

    @Test
    void aRelationCellWhoseTargetIsHiddenHasNoLinkAndShowsHashIdOnly() {
        ListView view = open("parcel");
        Cell vault = cell(view, rowWithReference(view, "REF-1"), "vault");

        assertThat(vault.type()).isEqualTo(Cell.Type.RELATION);
        assertThat(vault.display()).isEqualTo("#" + vaultId).doesNotContain("Hidden vault");
        assertThat(vault.href()).isNull();
    }

    @Test
    void aMissingRelationIsAnEmptyCellWithNoLink() {
        ListView view = open("parcel");
        ListView.Row row = rowWithReference(view, "REF-2");

        for (String relation : List.of("depot", "crate", "vault", "courier")) {
            assertThat(cell(view, row, relation).type()).as(relation).isEqualTo(Cell.Type.EMPTY);
            assertThat(cell(view, row, relation).href()).as(relation).isNull();
        }
    }

    @Test
    void theLinkCostsNoStatementBecauseTheTargetIdIsAlreadySelected() {
        AdminEntityRegistry registry = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
                DiscoverySettings.defaults());
        ListQueryService service = ListQueryService.over(ListTestPersistence.entityManager(), registry);
        RecordingStatementInspector.clear();
        io.github.mdmusahub.jpaadmin.service.ListPage page = service.list(registry.find("parcel").orElseThrow(),
                io.github.mdmusahub.jpaadmin.service.ListQuery.defaults());
        int afterTheRead = RecordingStatementInspector.statements().size();

        ListView view = ListViewFactory.build("/admin", "parcel", new ListView.EntityInfo("Parcel", "Parcels", "table"),
                page, new ListView.Actions(false, false, false, null, null), true);

        assertThat(RecordingStatementInspector.statements()).as("building the links reads nothing").hasSize(afterTheRead);
        assertThat(view.rows()).extracting(row -> cell(view, row, "depot").href()).contains("/admin/depot/" + depotId);
    }

    @Test
    void theIdInARelationLinkIsTheSameValueAsTheIdInTheRelationFilterDropdown() {
        ListView view = open("parcel");
        ListView.Filters.Definition depotFilter = view.filters().definitions().stream()
                .filter(definition -> definition.key().equals("depot")).findFirst().orElseThrow();
        FormView.Option option = depotFilter.options().stream().filter(o -> o.label().equals("Central <b>depot</b>"))
                .findFirst().orElseThrow();
        String href = cell(view, rowWithReference(view, "REF-1"), "depot").href();

        assertThat(option.value()).isEqualTo(String.valueOf(depotId));
        assertThat(href).isEqualTo("/admin/depot/" + option.value());
    }

    @Test
    void everyLinkOnTheListPageLiesUnderTheBasePathAndPassesTheLinkRule() {
        ListView view = open("parcel", "q", "http://x", "size", "50");
        List<String> links = new ArrayList<>();
        view.rows().forEach(row -> row.cells().forEach(c -> links.add(c.href())));
        links.add(view.actions().bulkDeleteHref());
        links.removeIf(link -> link == null);

        assertThat(links).isNotEmpty().allSatisfy(link -> {
            assertThat(link).startsWith("/admin/");
            assertThat(link).doesNotContain(" ", "'", "\"", "http://x", "//");
            new Cell(Cell.Type.TEXT, "x", Cell.Tone.NONE, link, null);
        });
    }

    // ---- bulk delete is offered exactly when the service accepts it ------------------------------------------------------------

    @Test
    void aDeletableEntityOffersBulkDeleteWithTheListStateInTheFormAddress() {
        ListView view = open("parcel", "q", "REF", "page", "0", "sort", "reference", "dir", "asc", "size", "10",
                "filter.status", "PENDING");

        assertThat(view.actions().canBulkDelete()).isTrue();
        assertThat(view.actions().bulkDeleteHref())
                .isEqualTo("/admin/parcel/bulk-delete?q=REF&filter.status=PENDING&sort=reference&dir=asc&size=10");
    }

    @Test
    void theBulkDeleteAddressKeepsThePageTheVisitorIsOn() {
        ListTestPersistence.inTransaction(em -> {
            for (int i = 0; i < 30; i++) {
                Parcel parcel = new Parcel();
                parcel.setReference("MORE-" + i);
                em.persist(parcel);
            }
        });

        ListView view = open("parcel", "size", "10", "page", "2");

        assertThat(view.actions().bulkDeleteHref()).isEqualTo("/admin/parcel/bulk-delete?page=2&size=10");
    }

    @Test
    void aReadOnlyAndANonDeletableEntityOfferNoBulkDelete() {
        for (String entity : List.of("archive", "anchor")) {
            ListView view = open(entity);

            assertThat(view.actions().canBulkDelete()).as(entity).isFalse();
            assertThat(view.actions().bulkDeleteHref()).as(entity).isNull();
        }
    }

    @Test
    void theRowsOfAReadOnlyEntityStillLinkToTheirDetailPage() {
        ListView view = open("archive");

        assertThat(cell(view, view.rows().get(0), "id").href()).isEqualTo("/admin/archive/" + view.rows().get(0).id());
    }

    // ---- ListLinks itself --------------------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"", ".", "..", "a/b", "a\\b", "line\nbreak"})
    void anIdThatCannotBeAnAddressSegmentIsNotAddressable(String id) {
        assertThat(ListLinks.addressable(id)).isFalse();
        assertThat(new ListLinks("/admin", "stamp").detail(id, io.github.mdmusahub.jpaadmin.service.ListQuery.defaults()))
                .isNull();
        assertThat(new ListLinks("/admin", "stamp").delete(id, io.github.mdmusahub.jpaadmin.service.ListQuery.defaults()))
                .isNull();
    }

    @Test
    void anOverlongOrMissingIdIsNotAddressable() {
        assertThat(ListLinks.addressable("a".repeat(256))).isFalse();
        assertThat(ListLinks.addressable(null)).isFalse();
        assertThat(ListLinks.addressable("a".repeat(255))).isTrue();
    }
}
