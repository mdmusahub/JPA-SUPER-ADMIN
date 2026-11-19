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
 * Phase 11 in a host that is not the demo: the CSV export of Book, Author and Publisher, with relation labels, the masked
 * {@code secretToken} absent from the file, and no library change needed for any of it. The host has no Bean Validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsoleExportsThisHostsEntitiesTest {

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

    private String csv(String path) throws Exception {
        MockHttpServletResponse response = send(MockMvcRequestBuilders.get(path));
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("text/csv");
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void booksAreExportedWithTheirRelationLabelsAndWithoutTheMaskedToken() throws Exception {
        signIn();

        String file = csv("/admin/book/export.csv");
        String[] lines = file.split("\r\n");

        assertThat(lines[0]).isEqualTo("ID,Author,Publisher,Title");
        assertThat(lines.length).isGreaterThan(1);
        assertThat(file).contains("Book 1", "Author 1", "Publisher 1");
        assertThat(file).doesNotContain("token-").doesNotContain("Secret token");
        assertThat(file).doesNotContain("not-a-real-secret");
    }

    @Test
    void authorsAndPublishersAreExportedToo() throws Exception {
        signIn();

        assertThat(csv("/admin/author/export.csv").split("\r\n")[0]).isEqualTo("ID,Name");
        assertThat(csv("/admin/publisher/export.csv")).contains("Publisher 1");
    }

    @Test
    void theSearchAndTheSortOfTheListAreTheFilesToo() throws Exception {
        signIn();

        MockHttpServletResponse response = send(MockMvcRequestBuilders.get("/admin/book/export.csv").param("q", "Book 1")
                .param("sort", "title").param("dir", "desc"));
        assertThat(response.getStatus()).isEqualTo(200);
        String[] lines = response.getContentAsString(StandardCharsets.UTF_8).split("\r\n");

        assertThat(lines.length).isGreaterThan(1);
        for (int i = 1; i < lines.length; i++) {
            assertThat(lines[i]).contains("Book 1");
        }
    }

    @Test
    void theListOffersTheButtonAndTheLinkCarriesTheSearch() throws Exception {
        signIn();

        String page = send(MockMvcRequestBuilders.get("/admin/book?q=Book&sort=title&dir=asc")).getContentAsString();

        assertThat(page).contains("href=\"/admin/book/export.csv?q=Book&amp;sort=title&amp;dir=asc\"");
    }

    @Test
    void withoutASessionTheExportIsRefused() throws Exception {
        MockHttpServletResponse response = mvc.perform(MockMvcRequestBuilders.get("/admin/book/export.csv")).andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getContentAsString()).doesNotContain("Book 1");
    }
}
