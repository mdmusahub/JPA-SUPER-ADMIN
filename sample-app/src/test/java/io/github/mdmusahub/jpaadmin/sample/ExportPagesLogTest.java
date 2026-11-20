package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SEC-1 for the log, as {@code ListPagesLogTest} but for the export: with every SQL statement, every bound parameter and every
 * value Hibernate reads back at the most talkative level, the customers are exported every way the address allows. If the
 * password column were selected its value would be in the log, and if a sort, search or filter could reach it its name would be in
 * a statement.
 */
@SpringBootTest(properties = {"logging.level.org.hibernate.SQL=DEBUG", "logging.level.org.hibernate.orm.jdbc.bind=TRACE",
        "logging.level.org.hibernate.orm.jdbc.extract=TRACE", "logging.level.io.github.mdmusahub.jpaadmin=TRACE",
        // Makes this a context of its own: a context shared with ListPagesLogTest would lose its levels to the reset below.
        "export.log.test=own-context"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ExportPagesLogTest {

    @BeforeEach
    void sqlLoggingIsOn() {
        SqlLogLevels.apply();
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LoggingSystem loggingSystem;

    /**
     * Logger levels are global to the test JVM, and a cached context does not undo them: left at TRACE, the bind logging of this
     * test would make a later test class (FormPagesLogTest, which expects it off) see typed values in the log.
     */
    @AfterEach
    void putTheLogLevelsBack() {
        for (String logger : List.of("org.hibernate.SQL", "org.hibernate.orm.jdbc.bind", "org.hibernate.orm.jdbc.extract",
                "io.github.mdmusahub.jpaadmin")) {
            loggingSystem.setLogLevel(logger, null);
        }
    }

    @Test
    void thePasswordValueIsNeverReadSoItIsNeverLoggedAndTheStatementsNeverNameTheColumn(CapturedOutput output) throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        int from = output.getAll().length();

        for (String path : List.of("/admin/customer/export.csv", "/admin/customer/export.csv?sort=password&dir=desc",
                "/admin/customer/export.csv?sort=email&dir=desc", "/admin/customer/export.csv?q=placeholder",
                "/admin/customer/export.csv?filter.password=demo", "/admin/order/export.csv", "/admin/product/export.csv?q=a&sort=name")) {
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
