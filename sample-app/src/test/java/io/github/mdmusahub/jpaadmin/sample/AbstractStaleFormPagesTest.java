package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.versiontest.Tally;
import jakarta.persistence.EntityManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A stale form is refused with the plain sentence, through the real application (Phase 14a, D-128; the rule is D-80, Phase 8).
 * The demo's entities have no {@code @Version} and none was added, so this test brings an entity of its own ({@link Tally}, in the
 * test sources) and adds its package to the entity scan of this one test context.
 *
 * <p>The story is the one the rule exists for: an administrator opens a record's edit form; someone else changes the record; the
 * first administrator saves. The save must be refused in words a person can act on, with no stack trace and no class or table
 * name, nothing must be written, and a second try with the same old form must be refused too. Two subclasses run it with
 * {@code spring.jpa.open-in-view} on and off (D-29).
 */
abstract class AbstractStaleFormPagesTest {

    /** Adds the test-only entity's package to the entity scan, next to the demo application's own. */
    @TestConfiguration
    @EntityScan(basePackageClasses = {SampleApplication.class, Tally.class})
    static class VersionedEntity {
    }

    private static final Pattern FORM_ACTION = Pattern.compile("action=\"(/admin/tally/\\d+[^\"]*)\"");
    private static final Pattern TRACE = Pattern.compile("(?i)exception|optimisticlock|staleobject|caused by|org\\.hibernate|\\bat java\\.");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockBrowser browser;
    private Long id;

    @BeforeEach
    void signInAndAddARecord() throws Exception {
        browser = MockBrowser.signedIn(mvc);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createQuery("delete from Tally").executeUpdate();
            Tally tally = new Tally("First title");
            entityManager.persist(tally);
            entityManager.flush();
            id = tally.getId();
        });
    }

    private Tally stored() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Tally tally = entityManager.find(Tally.class, id);
            entityManager.detach(tally);
            return tally;
        });
    }

    /** Someone else changes the record after the form was opened. */
    private void anotherAdministratorChangesTheRecord() {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> entityManager.find(Tally.class, id).setTitle("Changed by someone else"));
    }

    private String openTheEditForm() throws Exception {
        return browser.get("/admin/tally/" + id + "/edit").getContentAsString();
    }

    private static String actionOf(String form) {
        Matcher matcher = FORM_ACTION.matcher(form);
        assertThat(matcher.find()).as("the edit form posts to the record, with the version it was opened with").isTrue();
        return matcher.group(1).replace("&amp;", "&");
    }

    private MockHttpServletResponse save(String form, String title) throws Exception {
        return browser.postUri(actionOf(form), "title", title, "_csrf", MockBrowser.csrf(form));
    }

    @Test
    void theFormCarriesTheVersionItWasOpenedWithInItsAddressAndInNoField() throws Exception {
        String form = openTheEditForm();

        assertThat(actionOf(form)).endsWith("jpa-admin-version=0");
        assertThat(form).as("the version is in the address, never in a field the visitor could edit")
                .doesNotContain("name=\"revision\"").doesNotContain("name=\"jpa-admin-version\"");
    }

    @Test
    void aStaleFormIsRefusedWithThePlainSentenceAndNothingIsSaved() throws Exception {
        String form = openTheEditForm();
        anotherAdministratorChangesTheRecord();

        MockHttpServletResponse refused = save(form, "My change");
        String page = refused.getContentAsString();

        assertThat(refused.getStatus()).isEqualTo(422);
        assertThat(page).contains("This record was changed by someone else after you opened the form, so your changes were not saved.");
        assertThat(page).as("the sentence tells the visitor what to do").contains("Open the record again");
        assertThat(TRACE.matcher(page).find()).as("no stack trace, class or library name reaches the browser").isFalse();
        assertThat(page).as("no class or package name").doesNotContain("io.github").doesNotContain("versiontest");
        Tally now = stored();
        assertThat(now.getTitle()).as("nothing of the stale form was saved").isEqualTo("Changed by someone else");
        assertThat(now.getRevision()).isEqualTo(1);
    }

    @Test
    void aSecondTryWithTheSameOldFormIsRefusedToo() throws Exception {
        String form = openTheEditForm();
        anotherAdministratorChangesTheRecord();
        assertThat(save(form, "First try").getStatus()).isEqualTo(422);

        MockHttpServletResponse again = save(form, "Second try");

        assertThat(again.getStatus()).isEqualTo(422);
        assertThat(again.getContentAsString()).contains("changed by someone else");
        assertThat(stored().getTitle()).isEqualTo("Changed by someone else");
    }

    @Test
    void openingTheRecordAgainGivesAFormThatSaves() throws Exception {
        String stale = openTheEditForm();
        anotherAdministratorChangesTheRecord();
        assertThat(save(stale, "Lost change").getStatus()).isEqualTo(422);

        String fresh = openTheEditForm();
        MockHttpServletResponse saved = save(fresh, "Second attempt, from the new form");

        assertThat(actionOf(fresh)).endsWith("jpa-admin-version=1");
        assertThat(saved.getStatus()).isEqualTo(303);
        Tally now = stored();
        assertThat(now.getTitle()).isEqualTo("Second attempt, from the new form");
        assertThat(now.getRevision()).isEqualTo(2);
    }

    @Test
    void aFormThatIsStillCurrentSavesAndRaisesTheVersion() throws Exception {
        String form = openTheEditForm();

        MockHttpServletResponse saved = save(form, "A change nobody raced");

        assertThat(saved.getStatus()).isEqualTo(303);
        assertThat(stored().getTitle()).isEqualTo("A change nobody raced");
        assertThat(stored().getRevision()).isEqualTo(1);
    }

    @Test
    void aPostWithTheVersionLeftOutOfTheAddressIsRefusedAsStale() throws Exception {
        String form = openTheEditForm();

        MockHttpServletResponse refused = browser.post("/admin/tally/" + id, "title", "No version", "_csrf", MockBrowser.csrf(form));

        assertThat(refused.getStatus()).isEqualTo(422);
        assertThat(refused.getContentAsString()).contains("changed by someone else");
        assertThat(stored().getTitle()).as("an update that does not name its version is never saved").isEqualTo("First title");
    }

    @Test
    void aVersionPostedAsAFormFieldNeverMakesAStaleFormCurrent() throws Exception {
        String form = openTheEditForm();
        anotherAdministratorChangesTheRecord();

        // Only the version in the address counts; a body field with the current version (1) is not read.
        MockHttpServletResponse refused = browser.postUri(actionOf(form), "title", "Forged", "revision", "1", "_csrf",
                MockBrowser.csrf(form));

        assertThat(refused.getStatus()).isEqualTo(422);
        assertThat(stored().getTitle()).isEqualTo("Changed by someone else");
    }

    @Test
    void anEntityWithoutAVersionHasNoStaleCheckAndItsFormCarriesNoVersion() throws Exception {
        // The demo's tags have no @Version: the last save wins, as documented (D-80).
        String tags = browser.get("/admin/tag?q=new").getContentAsString();
        Matcher link = Pattern.compile("href=\"/admin/tag/(\\d+)[?\"]").matcher(tags);
        assertThat(link.find()).isTrue();

        String form = browser.get("/admin/tag/" + link.group(1) + "/edit").getContentAsString();

        assertThat(form).doesNotContain("jpa-admin-version");
    }
}
