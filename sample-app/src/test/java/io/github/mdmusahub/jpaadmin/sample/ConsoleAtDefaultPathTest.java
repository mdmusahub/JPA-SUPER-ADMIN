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
    void theConsolePathRespondsWithThePlaceholder() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(content().string("JPA Admin console is running"));
    }

    @Test
    void theConsolePathAnswersHead() throws Exception {
        mvc.perform(head("/admin")).andExpect(status().isOk());
    }

    @Test
    void theConsolePathIsReadOnly() throws Exception {
        mvc.perform(post("/admin")).andExpect(status().isMethodNotAllowed());
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
            "/admin/css/tokens.css",                   // under the console path but without /assets
            "/admin/tokens.css",
            "/admin/jpa-admin/assets/css/tokens.css",
            "/admin/assets/css/missing.css",           // a file that does not exist
            "/admin/assets/missing/tokens.css",
            "/jpa-admin/css/tokens.css",
            "/administrator/assets/css/tokens.css",    // a sibling path that merely starts with the console path
            "/admin-old/assets/css/tokens.css",
            "/META-INF/resources/css/tokens.css",
            "/static/css/tokens.css"})
    void assetsAreNotExposedAnywhereElse(String path) throws Exception {
        mvc.perform(get(URI.create(path))).andExpect(status().isNotFound());
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
        mvc.perform(get("/admin/host-marker.txt")).andExpect(status().isNotFound());
    }

    // ---- links (D-29) ------------------------------------------------------------------------------------------

    @Test
    void everyLinkBuiltFromTheBasePathResolvesAndLiesUnderIt() throws Exception {
        for (String link : new String[] {basePath.value(), basePath.link(TOKENS)}) {
            assertThat(link).satisfiesAnyOf(
                    l -> assertThat(l).isEqualTo(basePath.value()),
                    l -> assertThat(l).startsWith(basePath.value() + "/"));
            mvc.perform(get(URI.create(link))).andExpect(status().isOk());
        }
    }
}
