package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SEC-1 and SEC-12 for the log of the forms: a value typed into a form, a secret above all, is in no line the console
 * writes, whether the save worked or was refused, and a refused save writes no SQL, no stack trace and no constraint name
 * into the console's own lines.
 *
 * <p>The console's own loggers are at TRACE and Hibernate's statement logger is on (the statements hold {@code ?}, never
 * a value). Hibernate's <em>bind</em> logger ({@code org.hibernate.orm.jdbc.bind}) is left off on purpose: at TRACE it
 * prints every bound parameter of an insert, a typed password included. That log is Hibernate's and the host's to switch
 * on, not the console's, and the limitation is listed in the documentation.
 */
@SpringBootTest(properties = {"logging.level.org.hibernate.SQL=DEBUG", "logging.level.io.github.mdmusahub.jpaadmin=TRACE"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class FormPagesLogTest {

    private static final String SECRET = "needle-secret-for-the-log-9182";
    private static final Pattern FORM_ACTION = Pattern.compile("(?s)<form class=\"card form-card\"[^>]*?\\saction=\"([^\"]+)\"");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void removeTheRows() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> entityManager
                .createQuery("delete from Customer c where c.email like 'log-test-%'").executeUpdate());
    }

    private static List<String> consoleLines(String log) {
        return Arrays.stream(log.split("\\R")).filter(line -> line.contains("i.g.m.j.")).toList();
    }

    @Test
    void aSavedAndARefusedFormLogNoTypedValueAndNoSql(CapturedOutput output) throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);
        String token = MockBrowser.csrf(browser.getUri("/admin/customer/new").getContentAsString());
        int from = output.getAll().length();

        MockHttpServletResponse created = browser.postUri("/admin/customer", "fullName", "Log Test", "email",
                "log-test-ok@example.test", "password", SECRET, "active", "true", "createdAt", "2026-01-01T10:00", "_csrf", token);
        MockHttpServletResponse refused = browser.postUri("/admin/customer", "fullName", "Log Test", "email", "not-an-email",
                "password", SECRET, "active", "true", "createdAt", "2026-01-01T10:00", "_csrf", token);
        MockHttpServletResponse duplicate = browser.postUri("/admin/customer", "fullName", "Log Test", "email",
                "log-test-ok@example.test", "password", SECRET + "2", "active", "true", "createdAt", "2026-01-01T10:00",
                "_csrf", token);
        Long id = new TransactionTemplate(transactionManager).execute(status -> entityManager
                .createQuery("select c.id from Customer c where c.email = 'log-test-ok@example.test'", Long.class)
                .getSingleResult());
        String edit = browser.getUri("/admin/customer/" + id + "/edit").getContentAsString();
        Matcher action = FORM_ACTION.matcher(edit);
        assertThat(action.find()).isTrue();
        MockHttpServletResponse changed = browser.postUri(action.group(1).replace("&amp;", "&"), "fullName", "Log Test 2",
                "email", "log-test-ok@example.test", "password", SECRET + "3", "active", "true", "createdAt", "2026-01-01T10:00",
                "_csrf", MockBrowser.csrf(edit));
        String log = output.getAll().substring(from);

        assertThat(created.getStatus()).isEqualTo(303);
        assertThat(refused.getStatus()).isEqualTo(422);
        assertThat(duplicate.getStatus()).isEqualTo(422);
        assertThat(changed.getStatus()).isEqualTo(303);
        assertThat(log).as("the SQL was really logged, so this test can see a leak").contains("org.hibernate.SQL");
        assertThat(log).as("no typed secret anywhere in the log").doesNotContain(SECRET).doesNotContain(MockBrowser.PASSWORD);
        List<String> console = consoleLines(log);
        assertThat(console).as("the refusals are logged by the console").anySatisfy(line -> assertThat(line)
                .contains("could not save a record of 'customer'"));
        assertThat(console).allSatisfy(line -> assertThat(line.toLowerCase()).doesNotContain("select ", "insert into",
                "update customer", "log-test-ok@example.test", "not-an-email", "unique index", "violation of", "constraint \"",
                "\tat "));
        assertThat(String.join("\n", console)).doesNotContain("Exception:");
    }
}
