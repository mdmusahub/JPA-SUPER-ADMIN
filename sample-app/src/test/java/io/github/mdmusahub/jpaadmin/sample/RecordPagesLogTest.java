package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SEC-1 and SEC-12 for the log of the record pages: opening a record reads no sensitive column, so no sensitive value and
 * no sensitive column name is in any statement, bound parameter or extracted value Hibernate logs; and a delete the
 * database refuses writes no SQL, no stack trace and no stored value into the console's own log lines.
 *
 * <p>The console's own loggers are at TRACE here. Hibernate's loggers for statements and for the values read back are on
 * for the detail pages only: a delete goes through the persistence provider (so the host's own mapping decides what
 * follows, cascades included), and Hibernate itself reads the whole row of the record it removes. What Hibernate logs when
 * a host switches its extract logger to TRACE is Hibernate's, not the console's, and is listed under the limitations.
 */
@SpringBootTest(properties = {"logging.level.org.hibernate.SQL=DEBUG", "logging.level.org.hibernate.orm.jdbc.bind=TRACE",
        "logging.level.org.hibernate.orm.jdbc.extract=TRACE", "logging.level.io.github.mdmusahub.jpaadmin=TRACE"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RecordPagesLogTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void openingARecordReadsNoSensitiveColumnSoNoSensitiveValueIsLoggedAndNoStatementNamesTheColumn(CapturedOutput output)
            throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        int from = output.getAll().length();

        for (String path : List.of("/admin/customer/1", "/admin/customer/2?q=ava&sort=password&dir=desc",
                "/admin/order/1", "/admin/product/1")) {
            assertThat(browser.getUri(path).getStatus()).as(path).isEqualTo(200);
        }
        String log = output.getAll().substring(from);

        List<String> statements = Arrays.stream(log.split("\\R")).filter(line -> line.contains("org.hibernate.SQL")
                || line.toLowerCase().contains("select ")).toList();
        assertThat(statements).as("the SQL was really logged, so this test can see a leak").isNotEmpty();
        assertThat(statements).anySatisfy(line -> assertThat(line.toLowerCase()).contains("from customer"));
        assertThat(statements).allSatisfy(line -> assertThat(line.toLowerCase()).doesNotContain("password"));
        assertThat(log).as("the extracted values were logged too, and the secret is not among them")
                .contains("extracted value").doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER)
                .doesNotContain(MockBrowser.PASSWORD);
    }

    @Test
    void aDeleteTheDatabaseRefusesLeavesNoSqlNoStackTraceAndNoValueInTheConsolesOwnLogLines(CapturedOutput output)
            throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        String detail = browser.getUri("/admin/category/1").getContentAsString();
        Matcher form = Pattern.compile("(?s)<form class=\"confirm-panel\"[^>]*?\\saction=\"([^\"]+)\"").matcher(detail);
        assertThat(form.find()).isTrue();
        int from = output.getAll().length();

        MockHttpServletResponse response = browser.postUri(form.group(1), "_csrf", MockBrowser.csrf(detail));
        String log = output.getAll().substring(from);

        assertThat(response.getStatus()).isEqualTo(303);
        List<String> console = Arrays.stream(log.split("\\R")).filter(line -> line.contains("jpaadmin.service.DeleteService"))
                .toList();
        assertThat(console).as("the refusal is logged once, by the console").hasSize(1);
        assertThat(console.get(0)).contains("The database refused a delete in 'category' (ConstraintViolationException)")
                .doesNotContain("select", "delete from", "constraint", "foreign key", "Exception:", "\tat ");
        assertThat(log).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER).doesNotContain(MockBrowser.PASSWORD);
        // The browser sees none of it either (the page is checked in RecordPagesTest too).
        assertThat(browser.getUri(response.getHeader("Location")).getContentAsString().toLowerCase())
                .contains("was not deleted");
    }
}
