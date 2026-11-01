package io.github.mdmusahub.jpaadmin.sample;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A host that turns Spring Boot's default static mapping off ({@code spring.web.resources.add-mappings=false}) must
 * not lose the console's assets: they have their own handler (D-24).
 */
@SpringBootTest(properties = "spring.web.resources.add-mappings=false")
@AutoConfigureMockMvc
class ConsoleAssetsWithoutHostStaticTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theConsoleAssetsStillWorkWhenTheHostHasNoStaticMapping() throws Exception {
        mvc.perform(get("/admin/assets/css/tokens.css")).andExpect(status().isOk());
        mvc.perform(get("/host-marker.txt")).andExpect(status().isNotFound());
    }
}
