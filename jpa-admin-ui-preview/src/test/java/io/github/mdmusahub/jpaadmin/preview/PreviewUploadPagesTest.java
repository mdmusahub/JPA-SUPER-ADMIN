package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task U6: the upload field of the form in every state of {@code docs/FIXTURES.md} section 7 (the real template, the real
 * factory), the stub endpoint with the six states of section 8, and the scripts as text. The browser run of {@code uploader.js}
 * and {@code form-guard.js} is a manual item (and a Phase 14 item); what can be checked without a browser is checked here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreviewUploadPagesTest {

    @Autowired
    private MockMvc mvc;

    private String render(String state) throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/admin-preview/form").param("state", state)).andExpect(status().isOk())
                .andReturn().getResponse();
        return response.getContentAsString();
    }

    private static String card(String html) {
        Matcher card = Pattern.compile("(?s)<div class=\"card form-card\">.*</main>").matcher(html);
        assertThat(card.find()).isTrue();
        return card.group();
    }

    private static String asset(String name) throws IOException {
        try (InputStream in = PreviewUploadPagesTest.class.getResourceAsStream("/jpa-admin/assets/js/" + name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    // ---- the page: separate sections, no nested form -------------------------------------------------------------------------

    @Test
    void theRecordFormAndEachUploadFormAreSeparateFormsAndNoneIsInsideAnother() throws Exception {
        String html = card(render("upload-states"));

        assertThat(count(html, "<form ")).isEqualTo(5);
        assertThat(count(html, "</form>")).isEqualTo(5);
        assertThat(html).contains("<form id=\"record-form\" method=\"post\" data-form-guard action=\"/admin-preview/article\">");
        // Walk the tags: the depth never goes above one.
        int depth = 0;
        int deepest = 0;
        Matcher tags = Pattern.compile("(?i)<(/?)form\\b").matcher(html);
        while (tags.find()) {
            depth += tags.group(1).isEmpty() ? 1 : -1;
            deepest = Math.max(deepest, depth);
        }
        assertThat(deepest).isEqualTo(1);
        assertThat(HtmlRules.renderedViolations(render("upload-states"))).isEmpty();
    }

    @Test
    void theUploadSectionsAreBetweenTheRecordFormAndTheActionsAndSaveBelongsToTheRecordForm() throws Exception {
        String html = card(render("upload-states"));

        int recordFormEnds = html.indexOf("</form>");
        int firstUpload = html.indexOf("data-upload data-state");
        int actions = html.indexOf("<div class=\"form-actions\">");
        int save = html.indexOf("data-form-submit");
        assertThat(recordFormEnds).isPositive();
        assertThat(firstUpload).isGreaterThan(recordFormEnds);
        assertThat(actions).isGreaterThan(html.lastIndexOf("data-upload data-state"));
        assertThat(save).isGreaterThan(actions);
        assertThat(html.substring(actions)).contains("<button type=\"submit\" class=\"btn btn-primary\" form=\"record-form\" data-form-submit");
    }

    @Test
    void anUploadFieldHasOnlyItsHiddenAddressInsideTheRecordForm() throws Exception {
        String html = card(render("upload-states"));
        String recordForm = html.substring(0, html.indexOf("</form>"));

        assertThat(recordForm).contains("name=\"cover\" value=\"\" data-upload-value", "name=\"photo\" value=\"/admin-preview/files/cover.png\" data-upload-value",
                "name=\"brochure\" value=\"/admin-preview/files/brochure.pdf\" data-upload-value", "name=\"scan\" value=\"\" data-upload-value");
        assertThat(recordForm).doesNotContain("type=\"file\"").doesNotContain("ticket").doesNotContain("multipart");
        // The input text of the title is still an ordinary labelled text input.
        assertThat(recordForm).contains("id=\"f-title\"");
    }

    @Test
    void eachUploadFormPostsMultipartToTheEndpointUnderTheBasePathWithItsOwnHiddenFields() throws Exception {
        String html = card(render("upload-states"));
        Matcher forms = Pattern.compile("(?s)<form class=\"upload-form\".*?</form>").matcher(html);
        List<String> found = new ArrayList<>();
        while (forms.find()) {
            found.add(forms.group());
        }

        assertThat(found).hasSize(4);
        String[] keys = {"cover", "photo", "brochure", "scan"};
        String[] tickets = {"success-image", "slow", "error-too-large", "error-type"};
        for (int i = 0; i < 4; i++) {
            String form = found.get(i);
            assertThat(form).contains("method=\"post\" enctype=\"multipart/form-data\" action=\"/admin-preview/uploads\"")
                    .contains("name=\"_csrf\" value=\"preview-fake-token-not-a-secret\"", "name=\"entity\" value=\"article\"",
                            "name=\"field\" value=\"" + keys[i] + "\"", "name=\"ticket\" value=\"" + tickets[i] + "\"",
                            "type=\"file\" name=\"file\" id=\"f-" + keys[i] + "\"", "data-upload-submit");
            assertThat(form).as("a create form has no record").doesNotContain("name=\"record\"");
        }
    }

    @Test
    void aTicketIsPrintedOnlyInTheHiddenInputOfItsOwnFieldAndNeverInATextFieldOrALink() throws Exception {
        String html = render("upload-states");

        assertThat(count(html, "name=\"ticket\"")).isEqualTo(4);
        assertThat(html).doesNotContain("value=\"success-image\"x");
        for (String ticket : List.of("success-image", "slow", "error-too-large", "error-type")) {
            assertThat(count(html, "name=\"ticket\" value=\"" + ticket + "\"")).as(ticket).isEqualTo(1);
            assertThat(count(html, "href=\"" + ticket)).as("not in a link").isZero();
        }
        // A form with no upload field has no ticket input at all.
        assertThat(render("create-empty")).doesNotContain("name=\"ticket\"").doesNotContain("data-upload");
    }

    @Test
    void anEditFormGivesTheRecordIdToItsUploadFormSoThePlainFlowComesBack() throws Exception {
        String html = card(render("upload-edit"));

        assertThat(html).contains("name=\"record\" value=\"12\"");
        assertThat(html).contains("<form id=\"record-form\" method=\"post\" data-form-guard action=\"/admin-preview/article/12?jpa-admin-version=3\">");
    }

    // ---- labels, help, errors, preview, remove ---------------------------------------------------------------------------------------

    @Test
    void everyFileInputHasALabelAndTheHelpAndTheErrorAreLinkedToIt() throws Exception {
        String html = card(render("upload-states"));

        for (String key : List.of("cover", "photo", "brochure", "scan")) {
            assertThat(html).contains("<label for=\"f-" + key + "\">");
            assertThat(html).contains("id=\"f-" + key + "\"");
        }
        assertThat(html).contains("<p class=\"field-help\" id=\"h-cover\">Accepts PNG image, JPEG image, GIF image, WebP image, up to 2 MB.</p>");
        Matcher scan = Pattern.compile("(?s)<input class=\"upload-input\"[^>]*id=\"f-scan\"[^>]*>").matcher(html);
        assertThat(scan.find()).isTrue();
        assertThat(scan.group()).contains("aria-invalid=\"true\"", "aria-describedby=\"h-scan e-scan\"", "accept=\"image/png,image/jpeg,image/gif,image/webp\"");
        assertThat(html).contains("<p class=\"field-error\" id=\"e-scan\">")
                .contains("The file is not one this console uploaded. Upload it again.");
    }

    @Test
    void theAdvisoryLimitsAndTheCsrfHeaderNameReachTheScriptAsDataAttributesOnly() throws Exception {
        String html = card(render("upload-states"));

        assertThat(html).contains("data-accept=\"image/png,image/jpeg,image/gif,image/webp\"", "data-max-bytes=\"2097152\"",
                "data-csrf-header=\"X-CSRF-TOKEN\"", "data-accept=\"application/pdf\"");
        assertThat(html).doesNotContain("<script");
    }

    @Test
    void aStoredPictureIsPreviewedWithTheFieldLabelAsItsAlternativeTextAndADocumentIsALink() throws Exception {
        String html = card(render("upload-states"));

        assertThat(html).contains("<img class=\"upload-preview\" src=\"/admin-preview/files/cover.png\" alt=\"Photo (has a picture, the stub is slow)\" data-upload-preview>");
        assertThat(html).contains("<a class=\"cell-link cell-file\" href=\"/admin-preview/files/brochure.pdf\" data-upload-link>");
        assertThat(html).contains("No file yet.");
        assertThat(count(html, "<img ")).isEqualTo(1);
    }

    @Test
    void everyImageOfEveryFormFixtureHasAlternativeText() throws Exception {
        for (String state : FormFixtures.states()) {
            String html = render(state);
            Matcher images = Pattern.compile("(?is)<img\\b[^>]*>").matcher(html);
            while (images.find()) {
                assertThat(images.group()).as(state).containsPattern("alt=\"[^\"]+\"");
            }
            assertThat(HtmlRules.renderedViolations(html)).as(state).isEmpty();
        }
    }

    @Test
    void theCurrentFileCanBeRemovedWithoutScriptByACheckboxOfTheRecordForm() throws Exception {
        String html = card(render("upload-states"));

        assertThat(html).contains("<input type=\"checkbox\" value=\"true\" form=\"record-form\" id=\"r-photo\" name=\"photo-remove\">");
        assertThat(html).contains("<label for=\"r-photo\">Remove the current file when the record is saved</label>");
        Matcher empty = Pattern.compile("(?s)<p class=\"upload-remove\"[^>]*data-upload-remove-check[^>]*hidden[^>]*>").matcher(html);
        assertThat(empty.find()).as("the field with no file hides its checkbox until script needs it").isTrue();
    }

    @Test
    void theReplaceAndRemoveButtonsAreHiddenWithoutScriptAndHaveAriaLabels() throws Exception {
        String html = card(render("upload-edit"));

        assertThat(html).contains("<div class=\"upload-actions\" hidden data-upload-actions>");
        assertThat(html).contains("data-upload-replace aria-label=\"Replace the file of Cover\"")
                .contains("data-upload-remove aria-label=\"Remove the file of Cover\"");
    }

    @Test
    void thePlainFormSaysThatTextTypedAboveIsLostWhenAFileIsSent() throws Exception {
        String html = card(render("upload-states"));

        assertThat(html).contains("Sending a file reloads this page: anything you typed above and did not save is lost. Save first if you need to.");
        assertThat(html).contains("<progress class=\"upload-progress\" max=\"100\" value=\"0\" aria-label=\"Upload progress\" hidden data-upload-progress>");
        assertThat(html).contains("role=\"status\" aria-live=\"polite\"");
    }

    @Test
    void theUploaderScriptIsLoadedFromAnExternalFileOnEveryPage() throws Exception {
        String html = render("upload-states");

        assertThat(count(html, "/assets/js/uploader.js")).isEqualTo(1);
        assertThat(html).contains("<script src=\"/admin-preview/assets/js/uploader.js\" defer></script>");
    }

    // ---- cells and the detail strip -----------------------------------------------------------------------------------------------

    private String detail(String state) throws Exception {
        return mvc.perform(get("/admin-preview/detail").param("state", state)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
    }

    @Test
    void anImageCellIsAThumbnailUnderTheBasePathWithTheFieldLabelAsAlternativeTextAndTheStripIsFilledFromIt() throws Exception {
        String html = detail("upload-cells");

        assertThat(html).contains("<img class=\"cell-thumb\" src=\"/admin-preview/files/cover.png\" alt=\"Cover\">");
        assertThat(html).contains("<img class=\"detail-thumb\" src=\"/admin-preview/files/cover.png\" alt=\"Cover\">");
        assertThat(html).contains("<img class=\"detail-thumb\" src=\"/admin-preview/files/second.png\" alt=\"Photo &lt;i&gt;two&lt;/i&gt;\">");
        assertThat(count(html, "class=\"detail-thumb\"")).as("one picture in the strip for each image cell, none for a document").isEqualTo(2);
        assertThat(HtmlRules.renderedViolations(html)).isEmpty();
    }

    @Test
    void aFileCellIsALinkUnderTheBasePathWhoseNameAndHoverTextAreEscaped() throws Exception {
        String html = detail("upload-cells");

        assertThat(html).contains("href=\"/admin-preview/files/brochure.pdf\"").contains("&lt;b&gt;Brochure&lt;/b&gt; (PDF)")
                .contains("title=\"&lt;script&gt;x&lt;/script&gt;.pdf\"");
        assertThat(html).doesNotContain("<b>Brochure</b>").doesNotContain("<script>x</script>").doesNotContain("<img src=x");
    }

    @Test
    void aFileThatIsGoneIsPlainWordsNotAnImageOrALinkAndTheRawFileNameIsNeverAnAddress() throws Exception {
        String html = detail("upload-cells");

        assertThat(html).contains("Scan: file not found");
        assertThat(html).doesNotContain("src=\"Scan").doesNotContain("href=\"Scan");
        assertThat(count(html, "<img ")).isEqualTo(4);
        Matcher sources = Pattern.compile("(?is)<img\b[^>]*\ssrc=\"([^\"]*)\"").matcher(html);
        while (sources.find()) {
            assertThat(sources.group(1)).startsWith("/admin-preview/files/");
        }
    }

    @Test
    void theListShowsAnImageCellAsASmallThumbnailAndNeverTheFileNameAsAPath() throws Exception {
        String html = mvc.perform(get("/admin-preview/list").param("state", "all-cell-types")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();

        assertThat(html).contains("<img class=\"cell-thumb\" src=\"/admin-preview/files/cover.png\" alt=\"Image\">");
        assertThat(HtmlRules.renderedViolations(html)).isEmpty();
    }

    // ---- the stub endpoint: six states, nothing stored ------------------------------------------------------------------------------

    private MockHttpServletResponse stub(String ticket, String filename) throws Exception {
        return mvc.perform(multipart("/admin-preview/uploads").file(new MockMultipartFile("file", filename, "image/png", new byte[] {1, 2, 3}))
                .param("ticket", ticket).header("Accept", "application/json")).andReturn().getResponse();
    }

    @Test
    void theStubAnswersTheSixStatesOfFixturesMdSection8() throws Exception {
        MockHttpServletResponse image = stub("success-image", "my photo.png");
        assertThat(image.getStatus()).isEqualTo(200);
        assertThat(image.getContentAsString()).contains("\"url\":\"/admin-preview/files/cover.png\"", "\"name\":\"my photo.png\"", "\"size\":3")
                .contains("\"error\":null");

        MockHttpServletResponse file = stub("success-file", "doc.pdf");
        assertThat(file.getStatus()).isEqualTo(200);
        assertThat(file.getContentAsString()).contains("\"url\":\"/admin-preview/files/brochure.pdf\"");

        MockHttpServletResponse large = stub("error-too-large", "big.png");
        assertThat(large.getStatus()).isEqualTo(413);
        assertThat(large.getContentAsString()).contains("\"url\":null", "2 MB", "Choose a smaller file");

        MockHttpServletResponse type = stub("error-type", "x.png");
        assertThat(type.getStatus()).isEqualTo(415);
        assertThat(type.getContentAsString()).contains("whatever its name says");

        MockHttpServletResponse expired = stub("error-ticket-expired", "x.png");
        assertThat(expired.getStatus()).isEqualTo(403);
        assertThat(expired.getContentAsString()).contains("Open the form page again");
    }

    @Test
    void theSlowStateWaitsAboutFiveSecondsThenSucceeds() throws Exception {
        long before = System.nanoTime();

        MockHttpServletResponse slow = stub("slow", "x.png");

        assertThat(slow.getStatus()).isEqualTo(200);
        assertThat((System.nanoTime() - before) / 1_000_000).isGreaterThanOrEqualTo(4900);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "nonsense", "../../etc/passwd", "<script>"})
    void anyOtherTicketIsTheSuccessState(String ticket) throws Exception {
        MockHttpServletResponse response = stub(ticket, "a.png");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).doesNotContain(ticket.isEmpty() ? "\u0001" : ticket);
    }

    @Test
    void theStubStoresNothingAndKeepsNoFolder() throws Exception {
        MockHttpServletResponse response = stub("success-image", "../../evil.png");

        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"name\":\"evil.png\"").doesNotContain("..");
        assertThat(java.nio.file.Files.exists(java.nio.file.Path.of("uploads"))).as("no upload folder is made").isFalse();
    }

    @Test
    void theStubServesOnePictureOneDocumentAndNotFoundForTheMissingOne() throws Exception {
        MockHttpServletResponse picture = mvc.perform(get("/admin-preview/files/cover.png")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(picture.getContentType()).isEqualTo("image/png");
        assertThat(picture.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        MockHttpServletResponse document = mvc.perform(get("/admin-preview/files/brochure.pdf")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(document.getContentType()).isEqualTo("application/pdf");
        assertThat(document.getHeader("Content-Disposition")).isEqualTo("attachment");
        mvc.perform(get("/admin-preview/files/missing.png")).andExpect(status().isNotFound());
        mvc.perform(get("/admin-preview/files/page.html")).andExpect(status().isNotFound());
        mvc.perform(get("/admin-preview/files/logo.svg")).andExpect(status().isNotFound());
    }

    // ---- the scripts, as text -------------------------------------------------------------------------------------------------------

    @Test
    void theUploaderStoresNothingAndBuildsElementsWithoutMarkupFromStrings() throws Exception {
        String script = asset("uploader.js");

        assertThat(script).doesNotContain("localStorage").doesNotContain("sessionStorage").doesNotContain("indexedDB")
                .doesNotContain("document.cookie").doesNotContain("innerHTML").doesNotContain("outerHTML")
                .doesNotContain("insertAdjacentHTML").doesNotContain("eval(").doesNotContain("new Function")
                .doesNotContain("document.write").doesNotContain("http://").doesNotContain("https://");
        assertThat(script.length()).as("a small file").isLessThan(14_000);
    }

    @Test
    void theUploaderSendsTheTokenInTheHeaderAndTheTicketWithTheFileAndAsksForJson() throws Exception {
        String script = asset("uploader.js");

        assertThat(script).contains("request.setRequestHeader(csrfHeader, token())", "request.setRequestHeader('Accept', 'application/json')",
                "new FormData(form)", "data.set('file', file)", "new XMLHttpRequest()", "request.upload.addEventListener('progress'");
    }

    @Test
    void theUploaderDisablesSaveWhileAFileIsOnItsWayAndStopsTheRecordFormBeingSent() throws Exception {
        String script = asset("uploader.js");

        assertThat(script).contains("submitButton.disabled = running > 0;", "running += 1;", "running -= 1;");
        assertThat(script).contains("document.addEventListener('submit', function (event) {",
                "if (running > 0 && event.target === recordForm) {", "event.preventDefault();", "event.stopImmediatePropagation();", "}, true);");
    }

    @Test
    void theUploaderChecksTypeAndSizeOnlyAsAdviceAndTheServerDecides() throws Exception {
        String script = asset("uploader.js");

        assertThat(script).contains("THE BROWSER CHECKS ARE ADVISORY");
        assertThat(script).contains("function advice(file)").contains("the server checks the ticket");
        // Even a file the browser lets through is sent as it is; a refusal from the server is shown in the same place.
        assertThat(script).contains("fail(result.error)");
    }

    @Test
    void theUploaderOnlyPutsAPathOfThisSiteIntoThePage() throws Exception {
        String script = asset("uploader.js");

        assertThat(script).contains("function safeAddress(url)", "url.charAt(0) === '/' && url.charAt(1) !== '/'");
        assertThat(script).contains("var IMAGE = /\\.(png|jpg|gif|webp)$/i;");
        assertThat(script).doesNotContain("svg");
    }

    @Test
    void theFormGuardCountsAFinishedUploadAsAnUnsavedChangeAndFindsTheActionsOnTheDocument() throws Exception {
        String guard = asset("form-guard.js");

        assertThat(guard).contains("document.addEventListener('jpa-admin:upload-changed', markChanged);");
        assertThat(guard).contains("document.querySelector('[data-form-discard]')", "document.querySelector('[data-form-cancel] > summary')");
        assertThat(asset("uploader.js")).contains("'jpa-admin:upload-changed'");
        assertThat(guard).doesNotContain("localStorage").doesNotContain("document.cookie");
    }
}
