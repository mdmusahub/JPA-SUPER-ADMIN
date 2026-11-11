package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task U2 in the preview: every list fixture of {@code docs/FIXTURES.md} section 5 renders, and shows its key content.
 * The structure of the table (headers, {@code aria-sort}, cells, pager) is checked on the HTML the real templates
 * produce; the generic rules (labels, nothing inline, no external address) are checked for every fixture in every
 * chrome variant by {@code PreviewPagesTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreviewListPagesTest {

    @Autowired
    private MockMvc mvc;

    private String render(String state) throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/admin-preview/list").param("state", state))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).startsWith("text/html");
        return response.getContentAsString();
    }

    private String render(String state, String chrome) throws Exception {
        return mvc.perform(get("/admin-preview/list").param("state", state).param("chrome", chrome))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** The page with every run of white space as one space, so a test can match markup across line breaks. */
    private static String flat(String html) {
        return html.replaceAll("\\s+", " ");
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    private static String tbody(String html) {
        Matcher body = Pattern.compile("(?s)<tbody>(.*?)</tbody>").matcher(html);
        assertThat(body.find()).as("a table body").isTrue();
        return body.group(1);
    }

    private static String header(String html, String label) {
        Matcher header = Pattern.compile("(?s)<th\\b[^>]*scope=\"col\"[^>]*>(?:(?!</th>).)*?" + Pattern.quote(label)
                + "(?:(?!</th>).)*</th>").matcher(html);
        assertThat(header.find()).as("the header " + label).isTrue();
        return header.group();
    }

    // ---- the pages --------------------------------------------------------------------------------------------

    @Test
    void theNormalListShowsTheHeadingTheCountTheToolbarTheTableAndThePager() throws Exception {
        String html = render("normal");

        assertThat(html).contains("<title>Articles | JPA Admin</title>", "<h1 class=\"page-title\">Articles</h1>",
                        "94 records")
                .contains("class=\"sidebar\"", "class=\"topbar\"")
                .contains("href=\"/admin-preview/article\" aria-current=\"page\"");
        assertThat(count(tbody(html), "<tr>")).isEqualTo(25);
        assertThat(count(html, "scope=\"col\"")).isEqualTo(6);
        assertThat(html).contains("Showing 1–25 of 94", "Page 1 of 4", "Rows per page");
    }

    @Test
    void theSearchIsAGetFormWithALabelThatKeepsTheSortAndTheSize() throws Exception {
        String html = render("sorted-asc");

        assertThat(flat(html)).contains("<form class=\"list-toolbar\" method=\"get\" role=\"search\" action=\"/admin-preview/article\">")
                .contains("<label class=\"visually-hidden\" for=\"list-search\">Search articles</label>")
                .contains("id=\"list-search\"", "type=\"search\"", "name=\"q\"", "maxlength=\"100\"")
                .contains("<input type=\"hidden\" name=\"sort\" value=\"title\">", "<input type=\"hidden\" name=\"dir\" value=\"asc\">",
                        "<input type=\"hidden\" name=\"size\" value=\"25\">")
                .contains("<button type=\"submit\" class=\"btn\">Search</button>");
        assertThat(html).as("a form that posts would not keep the state in the address").doesNotContain("method=\"post\" role=\"search\"");
    }

    @Test
    void anUnsortedListHasNoHiddenSortFields() throws Exception {
        String html = render("normal");

        assertThat(html).doesNotContain("name=\"sort\"", "name=\"dir\"").contains("name=\"size\" value=\"25\"");
    }

    @Test
    void everySortableHeaderIsALinkWithAriaSortNoneAndTheOthersArePlainText() throws Exception {
        String html = render("normal");

        for (String label : List.of("ID", "Title", "Status", "Views", "Published")) {
            String th = header(html, label);
            assertThat(th).as(label).contains("aria-sort=\"none\"").contains("<a class=\"sort-link\"")
                    .contains("class=\"sort-mark\" aria-hidden=\"true\"");
        }
        String author = header(html, "Author");
        assertThat(author).as("a relation column is not sortable").doesNotContain("<a ").doesNotContain("aria-sort");
        assertThat(count(html, "class=\"sort-link\"")).isEqualTo(5);
    }

    @Test
    void theSortLinksComeFromTheRecordAndStartAscending() throws Exception {
        String html = render("normal");

        assertThat(html).contains("href=\"/admin-preview/article?sort=title&amp;dir=asc\"",
                "href=\"/admin-preview/article?sort=views&amp;dir=asc\"");
    }

    @Test
    void aSortedAscendingColumnSaysSoAndLinksToDescending() throws Exception {
        String html = render("sorted-asc");

        String title = header(html, "Title");
        assertThat(title).contains("aria-sort=\"ascending\"").contains("href=\"/admin-preview/article?sort=title&amp;dir=desc\"");
        assertThat(header(html, "Views")).contains("aria-sort=\"none\"");
        assertThat(count(html, "aria-sort=\"ascending\"")).isEqualTo(1);
        assertThat(count(html, "aria-sort=\"descending\"")).isZero();
    }

    @Test
    void aSortedDescendingColumnSaysSoAndLinksBackToAscending() throws Exception {
        String html = render("sorted-desc");

        assertThat(header(html, "Views")).contains("aria-sort=\"descending\"")
                .contains("href=\"/admin-preview/article?sort=views&amp;dir=asc\"");
        assertThat(count(html, "aria-sort=\"descending\"")).isEqualTo(1);
    }

    @Test
    void theFirstPageOffersNextAndLastButNoFirstOrPrevious() throws Exception {
        String html = render("normal");

        assertThat(html).contains("aria-label=\"Next page\"", "aria-label=\"Last page\"",
                "href=\"/admin-preview/article?page=1\"", "href=\"/admin-preview/article?page=3\"");
        assertThat(html).doesNotContain("aria-label=\"First page\"", "aria-label=\"Previous page\"");
        assertThat(count(html, "icon-button is-disabled")).isEqualTo(2);
    }

    @Test
    void aMiddlePageOffersAllFourControls() throws Exception {
        String html = render("middle-page");

        assertThat(html).contains("aria-label=\"First page\"", "aria-label=\"Previous page\"", "aria-label=\"Next page\"",
                "aria-label=\"Last page\"", "Showing 26–50 of 94", "Page 2 of 4");
        assertThat(count(html, "icon-button is-disabled")).isZero();
    }

    @Test
    void theLastPageIsPartialAndOffersOnlyFirstAndPrevious() throws Exception {
        String html = render("last-page");

        assertThat(count(tbody(html), "<tr>")).isEqualTo(19);
        assertThat(html).contains("Showing 76–94 of 94", "Page 4 of 4", "aria-label=\"First page\"",
                "aria-label=\"Previous page\"");
        assertThat(html).doesNotContain("aria-label=\"Next page\"", "aria-label=\"Last page\"");
    }

    @Test
    void aSinglePageHasNoPageControlsAndSaysSo() throws Exception {
        String html = render("single-page");

        assertThat(html).contains("11 records", "Showing 1–11 of 11", "Page 1 of 1");
        assertThat(html).doesNotContain("aria-label=\"Next page\"", "aria-label=\"Previous page\"");
        assertThat(count(html, "icon-button is-disabled")).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page-size-10:10:Showing 21–30 of 94", "page-size-50:50:Showing 1–50 of 94",
            "normal:25:Showing 1–25 of 94"})
    void thePageSizeChoicesMarkTheCurrentOneAndLinkTheOthers(String spec) throws Exception {
        String[] parts = spec.split(":");
        String html = render(parts[0]);

        assertThat(html).contains(parts[2]);
        assertThat(count(html, "class=\"page-size is-current\" aria-current=\"true\"")).isEqualTo(1);
        assertThat(html).contains("class=\"page-size is-current\" aria-current=\"true\">" + parts[1] + "</span>");
        for (String size : List.of("10", "25", "50", "100")) {
            if (!size.equals(parts[1])) {
                assertThat(html).contains("class=\"page-size\" href=");
                assertThat(html).contains(">" + size + "</a>");
            }
        }
        assertThat(count(html, "<a class=\"page-size\"")).isEqualTo(3);
    }

    @Test
    void aNewPageSizeLinkGoesBackToTheFirstPage() throws Exception {
        String html = render("page-size-10");

        assertThat(html).contains("href=\"/admin-preview/article\">25</a>").contains("href=\"/admin-preview/article?size=50\">50</a>")
                .contains("href=\"/admin-preview/article?size=100\">100</a>");
    }

    // ---- empty states -----------------------------------------------------------------------------------------

    @Test
    void anEmptyListWithoutRecordsExplainsWhatToDoNext() throws Exception {
        String html = render("empty-no-records");

        assertThat(html).contains("No records yet", "There are no articles yet", "0 records");
        assertThat(html).doesNotContain("<table", "Showing ", "Rows per page");
    }

    @Test
    void anEmptyListAfterASearchOffersToClearIt() throws Exception {
        String html = render("empty-no-matches");

        assertThat(html).contains("No matching records", "No articles match your search", "Clear search")
                .contains("value=\"no article has these words\"");
        assertThat(html).doesNotContain("<table");
        assertThat(count(html, "href=\"/admin-preview/article\">Clear search</a>")).isEqualTo(2);
    }

    @Test
    void theSearchBoxShowsTheTermAndTheListIsFiltered() throws Exception {
        String html = render("search-active");

        assertThat(html).contains("value=\"guide\"", "Clear search", "11 records");
        assertThat(tbody(html).toLowerCase()).contains("guide on");
        assertThat(html).contains("href=\"/admin-preview/article?q=guide&amp;sort=title&amp;dir=desc\"");
    }

    // ---- cells ------------------------------------------------------------------------------------------------

    @Test
    void aMaskedColumnShowsDotsOnlyWithNoValueNoSortAndNoTooltip() throws Exception {
        String html = render("masked-column");

        assertThat(count(html, "<span class=\"cell-masked\"><span aria-hidden=\"true\">••••••••</span><span class=\"visually-hidden\">Hidden value</span></span>"))
                .isEqualTo(6);
        assertThat(header(html, "Password")).doesNotContain("<a ", "aria-sort");
        assertThat(tbody(html)).doesNotContain("title=\"•", "secret", "hunter");
        assertThat(html).contains("<h1 class=\"page-title\">Members</h1>")
                .contains("href=\"/admin-preview/member\" aria-current=\"page\"");
    }

    @Test
    void relationCellsAreLinksWhenTheyHaveOneAndTextWhenTheyDoNot() throws Exception {
        String html = render("relation-cells");

        assertThat(html).contains("<a class=\"cell-link\" href=\"/admin-preview/member/1\">", "No value");
        assertThat(count(html, "class=\"cell-link\"")).isEqualTo(6);
    }

    @Test
    void aLongTextIsCutWrappedAndKeptWholeAsTheHoverText() throws Exception {
        String html = render("long-text");

        assertThat(html).contains("class=\"cell-text\"", "…");
        Matcher title = Pattern.compile("class=\"cell-text\" title=\"([^\"]{250,})\"").matcher(html);
        assertThat(title.find()).as("the full text in the title attribute").isTrue();
        assertThat(html).contains("A summary column with a very long label that keeps going to see how headers wrap");
    }

    @Test
    void unicodeAndMarkupInAValueAreShownAsTextAndEscaped() throws Exception {
        String html = render("unicode");

        assertThat(html).contains("Платёжные поручения за ноябрь", "配送先住所の変更", "Παραγγελίες πελατών", "مقال عن التصميم",
                "Ürün kategorileri güncellemesi", "Ünïcödé");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script>alert(1)");
        assertThat(html).contains("&amp; &quot;quotes&quot; &amp;").doesNotContain("<script>alert");
    }

    @Test
    void aTableWithManyColumnsScrollsInsideItsOwnBox() throws Exception {
        String html = render("many-columns");

        assertThat(count(html, "scope=\"col\"")).isEqualTo(20);
        assertThat(html).contains("<div class=\"table-scroll\" tabindex=\"0\" role=\"region\" aria-label=\"Articles table\">");
        assertThat(count(tbody(html), "<tr>")).isEqualTo(10);
    }

    @Test
    void everyCellTypeHasItsOwnRendering() throws Exception {
        String body = tbody(render("all-cell-types"));

        assertThat(body).contains("class=\"cell-text\">Plain text", "class=\"cell-number\">1,199.99",
                "class=\"cell-bool is-yes\">Yes", "class=\"cell-bool is-no\">No",
                "class=\"badge badge-green\">Published", "class=\"badge badge-amber\">Pending",
                "class=\"badge badge-red\">Cancelled", "class=\"badge badge-grey\">In transit",
                "class=\"badge badge-none\">Unknown",
                "<a class=\"cell-link\" href=\"/admin-preview/member/1\">Ava Stone</a>", "Ben Carter",
                "class=\"cell-date\" title=\"2026-11-01T10:15:30\">2026-11-01",
                "<img class=\"cell-thumb\" src=\"/admin-preview/files/cover.png\" alt=\"cover.png\">",
                "class=\"cell-link cell-file\" href=\"/admin-preview/files/report.pdf\"", "report.pdf",
                "class=\"cell-masked\"", "class=\"cell-empty\"", "No value");
    }

    // ---- actions ----------------------------------------------------------------------------------------------

    @Test
    void theNormalFixtureOffersExportAndANewRecordThroughTheRecord() throws Exception {
        String html = render("normal");

        assertThat(html).contains("href=\"/admin-preview/article/export.csv\">Export CSV</a>",
                "href=\"/admin-preview/article/new\">New record</a>");
    }

    @Test
    void aReadOnlyListOffersNoActionAndNoDisabledButton() throws Exception {
        String html = render("read-only");

        assertThat(html).doesNotContain("Export CSV", "New record", "list-actions");
        assertThat(html).as("no button is disabled").doesNotContainPattern("\\sdisabled[\\s=>]");
        assertThat(count(tbody(html), "<tr>")).isEqualTo(25);
    }

    // ---- links, base path, rules ------------------------------------------------------------------------------

    @Test
    void everyLinkOfThePageComesFromTheRecordUnderTheCustomBasePath() throws Exception {
        String html = render("middle-page", "custom-base-path");
        Matcher addresses = Pattern.compile("\\b(?:href|src|action)=\"([^\"]*)\"").matcher(html);
        int found = 0;
        while (addresses.find()) {
            String address = addresses.group(1);
            if (address.equals("#main") || address.equals("data:,")) {
                continue;
            }
            found++;
            assertThat(address).as(address).startsWith("/preview-alt");
        }

        assertThat(found).isGreaterThan(20);
        assertThat(html).contains("href=\"/preview-alt/article?page=2\"", "action=\"/preview-alt/article\"");
    }

    @Test
    void noTemplateBuildsASortPageOrSizeLinkItself() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<String> problems = new ArrayList<>();
        for (Resource template : resolver.getResources("classpath*:templates/jpa-admin/**/*.html")) {
            String source = new String(template.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // The parser comments explain the rules in words; only the markup matters here.
            String markup = source.replaceAll("(?s)<!--/\\*.*?\\*/-->", "");
            for (String forbidden : List.of("sort", "page", "size", "dir", "q")) {
                if (Pattern.compile("(?<![\\w-])" + forbidden + "=").matcher(markup).find()) {
                    problems.add(template.getFilename() + " contains " + forbidden + "=");
                }
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    void theListTemplatesAreThereAndThePageUsesTheLayout() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<String> names = new ArrayList<>();
        for (Resource template : resolver.getResources("classpath*:templates/jpa-admin/**/*.html")) {
            names.add(template.getFilename());
        }

        assertThat(names).contains("list.html", "table.html", "cell.html", "pager.html").doesNotContain("entity-notice.html");
        String list = new String(resolver.getResource("classpath:templates/jpa-admin/list.html").getInputStream()
                .readAllBytes(), StandardCharsets.UTF_8);
        assertThat(list).contains("~{jpa-admin/layout :: layout(");
    }

    /**
     * Regression test for a bug found in the browser pass of Phase 4: at phone width the customer list made the whole
     * page scroll sideways. The cause: a masked or empty cell holds a {@code visually-hidden} span, which is absolutely
     * positioned, and the scroll box was not its containing block, so the span escaped the box's clipping and widened
     * the page. The box must be positioned (so it contains and clips its absolutely positioned children) and must be
     * the thing that scrolls.
     */
    @Test
    void theTableScrollBoxContainsAndClipsAbsolutelyPositionedCellContent() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String css = new String(resolver.getResource("classpath:jpa-admin/assets/css/pages.css").getInputStream()
                .readAllBytes(), StandardCharsets.UTF_8);
        Matcher rule = Pattern.compile("(?s)\\.table-scroll\\s*\\{([^}]*)}").matcher(css);

        assertThat(rule.find()).as("a .table-scroll rule").isTrue();
        assertThat(rule.group(1)).contains("overflow-x: auto").contains("position: relative");
        assertThat(css).as("hiding the overflow instead would clip the focus ring of the first column").doesNotContain("overflow-x: hidden");
    }

    /**
     * Regression test for a second defect of the same browser pass: at phone width an e-mail address was broken after
     * every few letters. {@code overflow-wrap: anywhere} lowers a cell's minimum width to one letter, so the table
     * squeezed the column instead of scrolling. {@code break-word} keeps the natural width and still breaks a runaway
     * word once the cell reaches its maximum width.
     */
    @Test
    void aTextCellKeepsItsNaturalWidthAndOnlyBreaksARunawayWord() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String css = new String(resolver.getResource("classpath:jpa-admin/assets/css/pages.css").getInputStream()
                .readAllBytes(), StandardCharsets.UTF_8);
        Matcher rule = Pattern.compile("(?s)\\.cell-text\\s*\\{([^}]*)}").matcher(css);

        assertThat(rule.find()).as("a .cell-text rule").isTrue();
        assertThat(rule.group(1)).contains("max-width: 36ch").contains("overflow-wrap: break-word")
                .doesNotContain("anywhere");
        // And the table itself must not shrink below its natural width: on a phone it scrolls inside its box, it does
        // not squeeze "Dan Moore" onto two lines.
        Matcher table = Pattern.compile("(?s)\\.data-table\\s*\\{([^}]*)}").matcher(css);
        assertThat(table.find()).as("a .data-table rule").isTrue();
        assertThat(table.group(1)).contains("width: 100%").contains("min-width: max-content");
    }

    @Test
    void noPreviewPageMentionsTheRemovedNotice() throws Exception {
        for (String state : ListFixtures.states()) {
            assertThat(render(state)).as(state).doesNotContain("not available yet");
        }
    }
}
