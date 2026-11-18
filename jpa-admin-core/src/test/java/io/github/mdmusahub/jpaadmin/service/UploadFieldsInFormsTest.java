package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.testsupport.UploadTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.uploadmodel.Dossier;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Until the upload input exists (Phase 9b) an upload field is not a form field at all, so no plain text can be stored in it, and
 * a duplicate never carries a stored file (D-73).
 */
class UploadFieldsInFormsTest {

    private AdminEntityRegistry registry;
    private EntityManager entityManager;
    private AdminEntity dossier;
    private Long id;

    @BeforeEach
    void seed() {
        registry = UploadTestPersistence.registry();
        dossier = registry.find("dossier").orElseThrow();
        entityManager = UploadTestPersistence.entityManager();
        entityManager.getTransaction().begin();
        Dossier record = new Dossier();
        record.setTitle("Kept");
        record.setCover("/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png");
        record.setContract("/admin/files/vault/docs/scans/2026/11/fedcba9876543210fedcba9876543210.pdf");
        record.setScan("/admin/files/default/scans/2026/11/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png");
        entityManager.persist(record);
        entityManager.getTransaction().commit();
        id = record.getId();
    }

    @AfterEach
    void close() {
        entityManager.close();
    }

    private static List<String> keys(FormPage page) {
        return page.fields().stream().map(FormView.Field::key).toList();
    }

    @Test
    void theCreateFormOffersTheTitleAndNoUploadField() {
        FormPage page = FormQueryService.over(entityManager, registry).blank(dossier);

        assertThat(keys(page)).contains("title").doesNotContain("cover", "contract", "scan", "wrongType");
    }

    @Test
    void theEditFormOffersNoUploadFieldAndReadsNoUploadColumn() {
        FormPage page = FormQueryService.over(entityManager, registry).record(dossier, String.valueOf(id)).orElseThrow();

        assertThat(keys(page)).contains("title").doesNotContain("cover", "contract", "scan", "wrongType");
    }

    @Test
    void aDuplicateCopiesTheTitleAndNeverAStoredFile() {
        CopySeed seed = DetailQueryService.over(entityManager, registry).copySeed(dossier, String.valueOf(id)).orElseThrow();

        assertThat(seed.values()).extracting(CopySeed.Value::key).contains("title")
                .doesNotContain("cover", "contract", "scan", "wrongType");
        assertThat(seed.values()).extracting(CopySeed.Value::text).noneMatch(text -> text.contains("/files/"));
    }

    @Test
    void aPostedValueForAnUploadFieldIsNeverReadByTheSave() {
        List<String> asked = new ArrayList<>();
        RecordWriteService writes = RecordWriteService.over(entityManager, registry);

        entityManager.getTransaction().begin();
        writes.create(dossier, name -> {
            asked.add(name);
            return name.equals("title") ? "New" : "javascript:alert(1)";
        });
        entityManager.getTransaction().commit();

        assertThat(asked).contains("title").doesNotContain("cover", "contract", "scan", "wrongType");
        List<Dossier> created = entityManager.createQuery("select d from Dossier d where d.title = 'New'", Dossier.class)
                .getResultList();
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getCover()).isNull();
        assertThat(created.get(0).getContract()).isNull();
        assertThat(created.get(0).getScan()).isNull();
    }
}
