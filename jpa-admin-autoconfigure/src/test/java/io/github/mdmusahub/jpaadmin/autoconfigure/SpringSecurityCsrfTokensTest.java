package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

/** SEC-5: the console's pages get the request's own CSRF token, and a request without one gets a token that never validates. */
class SpringSecurityCsrfTokensTest {

    private final SpringSecurityCsrfTokens tokens = new SpringSecurityCsrfTokens();

    @Test
    void readsTheTokenThatSpringSecuritysCsrfFilterPutOnTheRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CsrfToken.class.getName(), new DefaultCsrfToken("X-XSRF-TOKEN", "_token_name", "abc123"));

        PageChrome.Csrf csrf = tokens.forRequest(request);

        assertThat(csrf).isEqualTo(new PageChrome.Csrf("_token_name", "X-XSRF-TOKEN", "abc123"));
    }

    @Test
    void aRequestWithoutATokenGetsAnEmptyOneSoAFormBuiltFromItFailsTheCheck() {
        PageChrome.Csrf csrf = tokens.forRequest(new MockHttpServletRequest());

        assertThat(csrf.token()).isEmpty();
        assertThat(csrf.parameterName()).isEqualTo("_csrf");
        assertThat(csrf.headerName()).isEqualTo("X-CSRF-TOKEN");
    }

    @Test
    void anAttributeOfTheWrongTypeIsIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CsrfToken.class.getName(), "not a token object");

        assertThat(tokens.forRequest(request).token()).isEmpty();
    }
}
