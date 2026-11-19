package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task U8, column visibility (Phase 12, D-112, D-115): the Columns panel and the table in every fixture state, drawn by the
 * real templates from records built by the real factory. The panel is a plain form control (no script): one labelled
 * checkbox per column that can be hidden, ticked when the column is hidden, inside the list's own GET form.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreviewColumnPanelTest {

    private static final Pattern HIDE_BOX = Pattern.compile("(?s)<input[^>]*name=\"hide\"[^>]*>");
    private static final Pattern TOOLBAR_FORM = Pattern.compile("(?s)<form class=\"list-toolbar\".*?</form>");
    private static final Pattern TABLE_HEADERS = Pattern.compile("(?s)<th[^>]*scope=\"col\"[^>]*>(.*?)</th>");

    @Autowired
    private MockMvc mvc;

    private String render(String state) throws Exception {
        return mvc.perform(get("/admin-preview/list").param("state", state)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    private static List<String> boxes(String html) {
        List<String> found = new ArrayList<>();
        Matcher boxes = HIDE_BOX.matcher(html);
        while (boxes.find()) {
            found.add(boxes.group());
        }
        return found;
    }

    private static String value(String box) {
        Matcher value = Pattern.compile("value=\"([^\"]*)\"").matcher(box);
        assertThat(value.find()).isTrue();
        return value.group(1);
    }

    private static List<String> tickedValues(String html) {
        return boxes(html).stream().filter(box -> box.contains("checked")).map(PreviewColumnPanelTest::value).toList();
    }

    private static List<String> headerTexts(String html) {
        String table = html.substring(html.indexOf("<table"));
        List<String> headers = new ArrayList<>();
        Matcher th = TABLE_HEADERS.matcher(table.substring(0, table.indexOf("</thead>")));
        while (th.find()) {
            headers.add(th.group(1).replaceAll("(?s)<[^>]*>", "").replaceAll("\\s+", " ").strip());
        }
        return headers;
    }

    private static String panel(String html) {
        int start = html.indexOf("<details class=\"filter-panel column-panel\"");
        assertThat(start).as("a Columns panel").isGreaterThanOrEqualTo(0);
        return html.substring(start, html.indexOf("</details>", start) + "</details>".length());
    }

    // ---- the catalog -------------------------------------------------------------------------------------------------

    @Test
    void theColumnStatesAreInTheListCatalog() {
        assertThat(PageFixtures.catalog().get("list")).contains("column-hidden", "columns-hidden-most", "every-column-hidden",
                "many-columns-hidden", "saved-views", "views-empty", "hidden-identifier", "hidden-sorted-column",
                "column-masked-absent", "hidden-middle-page");
    }

    // ---- the panel -----------------------------------------------------------------------------------------------------

    @Test
    void nothingHiddenMeansNoTickedBoxAndNoCountOnTheSummary() throws Exception {
        String html = render("normal");

        assertThat(boxes(html)).hasSize(6);
        assertThat(tickedValues(html)).isEmpty();
        assertThat(panel(html)).doesNotContain("filter-count");
        assertThat(headerTexts(html)).contains("ID", "Title", "Author", "Status", "Views", "Published");
    }

    @Test
    void aHiddenColumnIsTickedHasItsHeaderAndCellsSkippedAndTheSummaryCountsIt() throws Exception {
        String html = render("column-hidden");

        assertThat(tickedValues(html)).containsExactly("views");
        assertThat(boxes(html)).hasSize(7);
        assertThat(headerTexts(html)).doesNotContain("Views").contains("ID", "Title", "Author", "Status", "Published", "Featured");
        assertThat(panel(html)).contains("<span class=\"filter-count\"", "aria-label=\"1 hidden\"");
        assertThat(html).as("the view still holds one cell per column, the template skips the hidden one")
                .doesNotContain(">8,000<");
    }

    @Test
    void severalHiddenColumnsAreAllTickedAndCounted() throws Exception {
        String html = render("columns-hidden-most");

        assertThat(tickedValues(html)).containsExactly("author", "status", "views", "published");
        assertThat(headerTexts(html)).containsExactly("ID", "Title", "Featured");
        assertThat(panel(html)).contains("aria-label=\"4 hidden\"");
    }

    @Test
    void theManyColumnsFixtureHidesItsFiveColumns() throws Exception {
        String html = render("many-columns-hidden");

        assertThat(boxes(html)).hasSize(20);
        assertThat(tickedValues(html)).containsExactly("field3", "field6", "field9", "field12", "field15");
        assertThat(headerTexts(html)).hasSize(15).noneMatch(text -> text.startsWith("Field number 3"));
        assertThat(panel(html)).contains("aria-label=\"5 hidden\"");
    }

    @Test
    void everyCheckboxHasAVisibleLabelThatPointsAtItAndTheLabelsAreEscaped() throws Exception {
        String html = render("column-hidden");
        String panel = panel(html);

        for (String box : boxes(panel)) {
            Matcher id = Pattern.compile("id=\"([^\"]+)\"").matcher(box);
            assertThat(id.find()).isTrue();
            assertThat(panel).as(box).contains("<label for=\"" + id.group(1) + "\">");
        }
        assertThat(panel).contains(">Title</label>", ">Views</label>");
    }

    @Test
    void aLabelThatLooksLikeMarkupIsPrintedAsText() throws Exception {
        String html = render("empty-search-escaped");
        String panel = panel(html);

        assertThat(panel).contains("&lt;b&gt;Bold&lt;/b&gt; &amp; &quot;quoted&quot;", "&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(panel).doesNotContain("<b>Bold</b>", "<script>alert(1)");
    }

    @Test
    void aMaskedColumnHasNoCheckboxAndItsKeyIsPrintedNowhereButIsStillShown() throws Exception {
        String html = render("column-masked-absent");

        assertThat(boxes(html).stream().map(PreviewColumnPanelTest::value)).containsExactly("id", "name", "email", "joined");
        assertThat(tickedValues(html)).containsExactly("email");
        assertThat(headerTexts(html)).as("the masked column is still on the list").contains("Password").doesNotContain("Email");
        assertThat(panel(html)).doesNotContain("Password");
        assertThat(html).doesNotContain("value=\"password\"", "hide=password", "data-views-columns=\"id name email password");
    }

    @Test
    void thePanelIsPartOfTheListsGetFormWithTheSearchTheSortAndTheSizeAndNeverThePage() throws Exception {
        String html = render("hidden-middle-page");
        Matcher form = TOOLBAR_FORM.matcher(html);
        assertThat(form.find()).isTrue();
        String toolbar = form.group();

        assertThat(toolbar).contains("method=\"get\"", "name=\"hide\"", "name=\"q\"", "name=\"size\"", "Apply columns");
        assertThat(toolbar).doesNotContain("name=\"page\"");
        assertThat(toolbar.indexOf("<form", 1)).as("no form inside the form").isNegative();
        assertThat(panel(html)).contains("<button type=\"submit\" class=\"btn btn-primary\">Apply columns</button>");
    }

    @Test
    void thePanelIsNotShownWhenThereIsNothingToChooseBetween() throws Exception {
        // The no-searchable-columns fixture has three columns, so it still shows the panel; a table of one column would not.
        assertThat(render("empty-search-nothing-searchable")).contains("column-panel");
    }

    @Test
    void thePanelNeedsNoScript() throws Exception {
        String panel = panel(render("column-hidden"));

        assertThat(panel).startsWith("<details").doesNotContain("<script", "onclick", "data-views");
        assertThat(panel).contains("<summary class=\"btn filter-summary\">");
    }

    // ---- the links of the page keep the hidden set ------------------------------------------------------------------

    @Test
    void theExportLinkAndEveryPageSortAndSizeLinkKeepTheHiddenColumns() throws Exception {
        String html = render("hidden-middle-page");

        assertThat(html).contains("export.csv?hide=author&amp;hide=views\"");
        assertThat(html).contains("href=\"/admin-preview/article?hide=author&amp;hide=views&amp;page=2&amp;size=10\"");
        assertThat(html).contains("href=\"/admin-preview/article?hide=author&amp;hide=views&amp;size=50\"");
        assertThat(html).as("a size link starts again at the first page").doesNotContain("size=50&amp;page");
    }

    @Test
    void aHiddenColumnThatIsTheSortKeyKeepsItsSortStateOnTheOtherLinks() throws Exception {
        String html = render("hidden-sorted-column");

        assertThat(headerTexts(html)).doesNotContain("Views");
        assertThat(html).contains("sort=views&amp;dir=desc");
        assertThat(html).contains("hide=views");
    }

    // ---- the row link when the identifier is hidden ----------------------------------------------------------------------

    @Test
    void whenTheIdentifierIsHiddenTheFirstTextCellOpensTheRecord() throws Exception {
        String html = render("hidden-identifier");

        assertThat(headerTexts(html)).containsExactly("Title", "Views");
        assertThat(html).contains(">Article 1</a>").contains("href=\"/admin-preview/article/1?hide=id\"");
        assertThat(html.split("class=\"cell-link", -1).length - 1).as("one link per row, on the title").isEqualTo(6);
    }

    // ---- the notice -------------------------------------------------------------------------------------------------------

    @Test
    void aRequestThatHidesEveryColumnShowsEveryColumnAndTheRealNotice() throws Exception {
        String html = render("every-column-hidden");

        assertThat(html).contains("Every column was hidden, so all columns are shown. A list needs at least one column.");
        assertThat(tickedValues(html)).isEmpty();
        assertThat(headerTexts(html)).hasSize(7);
    }
}
