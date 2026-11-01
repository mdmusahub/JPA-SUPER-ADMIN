package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    void theConsoleRespondsAtTheCustomPath() throws Exception {
        mvc.perform(get("/backoffice")).andExpect(status().isOk())
                .andExpect(content().string("JPA Admin console is running"));
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
            "/backoffice/css/tokens.css",
            "/backoffice/jpa-admin/assets/css/tokens.css",
            "/backoffice/assets/css/missing.css",
            "/backoffice-old",
            "/backoffice/x"})
    void everythingElseIsNotFound(String path) throws Exception {
        mvc.perform(get(URI.create(path))).andExpect(status().isNotFound());
    }

    @Test
    void everyLinkBuiltFromTheBasePathResolvesAndLiesUnderIt() throws Exception {
        assertThat(basePath.value()).isEqualTo("/backoffice");
        for (String link : new String[] {basePath.value(), basePath.link("/assets/css/tokens.css")}) {
            assertThat(link).startsWith("/backoffice");
            mvc.perform(get(URI.create(link))).andExpect(status().isOk());
        }
    }
}
