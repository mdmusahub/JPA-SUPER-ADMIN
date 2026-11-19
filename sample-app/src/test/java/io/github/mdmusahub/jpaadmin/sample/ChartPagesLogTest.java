package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SEC-1 for the log of the chart: the statements Hibernate logs for the chart select the creation column and nothing else, so
 * no sensitive column, no title, no name and no secret can come with them, and no value read back is a secret. The console's
 * own loggers are at TRACE too, so anything the chart code writes is in the captured output.
 */
@SpringBootTest(properties = {"logging.level.org.hibernate.SQL=DEBUG", "logging.level.org.hibernate.orm.jdbc.bind=TRACE",
        "logging.level.org.hibernate.orm.jdbc.extract=TRACE", "logging.level.io.github.mdmusahub.jpaadmin=TRACE"})
@AutoConfigureMockMvc
@Import(FixedClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ChartPagesLogTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theChartStatementsSelectOnlyTheCreationColumnAndNoSecretIsLogged(CapturedOutput output) throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        int from = output.getAll().length();

        assertThat(browser.get("/admin/api/stats?range=90").getStatus()).isEqualTo(200);
        assertThat(browser.get("/admin?range=7").getStatus()).isEqualTo(200);
        String log = output.getAll().substring(from);

        List<String> chart = Arrays.stream(log.split("\\R")).filter(line -> line.contains("org.hibernate.SQL"))
                .filter(line -> line.toLowerCase().contains("order by")).toList();
        assertThat(chart).as("the SQL was really logged, so this test can see a leak").hasSize(4);
        for (String line : chart) {
            String sql = line.toLowerCase();
            String selected = sql.substring(sql.indexOf("select ") + 7, sql.indexOf(" from "));
            assertThat(selected).as(line).matches("[a-z0-9_]+\\.created_at");
            assertThat(sql).doesNotContain("password").doesNotContain("name").doesNotContain("email").doesNotContain("count(");
        }
        assertThat(log).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER).doesNotContain(MockBrowser.PASSWORD);
    }
}
