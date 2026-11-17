package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

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
 * The whole point of {@code host-check}: a host that adds only the starter and two properties, with its own
 * entities, gets a console that lists exactly those entities with the right counts and nothing of the demo shop.
 *
 * <p>If the library had hard-coded the demo entities, or assumed anything about the host's model, this test would
 * fail. It signs in as the console admin and reads the dashboard, exactly as the brief asks.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsoleListsThisHostsEntitiesTest {

    @Autowired
    private MockMvc mvc;

    private MockHttpSession session = new MockHttpSession();

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        if (session.isInvalid()) {
            session = new MockHttpSession();
        }
        String id = session.getId();
        return mvc.perform(request.session(session).with(each -> {
            each.setRequestedSessionId(id);
            return each;
        })).andReturn().getResponse();
    }

    private String signInAndOpenDashboard() throws Exception {
        String loginPage = send(MockMvcRequestBuilders.get("/admin/login")).getContentAsString();
        Matcher token = Pattern.compile("name=\"_csrf\"[^>]*?value=\"([^\"]+)\"").matcher(loginPage);
        assertThat(token.find()).isTrue();
        send(MockMvcRequestBuilders.post("/admin/login").param("username", "check-admin")
                .param("password", "check-only-password-NOT-REAL-1").param("_csrf", token.group(1)));
        MockHttpServletResponse dashboard = send(MockMvcRequestBuilders.get("/admin"));
        assertThat(dashboard.getStatus()).isEqualTo(200);
        return dashboard.getContentAsString();
    }

    private static List<String> sidebarEntities(String html) {
        Matcher items = Pattern.compile("<li data-nav-item=\"([^\"]+)\">").matcher(html);
        List<String> labels = new ArrayList<>();
        while (items.find()) {
            labels.add(items.group(1));
        }
        return labels;
    }

    @Test
    void theSidebarListsExactlyBookAuthorAndPublisherWithThisHostsCounts() throws Exception {
        String html = signInAndOpenDashboard();

        assertThat(sidebarEntities(html)).containsExactlyInAnyOrder("Book", "Author", "Publisher");
        assertThat(html).contains("href=\"/admin/book\"", "<span class=\"nav-count\">" + BookstoreData.BOOKS + "</span>")
                .contains("href=\"/admin/author\"", "<span class=\"nav-count\">" + BookstoreData.AUTHORS + "</span>")
                .contains("href=\"/admin/publisher\"", "<span class=\"nav-count\">" + BookstoreData.PUBLISHERS + "</span>");
    }

    @Test
    void nothingOfTheDemoShopIsAnywhereInTheConsole() throws Exception {
        String html = signInAndOpenDashboard();

        for (String demo : List.of("Category", "Product", "Customer", "Order", "Tag", "Widget", "Gadget")) {
            assertThat(html).as(demo).doesNotContain(demo);
        }
    }

    @Test
    void theDashboardAddsUpThisHostsRows() throws Exception {
        String html = signInAndOpenDashboard();
        int total = BookstoreData.BOOKS + BookstoreData.AUTHORS + BookstoreData.PUBLISHERS;

        assertThat(html).contains("<p class=\"stat-value\">" + total + "</p>", "Across 3 entities");
    }

    /** The text of every cell of every row of the table, tags removed. */
    private static List<List<String>> table(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher body = Pattern.compile("(?s)<tbody>(.*?)</tbody>").matcher(html);
        if (!body.find()) {
            return rows;
        }
        Matcher row = Pattern.compile("(?s)<tr>(.*?)</tr>").matcher(body.group(1));
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = Pattern.compile("(?s)<td\\b(?![^>]*select-col)[^>]*>(.*?)</td>").matcher(row.group(1));
            while (cell.find()) {
                cells.add(cell.group(1).replaceAll("(?s)<[^>]*>", "").strip());
            }
            rows.add(cells);
        }
        return rows;
    }

    private String page(String path) throws Exception {
        MockHttpServletResponse page = send(MockMvcRequestBuilders.get(path));
        assertThat(page.getStatus()).as(path).isEqualTo(200);
        return page.getContentAsString();
    }

    @Test
    void eachEntityListsItsRowsAndTheSecretTokenValueNeverAppears() throws Exception {
        signInAndOpenDashboard();

        assertThat(table(page("/admin/book"))).hasSize(BookstoreData.BOOKS);
        assertThat(table(page("/admin/author"))).hasSize(BookstoreData.AUTHORS);
        assertThat(table(page("/admin/publisher"))).hasSize(BookstoreData.PUBLISHERS);
        for (String entity : List.of("book", "author", "publisher")) {
            assertThat(page("/admin/" + entity)).as(entity).doesNotContain("not-a-real-secret");
        }
    }

    @Test
    void theBookListShowsTheTitlesTheRelationLabelsAndAMaskedTokenColumn() throws Exception {
        signInAndOpenDashboard();
        String html = page("/admin/book");

        // Columns by name: ID, Author, Publisher, Secret token, Title.
        assertThat(html).contains("<h1 class=\"page-title\">Books</h1>", "5 records");
        List<List<String>> rows = table(html);
        assertThat(rows.stream().map(row -> row.get(4))).containsExactly("Book 1", "Book 2", "Book 3", "Book 4", "Book 5");
        assertThat(rows.stream().map(row -> row.get(1))).containsExactly("Author 1", "Author 2", "Author 3", "Author 1", "Author 2");
        assertThat(rows.stream().map(row -> row.get(2))).containsExactly("Publisher 1", "Publisher 2", "Publisher 1", "Publisher 2", "Publisher 1");
        assertThat(rows).allSatisfy(row -> assertThat(row.get(3)).isEqualTo("••••••••Hidden value"));
        assertThat(html).contains("Secret token").doesNotContain("token-0", "not-a-real-secret");
    }

    @Test
    void searchSortAndPagingWorkOnThisHostsEntitiesToo() throws Exception {
        signInAndOpenDashboard();

        assertThat(table(page("/admin/book?q=BOOK 3")).stream().map(row -> row.get(4))).containsExactly("Book 3");
        assertThat(table(page("/admin/book?sort=title&dir=desc")).stream().map(row -> row.get(4)))
                .containsExactly("Book 5", "Book 4", "Book 3", "Book 2", "Book 1");
        assertThat(table(page("/admin/book?size=10&page=7"))).hasSize(BookstoreData.BOOKS);
        assertThat(page("/admin/book?q=not-a-real-secret")).contains("No matching records");
        assertThat(page("/admin/book?sort=secretToken&dir=asc")).doesNotContain("aria-sort=\"ascending\"");
    }

    @Test
    void aRelationIsSortedByTheRelatedRecordsLabelOnThisHostsEntitiesToo() throws Exception {
        signInAndOpenDashboard();
        // Books: 1 (Author 1, Publisher 1), 2 (A2, P2), 3 (A3, P1), 4 (A1, P2), 5 (A2, P1). Column 4 is the title.
        assertThat(table(page("/admin/book?sort=author&dir=asc")).stream().map(row -> row.get(4)))
                .containsExactly("Book 1", "Book 4", "Book 2", "Book 5", "Book 3");
        assertThat(table(page("/admin/book?sort=author&dir=desc")).stream().map(row -> row.get(4)))
                .containsExactly("Book 3", "Book 2", "Book 5", "Book 1", "Book 4");
        assertThat(table(page("/admin/book?sort=publisher&dir=asc")).stream().map(row -> row.get(4)))
                .containsExactly("Book 1", "Book 3", "Book 5", "Book 2", "Book 4");
        String sorted = page("/admin/book?sort=author&dir=asc");
        assertThat(sorted).contains("aria-sort=\"ascending\"", "href=\"/admin/book?sort=author&amp;dir=desc\"");
        assertThat(sorted.split("aria-sort=\"ascending\"", -1).length - 1).isEqualTo(1);
        // The masked column still cannot be sorted.
        assertThat(page("/admin/book?sort=secretToken&dir=asc")).doesNotContain("sort=secretToken");
    }

    /** The value of the option labelled {@code label} in the select with this id, as the page prints it. */
    private static String choice(String html, String selectId, String label) {
        int from = html.indexOf("id=\"" + selectId + "\"");
        assertThat(from).as(selectId).isGreaterThanOrEqualTo(0);
        Matcher option = Pattern.compile("<option value=\"([^\"]+)\"[^>]*>" + Pattern.quote(label) + "</option>").matcher(html.substring(from, html.indexOf("</select>", from)));
        assertThat(option.find()).as(label).isTrue();
        return option.group(1);
    }

    @Test
    void filtersWorkOnThisHostsEntitiesTooAndTheSecretTokenCanNotBeFiltered() throws Exception {
        signInAndOpenDashboard();
        String all = page("/admin/book");
        String authorTwo = choice(all, "filter-author", "Author 2");
        String publisherOne = choice(all, "filter-publisher", "Publisher 1");

        // Books: 1 (Author 1, Publisher 1), 2 (A2, P2), 3 (A3, P1), 4 (A1, P2), 5 (A2, P1). Column 4 is the title.
        assertThat(table(page("/admin/book?filter.author=" + authorTwo)).stream().map(row -> row.get(4))).containsExactly("Book 2", "Book 5");
        assertThat(table(page("/admin/book?filter.publisher=" + publisherOne + "&filter.author=" + authorTwo)).stream().map(row -> row.get(4)))
                .containsExactly("Book 5");
        assertThat(table(page("/admin/book?filter.title=BOOK 3")).stream().map(row -> row.get(4))).containsExactly("Book 3");
        String chips = page("/admin/book?filter.author=" + authorTwo);
        assertThat(chips).contains("<span class=\"chip-label\">Author:</span>", "Author 2", "Clear all filters");
        assertThat(page("/admin/book?filter.secretToken=token-0")).contains("1 filter in the address does not match any filter of this table and was ignored.")
                .doesNotContain("filter.secretToken\"", "token-0");
        assertThat(page("/admin/book?filter.author=999999")).contains("The Author filter was ignored: that choice is not available.");
    }

    @Test
    void anAddressThatIsNotOneOfThisHostsEntitiesIsNotFound() throws Exception {
        signInAndOpenDashboard();

        assertThat(send(MockMvcRequestBuilders.get("/admin/product")).getStatus()).isEqualTo(404);
        assertThat(send(MockMvcRequestBuilders.get("/admin/nothing")).getStatus()).isEqualTo(404);
    }
}
