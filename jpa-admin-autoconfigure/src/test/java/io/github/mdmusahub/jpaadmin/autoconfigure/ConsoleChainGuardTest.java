package io.github.mdmusahub.jpaadmin.autoconfigure;

import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.mvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The console fails closed: a request that did not pass through its security chain is refused, whatever the reason
 * (SEC-4). The reasons tested here are the realistic ones: the host told Spring Security to ignore the console path, and
 * the chain simply not being run.
 */
@ExtendWith(OutputCaptureExtension.class)
class ConsoleChainGuardTest {

    private final ConsoleChainGuard guard = new ConsoleChainGuard(BasePath.of("/admin"));

    // ---- the marker and the guard -------------------------------------------------------------------------------------

    @Test
    void theMarkerNotesThatTheRequestEnteredTheChainAndPassesItOn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        MockFilterChain chain = new MockFilterChain();

        new ConsoleChainGuard.Marker().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getAttribute(ConsoleChainGuard.ENTERED_CHAIN)).isEqualTo(Boolean.TRUE);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void aRequestThatEnteredTheChainIsLetThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        request.setAttribute(ConsoleChainGuard.ENTERED_CHAIN, Boolean.TRUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(guard.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void aRequestThatDidNotEnterTheChainIsRefusedWith500AndOneErrorInTheLog(CapturedOutput output) throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();

        assertThat(guard.preHandle(new MockHttpServletRequest("GET", "/admin"), first, new Object())).isFalse();
        assertThat(guard.preHandle(new MockHttpServletRequest("GET", "/admin/x"), second, new Object())).isFalse();

        assertThat(first.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(second.getStatus()).isEqualTo(500);
        assertThat(output.getAll()).containsOnlyOnce("did not pass through the console's security chain")
                .contains("spring.mvc.servlet.path");
    }

    // ---- inside a real host -------------------------------------------------------------------------------------------

    @RestController
    static class HostRoute {

        @GetMapping("/hello")
        String hello() {
            return "hello from the host";
        }
    }

    /** A host that tells Spring Security to ignore every request: no chain, so no filters, would run for the console. */
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @Import(HostRoute.class)
    static class HostThatIgnoresEverything {

        @Bean
        WebSecurityCustomizer ignoreAll() {
            return web -> web.ignoring().requestMatchers(request -> true);
        }
    }

    @Test
    void aHostThatTellsSpringSecurityToIgnoreTheConsolePathCannotExposeTheConsole(CapturedOutput output) {
        ConsoleTestHosts.withBootSecurity().withUserConfiguration(HostThatIgnoresEverything.class).run(context -> {
            MockMvc mvc = mvc(context);

            MockHttpServletResponse login = mvc.perform(get("/admin/login")).andReturn().getResponse();
            MockHttpServletResponse dashboard = mvc.perform(get("/admin")).andReturn().getResponse();
            MockHttpServletResponse asset = mvc.perform(get("/admin/assets/css/tokens.css")).andReturn().getResponse();
            MockHttpServletResponse theme = mvc.perform(get("/admin/theme.css")).andReturn().getResponse();

            for (MockHttpServletResponse response : List.of(login, dashboard, asset, theme)) {
                assertThat(response.getStatus()).isEqualTo(500);
                assertThat(response.getContentAsString()).doesNotContain("Welcome to").doesNotContain("--ac:");
            }
            // The guard watches the console path and everything below it, and nothing else.
            assertThat(mvc.perform(get("/admin/some/deeper/page")).andReturn().getResponse().getStatus()).isEqualTo(500);
            MockHttpServletResponse hostRoute = mvc.perform(get("/hello")).andReturn().getResponse();
            assertThat(hostRoute.getStatus()).isEqualTo(200);
            assertThat(hostRoute.getContentAsString()).isEqualTo("hello from the host");
        });
        assertThat(output.getAll()).contains("did not pass through the console's security chain");
    }

    @Test
    void whenTheChainRunsTheConsoleAnswersAsUsual() {
        ConsoleTestHosts.withoutSpringSecurity().run(context -> {
            MockMvc mvc = mvc(context);

            assertThat(mvc.perform(get("/admin/login")).andReturn().getResponse().getStatus()).isEqualTo(200);
            assertThat(mvc.perform(get("/admin")).andReturn().getResponse().getStatus()).isEqualTo(302);
        });
    }
}
