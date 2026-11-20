package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task U3 in the preview: every filter fixture of {@code docs/FIXTURES.md} section 5 renders and shows its key content.
 * The panel is checked as the real templates print it: one labelled control for each definition, in the list's own GET
 * form; the chips with their remove links and the clear-all link, which the real factory built; the notices; the empty
 * states; and that no value is printed unescaped. The generic rules (labels, nothing inline, no external address) are
 * checked for every fixture in every chrome variant by {@code PreviewPagesTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreviewFilterPagesTest {

    @Autowired
    private MockMvc mvc;

    private String render(String state) throws Exception {
        return mvc.perform(get("/admin-preview/list").param("state", state)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String flat(String html) {
        return html.replaceAll("\\s+", " ");
    }

    private static String between(String html, String start, String end) {
        int from = html.indexOf(start);
        assertThat(from).as("the start " + start).isGreaterThanOrEqualTo(0);
        int to = html.indexOf(end, from);
        assertThat(to).as("the end " + end).isGreaterThan(from);
        return html.substring(from, to + end.length());
    }

    private static String panel(String html) {
        return between(html, "<details class=\"filter-panel\"", "</details>");
    }

    private static String chips(String html) {
        return between(html, "<div class=\"filter-chips\"", "</ul>");
    }

    private static List<String> hrefs(String html, String cssClass) {
        List<String> found = new ArrayList<>();
        Matcher matcher = Pattern.compile("<a class=\"" + cssClass + "\" href=\"([^\"]*)\"").matcher(html);
        while (matcher.find()) {
            found.add(matcher.group(1).replace("&amp;", "&"));
        }
        return found;
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

    private static int count(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    // ---- the panel --------------------------------------------------------------------------------------------

    @Test
    void theSearchAndTheFiltersAreOneGetFormThatWorksWithoutScripts() throws Exception {
        String html = render("filters-open");
        String form = between(html, "<form class=\"list-toolbar\"", "</form>");

        assertThat(flat(form)).startsWith("<form class=\"list-toolbar\" method=\"get\" role=\"search\" data-filter-form action=\"/admin-preview/article\" aria-label=\"Search and filter articles\">")
                .contains("name=\"q\"", "name=\"filter.title\"", "name=\"filter.author\"", "name=\"filter.status\"", "name=\"filter.views.min\"",
                        "name=\"filter.views.max\"", "name=\"filter.published.from\"", "name=\"filter.published.to\"", "name=\"filter.featured\"")
                .contains("<button type=\"submit\" class=\"btn btn-primary\">Apply filters</button>");
        assertThat(html).doesNotContain("method=\"post\" role=\"search\"");
    }

    @Test
    void theFiltersAreADetailsElementSoTheyOpenWithoutScriptsAndAreClosedByDefault() throws Exception {
        String html = render("filters-open");

        assertThat(html).contains("<details class=\"filter-panel\">").doesNotContain("<details class=\"filter-panel\" open");
        assertThat(flat(panel(html))).contains("<summary class=\"btn filter-summary\"> <span>Filters</span>");
        assertThat(panel(html)).doesNotContain("filter-count");
    }

    @Test
    void everyControlHasALabelAndTheRightKindOfInput() throws Exception {
        String panel = flat(panel(render("filters-open")));

        assertThat(panel).contains("<label class=\"filter-label\" for=\"filter-title\">Title</label>")
                .contains("<input class=\"input\" type=\"text\" maxlength=\"100\" autocomplete=\"off\" id=\"filter-title\" name=\"filter.title\" value=\"\">")
                .contains("<label class=\"filter-label\" for=\"filter-author\">Author</label>", "<label class=\"filter-label\" for=\"filter-status\">Status</label>",
                        "<label class=\"filter-label\" for=\"filter-featured\">Featured</label>")
                .contains("<legend class=\"filter-label\">Views</legend>", "<legend class=\"filter-label\">Published</legend>")
                .contains("type=\"number\" step=\"any\" inputmode=\"decimal\" id=\"filter-views-min\" name=\"filter.views.min\"", "type=\"date\" id=\"filter-published-from\" name=\"filter.published.from\"");
        assertThat(count(panel, "<option value=\"\">Any</option>")).as("every select starts with Any").isEqualTo(3);
        assertThat(count(panel, "<fieldset")).isEqualTo(2);
        assertThat(panel).contains("for=\"filter-views-min\">Min<span class=\"visually-hidden\"> (Views)</span></label>",
                "for=\"filter-views-max\">Max<span class=\"visually-hidden\"> (Views)</span></label>",
                "for=\"filter-published-from\">From<span class=\"visually-hidden\"> (Published)</span></label>",
                "for=\"filter-published-to\">To<span class=\"visually-hidden\"> (Published)</span></label>");
    }

    @Test
    void enumAndBooleanAndRelationControlsOfferTheirChoices() throws Exception {
        String panel = flat(panel(render("filters-open")));

        assertThat(panel).contains("<option value=\"PUBLISHED\">Published</option>", "<option value=\"IN_REVIEW\">In review</option>",
                "<option value=\"true\">Yes</option>", "<option value=\"false\">No</option>", "<option value=\"1\">Ava Stone</option>");
    }

    @Test
    void noChipsAndNoClearAllWhenNothingIsFiltered() throws Exception {
        String html = render("filters-open");

        assertThat(html).doesNotContain("filter-chips", "Clear all filters", "chip-remove");
    }

    @Test
    void theScriptThatKeepsTheAddressShortIsAFileNotInlineAndTheOnlyScriptTheFiltersNeed() throws Exception {
        String html = render("filters-open");

        assertThat(html).contains("<script src=\"/admin-preview/assets/js/filters.js\" defer></script>");
        assertThat(html).doesNotContain("onclick=", "onchange=", "onsubmit=");
        assertThat(html).containsPattern("<script src=\"[^\"]+\"( defer)?></script>");
        assertThat(html).doesNotContainPattern("<script>[^<]");
    }

    // ---- active filters -----------------------------------------------------------------------------------------

    @Test
    void aTextFilterShowsItsValueInTheControlAndAChipAndTheCountOnTheSummary() throws Exception {
        String html = render("filter-text");

        assertThat(flat(panel(html))).contains("name=\"filter.title\" value=\"guide\"")
                .contains("<span class=\"filter-count\" aria-label=\"1 active\">1</span>");
        assertThat(flat(chips(html))).contains("<span class=\"chip-label\">Title:</span>", "<span class=\"chip-value\">guide</span>");
        assertThat(hrefs(html, "chip-remove")).containsExactly("/admin-preview/article");
    }

    @Test
    void anEnumAndABooleanAndARelationFilterSelectTheirChoice() throws Exception {
        assertThat(flat(panel(render("filter-enum")))).contains("<option value=\"IN_REVIEW\" selected=\"selected\">In review</option>");
        assertThat(flat(chips(render("filter-enum")))).contains("<span class=\"chip-value\">In review</span>");
        assertThat(flat(panel(render("filter-boolean")))).contains("<option value=\"true\" selected=\"selected\">Yes</option>");
        assertThat(flat(chips(render("filter-boolean")))).contains("Featured:", "<span class=\"chip-value\">Yes</span>");
        assertThat(flat(panel(render("filter-relation")))).contains("<option value=\"3\" selected=\"selected\">Chloe Reed</option>");
        assertThat(flat(chips(render("filter-relation")))).contains("Author:", "<span class=\"chip-value\">Chloe Reed</span>");
    }

    @Test
    void aNumberRangeFillsBothBoundsAndTheChipSaysItInWords() throws Exception {
        assertThat(flat(panel(render("filter-number-range")))).contains("name=\"filter.views.min\" value=\"1000\"")
                .contains("name=\"filter.views.max\" value=\"5000\"");
        assertThat(flat(chips(render("filter-number-range")))).contains("<span class=\"chip-value\">1000 to 5000</span>");
        String minOnly = render("filter-number-min-only");
        assertThat(flat(panel(minOnly))).contains("name=\"filter.views.min\" value=\"8000\"").contains("name=\"filter.views.max\" value=\"\"");
        assertThat(flat(chips(minOnly))).contains("<span class=\"chip-value\">at least 8000</span>");
    }

    @Test
    void aDateRangeFillsBothBoundsAndTheChipSaysItInWords() throws Exception {
        String html = render("filter-date-range");

        assertThat(flat(panel(html))).contains("name=\"filter.published.from\" value=\"2026-09-01\"")
                .contains("name=\"filter.published.to\" value=\"2026-10-15\"");
        assertThat(flat(chips(html))).contains("<span class=\"chip-value\">2026-09-01 to 2026-10-15</span>");
    }

    @Test
    void everyChipHasARemoveLinkWithAnAccessibleNameAndThereIsAClearAllLink() throws Exception {
        String html = render("filters-active");
        String chips = flat(chips(html));

        assertThat(count(chips, "class=\"chip\"")).isEqualTo(6);
        assertThat(count(chips, "class=\"chip-remove\"")).isEqualTo(6);
        assertThat(chips).contains("aria-label=\"Remove filter Title: report\"", "aria-label=\"Remove filter Status: Published\"",
                "aria-label=\"Remove filter Views: 100 to 8000\"", "aria-label=\"Remove filter Published: from 2026-01-01\"",
                "aria-label=\"Remove filter Featured: No\"", "aria-label=\"Remove filter Author: Ben Carter\"");
        assertThat(flat(html)).contains("<a class=\"btn btn-quiet\" href=\"/admin-preview/article\">Clear all filters</a>");
        assertThat(flat(panel(html))).contains("<span class=\"filter-count\" aria-label=\"6 active\">6</span>")
                .contains("<a class=\"btn btn-quiet\" href=\"/admin-preview/article\">Clear all filters</a>");
    }

    @Test
    void aChipsRemoveLinkRemovesOnlyThatFilterAndKeepsTheSearchTheSortAndTheSize() throws Exception {
        String html = render("filter-combined");
        List<String> removes = hrefs(chips(html), "chip-remove");

        assertThat(removes).hasSize(3);
        for (String remove : removes) {
            Map<String, String> kept = query(remove);
            assertThat(kept).containsEntry("q", "report").containsEntry("sort", "views").containsEntry("dir", "desc")
                    .containsEntry("size", "10").doesNotContainKey("page");
            assertThat(kept.keySet().stream().filter(k -> k.startsWith("filter.")).map(k -> k.split("[.]")[1]).distinct())
                    .as("two of the three filters stay").hasSize(2);
        }
        assertThat(removes.stream().map(r -> query(r).keySet().stream().filter(k -> k.startsWith("filter.")).map(k -> k.split("[.]")[1]).sorted().toList()).distinct())
                .as("each link removes a different filter").hasSize(3);
        assertThat(hrefs(html, "btn btn-quiet")).contains("/admin-preview/article?q=report&sort=views&dir=desc&size=10");
    }

    @Test
    void everyOtherLinkOnAFilteredPageKeepsTheFiltersAndOnlyThePageLinksKeepThePage() throws Exception {
        String html = render("filter-middle-page");

        assertThat(html).contains("filter.status=PUBLISHED");
        List<String> sorts = new ArrayList<>();
        Matcher sort = Pattern.compile("<a class=\"sort-link\" href=\"([^\"]*)\"").matcher(html);
        while (sort.find()) {
            sorts.add(sort.group(1).replace("&amp;", "&"));
        }
        assertThat(sorts).hasSize(7).allSatisfy(link -> assertThat(query(link)).containsEntry("filter.status", "PUBLISHED")
                .doesNotContainKey("page"));
        List<String> sizes = new ArrayList<>();
        Matcher size = Pattern.compile("<a class=\"page-size\" href=\"([^\"]*)\"").matcher(html);
        while (size.find()) {
            sizes.add(size.group(1).replace("&amp;", "&"));
        }
        assertThat(sizes).hasSize(3).allSatisfy(link -> assertThat(query(link)).containsEntry("filter.status", "PUBLISHED")
                .doesNotContainKey("page"));
        Matcher next = Pattern.compile("<a class=\"icon-button\" href=\"([^\"]*)\" aria-label=\"Next page\"").matcher(html);
        assertThat(next.find()).isTrue();
        assertThat(query(next.group(1).replace("&amp;", "&"))).containsEntry("filter.status", "PUBLISHED").containsEntry("page", "2");
    }

    // ---- notices and empty states ---------------------------------------------------------------------------------

    @Test
    void theNoticesAboutIgnoredFiltersAreAlertsAndAKeptValueIsInformation() throws Exception {
        String html = flat(render("filter-ignored"));

        assertThat(html).contains("The Views filter&#39;s minimum was ignored: it is not a number. Use digits, with a point for decimals.",
                "The Published filter was ignored: the &#39;from&#39; date is after the &#39;to&#39; date.",
                "The Status filter was ignored: that is not one of its values.",
                "2 filters in the address do not match any filter of this table and were ignored.");
        assertThat(html).contains("<div class=\"toast toast-warning\" role=\"alert\">", "<div class=\"toast toast-info\" role=\"status\">");
        assertThat(html).contains("The Title filter was cut to 100 characters.");
    }

    @Test
    void aFilterThatMatchesNothingSaysSoAndOffersClearAll() throws Exception {
        String html = flat(render("filter-no-matches"));

        assertThat(html).contains("<h2>No matching records</h2>",
                "No articles match the filters. Remove a filter, or clear all filters, to see more records.");
        assertThat(count(html, ">Clear all filters</a>")).as("in the chips, in the panel and in the empty state").isEqualTo(3);
        assertThat(html).doesNotContain(">Clear search</a>", "Clear search and filters");
    }

    @Test
    void aSearchAndAFilterThatMatchNothingOfferBothWaysBack() throws Exception {
        String html = flat(render("filter-no-matches-with-search"));

        assertThat(html).contains("No articles match your search and filters.", ">Clear search and filters</a>", ">Clear all filters</a>");
    }

    @Test
    void aRelationControlWithTheMostChoicesTheConsoleListsStillRendersAsOneSelect() throws Exception {
        String html = render("relation-many-options");
        String select = between(panel(html), "<select class=\"input\" id=\"filter-author\"", "</select>");

        assertThat(count(select, "<option")).as("Any plus 200 choices").isEqualTo(201);
        assertThat(count(panel(html), "<select")).isEqualTo(3);
    }

    // ---- escaping and what is absent ----------------------------------------------------------------------------------

    @Test
    void aRelationDropdownListsEveryChoiceAndEscapesTheLabels() throws Exception {
        String html = render("filter-relation-dropdown");
        String select = between(panel(html), "<select class=\"input\" id=\"filter-author\"", "</select>");

        assertThat(count(select, "<option")).isEqualTo(41);
        assertThat(select).contains("&lt;b&gt;Bold&lt;/b&gt; &amp; &quot;quoted&quot; &lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("<option value=\"2\" selected=\"selected\">").contains("Ünïcödé 東京 Платёж");
        assertThat(html).doesNotContain("<script>alert(1)</script>", "<b>Bold</b>");
    }

    @Test
    void valuesThatLookLikeMarkupAreEscapedInTheControlTheChipAndTheLabel() throws Exception {
        String html = render("filter-chips-escaped");

        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(flat(chips(html))).contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;quotes&quot; 東京")
                .contains("aria-label=\"Remove filter Title: &lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;quotes&quot; 東京\"");
        assertThat(panel(html)).contains("value=\"&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;quotes&quot; 東京\"");
        assertThat(hrefs(html, "chip-remove")).containsExactly("/admin-preview/article");
    }

    @Test
    void aMaskedColumnHasNoFilterAndTheWordPasswordIsNotInThePanel() throws Exception {
        String html = render("filter-sensitive-absent");

        assertThat(html).as("the masked column is still a column").contains(">Password</span>");
        assertThat(panel(html).toLowerCase()).doesNotContain("password", "filter.password");
        assertThat(flat(panel(html))).contains("name=\"filter.name\"", "name=\"filter.email\"", "name=\"filter.joined.from\"");
        assertThat(html).doesNotContain("filter.password");
    }

    @Test
    void aRelationToAHiddenEntityAndAMaskedColumnHaveNoControlAndNothingOfTheHiddenEntityIsListed() throws Exception {
        String html = render("filter-hidden-entity-absent");
        String panel = panel(html);

        assertThat(panel).doesNotContain("Reviewer", "reviewer", "Api key", "#10", "filter.apiKey");
        assertThat(panel).contains("name=\"filter.title\"", "id=\"filter-author\"");
        assertThat(count(panel, "<option")).as("Any plus six authors").isEqualTo(7);
        assertThat(html).contains("#101");
    }

    @Test
    void everyFilterFixtureRendersWithItsPanel() throws Exception {
        for (String state : ListFixtures.states()) {
            if (state.startsWith("filter")) {
                String html = render(state);
                assertThat(html).as(state).contains("<h1 class=\"page-title\">");
                if (!state.equals("filter-sensitive-absent") && !state.equals("filter-hidden-entity-absent")) {
                    assertThat(html).as(state).contains("<details class=\"filter-panel\"");
                }
            }
        }
    }
}
