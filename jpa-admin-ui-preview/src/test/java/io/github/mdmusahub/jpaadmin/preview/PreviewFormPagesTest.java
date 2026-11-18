package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task U5: the create and edit form in every state of {@code docs/FIXTURES.md} section 7. Every fixture is built by the
 * console's real factory and rendered by the real template, with no database. This class checks what the visitor sees: every
 * kind of input with its label, the required markers, the help text, the errors linked to their inputs, the masked input that
 * never has a value, the choices, the notices, the addresses, and that nothing typed or stored is ever printed unescaped.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreviewFormPagesTest {

    @Autowired
    private MockMvc mvc;

    private String render(String state) throws Exception {
        return render(state, null);
    }

    private String render(String state, String chrome) throws Exception {
        var request = get("/admin-preview/form").param("state", state);
        if (chrome != null) {
            request = request.param("chrome", chrome);
        }
        MockHttpServletResponse response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).startsWith("text/html");
        return response.getContentAsString();
    }

    /** The form itself, without the shell (the sidebar has a sign-out form of its own). */
    private String form(String state) throws Exception {
        return formOf(render(state));
    }

    private static String formOf(String html) {
        Matcher form = Pattern.compile("(?s)<form class=\"card form-card\".*?</form>").matcher(html);
        assertThat(form.find()).as("the form").isTrue();
        return form.group();
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

    /** The tag of the element that has this id. */
    private static String tagWithId(String html, String id) {
        Matcher tag = Pattern.compile("(?s)<(?:input|select|textarea)\\b[^>]*\\sid=\"" + Pattern.quote(id) + "\"[^>]*>").matcher(html);
        assertThat(tag.find()).as("an element with id " + id).isTrue();
        return tag.group();
    }

    static Stream<String> everyFormFixture() {
        return FormFixtures.states().stream();
    }

    // ---- every state obeys the rules ------------------------------------------------------------------------------------

    @ParameterizedTest(name = "form / {0}")
    @MethodSource("everyFormFixture")
    void everyFormFixtureRendersAndObeysTheUiRules(String state) throws Exception {
        String html = render(state);

        assertThat(html).startsWith("<!DOCTYPE html>").contains("</html>");
        assertThat(HtmlRules.renderedViolations(html)).isEmpty();
        assertThat(html).doesNotContain("th:utext").doesNotContain("th:");
        assertThat(html).contains("/assets/js/form-guard.js");
    }

    @Test
    void theCatalogHoldsTheNineFixturesOfFixturesMdSection7() {
        assertThat(FormFixtures.states()).contains("create-empty", "edit-prefilled", "long-identity", "validation-errors",
                "form-errors", "select-options", "masked-input", "long-form", "duplicate");
    }

    @Test
    void anUnknownStateIsNotFound() throws Exception {
        mvc.perform(get("/admin-preview/form").param("state", "nope")).andExpect(status().isNotFound());
    }

    // ---- create-empty -----------------------------------------------------------------------------------------------------

    @Test
    void theEmptyCreateFormHasAnInputOfEveryKindEachWithALabelAndItPostsToTheCollection() throws Exception {
        String html = render("create-empty");
        String form = flat(formOf(html));

        assertThat(html).contains("<h1 class=\"page-title\">New article</h1>");
        assertThat(form).contains("method=\"post\"", "action=\"/admin-preview/article\"");
        assertThat(form).contains("name=\"_csrf\"");
        assertThat(tagWithId(form, "f-title")).contains("type=\"text\"", "name=\"title\"", "required=\"required\"");
        assertThat(form).contains("<textarea").contains("name=\"body\"");
        assertThat(tagWithId(form, "f-views")).contains("type=\"text\"", "inputmode=\"decimal\"");
        assertThat(tagWithId(form, "f-featured")).contains("type=\"checkbox\"", "value=\"true\"").doesNotContain("checked");
        assertThat(form).contains("<input type=\"hidden\" value=\"false\" name=\"featured\">");
        assertThat(tagWithId(form, "f-publishedOn")).contains("type=\"date\"");
        assertThat(tagWithId(form, "f-updatedAt")).contains("type=\"datetime-local\"", "step=\"1\"");
        assertThat(form).contains("<select class=\"input\" id=\"f-status\"");
        assertThat(tagWithId(form, "f-accessKey")).contains("type=\"password\"", "autocomplete=\"new-password\"");
        assertThat(form).contains("Create record").contains("Cancel");
        for (String id : List.of("title", "body", "views", "rating", "featured", "publishedOn", "updatedAt", "status", "author",
                "accessKey")) {
            assertThat(form).as("label for " + id).contains("for=\"f-" + id + "\"");
        }
    }

    @Test
    void requiredFieldsHaveAVisibleMarkerAndTheWordForAScreenReaderAndOptionalOnesDoNot() throws Exception {
        String form = flat(form("create-empty"));

        // title, views, status, author and the access key (a new record needs its secret) are required.
        assertThat(count(form, "class=\"required-mark\"")).isEqualTo(5);
        assertThat(count(form, "(required)")).isEqualTo(5);
        assertThat(tagWithId(form, "f-body")).doesNotContain("required");
        assertThat(tagWithId(form, "f-rating")).doesNotContain("required");
    }

    @Test
    void helpTextIsPrintedAndLinkedToItsInput() throws Exception {
        String form = flat(form("create-empty"));

        assertThat(form).contains("<p class=\"field-help\" id=\"h-title\">Shown in the list and on the page.</p>");
        assertThat(tagWithId(form, "f-title")).contains("aria-describedby=\"h-title\"");
        assertThat(tagWithId(form, "f-body")).doesNotContain("aria-describedby");
    }

    @Test
    void theCancelQuestionIsADetailsElementThatNeedsNoScript() throws Exception {
        String form = flat(form("create-empty"));

        assertThat(form).contains("<details class=\"confirm\" data-form-cancel>").contains("<summary class=\"btn\">Cancel</summary>")
                .contains("Discard your changes?")
                .contains("<a class=\"btn btn-danger\" data-form-discard href=\"/admin-preview/article\">Yes, discard changes</a>");
    }

    // ---- edit-prefilled ---------------------------------------------------------------------------------------------------

    @Test
    void theEditFormIsPrefilledPostsToTheRecordWithItsVersionAndCancelLeadsToTheRecord() throws Exception {
        String html = render("edit-prefilled");
        String form = flat(formOf(html));

        assertThat(html).contains("<h1 class=\"page-title\">Edit Guide on onboarding 12</h1>").contains("Article #12");
        assertThat(form).contains("action=\"/admin-preview/article/12?jpa-admin-version=3\"");
        assertThat(tagWithId(form, "f-title")).contains("value=\"Guide on onboarding 12\"");
        assertThat(tagWithId(form, "f-views")).contains("value=\"1240\"");
        assertThat(tagWithId(form, "f-featured")).contains("checked");
        assertThat(tagWithId(form, "f-publishedOn")).contains("value=\"2026-10-15\"");
        assertThat(tagWithId(form, "f-updatedAt")).contains("value=\"2026-11-17T10:30:15\"");
        assertThat(form).contains("<option value=\"PUBLISHED\" selected=\"selected\">Published</option>")
                .contains("<option value=\"7\" selected=\"selected\">Member 7</option>");
        assertThat(form).contains("Save changes").contains("href=\"/admin-preview/article/12\">Yes, discard changes</a>");
        assertThat(html).contains("<a href=\"/admin-preview/article/12\">Guide on onboarding 12</a>");
    }

    @Test
    void aTextAreaKeepsItsLineBreaksAndPrintsMarkupAsText() throws Exception {
        String form = form("edit-prefilled");

        assertThat(form).contains("First line.\nSecond line, with &lt;b&gt;markup&lt;/b&gt; shown as text.</textarea>");
    }

    // ---- masked-input -----------------------------------------------------------------------------------------------------

    @Test
    void aMaskedInputNeverHasAValueInAnyMode() throws Exception {
        for (String state : List.of("masked-input", "masked-input-create", "edit-prefilled", "create-empty", "duplicate",
                "validation-errors")) {
            String form = form(state);
            Matcher passwords = Pattern.compile("(?is)<input\\b[^>]*type=\"password\"[^>]*>").matcher(form);
            int found = 0;
            while (passwords.find()) {
                found++;
                assertThat(passwords.group()).as(state).doesNotContain("value=");
            }
            assertThat(found).as(state).isGreaterThan(0);
        }
    }

    @Test
    void onEditTheMaskedInputSaysThatBlankKeepsTheCurrentValueAndOnCreateItSaysNothing() throws Exception {
        String edit = flat(form("masked-input"));
        String create = flat(form("masked-input-create"));

        assertThat(count(edit, "Leave blank to keep the current value.")).isEqualTo(2);
        assertThat(tagWithId(edit, "f-accessKey")).contains("aria-describedby=\"h-accessKey\"").doesNotContain("required");
        assertThat(create).doesNotContain("Leave blank");
        assertThat(tagWithId(create, "f-accessKey")).contains("required=\"required\"");
        assertThat(tagWithId(create, "f-recoveryPhrase")).doesNotContain("required");
    }

    // ---- errors -----------------------------------------------------------------------------------------------------------

    @Test
    void eachErrorIsNextToItsFieldAndLinkedToItsInput() throws Exception {
        String form = flat(form("validation-errors"));

        for (String key : List.of("title", "views", "publishedOn", "status", "author")) {
            assertThat(tagWithId(form, "f-" + key)).contains("aria-invalid=\"true\"").contains("e-" + key);
            assertThat(form).contains("<p class=\"field-error\" id=\"e-" + key + "\">");
        }
        assertThat(count(form, "aria-invalid=\"true\"")).isEqualTo(5);
        assertThat(form).contains("Enter a value. This field is required.")
                .contains("Enter a whole number, using digits only, for example 42.")
                .contains("Enter a date that exists, as year-month-day, for example 2026-11-17.");
        assertThat(tagWithId(form, "f-views")).contains("value=\"twelve\""); // what was typed is kept
        assertThat(tagWithId(form, "f-body")).doesNotContain("aria-invalid");
    }

    @Test
    void theSummaryAtTheTopIsAnAlertThatLinksToEachFieldAndTakesFocus() throws Exception {
        String form = flat(form("validation-errors"));

        assertThat(form).contains("class=\"form-summary\" role=\"alert\" tabindex=\"-1\" autofocus data-form-summary")
                .contains("5 fields need your attention. They are marked below.");
        for (String key : List.of("title", "views", "publishedOn", "status", "author")) {
            assertThat(form).contains("<a href=\"#f-" + key + "\">");
        }
        assertThat(form.indexOf("form-summary")).as("the summary comes before the first field").isLessThan(form.indexOf("f-title"));
    }

    @Test
    void aWholeFormErrorIsASummaryWithNoFieldMarkedAndNoLinks() throws Exception {
        String form = flat(form("form-errors"));

        assertThat(form).contains("role=\"alert\"", "was changed by someone else after you opened the form");
        assertThat(form).doesNotContain("aria-invalid").doesNotContain("class=\"field-error\"").doesNotContain("<li>");
    }

    @Test
    void aPageWithNoErrorHasNoSummary() throws Exception {
        assertThat(form("create-empty")).doesNotContain("form-summary").doesNotContain("aria-invalid");
    }

    // ---- selects ----------------------------------------------------------------------------------------------------------

    @Test
    void aSelectListsItsChoicesWithTheStoredOneSelectedAndABlankChoiceFirst() throws Exception {
        String form = flat(form("select-options"));

        assertThat(form).contains("<option value=\"\">None</option>")
                .contains("<option value=\"IN_REVIEW\" selected=\"selected\">In review</option>")
                .contains("<option value=\"\" selected=\"selected\">Choose...</option>"); // required and nothing chosen
        Matcher authors = Pattern.compile("(?s)<select[^>]*id=\"f-author\".*?</select>").matcher(form);
        assertThat(authors.find()).isTrue();
        assertThat(count(authors.group(), "<option")).isEqualTo(51); // 50 members and the blank choice
        assertThat(authors.group()).contains("<option value=\"7\" selected=\"selected\">Member 7</option>");
        Matcher reviewer = Pattern.compile("(?s)<select[^>]*id=\"f-reviewer\".*?</select>").matcher(form);
        assertThat(reviewer.find()).isTrue();
        assertThat(reviewer.group()).contains("<option value=\"\" selected=\"selected\">None</option>");
        assertThat(count(reviewer.group(), "selected=\"selected\"")).isEqualTo(1);
    }

    @Test
    void aSelectWithNoChoicesIsANoticeNotAControl() throws Exception {
        String form = flat(form("relation-notice"));

        assertThat(form).doesNotContain("id=\"f-author\"").doesNotContain("id=\"f-publisher\"");
        assertThat(form).contains("class=\"field-notice\" role=\"note\" id=\"h-author\"")
                .contains("This field has more than 200 records to choose from, so it cannot be set here yet.")
                .contains("There are no records to choose from yet. Create one first, then come back.");
        assertThat(count(form, "<select")).isZero();
        assertThat(count(form, "class=\"field-notice\"")).isEqualTo(2);
    }

    // ---- the other fixtures ------------------------------------------------------------------------------------------------

    @Test
    void aDuplicateIsACreateFormWithTheCopiedValuesAndTheNoticeThatSaysWhatIsMissing() throws Exception {
        String html = render("duplicate");
        String form = flat(formOf(html));

        assertThat(html).contains("<h1 class=\"page-title\">New article</h1>").contains("role=\"status\"")
                .contains("This form starts from a copy of article #12.");
        assertThat(form).contains("action=\"/admin-preview/article\"").doesNotContain("jpa-admin-version");
        assertThat(tagWithId(form, "f-title")).contains("value=\"Guide on onboarding 12\"");
        assertThat(tagWithId(form, "f-accessKey")).doesNotContain("value=");
        assertThat(form).contains("Create record");
    }

    @Test
    void aLongTitleAndALongEntityLabelAndAUuidWrapInTheHeading() throws Exception {
        String html = render("long-identity");

        assertThat(html).contains("123e4567-e89b-12d3-a456-426614174000");
        assertThat(html).contains("Edit " + "A long record title ".repeat(6));
        assertThat(flat(formOf(html))).contains("action=\"/admin-preview/article/123e4567-e89b-12d3-a456-426614174000?jpa-admin-version=0\"");
    }

    @Test
    void aLongFormHasThirtyFieldsInTheGridWithLongLabelsAndHelp() throws Exception {
        String form = form("long-form");

        assertThat(count(form, "class=\"field form-field")).isEqualTo(30);
        assertThat(form).contains("<div class=\"form-grid\">").contains("A rather long label that keeps going");
        assertThat(form).contains("AVeryLongWordWithoutAnySpacesAtAllToSeeWhetherItBreaksInsteadOfOverflowingTheScreen");
    }

    @Test
    void everythingStoredOrTypedIsPrintedAsText() throws Exception {
        String html = render("escaped");
        String form = flat(formOf(html));

        assertThat(html).doesNotContain("<script>alert").doesNotContain("<b>markup</b>").doesNotContain("<b>A</b>");
        assertThat(form).contains("&lt;/textarea&gt;&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("Help with &lt;b&gt;markup&lt;/b&gt; &amp; an ampersand")
                .contains("A &lt;b&gt;sentence&lt;/b&gt; with markup.").contains("&lt;b&gt;A&lt;/b&gt;");
        assertThat(tagWithId(form, "f-title")).doesNotContain("\"onfocus=\"");
    }

    @Test
    void everyLinkOfTheFormComesFromTheRecordSoACustomBasePathReachesThemAll() throws Exception {
        String html = render("edit-prefilled", "custom-base-path");
        String form = flat(formOf(html));

        assertThat(form).contains("action=\"" + ChromeFixtures.ALTERNATE_BASE + "/article/12?jpa-admin-version=3\"")
                .contains("href=\"" + ChromeFixtures.ALTERNATE_BASE + "/article/12\">Yes, discard changes</a>");
        List<String> outside = new ArrayList<>();
        Matcher addresses = Pattern.compile("\\b(?:href|action)=\"(/[^\"]*)\"").matcher(form);
        while (addresses.find()) {
            if (!addresses.group(1).startsWith(ChromeFixtures.ALTERNATE_BASE)) {
                outside.add(addresses.group(1));
            }
        }
        assertThat(outside).isEmpty();
    }
}
