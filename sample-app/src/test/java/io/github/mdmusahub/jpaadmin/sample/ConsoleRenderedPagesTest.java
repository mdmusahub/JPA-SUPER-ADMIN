package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UI-2, UI-4, UI-13 on the pages the real console renders (the preview module checks the fixtures with the same rules):
 * labelled fields, aria-label on icon-only buttons, a blocking theme.js in {@code <head>}, nothing inline, nothing
 * external, no session id in a link.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsoleRenderedPagesTest {

    @Autowired
    private MockMvc mvc;

    private List<String> pages() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        return List.of(new MockBrowser(mvc).get("/admin/login").getContentAsString(),
                new MockBrowser(mvc).get("/admin/login?error").getContentAsString(),
                new MockBrowser(mvc).get("/admin/login?logout").getContentAsString(),
                browser.get("/admin").getContentAsString(), browser.get("/admin/nope").getContentAsString(),
                browser.get("/admin/product").getContentAsString(), browser.get("/admin/customer").getContentAsString(),
                browser.get("/admin/product?q=zzzz").getContentAsString(),
                browser.get("/admin/product?size=10&page=1&sort=name&dir=desc").getContentAsString(),
                browser.get("/admin/error/forbidden").getContentAsString());
    }

    private static List<String> violations(String html) {
        List<String> problems = new ArrayList<>();
        if (!html.contains("<html lang=\"en\"")) {
            problems.add("no lang");
        }
        Matcher scripts = Pattern.compile("(?i)<script\\b([^>]*)>").matcher(html);
        int themeScripts = 0;
        while (scripts.find()) {
            if (!scripts.group(1).contains("src=")) {
                problems.add("inline script");
            }
            if (scripts.group(1).contains("/js/theme.js")) {
                themeScripts++;
                if (scripts.group(1).matches("(?is).*\\b(async|defer|module)\\b.*")) {
                    problems.add("theme.js is not blocking");
                }
                if (scripts.start() > html.indexOf("</head>")) {
                    problems.add("theme.js is not in head");
                }
                if (html.indexOf("rel=\"stylesheet\"") < scripts.start() && html.contains("rel=\"stylesheet\"")) {
                    problems.add("theme.js after a stylesheet");
                }
            }
        }
        if (themeScripts != 1) {
            problems.add("theme.js count " + themeScripts);
        }
        if (Pattern.compile("(?i)<style\\b|\\sstyle\\s*=|\\son[a-z]+\\s*=\\s*[\"']|https?://|jsessionid|th:|\\$\\{").matcher(html).find()) {
            problems.add("inline style/handler, external address, session id or template syntax");
        }
        Matcher buttons = Pattern.compile("(?is)<button\\b([^>]*)>(.*?)</button>").matcher(html);
        while (buttons.find()) {
            if (buttons.group(2).replaceAll("(?s)<[^>]*>", "").isBlank() && !buttons.group(1).contains("aria-label=\"")) {
                problems.add("icon-only button without aria-label");
            }
        }
        Matcher inputs = Pattern.compile("(?is)<input\\b([^>]*)>").matcher(html);
        while (inputs.find()) {
            Matcher id = Pattern.compile("\\bid=\"([^\"]+)\"").matcher(inputs.group(1));
            boolean labelled = id.find()
                    && Pattern.compile("(?is)<label\\b[^>]*\\sfor=\"" + Pattern.quote(id.group(1)) + "\"")
                            .matcher(html).find();
            if (!inputs.group(1).contains("type=\"hidden\"") && !labelled) {
                problems.add("input without label");
            }
        }
        return problems;
    }

    @Test
    void everyRealPageObeysTheUiRules() throws Exception {
        for (String html : pages()) {
            assertThat(violations(html)).as(html.substring(0, Math.min(html.length(), 0)) + "page").isEmpty();
        }
    }

    @Test
    void theSignInFormHasLabelsLinkedErrorsAndTheTokenAndWorksWithoutAnyScript() throws Exception {
        String ok = new MockBrowser(mvc).get("/admin/login").getContentAsString();
        String failed = new MockBrowser(mvc).get("/admin/login?error").getContentAsString();

        assertThat(ok).contains("<label for=\"username\">Username</label>", "<label for=\"password\">Password</label>",
                "name=\"_csrf\"", "method=\"post\"", "action=\"/admin/login\"").doesNotContain("aria-invalid");
        assertThat(failed).contains("id=\"sign-in-error\"", "aria-describedby=\"sign-in-error\"", "aria-invalid=\"true\"");
    }

    @Test
    void theCheckerCatchesTheThingsItIsMeantTo() {
        String good = "<html lang=\"en\"><head><script src=\"/admin/assets/js/theme.js\"></script></head><body></body></html>";

        assertThat(violations(good)).isEmpty();
        assertThat(violations(good.replace("theme.js\"", "theme.js\" defer"))).isNotEmpty();
        assertThat(violations(good.replace("<body>", "<body><script>x()</script>"))).isNotEmpty();
        assertThat(violations(good.replace("<body>", "<body><button><svg></svg></button>"))).isNotEmpty();
        assertThat(violations(good.replace("<body>", "<body><input id=\"a\">"))).isNotEmpty();
        assertThat(violations(good.replace("<body>", "<body><a href=\"https://x.example\">"))).isNotEmpty();
    }
}
