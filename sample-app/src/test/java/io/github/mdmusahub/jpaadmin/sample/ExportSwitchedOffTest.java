package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code jpa.admin.export-enabled=false} in the real demo application (Phase 11, SEC-11): no list offers the button, every export
 * address of an entity in the console is refused with 403 (an unknown or hidden entity is still the console's 404), and nothing
 * but the export changes.
 */
@SpringBootTest(properties = "jpa.admin.export-enabled=false")
@AutoConfigureMockMvc
class ExportSwitchedOffTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void noListOffersTheButtonAndTheOtherActionsStay() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);

        for (String entity : List.of("category", "customer", "order", "product", "tag")) {
            String page = browser.get("/admin/" + entity).getContentAsString();

            assertThat(page).as(entity).doesNotContain("export.csv", "Export CSV");
            assertThat(page).as(entity).contains("href=\"/admin/" + entity + "/new\">New record</a>");
        }
    }

    @Test
    void everyExportAddressOfAnEntityInTheConsoleIsForbiddenAndTheOthersAreNotFound() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);

        for (String entity : List.of("category", "customer", "order", "product", "tag")) {
            MockHttpServletResponse response = browser.get("/admin/" + entity + "/export.csv");

            assertThat(response.getStatus()).as(entity).isEqualTo(403);
            assertThat(response.getHeader("Content-Disposition")).as(entity).isNull();
            assertThat(response.getContentType()).as(entity).doesNotContain("csv");
        }
        assertThat(browser.get("/admin/nothing-like-this/export.csv").getStatus()).isEqualTo(404);
        assertThat(browser.get("/admin/internalauditentry/export.csv").getStatus()).isEqualTo(404);
    }

    @Test
    void aForbiddenExportDoesNotRevealTheRecords() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);

        assertThat(browser.get("/admin/customer/export.csv?q=a").getContentAsString()).doesNotContain("@", "Full name");
    }
}
