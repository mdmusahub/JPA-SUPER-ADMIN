package io.github.mdmusahub.jpaadmin.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.metadata.UploadSpec;
import io.github.mdmusahub.jpaadmin.testsupport.TestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.UploadTestPersistence;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/**
 * UPL-8 and the rest of the startup check: every case is a pure function of the registry, the configured storages and what the
 * provider says, so nothing here starts an application or touches the disk.
 */
class UploadStartupCheckTest {

    /** A provider that records what it was asked and answers from a table. */
    private static final class FakeProvider implements StorageProvider {

        final List<String> verified = new ArrayList<>();
        final Map<String, List<String>> problems = new HashMap<>();
        final Map<String, String> locations = new HashMap<>();
        boolean serveEverything = true;

        @Override
        public boolean supports(String storage) {
            return serveEverything;
        }

        @Override
        public List<String> verify(String storage) {
            verified.add(storage);
            return problems.getOrDefault(storage, List.of());
        }

        @Override
        public String location(String storage) {
            return locations.get(storage);
        }

        @Override
        public StoredFile store(String storage, String folder, String extension, InputStream content, long maxBytes)
                throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(StoredFile file) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredContent> open(String storage, String key) {
            return Optional.empty();
        }

        @Override
        public String url(StoredFile file) {
            return "/x";
        }
    }

    private static Map<String, StorageSettings> defaultsOnly() {
        Map<String, StorageSettings> storages = new LinkedHashMap<>();
        storages.put("default", StorageSettings.defaults());
        return storages;
    }

    private static StorageSettings storage(String name, String type) {
        return new StorageSettings(name, type, "./" + name, UploadLimits.DEFAULT_MAX_BYTES, null);
    }

    private static AdminField upload(String name, UploadSpec spec) {
        return new AdminField(name, name, String.class, AdminField.Kind.TEXT, List.of(), null, false, false, true, false,
                false, false, spec);
    }

    private static AdminField id() {
        return new AdminField("id", "Id", Long.class, AdminField.Kind.NUMBER, List.of(), null, true, false, false, false,
                false, false);
    }

    private static AdminEntityRegistry holder(AdminField... fields) {
        List<AdminField> all = new ArrayList<>();
        all.add(id());
        all.addAll(List.of(fields));
        AdminEntity entity = new AdminEntity(String.class, "Holder", "holder", "Holder", "Entities", "table", "id", null, all);
        return new AdminEntityRegistry(List.of(entity), List.of());
    }

    private static UploadSpec spec(String storage, String folder, String maxSize, String... accept) {
        return new UploadSpec(storage, folder, List.of(accept), maxSize);
    }

    private static UploadSpec defaultSpec() {
        return spec("default", "", "", "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");
    }

    private static UploadStartupCheck.Report run(AdminEntityRegistry registry, Map<String, StorageSettings> storages,
                                                 StorageProvider provider) {
        return UploadStartupCheck.run(registry, storages, provider, OptionalLong.empty(), OptionalLong.empty());
    }

    // ---- defaults pass, and a host without upload fields is not touched ----------------------------------------------------

    @Test
    void aHostWithNoUploadFieldPassesAndTheProviderIsNeverAsked() {
        FakeProvider provider = new FakeProvider();

        UploadStartupCheck.Report report = run(EntityRegistries.noUploads(), defaultsOnly(), provider);

        assertThat(report.ok()).isTrue();
        assertThat(report.problems()).isEmpty();
        assertThat(provider.verified).isEmpty();
        assertThat(report.notes()).isEmpty();
    }

    @Test
    void theDefaultsAloneArePassedByAFieldWithEveryDefault() {
        FakeProvider provider = new FakeProvider();
        provider.locations.put("default", "/srv/app/uploads");

        UploadStartupCheck.Report report = run(holder(upload("photo", defaultSpec())), defaultsOnly(), provider);

        assertThat(report.ok()).isTrue();
        assertThat(provider.verified).containsExactly("default");
        assertThat(report.notes()).hasSize(1);
        assertThat(report.notes().get(0)).contains("'default'", "/srv/app/uploads", "5 MB", "1 upload field");
        assertThat(report.warnings()).isEmpty();
    }

    @Test
    void theFolderOfAStorageIsCheckedOncePerStorageNotOncePerField() {
        FakeProvider provider = new FakeProvider();

        run(holder(upload("one", defaultSpec()), upload("two", defaultSpec()), upload("three", defaultSpec())),
                defaultsOnly(), provider);

        assertThat(provider.verified).containsExactly("default");
    }

    @Test
    void aStorageNoFieldUsesIsNeverVerifiedSoNoFolderIsMade() {
        FakeProvider provider = new FakeProvider();
        Map<String, StorageSettings> storages = defaultsOnly();
        storages.put("spare", storage("spare", "local"));

        UploadStartupCheck.Report report = run(holder(upload("photo", defaultSpec())), storages, provider);

        assertThat(report.ok()).isTrue();
        assertThat(provider.verified).containsExactly("default");
    }

    // ---- a storage that is not configured ------------------------------------------------------------------------------------

    @Test
    void aFieldThatNamesAStorageThatIsNotConfiguredIsListedWithTheWayToFixIt() {
        UploadStartupCheck.Report report = run(holder(upload("scan", spec("vault", "", "", "image/png"))),
                defaultsOnly(), new FakeProvider());

        assertThat(report.ok()).isFalse();
        assertThat(report.problems()).hasSize(1);
        assertThat(report.problems().get(0)).contains("Upload field java.lang.String.scan")
                .contains("'vault'", "not configured", "jpa.admin.storage.vault.directory", "'default'");
    }

    @Test
    void severalOffendingFieldsAreAllListedInOneReport() {
        AdminEntityRegistry registry = holder(upload("a", spec("vault", "", "", "image/png")),
                upload("b", spec("default", "../x", "", "image/png")),
                upload("c", spec("default", "", "lots", "image/png")),
                upload("d", spec("default", "", "", "image/svg+xml")),
                upload("e", defaultSpec()));

        UploadStartupCheck.Report report = run(registry, defaultsOnly(), new FakeProvider());

        assertThat(report.problems()).hasSize(4);
        assertThat(String.join("\n", report.problems())).contains("String.a", "String.b", "String.c", "String.d")
                .doesNotContain("String.e");
    }

    // ---- an unknown type ---------------------------------------------------------------------------------------------------------

    @Test
    void aStorageOfAnUnknownTypeIsReportedWithTheFieldsThatUseIt() {
        Map<String, StorageSettings> storages = defaultsOnly();
        storages.put("cloud", storage("cloud", "s3"));
        FakeProvider provider = new FakeProvider();

        UploadStartupCheck.Report report = run(holder(upload("a", spec("cloud", "", "", "image/png")),
                upload("b", spec("cloud", "", "", "image/png"))), storages, provider);

        assertThat(report.ok()).isFalse();
        assertThat(report.problems()).hasSize(1);
        assertThat(report.problems().get(0)).contains("Storage 'cloud'", "jpa.admin.storage.cloud.type", "only type is 'local'",
                "Used by: java.lang.String.a, java.lang.String.b").doesNotContain("s3");
        assertThat(provider.verified).isEmpty();
    }

    @Test
    void aStorageOfAnUnknownTypeFailsEvenWhenNoFieldUsesIt() {
        Map<String, StorageSettings> storages = defaultsOnly();
        storages.put("cloud", storage("cloud", "s3"));

        UploadStartupCheck.Report report = run(EntityRegistries.noUploads(), storages, new FakeProvider());

        assertThat(report.ok()).isFalse();
        assertThat(report.problems().get(0)).contains("Storage 'cloud'").doesNotContain("Used by");
    }

    @Test
    void aProviderThatDoesNotServeAConfiguredStorageIsAProblem() {
        FakeProvider provider = new FakeProvider();
        provider.serveEverything = false;

        UploadStartupCheck.Report report = run(holder(upload("photo", defaultSpec())), defaultsOnly(), provider);

        assertThat(report.ok()).isFalse();
        assertThat(report.problems().get(0)).contains("does not serve it", "String.photo");
    }

    // ---- a folder that cannot be created or written ---------------------------------------------------------------------------

    @Test
    void anUnwritableFolderIsReportedWithTheFieldsThatNeedIt() {
        FakeProvider provider = new FakeProvider();
        provider.problems.put("default", List.of("The folder /srv/up cannot be created or written to."));

        UploadStartupCheck.Report report = run(holder(upload("a", defaultSpec()), upload("b", defaultSpec())), defaultsOnly(),
                provider);

        assertThat(report.ok()).isFalse();
        assertThat(report.problems()).hasSize(1);
        assertThat(report.problems().get(0)).contains("Storage 'default'", "cannot be created or written to",
                "Used by: java.lang.String.a, java.lang.String.b");
        assertThat(report.notes()).isEmpty();
    }

    // ---- the field itself ----------------------------------------------------------------------------------------------------------

    @Test
    void aFieldThatIsNotAStringIsReported() {
        AdminField number = new AdminField("size", "Size", Integer.class, AdminField.Kind.NUMBER, List.of(), null, false, false,
                true, false, false, false, defaultSpec());

        UploadStartupCheck.Report report = run(holder(number), defaultsOnly(), new FakeProvider());

        assertThat(report.problems()).hasSize(1);
        assertThat(report.problems().get(0)).contains("String.size", "not a String");
    }

    @Test
    void aTypeTheConsoleCannotRecogniseIsNeverAccepted() {
        for (String type : List.of("image/svg+xml", "text/html", "application/x-msdownload", "text/csv", "application/zip")) {
            UploadStartupCheck.Report report = run(holder(upload("f", spec("default", "", "", "image/png", type))),
                    defaultsOnly(), new FakeProvider());

            assertThat(report.ok()).as(type).isFalse();
            assertThat(report.problems().get(0)).contains("cannot recognise", "never accepts");
        }
    }

    @Test
    void anEntryThatIsNotAMimeTypeAndAnEmptyListAreReported() {
        UploadStartupCheck.Report notMime = run(holder(upload("f", spec("default", "", "", "png"))), defaultsOnly(),
                new FakeProvider());
        UploadStartupCheck.Report empty = run(holder(upload("f", new UploadSpec("default", "", List.of(), ""))), defaultsOnly(),
                new FakeProvider());

        assertThat(notMime.problems().get(0)).contains("not a MIME type");
        assertThat(empty.problems().get(0)).contains("accepts no type");
    }

    @Test
    void aBadFolderIsReportedWithAReasonThatDoesNotRepeatIt() {
        UploadStartupCheck.Report report = run(holder(upload("f", spec("default", "../../etc", "", "image/png"))), defaultsOnly(),
                new FakeProvider());

        assertThat(report.problems().get(0)).contains("folder is not usable").doesNotContain("etc");
    }

    @Test
    void aMaxSizeThatIsNotASizeOrIsAboveTheCeilingIsReported() {
        UploadStartupCheck.Report notSize = run(holder(upload("f", spec("default", "", "huge", "image/png"))), defaultsOnly(),
                new FakeProvider());
        UploadStartupCheck.Report tooBig = run(holder(upload("f", spec("default", "", "500MB", "image/png"))), defaultsOnly(),
                new FakeProvider());

        assertThat(notSize.problems().get(0)).contains("maxSize is not a size");
        assertThat(tooBig.problems().get(0)).contains("above the ceiling of 100 MB");
    }

    @Test
    void aStorageNameThatIsNotAWordIsReportedWithoutLookingItUp() {
        UploadStartupCheck.Report report = run(holder(upload("f", spec("../x", "", "", "image/png"))), defaultsOnly(),
                new FakeProvider());

        assertThat(report.problems().get(0)).contains("storage name is not valid").doesNotContain("../x");
    }

    // ---- warnings: limits ---------------------------------------------------------------------------------------------------------

    @Test
    void aFieldAboveTheHostsMultipartLimitGetsAWarningThatNamesTheHostsProperties() {
        UploadStartupCheck.Report report = UploadStartupCheck.run(holder(upload("photo", defaultSpec())), defaultsOnly(),
                new FakeProvider(), OptionalLong.of(1024 * 1024), OptionalLong.of(10 * 1024 * 1024));

        assertThat(report.ok()).isTrue();
        assertThat(report.warnings()).hasSize(1);
        assertThat(report.warnings().get(0)).contains("String.photo", "5 MB", "1 MB", "spring.servlet.multipart.max-file-size",
                "never changes");
    }

    @Test
    void aRequestLimitBelowTheFieldLimitGetsAWarningToo() {
        UploadStartupCheck.Report report = UploadStartupCheck.run(holder(upload("photo", defaultSpec())), defaultsOnly(),
                new FakeProvider(), OptionalLong.of(50L * 1024 * 1024), OptionalLong.of(2 * 1024 * 1024));

        assertThat(report.warnings()).hasSize(1);
        assertThat(report.warnings().get(0)).contains("max-request-size");
    }

    @Test
    void noWarningWhenTheHostAllowsAtLeastAsMuch() {
        UploadStartupCheck.Report report = UploadStartupCheck.run(holder(upload("photo", defaultSpec())), defaultsOnly(),
                new FakeProvider(), OptionalLong.of(5L * 1024 * 1024), OptionalLong.of(5L * 1024 * 1024));

        assertThat(report.warnings()).isEmpty();
    }

    @Test
    void aFieldAskingForMoreThanItsStorageIsToldItsLimitIsTheStoragesNotRaised() {
        UploadStartupCheck.Report report = run(holder(upload("f", spec("default", "", "20MB", "image/png"))), defaultsOnly(),
                new FakeProvider());

        assertThat(report.ok()).isTrue();
        assertThat(report.warnings()).hasSize(1);
        assertThat(report.warnings().get(0)).contains("never raise");
        assertThat(UploadTargets.of(holder(upload("f", spec("default", "", "20MB", "image/png"))), defaultsOnly())
                .find("holder", "f").orElseThrow().maxBytes()).isEqualTo(5L * 1024 * 1024);
    }

    // ---- the real test model, and the targets ---------------------------------------------------------------------------------------

    @Test
    void theRealUploadModelIsReportedByFieldAndTheValidOnesAreTargets() {
        AdminEntityRegistry registry = UploadTestPersistence.registry();

        UploadStartupCheck.Report report = run(registry, defaultsOnly(), new FakeProvider());

        assertThat(report.problems()).hasSize(2);
        String all = String.join("\n", report.problems());
        assertThat(all).contains("Dossier.contract", "'vault'").contains("Dossier.wrongType", "not a String")
                .doesNotContain("Dossier.cover").doesNotContain("Dossier.scan");
        UploadTargets targets = UploadTargets.of(registry, defaultsOnly());
        assertThat(targets.all()).extracting(UploadTargets.Target::field).containsExactlyInAnyOrder("cover", "scan");
        assertThat(targets.find("dossier", "scan").orElseThrow().folder()).isEqualTo("scans");
        assertThat(targets.find("dossier", "contract")).isEmpty();
        assertThat(targets.find("dossier", "title")).isEmpty();
        assertThat(targets.find("nothing", "cover")).isEmpty();
        assertThat(targets.find(null, "cover")).isEmpty();
    }

    @Test
    void withTheVaultConfiguredTheContractFieldBecomesATargetWithItsOwnLimit() {
        Map<String, StorageSettings> storages = defaultsOnly();
        storages.put("vault", storage("vault", "local"));

        UploadTargets targets = UploadTargets.of(UploadTestPersistence.registry(), storages);

        UploadTargets.Target contract = targets.find("dossier", "contract").orElseThrow();
        assertThat(contract.storage()).isEqualTo("vault");
        assertThat(contract.folder()).isEqualTo("docs/scans");
        assertThat(contract.accept()).containsExactly("application/pdf", "image/png");
        assertThat(contract.maxBytes()).isEqualTo(2L * 1024 * 1024);
        assertThat(UploadTargets.none().isEmpty()).isTrue();
    }

    /** Registries without upload fields. */
    private static final class EntityRegistries {

        static AdminEntityRegistry noUploads() {
            return io.github.mdmusahub.jpaadmin.metadata.EntityScanner.scan(TestPersistence.factory().getMetamodel(),
                    io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings.defaults());
        }
    }
}
