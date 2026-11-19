package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task U8, saved views (Phase 12, D-114, D-115): the Saved views panel and what the page hands to {@code views.js}, in the
 * fixture states, drawn by the real templates. The views themselves live in the browser, so what is tested here is the server's
 * part: the panel's markup and labels, the data attributes taken from the record, the state address (which never has a page), the
 * sentence for a visitor without script, and the stored-JSON fixtures the preview serves.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreviewViewsPanelTest {

    private static final Pattern PANEL = Pattern.compile("(?s)<details class=\"filter-panel views-panel\".*?</template>\\s*</div>\\s*</details>");

    @Autowired
    private MockMvc mvc;

    private String render(String state) throws Exception {
        return mvc.perform(get("/admin-preview/list").param("state", state)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    private static String panel(String html) {
        Matcher panel = PANEL.matcher(html);
        assertThat(panel.find()).as("a Saved views panel").isTrue();
        return panel.group();
    }

    private static String attribute(String panel, String name) {
        Matcher value = Pattern.compile(name + "=\"([^\"]*)\"").matcher(panel);
        assertThat(value.find()).as(name).isTrue();
        return value.group(1);
    }

    // ---- the data attributes -----------------------------------------------------------------------------------------

    @Test
    void thePanelHandsTheScriptTheBaseTheStateTheColumnsAndTheFilters() throws Exception {
        String panel = panel(render("saved-views"));

        assertThat(attribute(panel, "data-views-base")).isEqualTo("/admin-preview/article");
        assertThat(attribute(panel, "data-views-columns")).isEqualTo("id title author status views published featured");
        assertThat(attribute(panel, "data-views-filters")).isEqualTo("title author status views published featured");
    }

    @Test
    void theStateIsTheServersEffectiveStateAndNeverHasAPage() throws Exception {
        String state = attribute(panel(render("saved-views")), "data-views-state");

        assertThat(state).isEqualTo("/admin-preview/article?q=guide&amp;filter.status=PUBLISHED&amp;hide=published&amp;hide=featured"
                + "&amp;sort=views&amp;dir=desc&amp;size=50");
        assertThat(state).doesNotContain("page=");
    }

    @Test
    void aListOnAMiddlePageStillHasAStateWithoutThePage() throws Exception {
        String state = attribute(panel(render("hidden-middle-page")), "data-views-state");

        assertThat(state).isEqualTo("/admin-preview/article?hide=author&amp;hide=views&amp;size=10");
        assertThat(state).doesNotContain("page=");
    }

    @Test
    void thePlainListHasThePlainAddressAsItsState() throws Exception {
        assertThat(attribute(panel(render("views-empty")), "data-views-state")).isEqualTo("/admin-preview/article");
    }

    @Test
    void aMaskedColumnsKeyIsNotHandedToTheScriptAndNeitherIsAFilterTheConsoleDoesNotOffer() throws Exception {
        String html = render("column-masked-absent");
        String panel = panel(html);

        assertThat(attribute(panel, "data-views-columns")).isEqualTo("id name email joined");
        assertThat(attribute(panel, "data-views-filters")).isEqualTo("name email joined");
        assertThat(panel).doesNotContain("password");
    }

    @Test
    void theBaseFollowsTheConsolePathOfThePage() throws Exception {
        String html = mvc.perform(get("/admin-preview/list").param("state", "normal").param("chrome", "custom-base-path"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(attribute(panel(html), "data-views-base")).isEqualTo("/preview-alt/article");
    }

    // ---- the markup --------------------------------------------------------------------------------------------------

    @Test
    void thePanelHasALabelledNameFieldASaveButtonAndALiveStatusLine() throws Exception {
        String panel = panel(render("views-empty"));

        assertThat(panel).contains("<label class=\"filter-label\" for=\"views-name\">Name for the current view</label>",
                "id=\"views-name\"", "maxlength=\"60\"", "Save current view", "role=\"status\"", "aria-live=\"polite\"");
        assertThat(panel).contains("No saved views yet.");
    }

    @Test
    void theEmptyMenuAndTheListAreForScriptAndTheNoScriptVisitorGetsOneSentence() throws Exception {
        String panel = panel(render("views-empty"));

        assertThat(panel).contains("class=\"views-nojs no-js-only\"", "Saved views need JavaScript and browser storage");
        assertThat(panel).contains("class=\"js-only views-script\"");
    }

    @Test
    void thePanelHasNoFormOfItsOwnAndItsControlsSendNothing() throws Exception {
        String panel = panel(render("views-empty"));

        assertThat(panel).doesNotContain("<form");
        assertThat(panel).doesNotContainPattern("<input[^>]*\\sname=");
        assertThat(panel).contains("<button type=\"button\" class=\"btn btn-primary\" data-views-save>");
    }

    @Test
    void theRowTemplateIsInertAndEveryControlInItIsLabelled() throws Exception {
        String panel = panel(render("views-empty"));
        String template = panel.substring(panel.indexOf("<template"));

        assertThat(template).contains("data-views-apply", "aria-label=\"Rename this view\"", "aria-label=\"Delete this view\"",
                "aria-label=\"Yes, delete this view\"", "<label class=\"visually-hidden\" for=\"views-rename-name\"",
                "id=\"views-rename-name\"");
        assertThat(template).as("delete asks first, with a details element (UI-5)").contains("<details class=\"confirm views-delete\">",
                "Delete this view?", "Yes, delete view");
        assertThat(template).doesNotContain("href=");
    }

    @Test
    void theScriptIsLoadedOnceAsAnExternalDeferredFile() throws Exception {
        String html = render("saved-views");

        assertThat(html.split("assets/js/views\\.js", -1).length - 1).isEqualTo(1);
        assertThat(html).contains("views.js\" defer");
    }

    // ---- the stored JSON the preview serves ---------------------------------------------------------------------------------

    @Test
    void everyStoredStateAnswersWithTheTextABrowserWouldHoldAndTheKeyItBelongsTo() throws Exception {
        for (String state : ViewsFixtures.storedStates()) {
            mvc.perform(get("/admin-preview/stored-views").param("state", state)).andExpect(status().isOk())
                    .andExpect(header().string("X-Storage-Key", "jpa-admin.views./admin-preview/article"));
        }
        assertThat(ViewsFixtures.storedStates()).contains("several", "empty", "dropped", "invalid-json", "full", "over-the-cap",
                "hides-every-column");
    }

    @Test
    void theSeveralStateHoldsFourViewsAndTheDroppedStateHoldsOneGoodViewAmongBadOnes() throws Exception {
        String several = mvc.perform(get("/admin-preview/stored-views").param("state", "several")).andReturn().getResponse()
                .getContentAsString();
        String dropped = mvc.perform(get("/admin-preview/stored-views").param("state", "dropped")).andReturn().getResponse()
                .getContentAsString();

        assertThat(several.split("\"name\"", -1).length - 1).isEqualTo(4);
        assertThat(dropped).contains("\"name\":\"Kept\"", "\"hiddenColumns\":[\"nothing\"]", "\"filter.apiKey\"",
                "N".repeat(61), "\"dir\":\"sideways\"", "\"pageSize\":30", "\"not an object\"");
    }

    @Test
    void theFullStateHoldsTwentyViewsAndTheOverTheCapStateTwentyTwo() throws Exception {
        String full = mvc.perform(get("/admin-preview/stored-views").param("state", "full")).andReturn().getResponse()
                .getContentAsString();
        String over = mvc.perform(get("/admin-preview/stored-views").param("state", "over-the-cap")).andReturn().getResponse()
                .getContentAsString();

        assertThat(full.split("\"name\"", -1).length - 1).isEqualTo(20);
        assertThat(over.split("\"name\"", -1).length - 1).isEqualTo(22);
    }

    @Test
    void theInvalidJsonStateIsNotJsonAtAll() throws Exception {
        mvc.perform(get("/admin-preview/stored-views").param("state", "invalid-json")).andExpect(status().isOk());
        assertThat(ViewsFixtures.stored("invalid-json")).contains("{this is not json");
    }

    @Test
    void anUnknownStoredStateAnswers404WithTheValidNames() throws Exception {
        String body = mvc.perform(get("/admin-preview/stored-views").param("state", "nope")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("several", "dropped", "full");
    }

    @Test
    void noStoredFixtureHoldsAnythingSensitive() {
        for (String state : ViewsFixtures.storedStates()) {
            assertThat(ViewsFixtures.stored(state).orElseThrow().toLowerCase()).as(state)
                    .doesNotContain("password", "token", "ticket", "csrf", "secret");
        }
    }
}
