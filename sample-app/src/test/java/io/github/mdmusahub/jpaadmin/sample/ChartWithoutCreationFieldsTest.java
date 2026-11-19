package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A console in which no entity has a creation field (Phase 10): the four entities that have one are excluded by the property,
 * so only Tag (which has none) and the hidden entity are left. The chart card is not on the dashboard, one sentence says why,
 * and the endpoint answers with an empty chart.
 */
@SpringBootTest(properties = "jpa.admin.excluded-entities=category,customer,order,product")
@AutoConfigureMockMvc
class ChartWithoutCreationFieldsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theCardIsHiddenAndOneSentenceSaysWhy() throws Exception {
        MockHttpServletResponse response = MockBrowser.signedIn(mvc).get("/admin");
        String html = response.getContentAsString();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(html).doesNotContain("chart-card").doesNotContain("range-switch").doesNotContain("id=\"chart\"")
                .doesNotContain("<svg class=\"chart-svg\"");
        assertThat(html.replaceAll("\\s+", " ")).contains("There is no chart: no entity has a creation date field.");
        assertThat(html).contains("href=\"/admin/tag\"");
    }

    @Test
    void theEndpointAnswersAnEmptyChart() throws Exception {
        MockHttpServletResponse response = MockBrowser.signedIn(mvc).get("/admin/api/stats?range=7");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).startsWith("{\"range\":7,\"entities\":[],\"days\":[{\"date\":\"")
                .contains("\"counts\":[]").endsWith("\"notes\":[]}");
    }
}
