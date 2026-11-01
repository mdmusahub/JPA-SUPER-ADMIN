package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * With {@code jpa.admin.path=/backoffice} the console and its assets move there, and the old default path is gone
 * (UI_PLAN section 10: "with a custom jpa.admin.path, the old path returns 404 and the new one serves the asset").
 */
@SpringBootTest(properties = "jpa.admin.path=/backoffice")
@AutoConfigureMockMvc
class ConsoleAtCustomPathTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BasePath basePath;

    @Test
    void theConsoleSignsInAndRespondsAtTheCustomPath() throws Exception {
        mvc.perform(get("/backoffice")).andExpect(status().isFound())
                .andExpect(header().string("Location", "/backoffice/login"));
        MockBrowser browser = new MockBrowser(mvc);

        var signedIn = browser.signInToConsole("/backoffice", MockBrowser.USER, MockBrowser.PASSWORD);
        var dashboard = browser.get("/backoffice");

        assertThat(signedIn.getHeader("Location")).isEqualTo("/backoffice");
        assertThat(dashboard.getStatus()).isEqualTo(200);
        assertThat(dashboard.getContentAsString()).contains("action=\"/backoffice/logout\"", "/backoffice/assets/css/tokens.css",
                "/backoffice/theme.css").doesNotContain("/admin");
        String token = MockBrowser.csrf(dashboard.getContentAsString());
        assertThat(browser.post("/backoffice/logout", "_csrf", token).getHeader("Location")).isEqualTo("/backoffice/login?logout");
        assertThat(browser.get("/backoffice").getStatus()).isEqualTo(302);
    }

    @Test
    void theThemeAndAssetsArePublicAtTheCustomPathAndTheOldPathHasNoConsole() throws Exception {
        mvc.perform(get("/backoffice/theme.css")).andExpect(status().isOk());
        mvc.perform(get("/backoffice/login")).andExpect(status().isOk());
        mvc.perform(get("/admin/login")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/theme.css")).andExpect(status().isNotFound());
        // The old path is not ours any more, so no console security headers or chain apply there.
        assertThat(mvc.perform(get("/admin")).andReturn().getResponse().getHeader("Content-Security-Policy")).isNull();
    }

    @Test
    void theAssetsAreServedAtTheCustomPath() throws Exception {
        mvc.perform(get("/backoffice/assets/css/tokens.css")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin",                                  // the old default path is gone
            "/admin/assets/css/tokens.css",
            "/jpa-admin/assets/css/tokens.css",
            "/assets/css/tokens.css",
            "/css/tokens.css",
            "/backoffice/assets/css/missing.css",
            "/backoffice-old"})
    void everythingElseIsNotFound(String path) throws Exception {
        mvc.perform(get(URI.create(path))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/backoffice/css/tokens.css", "/backoffice/jpa-admin/assets/css/tokens.css", "/backoffice/x"})
    void underTheConsolePathOutsideTheAssetFolderItIsSignInOrNotFound(String path) throws Exception {
        mvc.perform(get(URI.create(path))).andExpect(status().isFound());
        mvc.perform(get(URI.create(path)).session(MockBrowser.signedIn(mvc, "/backoffice").session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void everyLinkBuiltFromTheBasePathResolvesAndLiesUnderIt() throws Exception {
        assertThat(basePath.value()).isEqualTo("/backoffice");
        MockBrowser browser = MockBrowser.signedIn(mvc, "/backoffice");
        for (String link : new String[] {basePath.value(), basePath.link("/assets/css/tokens.css")}) {
            assertThat(link).startsWith("/backoffice");
            assertThat(browser.get(link).getStatus()).isEqualTo(200);
        }
    }
}
