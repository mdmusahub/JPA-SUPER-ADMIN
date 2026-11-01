package io.github.mdmusahub.jpaadmin.sample;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** A console path with more than one segment works the same way, and its parent path is not the console. */
@SpringBootTest(properties = "jpa.admin.path=/tools/admin")
@AutoConfigureMockMvc
class ConsoleAtNestedPathTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theConsoleAndItsAssetsAreServedAtTheNestedPath() throws Exception {
        mvc.perform(get("/tools/admin")).andExpect(status().isFound())
                .andExpect(header().string("Location", "/tools/admin/login"));
        mvc.perform(get("/tools/admin/assets/css/tokens.css")).andExpect(status().isOk());
        MockBrowser browser = MockBrowser.signedIn(mvc, "/tools/admin");
        org.assertj.core.api.Assertions.assertThat(browser.get("/tools/admin").getContentAsString())
                .contains("Welcome to <span>JPA Admin</span>", "action=\"/tools/admin/logout\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/tools", "/admin", "/admin/assets/css/tokens.css", "/tools/assets/css/tokens.css",
            "/assets/css/tokens.css", "/jpa-admin/assets/css/tokens.css"})
    void theParentAndEveryOtherPathAreNotFound(String path) throws Exception {
        mvc.perform(get(URI.create(path))).andExpect(status().isNotFound());
    }
}
