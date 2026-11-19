package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task U7: the dashboard's chart card in every state of {@code docs/FIXTURES.md} section 4 (the real template, drawn by the
 * real geometry): the range switch, the stacked bars, the legend, the daily table, the notes, the entities left out, and the
 * empty and unavailable states. The card needs no script, so none is checked here; what is checked is that it carries none.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreviewChartPagesTest {

    @Autowired
    private MockMvc mvc;

    private String render(String state) throws Exception {
        return mvc.perform(get("/admin-preview/dashboard").param("state", state)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    private static int count(String text, String needle) {
        int found = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            found++;
        }
        return found;
    }

    /** The chart card only. */
    private static String card(String html) {
        Matcher card = Pattern.compile("(?s)<section class=\"card chart-card\".*?</section>").matcher(html);
        assertThat(card.find()).as("a chart card").isTrue();
        return card.group();
    }

    // ---- the catalog ------------------------------------------------------------------------------------------------

    @Test
    void theChartStatesAreInTheDashboardCatalog() {
        assertThat(PageFixtures.catalog().get("dashboard")).contains("normal", "no-chart", "chart-range-7",
                "chart-range-90", "chart-single-entity", "chart-one-unavailable", "chart-all-unavailable", "chart-all-zero",
                "chart-spike", "chart-large-counts", "chart-long-labels", "chart-range-fallback", "chart-capped",
                "chart-many-entities", "chart-new-year");
    }

    // ---- the normal card --------------------------------------------------------------------------------------------

    @Test
    void theNormalCardHasTheTitleTheSwitchTheChartTheLegendAndTheTable() throws Exception {
        String html = render("normal");
        String card = card(html);

        assertThat(card).contains("id=\"chart\"", "aria-labelledby=\"chart-title\"", ">Records created<")
                .contains("Last 30 days, per day")
                .contains("<nav class=\"range-switch\" aria-label=\"Chart range\">")
                .contains("<svg class=\"chart-svg\"", "viewBox=\"0 0 300 1000\"", "role=\"img\"",
                        "aria-labelledby=\"chart-svg-title chart-svg-desc\"", "<title id=\"chart-svg-title\">Records created per day</title>")
                .contains("Not in the chart: <span>Tags</span>")
                .contains("<details class=\"chart-details\">", "<summary>Daily counts</summary>",
                        "aria-label=\"Daily counts table\"");
        assertThat(count(card, "<g class=\"chart-bar\">")).isEqualTo(30);
        assertThat(card).contains(">Comments<", ">Articles<", ">Members<");
        assertThat(html.indexOf("class=\"stat-cards\"")).isLessThan(html.indexOf("class=\"card chart-card\""));
        assertThat(html.indexOf("class=\"card chart-card\"")).isLessThan(html.indexOf("class=\"group-cards\""));
    }

    @Test
    void theRangeSwitchIsThreePlainLinksBuiltByTheBackendWithTheCurrentOneMarked() throws Exception {
        String card = card(render("normal"));

        assertThat(card).contains("<a class=\"range-link\" href=\"/admin-preview/dashboard?range=7\">7 days</a>")
                .contains("<a class=\"range-link is-current\" href=\"/admin-preview/dashboard?range=30\" aria-current=\"true\">30 days</a>")
                .contains("<a class=\"range-link\" href=\"/admin-preview/dashboard?range=90\">90 days</a>");
        assertThat(count(card, "aria-current")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 90})
    void theOtherRangesDrawOneBarPerDayAndMarkTheirOwnLink(int range) throws Exception {
        String card = card(render("chart-range-" + range));

        assertThat(count(card, "<g class=\"chart-bar\">")).isEqualTo(range);
        assertThat(card).contains("viewBox=\"0 0 " + range * 10 + " 1000\"", "Last " + range + " days, per day")
                .contains("is-current\" href=\"/admin-preview/dashboard?range=" + range + "\" aria-current=\"true\">");
        assertThat(count(card, "aria-current")).isEqualTo(1);
    }

    @Test
    void everyBarHasATooltipAndTheTableHasOneRowPerDay() throws Exception {
        String card = card(render("normal"));

        assertThat(count(card, "<title>")).as("one per bar").isEqualTo(30);
        assertThat(card).contains("<title>Nov 18: ");
        assertThat(count(card, "<th scope=\"row\">")).isEqualTo(30 + 1 /* the total row */);
        assertThat(card).contains("<th scope=\"row\">2026-11-18</th>", "<th scope=\"row\">Total</th>");
    }

    @Test
    void theAxisAndTheScaleAreDecorationForScreenReaders() throws Exception {
        String card = card(render("normal"));

        assertThat(card).contains("<div class=\"chart-y\" aria-hidden=\"true\">", "<div class=\"chart-axis\" aria-hidden=\"true\">")
                .contains("<span>Oct 20</span>", "<span>Nov 3</span>", "<span>Nov 18</span>");
    }

    @Test
    void theLegendNamesEveryEntityWithItsTotalAndAColourClass() throws Exception {
        String card = card(render("normal"));

        assertThat(card).contains("<ul class=\"chart-legend\" aria-label=\"Entities in the chart\">")
                .contains("chart-swatch chart-tone-0", "chart-swatch chart-tone-1", "chart-swatch chart-tone-2");
        Matcher totals = Pattern.compile("<span class=\"chart-legend-total\">([^<]*) created</span>").matcher(card);
        List<String> found = new java.util.ArrayList<>();
        while (totals.find()) {
            found.add(totals.group(1));
        }
        assertThat(found).hasSize(3).allSatisfy(total -> assertThat(total).matches("\\d{1,3}(,\\d{3})*"));
    }

    @Test
    void theCardCarriesNoScriptNoStyleAndNoHandler() throws Exception {
        for (String state : PageFixtures.catalog().get("dashboard")) {
            String card = state.equals("no-chart") ? "" : safeCard(render(state));
            assertThat(card).as(state).doesNotContain("<script").doesNotContain(" style=").doesNotContain("<style")
                    .doesNotContain(" onclick=").doesNotContain("javascript:").doesNotContainPattern("(?i)\\son[a-z]+=");
        }
    }

    private static String safeCard(String html) {
        return html.contains("class=\"card chart-card\"") ? card(html) : "";
    }

    @Test
    void theCardHasNoFormAndNoForm_insideAForm() throws Exception {
        String html = render("normal");

        assertThat(HtmlRules.renderedViolations(html)).isEmpty();
        assertThat(card(html)).doesNotContain("<form");
    }

    // ---- states -----------------------------------------------------------------------------------------------------

    @Test
    void whenNoEntityHasACreationFieldThereIsNoCardAndOneSentenceSaysWhy() throws Exception {
        String html = render("no-chart");

        assertThat(html).doesNotContain("chart-card").doesNotContain("id=\"chart\"").doesNotContain("range-switch")
                .doesNotContain("<svg class=\"chart-svg\"");
        assertThat(html).contains("id=\"chart-absent\"", "There is no chart: no entity has a creation date field")
                .contains("<code>createdAt</code>", "<code>createdDateTime</code>");
    }

    @Test
    void aConsoleWithoutEntitiesShowsNeitherTheCardNorTheSentence() throws Exception {
        String html = render("no-entities");

        assertThat(html).doesNotContain("chart-card").doesNotContain("chart-absent");
    }

    @Test
    void anUnavailableEntityIsMarkedAndTheOthersStillDraw() throws Exception {
        String card = card(render("chart-one-unavailable"));

        assertThat(card).contains("<span class=\"chart-legend-total chart-unavailable\">unavailable</span>")
                .contains("The creation dates of Articles could not be read, so it is not in the chart.")
                .contains("<ul class=\"chart-notes\">", "<svg class=\"chart-svg\"");
        assertThat(count(card, "chart-unavailable")).isEqualTo(1);
        assertThat(card).contains("<td class=\"align-end\">unavailable</td>");
        assertThat(count(card, " created</span>")).isEqualTo(2);
        assertThat(card).doesNotContain("chart-tone-1\" x=");
    }

    @Test
    void whenNoEntityCanBeReadTheCardSaysSoAndDrawsNothing() throws Exception {
        String card = card(render("chart-all-unavailable"));

        assertThat(card).contains("The creation dates could not be read just now, so there is nothing to draw.")
                .doesNotContain("<svg class=\"chart-svg\"").doesNotContain("Nothing to draw: no record");
        assertThat(count(card, "chart-unavailable")).isEqualTo(3);
        assertThat(card).contains("<a class=\"range-link", "aria-current=\"true\"");
    }

    @Test
    void whenNothingWasCreatedTheCardSaysSoInsteadOfAFlatChart() throws Exception {
        String card = card(render("chart-all-zero"));

        assertThat(card).contains("Nothing to draw: no record was created in the last 30 days.")
                .doesNotContain("<svg class=\"chart-svg\"").doesNotContain("could not be read just now");
        assertThat(card).contains("0 created");
    }

    @Test
    void theRangeFallbackIsSaidInTheCard() throws Exception {
        String card = card(render("chart-range-fallback"));

        assertThat(card).contains("<ul class=\"chart-notes\">")
                .contains("That range is not available, so the last 30 days are shown. Choose 7, 30 or 90 days.");
    }

    @Test
    void anIncompleteEntityHasAFlagAndANote() throws Exception {
        String card = card(render("chart-capped"));

        assertThat(card).contains("<span class=\"chart-legend-flag\">incomplete</span>")
                .contains("The counts for Comments up to and including 2026-10-02 are incomplete")
                .contains("more than 50,000 records in the last 90 days");
        assertThat(count(card, "chart-legend-flag")).isEqualTo(1);
    }

    @Test
    void aVeryLargeCountIsPrintedWithThousandsSeparators() throws Exception {
        String card = card(render("chart-spike"));

        assertThat(card).contains("48,0").contains("<span>48,0");
        String big = card(render("chart-large-counts"));
        assertThat(big).containsPattern("\\d,\\d{3},\\d{3} created");
        assertThat(big).containsPattern("<span>\\d,\\d{3},\\d{3}</span>");
    }

    @Test
    void aSingleEntityHasOneLegendItem() throws Exception {
        String card = card(render("chart-single-entity"));

        assertThat(count(card, "chart-legend-item")).isEqualTo(1);
        assertThat(card).doesNotContain("chart-tone-1");
    }

    @Test
    void manyEntitiesReuseTheEightColoursAndTheLegendWraps() throws Exception {
        String card = card(render("chart-many-entities"));

        assertThat(count(card, "chart-legend-item")).isEqualTo(12);
        assertThat(count(card, "chart-swatch chart-tone-0")).isEqualTo(2);
        assertThat(count(card, "chart-swatch chart-tone-7")).isEqualTo(1);
        assertThat(card).doesNotContain("chart-tone-8").doesNotContain("chart-tone-11");
    }

    @Test
    void longLabelsAreShownInFullAndEscaped() throws Exception {
        String card = card(render("chart-long-labels"));

        assertThat(card).contains("Customer support conversation transcripts from the regional operations team")
                .contains("Supercalifragilisticexpialidocious_records_with_an_unbreakable_name");
        assertThat(card).contains("Not in the chart: <span>Customer support conversation transcripts from the regional operations team and more</span>");
    }

    @Test
    void aRangeAcrossNewYearGivesTheAxisItsYear() throws Exception {
        String card = card(render("chart-new-year"));

        assertThat(card).contains("<span>Dec 10, 2026</span>", "<span>Jan 8, 2027</span>");
    }

    // ---- the stylesheet ---------------------------------------------------------------------------------------------

    @Test
    void theStylesheetHasEightTonesMixedFromTheAccentAndNoColourValue() throws Exception {
        String css = new String(PreviewChartPagesTest.class.getResourceAsStream("/jpa-admin/assets/css/pages.css")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int start = css.indexOf("dashboard chart: records created");
        String chart = css.substring(start);

        for (int tone = 0; tone < 8; tone++) {
            assertThat(chart).contains(".chart-tone-" + tone + " {");
        }
        assertThat(chart).doesNotContain(".chart-tone-8");
        Matcher tones = Pattern.compile("(?m)^\\.chart-tone-\\d \\{(.*)}$").matcher(chart);
        int found = 0;
        while (tones.find()) {
            found++;
            assertThat(tones.group(1)).contains("var(--ac)").doesNotContainPattern("#[0-9a-fA-F]{3,6}")
                    .doesNotContain("rgb(");
        }
        assertThat(found).isEqualTo(8);
        assertThat(chart).contains(":focus-visible");
        assertThat(chart).contains("@media (max-width: 600px)");
    }
}
