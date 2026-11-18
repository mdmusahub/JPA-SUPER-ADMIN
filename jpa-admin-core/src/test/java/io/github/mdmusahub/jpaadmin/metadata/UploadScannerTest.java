package io.github.mdmusahub.jpaadmin.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.testsupport.UploadTestPersistence;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code @AdminUpload} is read once, from the field or from its getter, with its defaults (ARCH-1); a field without it has no
 * upload spec; a wrong value never crashes the scanner (the startup check names it later).
 */
class UploadScannerTest {

    private static AdminField field(String name) {
        AdminEntityRegistry registry = UploadTestPersistence.registry();
        AdminEntity dossier = registry.find("dossier").orElseThrow();
        return dossier.fields().stream().filter(candidate -> candidate.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void anUploadFieldWithNoOptionsGetsTheDocumentedDefaults() {
        UploadSpec spec = field("cover").upload();

        assertThat(spec).isNotNull();
        assertThat(spec.storage()).isEqualTo("default");
        assertThat(spec.folder()).isEmpty();
        assertThat(spec.accept()).containsExactly("image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");
        assertThat(spec.maxSize()).isEmpty();
        assertThat(field("cover").isUpload()).isTrue();
    }

    @Test
    void theOptionsAreReadAsWrittenWithTheTypesStrippedAndInLowerCase() {
        UploadSpec spec = field("contract").upload();

        assertThat(spec.storage()).isEqualTo("vault");
        assertThat(spec.folder()).isEqualTo("docs/scans");
        assertThat(spec.accept()).containsExactly("application/pdf", "image/png");
        assertThat(spec.maxSize()).isEqualTo("2MB");
    }

    @Test
    void theAnnotationOnTheGetterIsReadToo() {
        UploadSpec spec = field("scan").upload();

        assertThat(spec).isNotNull();
        assertThat(spec.folder()).isEqualTo("scans");
    }

    @Test
    void aFieldThatIsNotAStringStillCarriesTheSpecSoTheStartupCheckCanNameIt() {
        assertThat(field("wrongType").upload()).isNotNull();
        assertThat(field("wrongType").javaType()).isEqualTo(Integer.class);
    }

    @Test
    void anOrdinaryFieldHasNoUploadSpec() {
        assertThat(field("title").upload()).isNull();
        assertThat(field("title").isUpload()).isFalse();
        assertThat(field("id").upload()).isNull();
    }

    @Test
    void anEntityWhoseAddressIsTheUploadEndpointsIsUnsupportedWithAReason() {
        AdminEntityRegistry registry = UploadTestPersistence.registry();

        assertThat(registry.find("uploads")).isEmpty();
        assertThat(registry.all()).extracting(AdminEntity::urlName).doesNotContain("uploads").contains("dossier");
        assertThat(registry.unsupported()).hasSize(1);
        assertThat(registry.unsupported().get(0).urlName()).isEqualTo("uploads");
        assertThat(registry.unsupported().get(0).unsupportedReason()).contains("'uploads'", "keeps for itself");
    }

    @Test
    void theOldTwelveArgumentConstructorStillMakesAFieldWithNoUpload() {
        AdminField plain = new AdminField("note", "Note", String.class, AdminField.Kind.TEXT, List.of(), null,
                false, false, true, false, false, false);

        assertThat(plain.upload()).isNull();
    }
}
