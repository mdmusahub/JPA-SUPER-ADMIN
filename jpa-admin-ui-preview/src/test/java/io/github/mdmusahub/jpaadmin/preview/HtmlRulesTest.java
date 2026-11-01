package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the rule checker itself with deliberately bad input. Without these, a pattern that silently stopped matching would
 * make {@code TemplateRulesTest} and the rendered-page tests pass for the wrong reason (TEST-2).
 */
class HtmlRulesTest {

    // ---- templates: bad input is caught ---------------------------------------------------------------------------

    @Test
    void catchesUnescapedOutput() {
        assertThat(HtmlRules.templateViolations("<p th:utext=\"${view.hint}\"></p>")).anyMatch(p -> p.contains("th:utext"));
        assertThat(HtmlRules.templateViolations("<p data-th-utext=\"${view.hint}\"></p>")).anyMatch(p -> p.contains("data-th-utext"));
        assertThat(HtmlRules.templateViolations("<p>[(${view.hint})]</p>")).anyMatch(p -> p.contains("[(...)]"));
    }

    @Test
    void catchesAnInlineScriptButNotAScriptWithASource() {
        assertThat(HtmlRules.templateViolations("<script>alert(1)</script>")).anyMatch(p -> p.contains("inline <script>"));
        assertThat(HtmlRules.templateViolations("<script type=\"text/javascript\">x()</script>")).isNotEmpty();
        assertThat(HtmlRules.templateViolations("<script th:src=\"@{${chrome.basePath} + '/assets/js/theme.js'}\"></script>"))
                .isEmpty();
    }

    @Test
    void catchesInlineStyles() {
        assertThat(HtmlRules.templateViolations("<style>body{}</style>")).anyMatch(p -> p.contains("<style>"));
        assertThat(HtmlRules.templateViolations("<p style=\"color:red\">x</p>")).anyMatch(p -> p.contains("style attribute"));
        assertThat(HtmlRules.templateViolations("<p th:style=\"'color:' + ${view.hint}\">x</p>")).isNotEmpty();
        assertThat(HtmlRules.templateViolations("<p th:attr=\"style=${view.hint}\">x</p>")).isNotEmpty();
    }

    @Test
    void catchesInlineEventHandlers() {
        assertThat(HtmlRules.templateViolations("<button onclick=\"go()\">x</button>")).anyMatch(p -> p.contains("event handler"));
        assertThat(HtmlRules.templateViolations("<button th:onclick=\"|go('${view.hint}')|\">x</button>")).isNotEmpty();
        assertThat(HtmlRules.templateViolations("<img onerror='x()'>")).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<link href=\"https://cdn.example.com/x.css\">", "<script src=\"http://example.com/x.js\"></script>",
            "<img src=\"//example.com/x.png\">", "<a href=\"https://example.com\">x</a>"})
    void catchesExternalAddresses(String template) {
        assertThat(HtmlRules.templateViolations(template)).anyMatch(p -> p.contains("external address"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"${user.name}", "${@accountService.find()}", "${#request.getParameter('x')}",
            "${#session.id}", "${T(java.lang.Runtime).getRuntime()}", "${#ctx.variables}", "${new java.util.Date()}",
            "${param.error}", "${#servletContext.contextPath}"})
    void catchesAnExpressionThatReachesOutsideTheModel(String expression) {
        assertThat(HtmlRules.templateViolations("<p th:text=\"" + expression + "\"></p>")).isNotEmpty();
    }

    // ---- templates: good input passes ---------------------------------------------------------------------------

    @Test
    void acceptsTheModelRootsAndLocalsThatTheTemplateDeclares() {
        assertThat(HtmlRules.templateViolations("<p th:text=\"${chrome.title}\"></p><p th:text=\"${view.hint}\"></p>")).isEmpty();
        assertThat(HtmlRules.templateViolations("<li th:each=\"crumb, state : ${chrome.breadcrumbs}\" th:text=\"${crumb.label + state.index}\"></li>"))
                .isEmpty();
        assertThat(HtmlRules.templateViolations("<div th:with=\"label=${view.title}\" th:text=\"${label}\"></div>")).isEmpty();
        assertThat(HtmlRules.templateViolations("<div th:fragment=\"box(title, open)\" th:text=\"${title}\" th:if=\"${open}\"></div>"))
                .isEmpty();
        assertThat(HtmlRules.templateViolations("<p th:text=\"${#strings.toLowerCase(chrome.theme) == 'dark' and not false}\"></p>"))
                .isEmpty();
    }

    @Test
    void doesNotMistakeAStringLiteralForAVariable() {
        assertThat(HtmlRules.templateViolations("<p th:text=\"${chrome.title + ' | the user and request'}\"></p>")).isEmpty();
    }

    @Test
    void ignoresParserLevelCommentsBecauseTheyNeverReachTheBrowser() {
        String template = "<!--/* th:utext and <script>x()</script> and https://example.com are only words here */--><p></p>";

        assertThat(HtmlRules.templateViolations(template)).isEmpty();
    }

    // ---- rendered pages -------------------------------------------------------------------------------------------

    private static final String GOOD_PAGE = "<!DOCTYPE html><html lang=\"en\"><head>"
            + "<script src=\"/admin/assets/js/theme.js\"></script>"
            + "<link rel=\"stylesheet\" href=\"/admin/assets/css/tokens.css\"></head><body>"
            + "<button type=\"button\" aria-label=\"Open navigation\"><svg></svg></button>"
            + "<button type=\"submit\">Sign in</button>"
            + "<label for=\"u\">Username</label><input id=\"u\" name=\"username\">"
            + "<input type=\"hidden\" name=\"_csrf\" value=\"t\">"
            + "<script src=\"/admin/assets/js/nav.js\" defer></script></body></html>";

    @Test
    void acceptsAWellFormedPage() {
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE)).isEmpty();
    }

    @Test
    void catchesThemeScriptProblems() {
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE.replace("theme.js\">", "theme.js\" defer>")))
                .anyMatch(p -> p.contains("not a blocking script"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE.replace("theme.js\">", "theme.js\" async>")))
                .anyMatch(p -> p.contains("not a blocking script"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE.replace("<script src=\"/admin/assets/js/theme.js\"></script>", "")))
                .anyMatch(p -> p.contains("no theme.js"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE.replace("</head><body>",
                "</head><body><script src=\"/admin/assets/js/theme.js\"></script>").replace(
                "<head><script src=\"/admin/assets/js/theme.js\"></script>", "<head>")))
                .anyMatch(p -> p.contains("not inside <head>"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE.replace("<head>", "<head><link rel=\"stylesheet\" href=\"/x.css\">")))
                .anyMatch(p -> p.contains("after a stylesheet"));
    }

    @Test
    void catchesAnIconButtonWithoutALabel() {
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE.replace(" aria-label=\"Open navigation\"", "")))
                .anyMatch(p -> p.contains("icon-only button has no aria-label"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE.replace(" aria-label=\"Open navigation\"", " aria-label=\"\"")))
                .anyMatch(p -> p.contains("icon-only button has no aria-label"));
    }

    @Test
    void catchesAnInputWithoutALabel() {
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE.replace("<label for=\"u\">Username</label>", "")))
                .anyMatch(p -> p.contains("input has no label"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE.replace("for=\"u\"", "for=\"other\"")))
                .anyMatch(p -> p.contains("input has no label"));
    }

    @Test
    void catchesInlineAndExternalThingsInARenderedPage() {
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE + "<script>x()</script>")).anyMatch(p -> p.contains("inline <script>"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE + "<style>a{}</style>")).anyMatch(p -> p.contains("<style>"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE + "<p style=\"x\">")).anyMatch(p -> p.contains("style attribute"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE + "<a onclick=\"x()\">")).anyMatch(p -> p.contains("event handler"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE + "<a href=\"https://example.com\">")).anyMatch(p -> p.contains("external address"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE + "<a href=\"/x;jsessionid=1\">")).anyMatch(p -> p.contains("session id"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE + "<p th:text=\"x\">")).anyMatch(p -> p.contains("unprocessed template syntax"));
        assertThat(HtmlRules.renderedViolations(GOOD_PAGE.replace(" lang=\"en\"", ""))).anyMatch(p -> p.contains("lang"));
    }
}
