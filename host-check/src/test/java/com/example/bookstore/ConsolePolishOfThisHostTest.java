package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Phase 13 in a host that is not the demo (Book, Author, Publisher), with no annotation at all: the plural names are guessed, the
 * state the views script reads is in the page, "Show all columns" and "Clear search" behave, the keyboard hint and the skip link are
 * there, and the page size is the default. No library change was needed for any of it (ARCH-12).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsolePolishOfThisHostTest {

    @Autowired
    private MockMvc mvc;

    private final MockHttpSession session = new MockHttpSession();

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        String id = session.getId();
        return mvc.perform(request.session(session).with(each -> {
            each.setRequestedSessionId(id);
            return each;
        })).andReturn().getResponse();
    }

    private void signIn() throws Exception {
        String loginPage = send(MockMvcRequestBuilders.get("/admin/login")).getContentAsString();
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*?value=\"([^\"]+)\"").matcher(loginPage);
        assertThat(token.find()).isTrue();
        send(MockMvcRequestBuilders.post("/admin/login").param("username", "check-admin")
                .param("password", "check-only-password-NOT-REAL-1").param("_csrf", token.group(1)));
    }

    private String get(String path) throws Exception {
        MockHttpServletResponse response = send(MockMvcRequestBuilders.get(path));
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private static String attribute(String html, String name) {
        Matcher match = Pattern.compile("\\b" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(html);
        assertThat(match.find()).as(name).isTrue();
        return match.group(1).replace("&amp;", "&");
    }

    @Test
    void thePluralsAreGuessedForBookAuthorAndPublisherAndUsedWhereThePlanSaysSo() throws Exception {
        signIn();

        String dashboard = get("/admin");
        assertThat(dashboard).contains(">Books</a>", ">Authors</a>", ">Publishers</a>");
        String list = get("/admin/book");
        assertThat(list).contains("<h1 class=\"page-title\">Books</h1>").contains("placeholder=\"Search books\"");
    }

    @Test
    void theStateTheShowAllLinkAndTheClearSearchLinkWorkInThisHostToo() throws Exception {
        signIn();

        String hidden = get("/admin/book?q=Book&hide=secretToken&hide=title&sort=title&dir=asc&size=10");
        assertThat(attribute(hidden, "data-views-state")).isEqualTo("/admin/book?q=Book&hide=title&sort=title&dir=asc&size=10");
        assertThat(attribute(hidden, "data-views-default-size")).isEqualTo("25");
        assertThat(hidden).as("the masked secretToken can never be hidden").doesNotContain("hide=secretToken");
        Matcher showAll = Pattern.compile("href=\"([^\"]*)\">Show all columns</a>").matcher(hidden);
        assertThat(showAll.find()).isTrue();
        assertThat(showAll.group(1).replace("&amp;", "&")).isEqualTo("/admin/book?q=Book&sort=title&dir=asc&size=10");

        String searched = get("/admin/book?q=nothing-matches&hide=title");
        assertThat(searched).contains("href=\"/admin/book?hide=title\">Clear search</a>");
        assertThat(get("/admin/book")).doesNotContain("Show all columns");
    }

    @Test
    void theKeyboardHintTheSkipLinkAndTheShortcutScriptAreInThisHostsPages() throws Exception {
        signIn();

        assertThat(get("/admin/book")).contains("aria-keyshortcuts=\"/\"", "data-search-input",
                "<a class=\"skip-link\" href=\"#list-search\">Skip to search</a>", "/admin/assets/js/shortcuts.js");
        assertThat(get("/admin")).doesNotContain("Skip to search").contains("/admin/assets/js/shortcuts.js");
    }

    @Test
    void thePageSizeIsTheDefaultOfTwentyFiveWhenNothingIsSet() throws Exception {
        signIn();

        String html = get("/admin/book");

        assertThat(html).contains("href=\"/admin/book?size=10\"", "href=\"/admin/book?size=50\"", "href=\"/admin/book\"");
        assertThat(html).doesNotContain("size=25");
    }
}
