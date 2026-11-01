package io.github.mdmusahub.jpaadmin.sample;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SEC-4: the console never moves or shadows the host application's own routes. A host controller is not given the
 * console prefix (including one mapped at the site root), and the console still answers at its own path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(HostRoutesUntouchedTest.HostController.class)
class HostRoutesUntouchedTest {

    /** Stands in for the host application's own controller. Not in the console package, so never prefixed. */
    @RestController
    static class HostController {

        @GetMapping("/host/ping")
        String ping() {
            return "pong";
        }

        @GetMapping("/")
        String root() {
            return "host-root";
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void aHostRouteKeepsItsOwnPath() throws Exception {
        mvc.perform(get("/host/ping")).andExpect(status().isOk()).andExpect(content().string("pong"));
    }

    @Test
    void aHostRouteAtTheSiteRootIsNotShadowed() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string("host-root"));
    }

    @Test
    void aHostRouteDoesNotAppearUnderTheConsolePath() throws Exception {
        mvc.perform(get("/admin/host/ping")).andExpect(status().isNotFound());
    }

    @Test
    void theConsoleStillAnswersAtItsOwnPath() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isOk())
                .andExpect(content().string("JPA Admin console is running"));
    }
}
