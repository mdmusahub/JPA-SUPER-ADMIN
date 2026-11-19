package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Phase 10 in a host that is not the demo: none of Book, Author or Publisher has a creation date field, so the dashboard has
 * no chart card (and says why in one sentence), and the JSON endpoint answers an empty chart. No library change was needed
 * for the card to adapt to this host.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsoleHasNoChartForThisHostTest {

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

    @Test
    void theDashboardHasNoChartCardAndSaysWhy() throws Exception {
        signIn();

        String html = send(MockMvcRequestBuilders.get("/admin")).getContentAsString();

        assertThat(html).doesNotContain("chart-card").doesNotContain("range-switch").doesNotContain("<svg class=\"chart-svg\"");
        assertThat(html.replaceAll("\\s+", " ")).contains("There is no chart: no entity has a creation date field.");
        assertThat(html).contains("Book", "Author", "Publisher");
    }

    @Test
    void theEndpointAnswersAnEmptyChart() throws Exception {
        signIn();

        MockHttpServletResponse response = send(MockMvcRequestBuilders.get("/admin/api/stats?range=90"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).startsWith("{\"range\":90,\"entities\":[],\"days\":[")
                .endsWith("\"notes\":[]}").doesNotContain("book").doesNotContain("author");
    }
}
