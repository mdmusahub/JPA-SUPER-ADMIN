package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 13 in the real demo application, through the real security chain and the real database (D-118 to D-124): the badge
 * classes the host chose with {@code @AdminBadge}, the relation label of {@code @AdminLookup}, the plural names in the sidebar and
 * on the dashboard, the state field the views script reads, the "Show all columns" and "Clear search" links, the keyboard hint,
 * the skip link and the one external shortcut script. The demo entity names appear here because this is the sample application
 * (ARCH-12).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PolishPagesTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AdminEntityRegistry registry;

    private MockBrowser browser;

    @BeforeEach
    void signIn() throws Exception {
        browser = MockBrowser.signedIn(mvc);
    }

    private String page(String path) throws Exception {
        MockHttpServletResponse response = browser.get(path);
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private static String attribute(String html, String name) {
        Matcher match = Pattern.compile("\\b" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(html);
        assertThat(match.find()).as(name).isTrue();
        return match.group(1).replace("&amp;", "&");
    }

    /** The class lists of the badges in the Status column's cells, in row order. */
    private static List<String> badgeClasses(String html) {
        List<String> found = new ArrayList<>();
        Matcher badge = Pattern.compile("<span class=\"badge (badge-[a-z]+)\">([^<]*)</span>").matcher(html);
        while (badge.find()) {
            found.add(badge.group(2) + "=" + badge.group(1));
        }
        return found;
    }

    // ---- @AdminBadge --------------------------------------------------------------------------------------------------

    @Test
    void theProductStatusBadgesTakeTheirClassFromTheHostsAnnotationAndNeverCarryAStyle() throws Exception {
        String html = page("/admin/product?size=100");

        assertThat(badgeClasses(html)).contains("Draft=badge-amber", "Published=badge-green", "Archived=badge-grey");
        assertThat(badgeClasses(html)).as("an archived product is grey now, not the red of the word list")
                .doesNotContain("Archived=badge-red");
        assertThat(html).doesNotContainPattern("class=\"badge[^\"]*\"[^>]*style=");
    }

    @Test
    void theDetailPageShowsTheSameBadge() throws Exception {
        String html = page("/admin/product/1");

        assertThat(badgeClasses(html)).containsExactly("Draft=badge-amber");
    }

    @Test
    void theOrderStatusWithoutAnAnnotationKeepsTheWordList() throws Exception {
        String html = page("/admin/order?size=100");

        assertThat(badgeClasses(html)).contains("New=badge-amber", "Paid=badge-green", "Shipped=badge-green",
                "Cancelled=badge-red");
    }

    // ---- @AdminLookup -------------------------------------------------------------------------------------------------

    @Test
    void theRelationFieldCarriesTheLookupTheHostWroteAndTheListShowsThatLabel() throws Exception {
        assertThat(registry.find("product").orElseThrow().visibleField("category").orElseThrow().lookup()).isEqualTo("name");
        assertThat(registry.find("order").orElseThrow().visibleField("customer").orElseThrow().lookup()).isNull();

        String html = page("/admin/product");

        assertThat(html).contains(">Books</a>", ">Electronics</a>");
    }

    // ---- plural names -------------------------------------------------------------------------------------------------

    @Test
    void theSidebarAndTheDashboardNameTheTablesInThePlural() throws Exception {
        String dashboard = page("/admin");

        assertThat(dashboard).contains(">Categories</a>", ">Customers</a>", ">Orders</a>", ">Products</a>", ">Tags</a>");
        assertThat(dashboard).containsPattern("Largest table</[^>]*>\\s*<[^>]*>\\s*Orders");
        String sidebar = dashboard.substring(dashboard.indexOf("id=\"sidebar\""), dashboard.indexOf("</aside>"));
        assertThat(sidebar).contains("Products").doesNotContain(">Product<").doesNotContain("Category<");
    }

    @Test
    void theListHeadingAndTheSearchBoxUseThePlural() throws Exception {
        String html = page("/admin/category");

        assertThat(html).contains("<h1 class=\"page-title\">Categories</h1>").contains("placeholder=\"Search categories\"");
    }

    // ---- the state field, show all columns and clear search ---------------------------------------------------------

    @Test
    void theViewsPanelGetsTheStateAndTheDefaultSizeFromTheRecord() throws Exception {
        String html = page("/admin/product?q=book&hide=sku&sort=price&dir=desc&size=10&page=0");

        assertThat(attribute(html, "data-views-state")).isEqualTo("/admin/product?q=book&hide=sku&sort=price&dir=desc&size=10");
        assertThat(attribute(html, "data-views-default-size")).isEqualTo("25");
        assertThat(attribute(html, "data-views-base")).isEqualTo("/admin/product");
    }

    @Test
    void theStateNeverCarriesAPageOrASensitiveKey() throws Exception {
        String html = page("/admin/customer?hide=password&page=0&size=10");

        assertThat(attribute(html, "data-views-state")).isEqualTo("/admin/customer?size=10");
        assertThat(attribute(html, "data-views-columns")).doesNotContain("password");
    }

    @Test
    void showAllColumnsIsALinkExactlyWhenAColumnIsHidden() throws Exception {
        assertThat(page("/admin/product")).doesNotContain("Show all columns");
        String hidden = page("/admin/product?hide=sku&hide=stock&sort=price&dir=asc&size=10&page=1");

        Matcher link = Pattern.compile("href=\"([^\"]*)\">Show all columns</a>").matcher(hidden);
        assertThat(link.find()).isTrue();
        assertThat(link.group(1).replace("&amp;", "&")).isEqualTo("/admin/product?sort=price&dir=asc&size=10");
    }

    @Test
    void aRequestThatHidesAMaskedColumnShowsNoShowAllLink() throws Exception {
        // The password column cannot be hidden, so nothing was hidden and there is nothing to show again.
        assertThat(page("/admin/customer?hide=password")).doesNotContain("Show all columns");
    }

    @Test
    void clearSearchKeepsTheHiddenColumnsAndNothingElse() throws Exception {
        String html = page("/admin/product?q=zzzz&hide=sku&sort=price&dir=asc&size=10");

        assertThat(html).contains("href=\"/admin/product?hide=sku\">Clear search</a>");
        assertThat(html).as("the empty state offers the same way back").contains("href=\"/admin/product?hide=sku\">Clear search</a>");
    }

    @Test
    void clearSearchWithNothingHiddenIsThePlainAddress() throws Exception {
        assertThat(page("/admin/product?q=zzzz&sort=price&dir=asc")).contains("href=\"/admin/product\">Clear search</a>");
    }

    // ---- the keyboard shortcut and the skip link --------------------------------------------------------------------

    @Test
    void everyListHasTheHintTheNamedShortcutTheSkipLinkAndTheOneExternalScript() throws Exception {
        for (String entity : List.of("category", "customer", "order", "product", "tag")) {
            String html = page("/admin/" + entity);

            assertThat(html).as(entity).contains("aria-keyshortcuts=\"/\"", "data-search-input",
                    "<kbd class=\"search-hint js-only\" aria-hidden=\"true\">/</kbd>",
                    "<a class=\"skip-link\" href=\"#list-search\">Skip to search</a>",
                    "<script src=\"/admin/assets/js/shortcuts.js\" defer></script>");
        }
    }

    @Test
    void thePagesWithoutASearchBoxHaveNoSkipToSearchAndNoHint() throws Exception {
        for (String path : List.of("/admin", "/admin/product/1", "/admin/product/new", "/admin/product/1/edit")) {
            assertThat(page(path)).as(path).doesNotContain("Skip to search").doesNotContain("search-hint")
                    .contains("/admin/assets/js/shortcuts.js");
        }
    }

    @Test
    void theShortcutScriptIsServedAndHoldsNothingThatTalksToTheNetworkOrStores() throws Exception {
        MockHttpServletResponse response = browser.get("/admin/assets/js/shortcuts.js");

        assertThat(response.getStatus()).isEqualTo(200);
        String code = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(code).contains("event.key !== '/'", "data-search-input").doesNotContain("fetch(").doesNotContain("XMLHttpRequest")
                .doesNotContain("localStorage").doesNotContain("document.cookie").doesNotContain("innerHTML").doesNotContain("eval(");
    }
}
