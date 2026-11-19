package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task U8, the export part (Phase 11, D-110): the export button in the list header in every state of
 * {@code docs/FIXTURES.md} (offered with the current search, filters and sort; absent while the other actions stay; and the
 * notice about the cap), drawn by the real template from records built by the real factory. The button is a plain link, so no
 * script is involved and none is checked beyond the rules that every page follows.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreviewExportPagesTest {

    private static final Pattern EXPORT_LINK = Pattern.compile("(?s)<a class=\"btn export-link\"[^>]*>.*?</a>");

    @Autowired
    private MockMvc mvc;

    private String render(String state) throws Exception {
        return mvc.perform(get("/admin-preview/list").param("state", state)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    private static String button(String html) {
        Matcher link = EXPORT_LINK.matcher(html);
        assertThat(link.find()).as("an export button").isTrue();
        return link.group();
    }

    // ---- the catalog -------------------------------------------------------------------------------------------------

    @Test
    void theExportStatesAreInTheListCatalog() {
        assertThat(PageFixtures.catalog().get("list")).contains("export-enabled", "export-disabled", "export-limited");
    }

    // ---- enabled: idle state ----------------------------------------------------------------------------------------------

    @Test
    void theEnabledStateOffersALinkWithTheCurrentSearchFiltersAndSortAndNoPage() throws Exception {
        String html = render("export-enabled");

        assertThat(button(html)).contains("href=\"/admin-preview/article/export.csv?q=guide&amp;filter.status=PUBLISHED&amp;sort=views&amp;dir=desc\"");
        assertThat(button(html)).doesNotContain("page=", "size=");
    }

    @Test
    void theButtonHasItsWordsAnIconAndAnAccessibleNameThatContainsTheVisibleText() throws Exception {
        String button = button(render("export-enabled"));

        assertThat(button).contains("<span>Export CSV</span>", "aria-label=\"Export CSV of the matching records\"",
                "title=\"Download the records that match the current search and filters as a CSV file\"");
        assertThat(button).as("the icon is decoration").contains("<svg class=\"icon\"", "aria-hidden=\"true\"", "focusable=\"false\"");
    }

    @Test
    void theButtonIsAPlainLinkNotAFormAButtonOrAScript() throws Exception {
        String html = render("export-enabled");
        String button = button(html);

        assertThat(button).startsWith("<a ").endsWith("</a>").doesNotContain("<button", "<form", "onclick", "data-");
        assertThat(button).doesNotContain("disabled", "aria-disabled");
    }

    @Test
    void theNormalFixtureOffersExportAndANewRecordAsLinks() throws Exception {
        String html = render("normal");

        assertThat(button(html)).contains("href=\"/admin-preview/article/export.csv\"");
        assertThat(html).contains("href=\"/admin-preview/article/new\">New record</a>");
    }

    @Test
    void anActiveSearchAndSortAreCarriedByTheNormalListsLink() throws Exception {
        assertThat(button(render("search-active"))).contains("export.csv?q=guide&amp;sort=title&amp;dir=asc");
        assertThat(button(render("sorted-desc"))).contains("export.csv?sort=views&amp;dir=desc");
    }

    @Test
    void theLinkOfAPageThatIsNotTheFirstStillHasNoPageNumber() throws Exception {
        assertThat(button(render("middle-page"))).contains("href=\"/admin-preview/article/export.csv\"")
                .doesNotContain("page=");
        assertThat(button(render("page-size-10"))).doesNotContain("size=");
    }

    @Test
    void theLinkLiesUnderACustomBasePath() throws Exception {
        String html = mvc.perform(get("/admin-preview/list").param("state", "export-enabled").param("chrome", "custom-base-path"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(button(html)).contains("href=\"/preview-alt/article/export.csv").doesNotContain("href=\"/admin-preview/");
    }

    // ---- disabled: the button is absent (D-57) ------------------------------------------------------------------------

    @Test
    void whenExportIsSwitchedOffThereIsNoButtonAndTheOtherActionsStay() throws Exception {
        String html = render("export-disabled");

        assertThat(html).doesNotContain("export.csv", "Export CSV", "export-link");
        assertThat(html).contains("href=\"/admin-preview/article/new\">New record</a>", "list-actions");
        assertThat(html).as("nothing is a disabled button").doesNotContainPattern("\\sdisabled[\\s=>]");
    }

    @Test
    void aReadOnlyListStillHasNoExportAndNoActionsAtAll() throws Exception {
        assertThat(render("read-only")).doesNotContain("export.csv", "Export CSV", "list-actions");
    }

    // ---- the notice about the cap (D-110) -------------------------------------------------------------------------------

    @Test
    void whenTheFileWouldBeCutThePageSaysSoAndTheButtonStays() throws Exception {
        String html = render("export-limited");

        assertThat(html).contains("Export is limited to the first 50,000 of 120,345 matching records. "
                + "Narrow the search or the filters to export the rest.");
        assertThat(button(html)).contains("export.csv");
    }

    @Test
    void theNoticeIsAnInfoMessageNotAWarningOrAnError() throws Exception {
        String html = render("export-limited");
        int at = html.indexOf("Export is limited");
        String before = html.substring(Math.max(0, at - 600), at);

        assertThat(before).contains("toast-info").doesNotContain("toast-error", "toast-warning");
    }

    @Test
    void theNormalListHasNoCapNotice() throws Exception {
        assertThat(render("normal")).doesNotContain("Export is limited");
    }

    // ---- the rules every page follows ---------------------------------------------------------------------------------------

    @Test
    void everyExportStateFollowsTheHtmlRules() throws Exception {
        for (String state : new String[] {"export-enabled", "export-disabled", "export-limited"}) {
            assertThat(HtmlRules.renderedViolations(render(state))).as(state).isEmpty();
        }
    }
}
