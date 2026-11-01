package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 1 acceptance, at the default console path: the starter and the two properties are enough for {@code /admin}
 * to respond, the console's assets are served at {@code /admin/assets/**} and nowhere else (UI_PLAN section 10), and
 * every link built from the base path resolves and lies under it (D-29).
 *
 * <p>The two properties come from {@code src/test/resources/application.properties}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsoleAtDefaultPathTest {

    private static final String TOKENS = "/assets/css/tokens.css";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BasePath basePath;

    @Test
    void theConsolePathRequiresSignInAndThenShowsTheDashboard() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isFound()).andExpect(header().string("Location", "/admin/login"));

        var dashboard = mvc.perform(get("/admin").session(signedIn().session())).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8")).andReturn().getResponse();
        assertThat(dashboard.getContentAsString()).contains("<h1 class=\"page-title\">Dashboard</h1>");
    }

    @Test
    void theConsolePathAnswersHead() throws Exception {
        mvc.perform(head("/admin")).andExpect(status().isFound());
        mvc.perform(head("/admin").session(signedIn().session())).andExpect(status().isOk());
    }

    @Test
    void theConsolePathIsReadOnly() throws Exception {
        MockBrowser browser = signedIn();
        String token = MockBrowser.csrf(browser.get("/admin").getContentAsString());

        assertThat(browser.post("/admin", "_csrf", token).getStatus()).isEqualTo(405);
        assertThat(browser.post("/admin").getStatus()).as("without the token it is refused first").isEqualTo(403);
        mvc.perform(post("/admin")).andExpect(status().isForbidden());
    }

    private MockBrowser signedIn() throws Exception {
        return MockBrowser.signedIn(mvc);
    }

    @Test
    void theBasePathBeanIsTheDefault() {
        assertThat(basePath.value()).isEqualTo("/admin");
    }

    // ---- assets: served under the console path only (UI_PLAN section 10) ---------------------------------------

    @Test
    void anAssetIsServedUnderTheConsolePath() throws Exception {
        var response = mvc.perform(get("/admin" + TOKENS))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/css"))
                .andReturn().getResponse();

        assertThat(response.getContentAsString()).contains("design tokens");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/jpa-admin/assets/css/tokens.css",        // the folder name on the classpath
            "/assets/css/tokens.css",                  // the asset folder at the site root
            "/css/tokens.css",                         // the asset folder's content at the site root
            "/tokens.css",
            "/administrator/assets/css/tokens.css",    // a sibling path that merely starts with the console path
            "/admin-old/assets/css/tokens.css",
            "/META-INF/resources/css/tokens.css",
            "/static/css/tokens.css",
            "/admin/assets/css/missing.css",           // a file that does not exist (public asset path)
            "/admin/assets/missing/tokens.css"})
    void assetsAreNotExposedAnywhereElse(String path) throws Exception {
        mvc.perform(get(URI.create(path))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/css/tokens.css",                   // under the console path but without /assets
            "/admin/tokens.css",
            "/admin/jpa-admin/assets/css/tokens.css",
            "/jpa-admin/css/tokens.css",
            "/admin/host-marker.txt"})
    void underTheConsolePathButOutsideTheAssetFolderNothingIsServedSignedOutOrSignedIn(String path) throws Exception {
        assertThat(mvc.perform(get(URI.create(path))).andReturn().getResponse().getStatus())
                .as("signed out: sent to sign-in or not found, never a file").isIn(302, 404);
        var signedIn = mvc.perform(get(URI.create(path)).session(signedIn().session())).andReturn().getResponse();
        if (path.startsWith("/admin/")) {
            assertThat(signedIn.getStatus()).as("signed in").isEqualTo(404);
        }
        assertThat(signedIn.getContentAsString()).doesNotContain("design tokens").doesNotContain("host-static-content");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/assets/../application.yml",
            "/admin/assets/css/../../application.yml",
            "/admin/assets/css/../../../application.properties",
            "/admin/assets/%2e%2e/application.yml",
            "/admin/assets/%2E%2E/application.yml",
            "/admin/assets/..%2fapplication.yml",
            "/admin/assets/%2e%2e%2fapplication.yml",
            "/admin/assets/css/..%5c..%5capplication.yml",
            "/admin/assets/css/%2e%2e/%2e%2e/application.yml",
            "/admin/assets/..;/application.yml"})
    void aTraversalOutOfTheAssetFolderNeverReturnsAFile(String path) throws Exception {
        // application.yml and application.properties sit at the classpath root, one level above jpa-admin/assets/.
        // If a traversal worked, the body would hold their text.
        var result = mvc.perform(get(URI.create(path))).andReturn().getResponse();

        assertThat(result.getStatus()).as(path).isBetween(400, 499);
        assertThat(result.getContentAsString()).doesNotContain("open-in-view").doesNotContain("jpa.admin");
    }

    @Test
    void theHostsOwnStaticContentIsStillServedAndIsNotReachableThroughTheConsoleHandler() throws Exception {
        // src/test/resources/static/host-marker.txt stands in for a host's own static file (Spring Boot's default
        // location). The console must neither hide it nor expose it under its own path.
        mvc.perform(get("/host-marker.txt")).andExpect(status().isOk())
                .andExpect(content().string("host-static-content\n"));
        mvc.perform(get("/admin/assets/host-marker.txt")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/host-marker.txt").session(signedIn().session())).andExpect(status().isNotFound());
    }

    // ---- links (D-29) ------------------------------------------------------------------------------------------

    @Test
    void everyLinkBuiltFromTheBasePathResolvesAndLiesUnderIt() throws Exception {
        MockBrowser browser = signedIn();
        for (String link : new String[] {basePath.value(), basePath.link(TOKENS), basePath.link("/theme.css"),
                basePath.link("/login")}) {
            assertThat(link).satisfiesAnyOf(
                    l -> assertThat(l).isEqualTo(basePath.value()),
                    l -> assertThat(l).startsWith(basePath.value() + "/"));
            assertThat(browser.get(link).getStatus()).as(link).isEqualTo(200);
        }
    }

    /** D-29 from Phase 1 on: every link a controller builds lies under the base path (here: in the rendered pages). */
    @Test
    void everyLinkInTheRenderedPagesLiesUnderTheBasePath() throws Exception {
        MockBrowser browser = signedIn();
        for (String html : new String[] {new MockBrowser(mvc).get("/admin/login").getContentAsString(),
                browser.get("/admin").getContentAsString(), browser.get("/admin/nope").getContentAsString(),
                browser.get("/admin/login?error").getContentAsString()}) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(?:href|src|action)=\"([^\"]*)\"").matcher(html);
            int found = 0;
            while (matcher.find()) {
                found++;
                String address = matcher.group(1);
                if (!address.equals("#main") && !address.equals("data:,")) {
                    assertThat(address).satisfiesAnyOf(a -> assertThat(a).isEqualTo("/admin"), a -> assertThat(a).startsWith("/admin/"));
                }
            }
            assertThat(found).isPositive();
        }
    }
}
