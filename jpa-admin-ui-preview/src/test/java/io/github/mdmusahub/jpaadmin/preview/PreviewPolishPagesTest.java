package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task U9 and the contract change of Phase 13 (D-118 to D-124), drawn by the real templates from records built by the real
 * factory: the state the views script reads, the "Show all columns" link, "Clear search" keeping the hidden columns, the default
 * page size in the links, the plural name, the relation label, the keyboard hint and the skip link, the shortcut script and the
 * badge classes of every tone.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreviewPolishPagesTest {

    @Autowired
    private MockMvc mvc;

    private String render(String page, String state) throws Exception {
        return mvc.perform(get("/admin-preview/" + page).param("state", state)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    private static String attribute(String html, String name) {
        Matcher match = Pattern.compile("\\b" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(html);
        assertThat(match.find()).as(name).isTrue();
        return match.group(1).replace("&amp;", "&");
    }

    static List<String> everyListState() {
        return new ArrayList<>(PageFixtures.catalog().get("list"));
    }

    // ---- the state field ---------------------------------------------------------------------------------------------

    @Test
    void theViewsPanelReadsTheStateFieldAndTheDefaultSizeNotThePageSizeLink() throws Exception {
        String html = render("list", "search-columns-hidden");

        assertThat(attribute(html, "data-views-state")).isEqualTo("/admin-preview/article?q=guide&hide=views&sort=title&dir=asc");
        assertThat(attribute(html, "data-views-default-size")).isEqualTo("25");
        assertThat(html).as("the old coupling is gone").doesNotContain("paging.sizes.^[current]");
    }

    @Test
    void theDefaultSizeOfTheConsoleReachesThePageAndTheLinks() throws Exception {
        String html = render("list", "page-size-default-50");

        assertThat(attribute(html, "data-views-default-size")).isEqualTo("50");
        assertThat(attribute(html, "data-views-state")).isEqualTo("/admin-preview/article");
        // A link for 50 rows needs no size under a default of 50; a link for 25 or 10 says so.
        assertThat(html).contains("href=\"/admin-preview/article?size=10\"", "href=\"/admin-preview/article?size=25\"",
                "href=\"/admin-preview/article\"", "href=\"/admin-preview/article?size=100\"");
        assertThat(html).doesNotContain("href=\"/admin-preview/article?size=50\"");
    }

    @Test
    void underADefaultOfFiftyAnAddressForTwentyFiveKeepsItsSize() throws Exception {
        String html = render("list", "page-size-default-50-asked-25");

        assertThat(attribute(html, "data-views-state")).isEqualTo("/admin-preview/article?size=25");
        assertThat(html).contains("Showing 1–25 of 94");
    }

    // ---- show all columns and clear search ---------------------------------------------------------------------------

    @Test
    void showAllColumnsIsALinkExactlyWhenAColumnIsHidden() throws Exception {
        assertThat(render("list", "column-hidden")).contains("class=\"btn btn-quiet\" href=\"/admin-preview/article\">Show all columns</a>");
        assertThat(render("list", "columns-hidden-most")).contains(">Show all columns</a>");
        assertThat(render("list", "hidden-middle-page")).contains("href=\"/admin-preview/article?size=10\">Show all columns</a>");
        for (String state : List.of("normal", "middle-page", "views-empty", "sorted-asc", "search-active", "empty-no-matches")) {
            assertThat(render("list", state)).as(state).doesNotContain("Show all columns");
        }
    }

    @Test
    void showAllColumnsKeepsTheSearchTheFiltersTheSortAndTheSizeButNotThePageOrTheHiddenColumns() throws Exception {
        String html = render("list", "hidden-middle-page");

        Matcher link = Pattern.compile("href=\"([^\"]*)\">Show all columns</a>").matcher(html);
        assertThat(link.find()).isTrue();
        assertThat(link.group(1)).doesNotContain("hide=").doesNotContain("page=");
    }

    @Test
    void clearSearchKeepsTheHiddenColumnsAndNothingElse() throws Exception {
        String html = render("list", "search-columns-hidden");

        assertThat(html).contains("href=\"/admin-preview/article?hide=views\">Clear search</a>");
        assertThat(html).doesNotContain("href=\"/admin-preview/article\">Clear search");
    }

    @Test
    void clearSearchWithNothingHiddenIsThePlainAddress() throws Exception {
        assertThat(render("list", "search-active")).contains("href=\"/admin-preview/article\">Clear search</a>");
    }

    @Test
    void theEmptyStatesOfASearchClearItTheSameWay() throws Exception {
        String html = render("list", "empty-no-matches");

        assertThat(html).contains("href=\"/admin-preview/article\">Clear search</a>");
    }

    // ---- the plural and the relation label ---------------------------------------------------------------------------

    @Test
    void thePluralNameIsInTheHeadingTheSearchBoxTheBreadcrumbAndTheEmptyState() throws Exception {
        String html = render("list", "plural-people");

        assertThat(html).contains("<h1 class=\"page-title\">People</h1>").contains("placeholder=\"Search people\"")
                .contains("There are no people yet.").doesNotContain("Persons");
    }

    @Test
    void aRelationShowsTheLabelTheHostChoseAndTheRecordsNumberWhenItHasNone() throws Exception {
        String html = render("list", "relation-lookup");

        assertThat(html).contains(">MEM-0017</a>", ">MEM-0034</a>", ">#43</a>");
    }

    // ---- the keyboard shortcut and the skip link ---------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("everyListState")
    void everyListHasTheSearchBoxHintTheNamedShortcutAndTheSkipLink(String state) throws Exception {
        String html = render("list", state);

        assertThat(html).as(state).contains("id=\"list-search\"").contains("aria-keyshortcuts=\"/\"")
                .contains("data-search-input");
        assertThat(html).as(state).contains("<kbd class=\"search-hint js-only\" aria-hidden=\"true\">/</kbd>");
        assertThat(html).as(state).contains("<a class=\"skip-link\" href=\"#list-search\">Skip to search</a>");
        assertThat(html.indexOf("Skip to content")).as("the skip links come first").isLessThan(html.indexOf("Skip to search"));
        assertThat(html.indexOf("Skip to search")).isLessThan(html.indexOf("id=\"sidebar\""));
    }

    @Test
    void thePagesWithoutASearchBoxHaveNoSearchSkipLinkOrHint() throws Exception {
        for (String[] page : new String[][] {{"dashboard", "normal"}, {"detail", "normal"}, {"form", "create-empty"}, {"error", "not-found"}}) {
            String html = render(page[0], page[1]);
            assertThat(html).as(page[0]).doesNotContain("Skip to search").doesNotContain("search-hint")
                    .doesNotContain("data-search-input");
            assertThat(html).as(page[0]).contains("Skip to content");
        }
    }

    @Test
    void theShortcutScriptIsOneExternalDeferredFileOnEveryPageAndNoPageHasAnInlineScript() throws Exception {
        for (String[] page : new String[][] {{"list", "normal"}, {"dashboard", "normal"}, {"detail", "normal"}, {"form", "create-empty"},
                {"error", "not-found"}}) {
            String html = render(page[0], page[1]);
            assertThat(html).as(page[0]).contains("<script src=\"/admin-preview/assets/js/shortcuts.js\" defer></script>");
            assertThat(html).as(page[0]).doesNotContainPattern("<script(?![^>]*\\ssrc=)[^>]*>");
            assertThat(html).as(page[0]).doesNotContainPattern("\\son[a-z]+=\"");
        }
    }

    // ---- the badge tones ---------------------------------------------------------------------------------------------

    @Test
    void everyToneOfThePaletteHasItsBadgeClass() throws Exception {
        String html = render("list", "all-cell-types");

        assertThat(html).contains("class=\"badge badge-green\"", "class=\"badge badge-amber\"", "class=\"badge badge-red\"",
                "class=\"badge badge-grey\"");
        assertThat(html).as("a tone is a class name, never a style").doesNotContainPattern("badge[^>]*style=");
    }
}
