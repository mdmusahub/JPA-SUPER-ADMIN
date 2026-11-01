package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The preview serves the console's stylesheets and scripts the way the real console does: under {@code {base}/assets/**}
 * and nowhere else, so what the browser loads here is what it loads from the real console.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreviewAssetsTest {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/admin-preview/assets/css/tokens.css", "/admin-preview/assets/css/base.css",
            "/admin-preview/assets/css/components.css", "/admin-preview/assets/css/pages.css",
            "/admin-preview/assets/js/theme.js", "/admin-preview/assets/js/nav.js",
            "/preview-alt/assets/css/tokens.css", "/preview-alt/assets/js/theme.js"})
    void theAssetsAreServedUnderBothBasePaths(String path) throws Exception {
        MockHttpServletResponse response = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(response.getContentAsString()).isNotBlank();
    }

    @Test
    void themeCssSetsOnlyTheAccentVariableUnderBothBasePaths() throws Exception {
        for (String path : new String[] {"/admin-preview/theme.css", "/preview-alt/theme.css"}) {
            MockHttpServletResponse response = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse();

            assertThat(response.getContentType()).startsWith("text/css");
            assertThat(response.getContentAsString()).isEqualTo(":root { --ac: #0d9f6e; }\n");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/assets/css/tokens.css", "/jpa-admin/assets/css/tokens.css", "/css/tokens.css",
            "/admin/assets/css/tokens.css", "/admin-preview/css/tokens.css", "/admin-preview/assets/css/missing.css",
            "/preview-alt/css/tokens.css", "/theme.css"})
    void nothingIsServedOutsideTheAssetFolderOfABasePath(String path) throws Exception {
        mvc.perform(get(URI.create(path))).andExpect(status().isNotFound());
    }

    @Test
    void theTemplatesAreNeverServedAsFiles() throws Exception {
        mvc.perform(get("/admin-preview/assets/../templates/jpa-admin/login.html")).andExpect(status().is4xxClientError());
        mvc.perform(get("/admin-preview/assets/templates/jpa-admin/login.html")).andExpect(status().isNotFound());
    }
}
