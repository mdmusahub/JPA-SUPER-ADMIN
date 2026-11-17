package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

/**
 * A redirect after a POST goes only to a link the console built, under the base path, and arrives as a 303 with the
 * servlet context path in front. Anything else is a bug, so it is an exception and never a redirect elsewhere.
 */
class ConsoleRedirectTest {

    private static final BasePath BASE = BasePath.of("/admin");

    private static MockHttpServletResponse render(ModelAndView redirect, String contextPath) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath(contextPath);
        MockHttpServletResponse response = new MockHttpServletResponse();
        View view = redirect.getView();
        view.render(Map.of(), request, response);
        return response;
    }

    @Test
    void aLinkUnderTheBasePathIsRedirectedToWithA303AndTheContextPathInFront() throws Exception {
        MockHttpServletResponse response = render(ConsoleRedirect.to(BASE, "/admin/parcel?q=a%20b&page=2"), "/shop");

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getRedirectedUrl()).isEqualTo("/shop/admin/parcel?q=a%20b&page=2");
    }

    @Test
    void withNoContextPathTheAddressIsTheLinkItself() throws Exception {
        MockHttpServletResponse response = render(ConsoleRedirect.to(BASE, "/admin"), "");

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin");
    }

    @Test
    void aPlaceholderInTheLinkIsNotExpandedBecauseTheLinkWasEncodedAlready() throws Exception {
        MockHttpServletResponse response = render(ConsoleRedirect.to(BASE, "/admin/parcel?q=%7Bid%7D"), "");

        assertThat(response.getRedirectedUrl()).isEqualTo("/admin/parcel?q=%7Bid%7D");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/", "http://evil.example/admin", "//evil.example/admin", "/other/place",
            "/administrator", "/admin/x y", "javascript:alert(1)", "\\evil", "/admin\\x", "admin/parcel", "", "/",
            "/admin//evil", "/admin/x\ty", "/admin/x\ny"})
    void aLinkThatBreaksTheRuleOrLiesOutsideTheBasePathIsRefused(String href) {
        assertThatThrownBy(() -> ConsoleRedirect.to(BASE, href)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("evil");
    }

    @Test
    void noLinkAtAllIsRefused() {
        assertThatThrownBy(() -> ConsoleRedirect.to(BASE, null)).isInstanceOf(NullPointerException.class);
    }
}
