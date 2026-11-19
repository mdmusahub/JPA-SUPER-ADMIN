package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
 * Phase 12 in a host that is not the demo: hiding columns of Book, Author and Publisher with the {@code hide} parameter, the
 * masked {@code secretToken} that can never be hidden or revealed, the export that follows the list, and the saved views panel's
 * data. No library change was needed for any of it (ARCH-12).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsoleHidesColumnsOfThisHostTest {

    private static final Pattern HEADER = Pattern.compile("(?s)<th[^>]*scope=\"col\"[^>]*>(.*?)</th>");

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

    private static List<String> headers(String html) {
        String table = html.substring(html.indexOf("<table"));
        List<String> found = new ArrayList<>();
        Matcher th = HEADER.matcher(table.substring(0, table.indexOf("</thead>")));
        while (th.find()) {
            found.add(th.group(1).replaceAll("(?s)<[^>]*>", "").replaceAll("\\s+", " ").strip());
        }
        return found;
    }

    @Test
    void aColumnOfBookIsHiddenFromTheListAndTheExport() throws Exception {
        signIn();

        assertThat(headers(get("/admin/book"))).contains("Title", "Author", "Publisher");
        assertThat(headers(get("/admin/book?hide=author"))).doesNotContain("Author").contains("ID", "Title", "Publisher");
        String file = get("/admin/book/export.csv?hide=author&hide=publisher");

        assertThat(file.split("\r\n")[0]).isEqualTo("ID,Title");
        assertThat(file).doesNotContain("Author 1", "Publisher 1").contains("Book 1");
    }

    @Test
    void theMaskedTokenCanNeverBeHiddenAndNothingRevealsIt() throws Exception {
        signIn();

        String plain = get("/admin/book");
        String asked = get("/admin/book?hide=secretToken");

        assertThat(headers(asked)).isEqualTo(headers(plain));
        assertThat(asked).doesNotContain("token-").doesNotContain("not-a-real-secret").doesNotContain("value=\"secretToken\"");
        assertThat(get("/admin/book/export.csv?hide=secretToken")).isEqualTo(get("/admin/book/export.csv"));
    }

    @Test
    void aRequestThatHidesEveryColumnIsIgnoredAndSaysSo() throws Exception {
        signIn();

        String html = get("/admin/book?hide=id&hide=title&hide=author&hide=publisher");

        assertThat(html).contains("Every column was hidden, so all columns are shown.");
        assertThat(headers(html)).contains("ID", "Title", "Author", "Publisher");
    }

    @Test
    void theOtherEntitiesOfTheHostHideTheirColumnsToo() throws Exception {
        signIn();

        assertThat(headers(get("/admin/author"))).contains("Name");
        assertThat(headers(get("/admin/author?hide=id"))).doesNotContain("ID").contains("Name");
        assertThat(get("/admin/publisher/export.csv?hide=id").split("\r\n")[0]).doesNotContain("ID");
    }

    @Test
    void theSavedViewsPanelIsHandedThisHostsKeysAndNeverTheMaskedOne() throws Exception {
        signIn();

        String html = get("/admin/book?hide=title");

        Matcher columns = Pattern.compile("data-views-columns=\"([^\"]*)\"").matcher(html);
        assertThat(columns.find()).isTrue();
        assertThat(List.of(columns.group(1).split(" "))).containsExactly("id", "author", "publisher", "title");
        assertThat(html).contains("data-views-base=\"/admin/book\"").contains("data-views-state=\"/admin/book?hide=title\"");
    }
}
