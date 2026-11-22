package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.sample.domain.Category;
import io.github.mdmusahub.jpaadmin.storage.OrphanUploadReport;
import io.github.mdmusahub.jpaadmin.storage.StorageProvider;
import io.github.mdmusahub.jpaadmin.storage.StoredFile;
import io.github.mdmusahub.jpaadmin.storage.UploadTargets;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The read-only report of upload files that no record holds, run in the real demo application (Phase 14a, UPL-9, D-132): the real
 * storage folder, the real provider, the real {@code Category.image} field. The demo's storage folder is shared with other tests, so
 * the assertions are about the files this test made, and about the report changing nothing.
 *
 * <p>The documented command to see the report for a folder of the demo is
 * {@code mvn -B -pl sample-app -am test -Dtest=OrphanUploadReportPagesTest}; the test prints nothing and the report is read through
 * {@code OrphanUploadReport.Report#describe()}.
 */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
class OrphanUploadReportPagesTest {

    @Autowired
    private StorageProvider provider;

    @Autowired
    private UploadTargets targets;

    @Autowired
    private AdminEntityRegistry registry;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<StoredFile> made = new ArrayList<>();
    private Long categoryId;

    @BeforeEach
    void pickACategory() {
        categoryId = new TransactionTemplate(transactionManager).execute(status -> entityManager
                .createQuery("select c.id from Category c where c.name = 'Books'", Long.class).getSingleResult());
    }

    @AfterEach
    void putTheDemoDataBackAndRemoveWhatThisTestMade() throws IOException {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createQuery("update Category c set c.image = null where c.id = :id").setParameter("id", categoryId)
                        .executeUpdate());
        for (StoredFile file : made) {
            provider.delete(file); // the test's own cleanup, through the provider; the report itself deletes nothing
        }
    }

    private StoredFile store(String content) throws IOException {
        StoredFile file = provider.store("default", "categories", "png",
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), 1024);
        made.add(file);
        return file;
    }

    private OrphanUploadReport.Report report() {
        return new TransactionTemplate(transactionManager).execute(status ->
                OrphanUploadReport.find(provider, targets, registry, entityManager));
    }

    private Path pathOf(StoredFile file) {
        return Path.of(provider.location(file.storage())).resolve(file.key());
    }

    @Test
    void theDemosOwnUploadFieldIsReadAndAnUnheldFileIsListedButAHeldOneIsNot() throws IOException {
        StoredFile held = store("the picture of Books");
        StoredFile orphan = store("an old picture of Books, replaced since");
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.find(Category.class, categoryId).setImage(provider.url(held)));

        OrphanUploadReport.Report report = report();

        assertThat(report.orphans()).extracting(OrphanUploadReport.Orphan::key).contains(orphan.key()).doesNotContain(held.key());
        assertThat(report.filesHeld()).isGreaterThanOrEqualTo(1);
        assertThat(report.orphans()).filteredOn(found -> found.key().equals(orphan.key())).singleElement()
                .satisfies(found -> assertThat(found.address()).startsWith("/admin/files/default/categories/"));
    }

    @Test
    void theReportDeletesNothingEveryFileIsStillThereWithItsBytes() throws IOException {
        StoredFile held = store("held");
        StoredFile orphan = store("an orphan");
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.find(Category.class, categoryId).setImage(provider.url(held)));
        byte[] orphanBytes = Files.readAllBytes(pathOf(orphan));

        OrphanUploadReport.Report report = report();
        report.describe();

        assertThat(report.orphans()).extracting(OrphanUploadReport.Orphan::key).contains(orphan.key());
        assertThat(Files.exists(pathOf(held))).isTrue();
        assertThat(Files.exists(pathOf(orphan))).as("an orphan is reported, never deleted").isTrue();
        assertThat(Files.readAllBytes(pathOf(orphan))).isEqualTo(orphanBytes);
        String stillHeld = new TransactionTemplate(transactionManager)
                .execute(status -> entityManager.find(Category.class, categoryId).getImage());
        assertThat(stillHeld).as("the record is untouched").isEqualTo(provider.url(held));
    }
}
