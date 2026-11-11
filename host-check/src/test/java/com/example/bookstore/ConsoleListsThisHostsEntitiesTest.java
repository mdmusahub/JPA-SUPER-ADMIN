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

    @Test
    void eachEntityHasAPageAndTheSecretTokenValueNeverAppears() throws Exception {
        signInAndOpenDashboard();

        for (String entity : List.of("book", "author", "publisher")) {
            MockHttpServletResponse page = send(MockMvcRequestBuilders.get("/admin/" + entity));
            assertThat(page.getStatus()).as(entity).isEqualTo(200);
            assertThat(page.getContentAsString()).contains("This table view is not available yet.")
                    .doesNotContain("not-a-real-secret");
        }
    }

    @Test
    void anAddressThatIsNotOneOfThisHostsEntitiesIsNotFound() throws Exception {
        signInAndOpenDashboard();

        assertThat(send(MockMvcRequestBuilders.get("/admin/product")).getStatus()).isEqualTo(404);
        assertThat(send(MockMvcRequestBuilders.get("/admin/nothing")).getStatus()).isEqualTo(404);
    }
}
