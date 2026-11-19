package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.service.CreationDates;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.persistence.EntityManagerFactory;

/**
 * A table that cannot be read (Phase 10): the product table "fails" for the chart only. The chart shows it as unavailable
 * with a plain message, the other entities still show their numbers, the dashboard and the endpoint still answer 200, and the
 * failure is logged once, without a stack trace, however many pages ask (the failed read is cached for the window like any
 * other).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({FixedClockConfiguration.class, ChartFailureTest.BrokenProductTable.class})
@ExtendWith(OutputCaptureExtension.class)
class ChartFailureTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class BrokenProductTable {

        @Bean
        CreationDates brokenProductCreationDates(EntityManagerFactory factory) {
            CreationDates real = CreationDates.over(SharedEntityManagerCreator.createSharedEntityManager(factory));
            return new CreationDates(null) {
                @Override
                public Days read(AdminEntity entity, LocalDate first, LocalDate last, ZoneId zone, int rowCap) {
                    if (entity.urlName().equals("product")) {
                        throw new IllegalStateException("the table is not there");
                    }
                    return real.read(entity, first, last, zone, rowCap);
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void aBrokenTableIsUnavailableTheOthersShowAndEveryPageAnswers200(CapturedOutput output) throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        int from = output.getAll().length();

        MockHttpServletResponse page = browser.get("/admin");
        MockHttpServletResponse sevenDays = browser.get("/admin?range=7");
        MockHttpServletResponse json = browser.get("/admin/api/stats?range=30");

        assertThat(page.getStatus()).isEqualTo(200);
        assertThat(sevenDays.getStatus()).isEqualTo(200);
        assertThat(json.getStatus()).isEqualTo(200);
        String html = page.getContentAsString();
        assertThat(html).contains("The creation dates of Product could not be read, so it is not in the chart.")
                .contains("<span class=\"chart-legend-total chart-unavailable\">unavailable</span>")
                .contains("<span class=\"chart-legend-total\">30 created</span>")
                .contains("<span class=\"chart-legend-total\">12 created</span>")
                .contains("<span class=\"chart-legend-total\">6 created</span>");
        assertThat(html).as("the rest of the dashboard is intact").contains("Total records", "href=\"/admin/product\"");
        assertThat(json.getContentAsString()).contains("\"name\":\"product\",\"label\":\"Product\",\"available\":false,\"total\":0")
                .contains("\"name\":\"order\",\"label\":\"Order\",\"available\":true,\"total\":30");

        List<String> lines = Arrays.stream(output.getAll().substring(from).split("\\R"))
                .filter(line -> line.contains("could not read the creation dates")).toList();
        assertThat(lines).as("three pages, one line").hasSize(1);
        assertThat(lines.get(0)).contains("sample.domain.Product").contains("unavailable").doesNotContain("\tat ");
        assertThat(output.getAll().substring(from)).doesNotContain("the table is not there\n\tat")
                .doesNotContain("at io.github.mdmusahub.jpaadmin.service");
    }
}
