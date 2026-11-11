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
import org.springframework.test.web.servlet.MockMvc;

/**
 * SEC-1 for the log: a sensitive value is in no log line. The test turns on the most talkative logging there is for
 * this question: every SQL statement, every bound parameter and every value Hibernate reads back from a result set
 * ({@code org.hibernate.orm.jdbc.extract}), and the console's own loggers at TRACE. Then it lists the customers every
 * way it can. If the password column were selected, its value would be in the log; if a sort or search could reach it,
 * its name would be in the statement.
 *
 * <p>Only what is logged after the sign-in counts: the demo data is inserted at startup, and the insert of a seeded
 * customer rightly mentions the column.
 */
@SpringBootTest(properties = {"logging.level.org.hibernate.SQL=DEBUG", "logging.level.org.hibernate.orm.jdbc.bind=TRACE",
        "logging.level.org.hibernate.orm.jdbc.extract=TRACE", "logging.level.io.github.mdmusahub.jpaadmin=TRACE"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ListPagesLogTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void thePasswordValueIsNeverReadSoItIsNeverLoggedAndTheStatementsNeverNameTheColumn(CapturedOutput output)
            throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        int from = output.getAll().length();

        for (String path : List.of("/admin/customer", "/admin/customer?size=100", "/admin/customer?sort=password&dir=desc",
                "/admin/customer?sort=email&dir=desc", "/admin/customer?q=placeholder", "/admin/customer?q=ava",
                "/admin/order?size=100", "/admin/product?q=a&sort=name")) {
            assertThat(browser.get(path).getStatus()).as(path).isEqualTo(200);
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
}
