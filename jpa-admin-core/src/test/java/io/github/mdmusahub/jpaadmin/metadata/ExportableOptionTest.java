package io.github.mdmusahub.jpaadmin.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.annotation.AdminResource;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** {@code @AdminResource(exportable)} and what the metadata makes of it (Phase 11, D-106). */
class ExportableOptionTest {

    private static final AdminEntityRegistry REGISTRY =
            EntityScanner.scan(ListTestPersistence.factory().getMetamodel(), DiscoverySettings.defaults());

    private static AdminEntity entity(String urlName) {
        return REGISTRY.find(urlName).orElseThrow();
    }

    @Test
    void theOptionDefaultsToTrueSoAnAnnotatedEntityIsStillExportable() throws NoSuchMethodException {
        Method exportable = AdminResource.class.getMethod("exportable");

        assertThat(exportable.getDefaultValue()).isEqualTo(true);
    }

    @Test
    void anEntityWithoutTheAnnotationCanBeExported() {
        assertThat(entity("parcel").access()).isEqualTo(AdminEntity.Access.FULL);
        assertThat(entity("parcel").canExport()).isTrue();
    }

    @Test
    void exportableFalseIsReadOnceIntoTheMetadata() {
        assertThat(entity("shielded").access().exportable()).isFalse();
        assertThat(entity("shielded").canExport()).isFalse();
    }

    @Test
    void exportableFalseChangesNothingElse() {
        AdminEntity shielded = entity("shielded");

        assertThat(shielded.canCreate()).isTrue();
        assertThat(shielded.canUpdate()).isTrue();
        assertThat(shielded.canDelete()).isTrue();
    }

    @Test
    void aReadOnlyEntityCanStillBeExportedBecauseAnExportOnlyReads() {
        AdminEntity archive = entity("archive");

        assertThat(archive.access().readOnly()).isTrue();
        assertThat(archive.canExport()).isTrue();
    }

    @Test
    void otherOptionsDoNotSwitchTheExportOff() {
        assertThat(entity("anchor").canExport()).as("deletable = false").isTrue();
        assertThat(entity("frozen").canExport()).as("updatable = false").isTrue();
        assertThat(entity("sealed").canExport()).as("creatable = false").isTrue();
    }

    @Test
    void theOlderConstructorsOfAccessStayExportable() {
        assertThat(new AdminEntity.Access(true, true).exportable()).isTrue();
        assertThat(new AdminEntity.Access(false, true, true, true).exportable()).isTrue();
        assertThat(new AdminEntity.Access(true, false, false, false, false).canExport()).isFalse();
    }
}
