package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task U4 in the preview: the detail page, the row links, the checkboxes and the bulk bar, the confirmations that need no
 * script, and the messages after a delete. Every fixture is built by the console's real factories; this class checks the
 * HTML the real templates make of them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreviewRecordPagesTest {

    @Autowired
    private MockMvc mvc;

    private String render(String page, String state) throws Exception {
        return render(page, state, null);
    }

    private String render(String page, String state, String chrome) throws Exception {
        var request = get("/admin-preview/" + page).param("state", state);
        if (chrome != null) {
            request = request.param("chrome", chrome);
        }
        MockHttpServletResponse response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).startsWith("text/html");
        return response.getContentAsString();
    }

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

    /** The page's own content, without the shell (the sidebar has a sign-out form of its own). */
    private static String main(String html) {
        Matcher main = Pattern.compile("(?s)<main\\b.*?</main>").matcher(html);
        assertThat(main.find()).as("a <main> element").isTrue();
        return main.group();
    }

    // ---- every new page obeys the rules ------------------------------------------------------------------------------

    static Stream<Arguments> everyRecordFixtureOnItsOwnPage() {
        List<Arguments> all = new ArrayList<>();
        for (String state : RecordFixtures.detailStates()) {
            all.add(Arguments.of("detail", state));
        }
        for (String state : RecordFixtures.listStates()) {
            all.add(Arguments.of("list", state));
        }
        return all.stream();
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("everyRecordFixtureOnItsOwnPage")
    void everyRecordFixtureRendersAndObeysTheUiRulesWithNoFormInsideAnother(String page, String state) throws Exception {
        String html = render(page, state);

        assertThat(html).startsWith("<!DOCTYPE html>").contains("</html>");
        assertThat(HtmlRules.renderedViolations(html)).isEmpty();
        assertThat(html).doesNotContain("th:utext");
    }

    @Test
    void noPageOfAnyFixtureHasAFormInsideAnotherForm() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Set<String>> page : PageFixtures.catalog().entrySet()) {
            for (String state : page.getValue()) {
                String html = render(page.getKey(), state);
                int depth = 0;
                Matcher tags = Pattern.compile("(?i)<(/?)form\\b").matcher(html);
                while (tags.find()) {
                    if (tags.group(1).isEmpty()) {
                        if (depth > 0) {
                            problems.add(page.getKey() + "/" + state);
                        }
                        depth++;
                    } else if (depth > 0) {
                        depth--;
                    }
                }
            }
        }

        assertThat(problems).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("taskU4Chromes")
    void theMessageVariantsRenderOnFourPagesWithoutBreakingTheShell(String chrome) throws Exception {
        for (String[] page : new String[][] {{"dashboard", "normal"}, {"error", "not-found"}, {"list", "normal"},
                {"detail", "normal"}}) {
            String html = render(page[0], page[1], chrome);
            assertThat(HtmlRules.renderedViolations(html)).as(page[0] + " in " + chrome).isEmpty();
        }
    }

    static Stream<String> taskU4Chromes() {
        List<String> names = new ArrayList<>(RecordFixtures.messages().keySet());
        names.add("article-detail");
        return names.stream();
    }

    // ---- the detail page ------------------------------------------------------------------------------------------------

    @Test
    void theNormalDetailPageHasTheTitleTheGridAndAllThreeActions() throws Exception {
        String html = render("detail", "normal");

        assertThat(html).contains("<title>Guide on onboarding 7 | JPA Admin</title>")
                .contains("<h1 class=\"page-title\">Guide on onboarding 7</h1>")
                .contains("<p class=\"muted detail-kicker\">Article #7</p>");
        assertThat(count(main(html), "<div class=\"detail-field\">")).isEqualTo(8);
        assertThat(flat(html)).contains("<dt>Title</dt>", "<dt>Author</dt>", "<dt>Status</dt>", "<dt>Views</dt>")
                .contains("<a class=\"btn\" href=\"/admin-preview/article/7/edit\">Edit record</a>",
                        "<a class=\"btn\" href=\"/admin-preview/article/new?copyFrom=7\">Duplicate record</a>");
        assertThat(html).contains("aria-label=\"Breadcrumb\"", "href=\"/admin-preview/article\"");
    }

    @Test
    void theDeleteConfirmationNeedsNoScriptItIsADetailsElementWithAPlainPostForm() throws Exception {
        String html = flat(main(render("detail", "normal")));

        assertThat(html).contains("<details class=\"confirm\">", "<summary class=\"btn btn-danger\">Delete record</summary>")
                .contains("<form class=\"confirm-panel\" method=\"post\" action=\"/admin-preview/article/7/delete\">")
                .contains("<input type=\"hidden\" name=\"_csrf\" value=\"preview-fake-token-not-a-secret\">")
                .contains("<button type=\"submit\" class=\"btn btn-danger\">Yes, delete record</button>")
                .contains("This permanently deletes <strong>Guide on onboarding 7</strong>");
        assertThat(html.indexOf("<details")).isLessThan(html.indexOf("<form"));
        assertThat(html).doesNotContain("<script");
    }

    @Test
    void theRealConsoleOffersOnlyDeleteSoThereIsNoEditOrDuplicateLink() throws Exception {
        String html = main(render("detail", "real-actions"));

        assertThat(html).contains("Delete record").doesNotContain("Edit record", "Duplicate record", "copyFrom");
    }

    @Test
    void aReadOnlyRecordHasNoActionAndNoFormAtAll() throws Exception {
        String html = main(render("detail", "read-only"));

        assertThat(html).doesNotContain("Delete record", "Edit record", "Duplicate record", "<form", "<details class=\"confirm\"",
                "csrf");
        assertThat(count(html, "<div class=\"detail-field\">")).isEqualTo(8);
    }

    @Test
    void emptyFieldsShowADashWithNoValueForAScreenReaderAndTheTitleFallsBackToTheEntityAndId() throws Exception {
        String html = render("detail", "empty-fields");

        assertThat(count(main(html), "class=\"cell-empty\"")).isEqualTo(7);
        assertThat(main(html)).contains("No value");
        assertThat(html).contains("<h1 class=\"page-title\">Article #12</h1>");
    }

    @Test
    void aLongTextKeepsItsLineBreaksSitsBehindASummaryAndABriefOneDoesNot() throws Exception {
        String html = render("detail", "long-text");

        assertThat(html).contains("<details class=\"detail-more\">").contains("class=\"detail-long\"");
        Matcher summary = Pattern.compile("(?s)<summary>(.*?)</summary>").matcher(main(html));
        assertThat(summary.find()).isTrue();
        assertThat(summary.group(1).strip()).hasSizeLessThanOrEqualTo(200).endsWith("...");
        assertThat(count(main(html), "<details class=\"detail-more\">")).as("only the body is long").isEqualTo(1);
        assertThat(html).contains("keeps\nits line break.");
        assertThat(html).contains("Supercalifragilisticexpialidocious");
    }

    @Test
    void aRelationFieldLinksToTheRelatedRecordAndAHiddenTargetAndAMissingOneDoNot() throws Exception {
        String html = flat(main(render("detail", "relations")));

        assertThat(html).contains("<a class=\"cell-link\" href=\"/admin-preview/member/3\">Ava Stone</a>")
                .contains("<a class=\"cell-link\" href=\"/admin-preview/category/4\">#4</a>");
        assertThat(html).contains("<span class=\"cell-text\">#9</span>").doesNotContain("href=\"/admin-preview/#9");
        assertThat(count(html, "class=\"cell-empty\"")).isEqualTo(1);
        assertThat(html).as("the label of a related record is escaped").contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .doesNotContain("<script>alert(1)</script>");
    }

    @Test
    void aMaskedFieldIsDotsOnlyWithNoValueAndAScreenReaderHearsHiddenValue() throws Exception {
        String html = main(render("detail", "masked-field"));

        assertThat(count(html, "class=\"cell-masked\"")).isEqualTo(2);
        assertThat(count(html, "Hidden value")).isEqualTo(2);
        assertThat(html).contains("••••••••");
    }

    @Test
    void imagesAndFilesAreLinksUnderTheBasePathWithEscapedAlternativeText() throws Exception {
        String html = flat(main(render("detail", "with-images")));

        assertThat(html).contains("<img class=\"detail-thumb\" src=\"/admin-preview/files/cover.png\" alt=\"Cover of the article\">")
                .contains("alt=\"Second picture &lt;b&gt;escaped&lt;/b&gt;\"")
                .contains("href=\"/admin-preview/files/report.pdf\"");
        assertThat(html).doesNotContain("<b>escaped</b>");
    }

    @Test
    void everyCellTypeRendersInTheGrid() throws Exception {
        String html = main(render("detail", "all-cell-types"));

        assertThat(html).contains("class=\"cell-number\"", "class=\"cell-bool is-yes\"", "class=\"cell-bool is-no\"",
                "badge badge-green", "badge badge-amber", "badge badge-red", "badge badge-grey", "cell-link",
                "class=\"cell-date\"", "class=\"cell-masked\"", "class=\"cell-empty\"", "cell-thumb", "cell-file", "(not shown)");
    }

    @Test
    void aCollectionAnEmbeddedValueAndBinaryDataShowTheirLabelAndNotShown() throws Exception {
        String html = flat(main(render("detail", "collection-not-shown")));

        assertThat(count(html, "(not shown)")).isEqualTo(3);
        assertThat(html).contains("<dt>Comments</dt>", "<dt>Tags</dt>", "<dt>Cover bytes</dt>");
    }

    @Test
    void markupInEveryValueLabelAndTitleIsEscaped() throws Exception {
        String html = render("detail", "escaped");

        assertThat(html).doesNotContain("<script>alert(1)</script>", "<img src=x", "<b>bold label</b>");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;", "&lt;b&gt;bold label&lt;/b&gt;", "&quot;quotes&quot;");
        assertThat(html).contains("Ürün kategorileri", "配送先住所", "😀");
    }

    @Test
    void theListStateTravelsInTheBreadcrumbAndInTheDeleteAddress() throws Exception {
        String html = render("detail", "with-list-state");

        String state = "q=guide%20%26%20more&amp;sort=views&amp;dir=desc&amp;page=2&amp;size=10";
        assertThat(html).contains("href=\"/admin-preview/article?" + state + "\"")
                .contains("action=\"/admin-preview/article/7/delete?" + state + "\"");
    }

    @Test
    void afterABlockedDeleteTheRecordShowsTheFriendlyMessageWithNoSql() throws Exception {
        String html = render("detail", "delete-blocked");

        assertThat(html).contains("role=\"alert\"", "was not deleted. Other records still depend on it");
        assertThat(html.toLowerCase()).doesNotContain("sql", "constraint", "foreign key", "exception", "select ");
        assertThat(html).contains("Yes, delete record");
    }

    // ---- the list: row links, checkboxes and the bulk bar ---------------------------------------------------------------------

    @Test
    void theIdentifierAndAnAuthorLinkToTheirRecordsAndAHiddenTargetAndAMissingOneDoNot() throws Exception {
        String html = flat(render("list", "row-links"));

        assertThat(html).contains("<a class=\"cell-link cell-number\" href=\"/admin-preview/article/1?size=10\">1</a>")
                .contains("<a class=\"cell-link\" href=\"/admin-preview/member/");
        assertThat(count(html, "<a class=\"cell-link cell-number\"")).isEqualTo(8);
        assertThat(count(html, "<a class=\"cell-link\" href=\"/admin-preview/member/")).as("six of eight have an author").isEqualTo(6);
        assertThat(html).as("the reviewer's entity is hidden: text only").contains("<span class=\"cell-text\">#2</span>");
        assertThat(html).doesNotContain("data-select-row", "name=\"ids\"");
    }

    @Test
    void whenBulkDeleteIsOfferedEveryRowHasACheckboxThatNamesItsRow() throws Exception {
        String html = flat(render("list", "bulk-delete"));

        assertThat(count(html, "type=\"checkbox\" name=\"ids\"")).isEqualTo(25);
        assertThat(html).contains("<input type=\"checkbox\" name=\"ids\" class=\"select-box\" data-select-row id=\"select-row-0\" value=\"1\">")
                .contains("<label class=\"visually-hidden\" for=\"select-row-0\">Select Article 1</label>")
                .contains("<label class=\"visually-hidden\" for=\"select-row-24\">Select Article 25</label>");
    }

    @Test
    void theHeaderCheckboxSelectsThisPageAndSaysSoAndIsAScriptFeature() throws Exception {
        String html = flat(render("list", "bulk-delete"));

        assertThat(html).contains("<span class=\"js-only\"> <input type=\"checkbox\" id=\"select-all\" class=\"select-box\" data-select-all>")
                .contains("<label class=\"visually-hidden\" for=\"select-all\">Select all articles on this page</label>");
        assertThat(count(html, "data-select-all")).isEqualTo(1);
    }

    @Test
    void theBulkFormIsAPostFormWithTheTokenAroundTheTableAndTheBar() throws Exception {
        String html = flat(render("list", "bulk-delete"));

        assertThat(html).contains("<form class=\"bulk-form\" method=\"post\" data-bulk-form action=\"/admin-preview/article/bulk-delete\">")
                .contains("<input type=\"hidden\" name=\"_csrf\" value=\"preview-fake-token-not-a-secret\">");
        int open = html.indexOf("<form class=\"bulk-form\"");
        int close = html.indexOf("</form>", open);
        String inside = html.substring(open, close);
        assertThat(inside).contains("<table class=\"data-table\">", "data-bulk-bar", "Yes, delete selected")
                .doesNotContain("name=\"q\"", "name=\"sort\"", "name=\"size\"", "filter.", "data-filter-form");
    }

    @Test
    void theSearchAndFilterFormIsAGetFormOfItsOwnThatHoldsNoCheckboxAndNoTable() throws Exception {
        String html = flat(render("list", "bulk-delete"));

        int open = html.indexOf("<form class=\"list-toolbar\"");
        int close = html.indexOf("</form>", open);
        String toolbar = html.substring(open, close);
        assertThat(toolbar).contains("method=\"get\"", "data-filter-form", "name=\"q\"")
                .doesNotContain("name=\"ids\"", "<table", "data-bulk", "bulk-delete");
        assertThat(close).as("the GET form is closed before the POST form opens").isLessThan(html.indexOf("<form class=\"bulk-form\""));
    }

    @Test
    void theBulkBarAsksBeforeDeletingAndWorksWithoutScript() throws Exception {
        String html = flat(render("list", "bulk-delete"));

        assertThat(html).contains("<div class=\"bulk-bar\" data-bulk-bar data-empty role=\"group\" aria-label=\"Bulk actions\">")
                .contains("<summary class=\"btn btn-danger\">Delete selected</summary>")
                .contains("<button type=\"submit\" class=\"btn btn-danger\">Yes, delete selected</button>")
                .contains("Tick the rows to delete, then choose Delete selected.")
                .contains("This cannot be undone, and if one of them cannot be deleted, none is.");
        assertThat(html).contains("<span class=\"js-only\"><span data-bulk-count>0</span> selected</span>");
    }

    @Test
    void theBulkFormAddressCarriesTheListStateSoTheAnswerReturnsToTheSameList() throws Exception {
        String html = render("list", "bulk-delete-state");

        assertThat(html).contains("action=\"/admin-preview/article/bulk-delete?q=guide&amp;sort=title&amp;dir=asc&amp;page=1&amp;size=10\"");
    }

    @Test
    void aReadOnlyListHasNoCheckboxNoBarAndNoBulkForm() throws Exception {
        String html = render("list", "bulk-read-only");

        assertThat(html).doesNotContain("data-select-row", "data-select-all", "data-bulk", "bulk-delete", "Delete selected",
                "name=\"ids\"");
        assertThat(html).contains("<a class=\"cell-link cell-number\" href=\"/admin-preview/article/1\">1</a>");
    }

    @Test
    void theListWithoutBulkDeleteTheOldReadOnlyFixtureHasNoCheckboxEither() throws Exception {
        assertThat(render("list", "read-only")).doesNotContain("data-select-row", "data-bulk-form");
        assertThat(render("list", "normal")).doesNotContain("data-select-row", "data-bulk-form");
    }

    @Test
    void checkboxLabelsAndValuesAreEscapedWhateverTheRowIdHolds() throws Exception {
        String html = render("list", "bulk-escaped");

        assertThat(html).doesNotContain("<script>alert(1)</script>", "value=\"\"onfocus=\"");
        assertThat(html).contains("Select Article &lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("value=\"&lt;script&gt;alert(1)&lt;/script&gt;\"")
                .contains("value=\"&quot;onfocus=&quot;alert(1)\"");
        assertThat(count(html, "name=\"ids\"")).isEqualTo(4);
    }

    @Test
    void aLinkIsGivenOnlyToAnIdThatCanBeWrittenIntoAnAddress() throws Exception {
        String html = render("list", "bulk-escaped");

        // The fixture's ids contain slashes or quotes: the identifier cell is plain text, never a broken link.
        assertThat(html).doesNotContain("href=\"/admin-preview/article/<", "href=\"/admin-preview/article/&lt;script");
    }

    @Test
    void theMessagesAfterADeleteAreShownAtTheTopOfTheList() throws Exception {
        assertThat(render("list", "delete-done")).contains("role=\"status\"", "Deleted article “Guide on onboarding 3”.");
        assertThat(render("list", "bulk-done")).contains("role=\"status\"", "Deleted 3 records.");
        assertThat(render("list", "bulk-partial")).contains("role=\"alert\"", "Deleted 2 records.",
                "1 selected record was skipped because it no longer exists.");
        assertThat(render("list", "bulk-nothing-selected")).contains("No records were selected.");
    }

    @Test
    void aRefusedBulkDeleteOverTheCapSaysSoAndNothingElseOnTheList() throws Exception {
        String html = render("list", "bulk-over-cap");

        assertThat(html).contains("role=\"alert\"", "You can delete at most 100 records at a time, and nothing was deleted.");
        assertThat(count(html, "data-select-row")).isEqualTo(25);
    }

    @Test
    void aBlockedBulkDeleteExplainsWithoutSql() throws Exception {
        String html = render("list", "bulk-blocked");

        assertThat(html).contains("role=\"alert\"", "Nothing was deleted. At least one of the 4 selected records");
        assertThat(html.toLowerCase()).doesNotContain("sql", "constraint", "foreign key", "exception");
    }

    @Test
    void theLastPageOfARealListStillHasItsCheckboxes() throws Exception {
        String html = render("list", "bulk-last-page");

        assertThat(count(html, "data-select-row")).isEqualTo(19);
    }

    // ---- files ------------------------------------------------------------------------------------------------------------

    @Test
    void bulkJsIsAPlainDeferredFileThatStoresNothing() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String script = new String(resolver.getResource("classpath:jpa-admin/assets/js/bulk.js").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(script).contains("data-bulk-form", "data-select-all", "data-select-row", "pageshow", "Escape", "indeterminate");
        assertThat(script).doesNotContain("localStorage", "sessionStorage", "document.cookie", "indexedDB", "fetch(",
                "XMLHttpRequest", "innerHTML", "eval(", "import ", "require(");
        assertThat(render("list", "bulk-delete")).contains("<script src=\"/admin-preview/assets/js/bulk.js\" defer></script>");
    }

    @Test
    void theCheckboxAndBarStylesUseTokensOnlySoBothThemesFollow() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String css = new String(resolver.getResource("classpath:jpa-admin/assets/css/pages.css").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int from = css.indexOf("row checkboxes and the bulk bar");
        String section = css.substring(from);

        assertThat(section).doesNotContainPattern("#[0-9a-fA-F]{3,8}\\b").doesNotContainPattern("rgb\\(")
                .contains(".js .bulk-bar", ".js .bulk-bar[data-empty]");
        assertThat(css).contains("@media (max-width: 599px)");
    }

    // ---- links built by the backend, not the templates ------------------------------------------------------------------------

    @Test
    void noTemplateBuildsAQueryStringThatCarriesStateOrTheReturnAddress() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<String> problems = new ArrayList<>();
        // A parameter of Thymeleaf's own link syntax, (copyFrom=${...}), is encoded by the engine and carries no list state.
        List<Pattern> forbidden = List.of(
                Pattern.compile("(?<![\\w-])(?:sort|page|size|dir|q|ids|returnTo|return|next|redirect)="),
                Pattern.compile("filter\\.[\\w.{}$\\[\\]'+ ]*="),
                Pattern.compile("[?&]\\w+="),
                Pattern.compile("/delete|bulk-delete"));
        for (Resource template : resolver.getResources("classpath*:templates/jpa-admin/**/*.html")) {
            String source = new String(template.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // The parser comments explain the rules in words; only the markup matters here.
            String markup = source.replaceAll("(?s)<!--/\\*.*?\\*/-->", "");
            for (Pattern pattern : forbidden) {
                if (pattern.matcher(markup).find()) {
                    problems.add(template.getFilename() + " matches " + pattern.pattern());
                }
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    void theDetailAndBulkTemplatesExistAndUseTheLayout() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<String> names = new ArrayList<>();
        for (Resource template : resolver.getResources("classpath*:templates/jpa-admin/**/*.html")) {
            names.add(template.getFilename());
        }

        assertThat(names).contains("detail.html", "bulk-bar.html");
        String detail = new String(resolver.getResource("classpath:templates/jpa-admin/detail.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(detail).contains("~{jpa-admin/layout :: layout(");
    }
}
