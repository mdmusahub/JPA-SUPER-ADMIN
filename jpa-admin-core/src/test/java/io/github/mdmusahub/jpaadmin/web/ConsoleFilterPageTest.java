package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Courier;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Vault;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.ListView.Filters.Definition;
import io.github.mdmusahub.jpaadmin.web.view.ListView.Filters.Definition.Kind;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

/**
 * The filter part of the list page, through the real controller, factory and service on a real database: the panel's
 * definitions, one chip per active filter, the links, what is told about an ignored filter, and that nothing a visitor
 * must not see (a sensitive value, a hidden field, a hidden entity's row) reaches the records the template reads.
 */
class ConsoleFilterPageTest {

    private record Page(String template, Locale locale) implements View {
        @Override
        public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
            throw new UnsupportedOperationException("the test double renders nothing");
        }
    }

    private static final BasePath BASE = BasePath.of("/admin");
    private static final String SECRET = "needle-secret-token";
    private static final CsrfTokens TOKENS = request -> new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", "token-123");

    /** A link: the base path, then letters, digits, hyphens and slashes, then an encoded query. Nothing raw. */
    private static final Pattern SAFE_LINK = Pattern.compile("/admin(/[a-z0-9-]+)?(\\?[A-Za-z0-9_.%=&-]*)?");

    private static EntityController controller;
    private static long centralId;
    private static long secondId;

    @BeforeAll
    static void wire() {
        ListTestPersistence.empty();
        ListTestPersistence.inTransaction(em -> {
            Depot central = depot("Central depot");
            Depot second = depot("Second depot");
            em.persist(central);
            em.persist(second);
            centralId = central.getId();
            secondId = second.getId();
            Courier courier = new Courier();
            courier.setBadgeNumber("BADGE-" + SECRET);
            courier.setCallSign("Falcon");
            em.persist(courier);
            Vault vault = new Vault();
            vault.setName("Deep vault name");
            em.persist(vault);
            for (int i = 1; i <= 60; i++) {
                Parcel parcel = new Parcel();
                parcel.setReference("REF-" + i);
                parcel.setNotes(i == 1 ? "see http://x?a=1&b=2 #tag 100% 'q' \"d\" /\\ ü 東京" : "note " + i);
                parcel.setWeight(BigDecimal.valueOf(i));
                parcel.setPieces(i % 7);
                parcel.setFragile(i % 3 == 0);
                parcel.setStatus(ParcelStatus.values()[i % 4]);
                parcel.setShippedOn(LocalDate.of(2026, 1, 1).plusDays(i));
                parcel.setCreatedAt(LocalDateTime.of(2026, 3, 1, 0, 0).plusHours(6L * i));
                parcel.setDepot(i % 2 == 0 ? central : second);
                parcel.setCourier(i % 4 == 0 ? courier : null);
                parcel.setVault(i % 5 == 0 ? vault : null);
                parcel.setAccessToken(SECRET + i);
                parcel.setMemo("memo " + SECRET + i);
                parcel.setInternalCode("internal-" + i);
                em.persist(parcel);
            }
        });
        AdminEntityRegistry registry = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
                DiscoverySettings.defaults());
        var entityManager = ListTestPersistence.entityManager();
        ConsoleNavigation navigation = new ConsoleNavigation(registry, CountService.over(entityManager, Clock.systemUTC()));
        ConsoleChromeFactory chromes = new ConsoleChromeFactory(BASE, new ConsoleSettings("Back office", "#0d9f6e"), TOKENS,
                navigation);
        controller = new EntityController(chromes, new ConsoleViews(Page::new), navigation,
                ListQueryService.over(entityManager, registry), BASE);
    }

    private static Depot depot(String name) {
        Depot depot = new Depot();
        depot.setName(name);
        depot.setApiKey("DEPOT-KEY-" + SECRET);
        return depot;
    }

    private static ModelAndView openWith(String q, String sort, String dir, String page, String size, String... filters) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal((Principal) () -> "admin");
        for (int i = 0; i < filters.length; i += 2) {
            request.addParameter(filters[i], filters[i + 1]);
        }
        return controller.entity("parcel", q, sort, dir, page, size, request);
    }

    private static ModelAndView open(String... filters) {
        return openWith(null, null, null, null, null, filters);
    }

    private static ListView view(ModelAndView result) {
        return (ListView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE);
    }

    private static PageChrome chrome(ModelAndView result) {
        return (PageChrome) result.getModel().get(PageChrome.ATTRIBUTE);
    }

    private static Definition definition(ListView view, String key) {
        return view.filters().definitions().stream().filter(d -> d.key().equals(key)).findFirst().orElseThrow();
    }

    private static Map<String, String> query(String link) {
        Map<String, String> parameters = new LinkedHashMap<>();
        int at = link.indexOf('?');
        if (at >= 0) {
            for (String pair : link.substring(at + 1).split("&")) {
                String[] keyAndValue = pair.split("=", 2);
                parameters.put(keyAndValue[0], URLDecoder.decode(keyAndValue[1], StandardCharsets.UTF_8));
            }
        }
        return parameters;
    }

    private static List<String> everyLink(ListView view) {
        List<String> links = new ArrayList<>();
        view.columns().stream().map(ListView.Column::sortHref).filter(l -> l != null).forEach(links::add);
        for (String link : new String[] {view.paging().firstHref(), view.paging().prevHref(), view.paging().nextHref(),
                view.paging().lastHref(), view.filters().clearAllHref()}) {
            if (link != null) {
                links.add(link);
            }
        }
        view.paging().sizes().forEach(size -> links.add(size.href()));
        view.filters().chips().forEach(chip -> links.add(chip.removeHref()));
        return links;
    }

    // ---- the panel --------------------------------------------------------------------------------------------

    @Test
    void thePanelHasOneDefinitionForEveryFilterableFieldAndNoneForAnyOtherField() {
        ListView view = view(open());

        assertThat(view.filters().definitions().stream().map(Definition::key)).containsExactly("courier", "createdAt", "depot",
                "fragile", "notes", "pieces", "reference", "shippedOn", "status", "weight");
        assertThat(view.filters().definitions().stream().map(Definition::kind)).containsExactly(Kind.RELATION, Kind.DATE_RANGE,
                Kind.RELATION, Kind.BOOLEAN, Kind.TEXT, Kind.NUMBER_RANGE, Kind.TEXT, Kind.DATE_RANGE, Kind.ENUM, Kind.NUMBER_RANGE);
        assertThat(view.filters().definitions().stream().map(Definition::key)).doesNotContain("accessToken", "memo", "internalCode",
                "crate", "vault", "items", "picture", "dimensions", "id");
        assertThat(view.filters().chips()).isEmpty();
        assertThat(view.filters().clearAllHref()).isNull();
    }

    @Test
    void aColumnIsMarkedFilterableExactlyWhenItHasADefinition() {
        ListView view = view(open());
        List<String> defined = view.filters().definitions().stream().map(Definition::key).toList();

        assertThat(view.columns()).isNotEmpty().allSatisfy(column -> assertThat(column.filterable())
                .as(column.key()).isEqualTo(defined.contains(column.key())));
        assertThat(view.columns()).filteredOn(c -> c.key().equals("accessToken")).singleElement()
                .satisfies(c -> assertThat(c.filterable()).isFalse());
    }

    @Test
    void theControlsStartWithNoValueAndAFilterShowsItsValueInItsDefinition() {
        ListView plain = view(open());
        assertThat(plain.filters().definitions()).allSatisfy(d -> {
            assertThat(d.value()).isNull();
            assertThat(d.from()).isNull();
            assertThat(d.to()).isNull();
        });

        ListView filtered = view(open("filter.status", "LOST", "filter.weight.min", "5", "filter.weight.max", "9.5",
                "filter.shippedOn.from", "2026-01-10", "filter.reference", "REF"));
        assertThat(definition(filtered, "status").value()).isEqualTo("LOST");
        assertThat(definition(filtered, "weight").from()).isEqualTo("5");
        assertThat(definition(filtered, "weight").to()).isEqualTo("9.5");
        assertThat(definition(filtered, "shippedOn").from()).isEqualTo("2026-01-10");
        assertThat(definition(filtered, "shippedOn").to()).isNull();
        assertThat(definition(filtered, "reference").value()).isEqualTo("REF");
    }

    @Test
    void enumAndBooleanControlsOfferTheirChoicesAndARelationControlOffersTheTargetsLabels() {
        ListView view = view(open());

        assertThat(definition(view, "status").options().stream().map(o -> o.value() + "=" + o.label()))
                .containsExactly("PENDING=Pending", "IN_TRANSIT=In transit", "DELIVERED=Delivered", "LOST=Lost");
        assertThat(definition(view, "fragile").options().stream().map(o -> o.value() + "=" + o.label()))
                .containsExactly("true=Yes", "false=No");
        assertThat(definition(view, "depot").options().stream().map(o -> o.value() + "=" + o.label())).containsExactly(
                centralId + "=Central depot", secondId + "=Second depot");
        assertThat(definition(view, "courier").options()).extracting(o -> o.label()).containsExactly("Falcon");
        assertThat(definition(view, "weight").options()).isEmpty();
    }

    // ---- results and chips ------------------------------------------------------------------------------------

    @Test
    void eachFilterTypeNarrowsTheListAndTheTotal() {
        assertThat(view(open("filter.reference", "ref-1")).paging().total()).isEqualTo(11);
        assertThat(view(open("filter.status", "LOST")).paging().total()).isEqualTo(15);
        assertThat(view(open("filter.fragile", "true")).paging().total()).isEqualTo(20);
        assertThat(view(open("filter.depot", String.valueOf(centralId))).paging().total()).isEqualTo(30);
        assertThat(view(open("filter.weight.min", "10", "filter.weight.max", "19")).paging().total()).isEqualTo(10);
        assertThat(view(open("filter.shippedOn.from", "2026-01-11", "filter.shippedOn.to", "2026-01-20")).paging().total())
                .isEqualTo(10);
    }

    @Test
    void thereIsOneChipPerActiveFilterWithTheLabelAndAReadableValue() {
        ListView view = view(open("filter.status", "IN_TRANSIT", "filter.fragile", "true", "filter.depot", String.valueOf(secondId),
                "filter.weight.min", "5", "filter.weight.max", "9", "filter.shippedOn.from", "2026-01-10", "filter.reference", "REF"));

        assertThat(view.filters().chips()).extracting(c -> c.label() + ": " + c.display()).containsExactly(
                "Depot: Second depot", "Fragile: Yes", "Reference: REF", "Shipped on: from 2026-01-10", "Status: In transit",
                "Weight: 5 to 9");
        assertThat(view(open("filter.weight.min", "5")).filters().chips()).extracting(c -> c.display()).containsExactly("at least 5");
        assertThat(view(open("filter.weight.max", "5")).filters().chips()).extracting(c -> c.display()).containsExactly("at most 5");
        assertThat(view(open("filter.shippedOn.to", "2026-01-10")).filters().chips()).extracting(c -> c.display())
                .containsExactly("until 2026-01-10");
    }

    @Test
    void aChipsRemoveLinkRemovesOnlyThatFilterAndKeepsTheSearchTheSortTheSizeAndEveryOtherFilter() {
        ListView view = view(openWith("REF", "weight", "desc", "1", "10", "filter.status", "LOST", "filter.fragile", "false",
                "filter.weight.min", "5"));

        assertThat(view.filters().chips()).hasSize(3);
        for (ListView.Filters.Chip chip : view.filters().chips()) {
            Map<String, String> kept = query(chip.removeHref());
            assertThat(chip.removeHref()).startsWith("/admin/parcel?");
            assertThat(kept).as(chip.key()).containsEntry("q", "REF").containsEntry("sort", "weight").containsEntry("dir", "desc")
                    .containsEntry("size", "10").doesNotContainKey("page");
            assertThat(kept.keySet().stream().filter(k -> k.startsWith("filter."))).as(chip.key() + ": two of three stay")
                    .hasSize(chip.key().equals("weight") ? 2 : 2);
            assertThat(kept.keySet().stream().filter(k -> k.startsWith("filter." + chip.key()))).as("the removed one is gone").isEmpty();
        }
    }

    @Test
    void clearAllRemovesEveryFilterKeepsTheSearchTheSortAndTheSizeAndStartsAtTheFirstPage() {
        ListView view = view(openWith("REF", "weight", "desc", "2", "10", "filter.status", "LOST", "filter.weight.min", "5"));

        assertThat(view.filters().clearAllHref()).isEqualTo("/admin/parcel?q=REF&sort=weight&dir=desc&size=10");
    }

    @Test
    void clearAllWithNothingElseIsThePlainAddressAndTheLastRemovedFilterLeavesNoClearAll() {
        assertThat(view(open("filter.status", "LOST")).filters().clearAllHref()).isEqualTo("/admin/parcel");
        assertThat(view(open("filter.status", "LOST")).filters().chips().get(0).removeHref()).isEqualTo("/admin/parcel");
    }

    @Test
    void everyOtherLinkOfThePageKeepsTheFilters() {
        ListView view = view(openWith(null, "weight", "asc", "1", "10", "filter.status", "LOST", "filter.fragile", "false"));
        List<String> others = new ArrayList<>();
        view.columns().stream().map(ListView.Column::sortHref).filter(l -> l != null).forEach(others::add);
        for (String link : new String[] {view.paging().firstHref(), view.paging().prevHref(), view.paging().nextHref(),
                view.paging().lastHref()}) {
            if (link != null) {
                others.add(link);
            }
        }
        view.paging().sizes().forEach(size -> others.add(size.href()));

        assertThat(others).hasSizeGreaterThan(10).allSatisfy(link -> assertThat(query(link))
                .containsEntry("filter.status", "LOST").containsEntry("filter.fragile", "false"));
    }

    @Test
    void aNewSortAndANewPageSizeStartAtTheFirstPageButAPageLinkKeepsItsPage() {
        ListView view = view(openWith(null, "weight", "asc", "1", "10", "filter.fragile", "false"));

        assertThat(view.columns()).filteredOn(c -> c.sortHref() != null).allSatisfy(c -> assertThat(query(c.sortHref())).doesNotContainKey("page"));
        assertThat(view.paging().sizes()).allSatisfy(s -> assertThat(query(s.href())).doesNotContainKey("page"));
        assertThat(query(view.paging().nextHref())).containsEntry("page", "2");
        assertThat(query(view.paging().prevHref())).doesNotContainKey("page");
    }

    @Test
    void aFilteredListPagesThroughExactlyItsMatches() {
        List<String> seen = new ArrayList<>();
        ListView page = view(openWith(null, null, null, null, "10", "filter.fragile", "false"));
        assertThat(page.paging().total()).isEqualTo(40);
        while (true) {
            page.rows().forEach(row -> seen.add(row.id()));
            if (page.paging().nextHref() == null) {
                break;
            }
            Map<String, String> next = query(page.paging().nextHref());
            page = view(openWith(null, null, null, next.get("page"), next.get("size"), "filter.fragile", next.get("filter.fragile")));
        }

        assertThat(seen).hasSize(40).doesNotHaveDuplicates();
    }

    // ---- awkward values ---------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"a b", "a/b", "it's", "say \"hi\"", "ü 東京", "http://x", "a&b=c", "100%", "a+b", "x#y", "a\\b", "..", "<script>alert(1)</script>"})
    void aFilterValueWithAwkwardCharactersIsEncodedInEveryLinkAndRoundTrips(String value) {
        ListView view = view(open("filter.notes", value, "filter.reference", value));

        assertThat(view.filters().chips()).hasSize(2);
        assertThat(everyLink(view)).isNotEmpty().allSatisfy(link -> assertThat(link).matches(SAFE_LINK));
        assertThat(view.filters().definitions().stream().filter(d -> d.key().equals("notes")).findFirst().orElseThrow().value())
                .isEqualTo(value.strip());
        for (ListView.Filters.Chip chip : view.filters().chips()) {
            String other = chip.key().equals("notes") ? "filter.reference" : "filter.notes";
            assertThat(query(chip.removeHref())).containsEntry(other, value.strip());
        }
    }

    @Test
    void aLiteralHttpAddressAsAFilterValueIsDataNotALink() {
        ListView view = view(open("filter.notes", "http://x"));

        assertThat(view.filters().chips().get(0).removeHref()).isEqualTo("/admin/parcel");
        assertThat(view.filters().clearAllHref()).startsWith("/admin");
        assertThat(view.paging().sizes().get(0).href()).isEqualTo("/admin/parcel?filter.notes=http%3A%2F%2Fx&size=10");
    }

    @Test
    void theValueOfTheFirstParcelsNotesWithEveryAwkwardCharacterIsFoundByAFilterOnItsPieces() {
        assertThat(view(open("filter.notes", "http://x?a=1&b=2 #tag 100% 'q' \"d\" /\\ ü 東京")).paging().total()).isEqualTo(1);
    }

    // ---- what is told about a filter that was ignored ---------------------------------------------------------------

    @Test
    void anInvalidFilterIsIgnoredTheListIsUnfilteredAndTheNoticeNamesTheFilterAndTheReason() {
        ModelAndView result = open("filter.weight.min", "abc", "filter.status", "NOPE", "filter.shippedOn.from", "2026-02-30");

        assertThat(view(result).paging().total()).isEqualTo(60);
        assertThat(view(result).filters().chips()).isEmpty();
        assertThat(chrome(result).flash()).extracting(PageChrome.FlashMessage::text).containsExactly(
                "The Shipped on filter's 'from' date was ignored: it is not a valid date. Use yyyy-MM-dd, for example 2026-11-30.",
                "The Status filter was ignored: that is not one of its values.",
                "The Weight filter's minimum was ignored: it is not a number. Use digits, with a point for decimals.");
        assertThat(chrome(result).flash()).allSatisfy(m -> assertThat(m.level()).isEqualTo(PageChrome.FlashMessage.Level.WARNING));
    }

    @Test
    void aFromAfterAToIgnoresTheWholeDateFilterAndSaysSo() {
        ModelAndView result = open("filter.shippedOn.from", "2026-02-10", "filter.shippedOn.to", "2026-02-01");

        assertThat(view(result).filters().chips()).isEmpty();
        assertThat(chrome(result).flash()).extracting(PageChrome.FlashMessage::text)
                .containsExactly("The Shipped on filter was ignored: the 'from' date is after the 'to' date.");
    }

    @Test
    void aMinimumGreaterThanTheMaximumIgnoresTheWholeNumberFilterAndSaysSo() {
        ModelAndView result = open("filter.weight.min", "50", "filter.weight.max", "10");

        assertThat(view(result).paging().total()).isEqualTo(60);
        assertThat(chrome(result).flash()).extracting(PageChrome.FlashMessage::text)
                .containsExactly("The Weight filter was ignored: the minimum is greater than the maximum.");
    }

    @Test
    void aRelationChoiceThatDoesNotExistIsIgnoredAndSaid() {
        ModelAndView result = open("filter.depot", "424242");

        assertThat(view(result).paging().total()).isEqualTo(60);
        assertThat(chrome(result).flash()).extracting(PageChrome.FlashMessage::text)
                .containsExactly("The Depot filter was ignored: that choice is not available.");
    }

    @Test
    void aValidFilterAndAnIgnoredOneTogetherApplyTheFirstAndReportTheSecond() {
        ModelAndView result = open("filter.status", "LOST", "filter.fragile", "maybe");

        assertThat(view(result).paging().total()).isEqualTo(15);
        assertThat(view(result).filters().chips()).extracting(ListView.Filters.Chip::key).containsExactly("status");
        assertThat(chrome(result).flash()).extracting(PageChrome.FlashMessage::text)
                .containsExactly("The Fragile filter was ignored: use true, false or any.");
    }

    @Test
    void aCutTextFilterIsAppliedAndTheNoticeIsInformation() {
        ModelAndView result = open("filter.reference", "R" + "x".repeat(200));

        assertThat(chrome(result).flash()).singleElement().satisfies(message -> {
            assertThat(message.level()).isEqualTo(PageChrome.FlashMessage.Level.INFO);
            assertThat(message.text()).isEqualTo("The Reference filter was cut to 100 characters.");
        });
        assertThat(view(result).filters().chips()).hasSize(1);
    }

    @Test
    void aGoodRequestRaisesNoNotice() {
        assertThat(chrome(open("filter.status", "LOST", "filter.weight.min", "3")).flash()).isEmpty();
        assertThat(chrome(open("filter.status", "", "filter.weight.min", "", "filter.depot", "")).flash())
                .as("the blank values a form sends are no filters and no notices").isEmpty();
    }

    @Test
    void moreNotesThanTheLimitAreCountedInsteadOfListed() {
        ModelAndView result = open("filter.weight.min", "x", "filter.pieces.min", "x", "filter.status", "x", "filter.fragile", "x",
                "filter.shippedOn.from", "x", "filter.createdAt.from", "x", "filter.depot", "x1");

        List<String> texts = chrome(result).flash().stream().map(PageChrome.FlashMessage::text).toList();
        assertThat(texts).hasSize(6);
        assertThat(texts.get(5)).isEqualTo("2 more notes about the filters are not shown.");
    }

    // ---- what must never reach a page -------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"accessToken", "ACCESSTOKEN", "access_token", "memo", "internalCode", "crate", "vault", "items", "picture",
            "dimensions", "id", "nosuch"})
    void aFilterOnASensitiveHiddenOrUnknownFieldIsIgnoredIdenticallyAndShowsNoValue(String key) {
        ModelAndView result = open("filter." + key, SECRET + "1");

        assertThat(view(result).paging().total()).isEqualTo(60);
        assertThat(view(result).filters().chips()).isEmpty();
        assertThat(view(result).filters().definitions().stream().map(Definition::key)).doesNotContain(key);
        assertThat(chrome(result).flash()).extracting(PageChrome.FlashMessage::text)
                .containsExactly("1 filter in the address does not match any filter of this table and was ignored.");
        assertThat(String.valueOf(result.getModel())).doesNotContain(SECRET);
    }

    @Test
    void theRecordsHandedToTheTemplateHoldNoSensitiveValueNoHiddenFieldAndNoHiddenEntitysRow() {
        ModelAndView result = open("filter.status", "LOST", "filter.depot", String.valueOf(centralId), "filter.accessToken", SECRET,
                "filter.vault", "1");
        String everything = String.valueOf(result.getModel());

        assertThat(everything).doesNotContain(SECRET, "DEPOT-KEY", "BADGE-", "internal-", "memo ", "Deep vault name", "Parcel@");
    }

    @Test
    void hostileFilterParametersNeverMakeThePageFail() {
        for (String hostile : new String[] {"\u0000", "' or 1=1 --", "x".repeat(100_000), "\uD800", "../../etc/passwd", "${7*7}", "#{7*7}"}) {
            ModelAndView result = open("filter.weight.min", hostile, "filter.status", hostile, "filter.notes", hostile,
                    "filter.depot", hostile, "filter.shippedOn.to", hostile, "filter.accessToken", hostile);

            assertThat(result.getModel()).containsKey(PageChrome.VIEW_ATTRIBUTE);
            assertThat(chrome(result).flash().toString()).doesNotContain("etc/passwd", "7*7");
        }
    }

    // ---- the empty state ----------------------------------------------------------------------------------------

    @Test
    void aFilterThatMatchesNothingSaysSoAndOffersTheWayBack() {
        ListView view = view(open("filter.status", "LOST", "filter.weight.max", "1"));

        assertThat(view.rows()).isEmpty();
        assertThat(view.empty().reason()).isEqualTo(ListView.Empty.Reason.NO_MATCHES);
        assertThat(view.empty().hint()).isEqualTo("No parcels match the filters. Remove a filter, or clear all filters, to see more records.");
        assertThat(view.filters().clearAllHref()).isNotNull();
    }

    @Test
    void aSearchAndAFilterTogetherThatMatchNothingNameBoth() {
        ListView view = view(openWith("zzz", null, null, null, null, "filter.status", "LOST"));

        assertThat(view.empty().hint()).startsWith("No parcels match your search and filters. The search looked in these text columns: ")
                .endsWith("Check the spelling, or clear the search or a filter, to see more records.");
    }

    @Test
    void anUnfilteredListWithNoRecordsStillSaysThereAreNone() {
        ListView view = view(openWith("zzz", null, null, null, null));

        assertThat(view.empty().hint()).startsWith("No parcels match your search. ");
    }
}
