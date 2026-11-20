package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The content security policy in a real browser, on the packaged application's own pages (Phase 13, SEC-13, SEC-15, UI-13): a page
 * the console served refuses an inline {@code <style>} block, an inline {@code style} attribute and an inline script, and
 * the browser reports each refusal. The driver never turns the policy off here ({@code Page.setBypassCSP} is a test-only switch of the
 * browser, used once by {@link ListLayoutBrowserTest} to force a font, and never by this test: with it on the three injections below
 * would succeed and this test would fail, mutation g).
 *
 * <p>It also reads the policy from a real response and checks that it has no {@code unsafe-inline} and no {@code unsafe-eval}, that
 * the console's colour reaches the page only through the generated stylesheet ({@code theme.css}), and that no page holds an inline
 * script, handler or style of its own.
 *
 * <p>When the machine has no Chrome or Edge the test is <b>skipped</b>, with the reason, and is never reported as passed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CspBrowserTest {

    @LocalServerPort
    private int port;

    @Test
    void theBrowserRefusesAnInlineStyleAnInlineStyleAttributeAndAnInlineScriptOnAConsolePage() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.go("/product");
            browser.js("window.__violations = []; document.addEventListener('securitypolicyviolation', function (e) { "
                    + "window.__violations.push(e.violatedDirective); }); 'ok'");
            String before = browser.js("getComputedStyle(document.body).backgroundColor");

            browser.js("(function () { var s = document.createElement('style'); s.textContent = 'body { background: rgb(255, 0, 0) !important }'; "
                    + "document.head.appendChild(s); return 'ok'; })()");
            browser.js("document.body.setAttribute('style', 'background: rgb(0, 255, 0)'); 'ok'");
            browser.js("(function () { var s = document.createElement('script'); s.textContent = 'window.__ran = true'; "
                    + "document.head.appendChild(s); return 'ok'; })()");

            assertThat(browser.js("getComputedStyle(document.body).backgroundColor")).as("neither injected style took effect")
                    .isEqualTo(before).doesNotContain("255, 0, 0").doesNotContain("0, 255, 0");
            assertThat(browser.js("String(window.__ran)")).as("the injected script did not run").isEqualTo("undefined");
            String violations = browser.js("window.__violations.join(',')");
            assertThat(violations).as("the browser reported each refusal").contains("style-src-elem", "style-src-attr", "script-src-elem");
        }
    }

    @Test
    void thePolicyOfARealResponseHasNoUnsafeKeywordsAndKeepsTheStyleAndScriptSourcesToTheConsoleItself() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.go("/product");
            String policy = browser.js("(function () { var x = new XMLHttpRequest(); x.open('GET', location.href, false); x.send(); "
                    + "return x.getResponseHeader('Content-Security-Policy') || 'none'; })()");

            assertThat(policy).contains("style-src 'self'", "script-src 'self'", "default-src 'none'", "frame-ancestors 'none'",
                    "form-action 'self'", "base-uri 'none'");
            assertThat(policy).doesNotContain("unsafe-inline").doesNotContain("unsafe-eval").doesNotContain("*").doesNotContain("http:")
                    .doesNotContain("https:");
        }
    }

    @Test
    void noConsolePageHoldsAnInlineScriptHandlerOrStyleAndTheColourComesFromTheGeneratedStylesheet() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            for (String path : new String[] {"", "/product", "/product/1", "/product/1/edit", "/product/new", "/category/1/edit",
                    "/nothing-here"}) {
                browser.go(path);
                assertThat(browser.js("String(document.querySelectorAll('script:not([src])').length)")).as(path + ": inline scripts")
                        .isEqualTo("0");
                assertThat(browser.js("String(document.querySelectorAll('style').length)")).as(path + ": style blocks").isEqualTo("0");
                assertThat(browser.js("String(document.querySelectorAll('[style]').length)")).as(path + ": style attributes")
                        .isEqualTo("0");
                assertThat(browser.js("String(Array.prototype.filter.call(document.querySelectorAll('*'), function (e) { "
                        + "return Array.prototype.some.call(e.attributes, function (a) { return /^on/i.test(a.name); }); }).length)"))
                        .as(path + ": inline handlers").isEqualTo("0");
                assertThat(browser.js("String(document.querySelectorAll('link[rel=stylesheet][href$=\"/theme.css\"]').length)"))
                        .as(path + ": the accent colour stylesheet").isEqualTo("1");
            }
        }
    }
}
