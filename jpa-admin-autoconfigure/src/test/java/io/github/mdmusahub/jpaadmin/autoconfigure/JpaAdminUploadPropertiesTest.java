package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.storage.StorageSettings;
import io.github.mdmusahub.jpaadmin.storage.UploadLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * The upload properties (Phase 9a): the documented defaults, binding from real property sources, and a bad value stops
 * startup with a message that names the property and never prints the value (SEC-6).
 */
class JpaAdminUploadPropertiesTest {

    private static final String PASSWORD = "S3cret-test-value-NOT-REAL";
    private static final String KEY = "0123456789-abcdefghijklmnopqrstuvwxyz-KEY";

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Properties.class)
            .withPropertyValues("jpa.admin.username=admin", "jpa.admin.password=" + PASSWORD);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JpaAdminProperties.class)
    static class Properties {
    }

    private static JpaAdminProperties valid() {
        JpaAdminProperties properties = new JpaAdminProperties();
        properties.setUsername("admin");
        properties.setPassword(PASSWORD);
        return properties;
    }

    private static String problemsOf(JpaAdminProperties properties) {
        return assertThatThrownBy(properties::validate).isInstanceOf(JpaAdminConfigurationException.class)
                .actual().getMessage();
    }

    // ---- defaults -----------------------------------------------------------------------------------------------------

    @Test
    void theDefaultStorageExistsWithoutAnyProperty() {
        JpaAdminProperties properties = valid();

        assertThat(properties.getStorage()).isEmpty();
        assertThat(properties.storageSettings()).containsOnlyKeys("default");
        StorageSettings storage = properties.storageSettings().get("default");
        assertThat(storage.type()).isEqualTo("local");
        assertThat(storage.directory()).isEqualTo("./uploads");
        assertThat(storage.maxBytes()).isEqualTo(5L * 1024 * 1024);
        assertThat(storage.publicPath()).isNull();
        assertThat(properties.signingKey()).isNull();
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void aStorageClassHasTheDocumentedDefaults() {
        JpaAdminProperties.Storage storage = new JpaAdminProperties.Storage();

        assertThat(storage.getType()).isEqualTo("local");
        assertThat(storage.getDirectory()).isEqualTo("./uploads");
        assertThat(storage.getMaxSize().toBytes()).isEqualTo(5L * 1024 * 1024);
        assertThat(storage.getPublicPath()).isNull();
    }

    // ---- binding ------------------------------------------------------------------------------------------------------

    @Test
    void bindsTheDefaultStorageAndTheSigningKeyAndAnotherStorage() {
        runner.withPropertyValues("jpa.admin.storage.default.directory=/var/data/up", "jpa.admin.storage.default.max-size=2MB",
                "jpa.admin.storage.default.public-path=/files", "jpa.admin.storage.vault.directory=./vault",
                "jpa.admin.storage.vault.type=LOCAL", "jpa.admin.upload.signing-key=" + KEY).run(context -> {
                    assertThat(context).hasNotFailed();
                    JpaAdminProperties properties = context.getBean(JpaAdminProperties.class);
                    StorageSettings standard = properties.storageSettings().get("default");
                    assertThat(standard.directory()).isEqualTo("/var/data/up");
                    assertThat(standard.maxBytes()).isEqualTo(2L * 1024 * 1024);
                    assertThat(standard.publicPath()).isEqualTo("/files");
                    StorageSettings vault = properties.storageSettings().get("vault");
                    assertThat(vault.type()).isEqualTo("local");
                    assertThat(vault.directory()).isEqualTo("./vault");
                    assertThat(vault.maxBytes()).isEqualTo(UploadLimits.DEFAULT_MAX_BYTES);
                    assertThat(properties.signingKey()).isEqualTo(KEY);
                });
    }

    @Test
    void aBlankPublicPathIsTheSameAsNone() {
        JpaAdminProperties properties = valid();
        JpaAdminProperties.Storage storage = new JpaAdminProperties.Storage();
        storage.setPublicPath("  ");
        properties.getStorage().put("default", storage);

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.storageSettings().get("default").publicPath()).isNull();
    }

    // ---- bad values stop startup and are named, never printed --------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"jpa.admin.storage.default.max-size=0", "jpa.admin.storage.default.max-size=101MB",
            "jpa.admin.storage.default.max-size=1GB"})
    void aMaxSizeOutsideTheCeilingStopsStartupNamingTheProperty(String setting) {
        runner.withPropertyValues(setting).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().rootCause().isInstanceOf(JpaAdminConfigurationException.class)
                    .hasMessageContaining("jpa.admin.storage.default.max-size");
        });
    }

    @Test
    void theCeilingItselfIsAccepted() {
        JpaAdminProperties properties = valid();
        JpaAdminProperties.Storage storage = new JpaAdminProperties.Storage();
        storage.setMaxSize(org.springframework.util.unit.DataSize.ofMegabytes(100));
        properties.getStorage().put("default", storage);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void aBlankOrBrokenDirectoryStopsStartupWithoutPrintingIt() {
        JpaAdminProperties properties = valid();
        JpaAdminProperties.Storage blank = new JpaAdminProperties.Storage();
        blank.setDirectory(" ");
        JpaAdminProperties.Storage broken = new JpaAdminProperties.Storage();
        broken.setDirectory("bad\u0000dir");
        properties.getStorage().put("default", blank);
        properties.getStorage().put("vault", broken);

        String message = problemsOf(properties);

        assertThat(message).contains("jpa.admin.storage.default.directory", "jpa.admin.storage.vault.directory")
                .doesNotContain("bad");
    }

    @Test
    void aTypeThatIsNotAPlainWordStopsStartupAndAnUnknownWordIsLeftToTheStartupCheck() {
        JpaAdminProperties properties = valid();
        JpaAdminProperties.Storage weird = new JpaAdminProperties.Storage();
        weird.setType("lo cal/../");
        JpaAdminProperties.Storage cloud = new JpaAdminProperties.Storage();
        cloud.setType("s3");
        properties.getStorage().put("weird", weird);
        properties.getStorage().put("cloud", cloud);

        String message = problemsOf(properties);

        assertThat(message).contains("jpa.admin.storage.weird.type").doesNotContain("jpa.admin.storage.cloud.type")
                .doesNotContain("lo cal");
        assertThat(properties.storageSettings().get("cloud").supportedType()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Default", "1x", "a b", "a/b", "..", "A", "with.dot"})
    void aStorageNameThatIsNotAWordStopsStartupWithoutPrintingIt(String name) {
        JpaAdminProperties properties = valid();
        properties.getStorage().put(name, new JpaAdminProperties.Storage());

        String message = problemsOf(properties);

        assertThat(message).contains("jpa.admin.storage has a storage name that is not valid");
        assertThat(message).doesNotContain("'" + name + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"relative-path", "/", "/files/", "/a b", "/..", "/a//b", "/files?x", "https://x.example/f", "//x",
            "/Filés"})
    void aBadPublicPathStopsStartupNamingThePropertyOnly(String path) {
        JpaAdminProperties properties = valid();
        JpaAdminProperties.Storage storage = new JpaAdminProperties.Storage();
        storage.setPublicPath(path);
        properties.getStorage().put("default", storage);

        String message = problemsOf(properties);

        assertThat(message).contains("jpa.admin.storage.default.public-path");
        // A one or two character input ("/") is a substring of any sentence, so only longer inputs can be checked.
        if (path.length() >= 3) {
            assertThat(message).doesNotContain(path);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/files", "/"})
    void aPublicPathInsideOrAtTheConsolePathIsRefused(String path) {
        JpaAdminProperties properties = valid();
        JpaAdminProperties.Storage storage = new JpaAdminProperties.Storage();
        storage.setPublicPath(path);
        properties.getStorage().put("default", storage);

        assertThat(problemsOf(properties)).contains("jpa.admin.storage.default.public-path");
    }

    @Test
    void aPublicPathThatContainsTheConsolePathIsRefusedToo() {
        JpaAdminProperties properties = valid();
        properties.setPath("/tools/admin");
        JpaAdminProperties.Storage storage = new JpaAdminProperties.Storage();
        storage.setPublicPath("/tools");
        properties.getStorage().put("default", storage);

        assertThat(problemsOf(properties)).contains("jpa.admin.storage.default.public-path");
    }

    @Test
    void twoStoragesCannotShareAPublicPathOrOverlap() {
        JpaAdminProperties properties = valid();
        JpaAdminProperties.Storage one = new JpaAdminProperties.Storage();
        one.setPublicPath("/files");
        JpaAdminProperties.Storage two = new JpaAdminProperties.Storage();
        two.setPublicPath("/files/more");
        properties.getStorage().put("default", one);
        properties.getStorage().put("vault", two);

        assertThat(problemsOf(properties)).contains("jpa.admin.storage.vault.public-path");
    }

    @Test
    void aPathThatOnlySharesAPrefixOfLettersIsNotAnOverlap() {
        JpaAdminProperties properties = valid();
        JpaAdminProperties.Storage storage = new JpaAdminProperties.Storage();
        storage.setPublicPath("/administrator");
        properties.getStorage().put("default", storage);

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(JpaAdminProperties.overlaps("/a", "/ab")).isFalse();
        assertThat(JpaAdminProperties.overlaps("/a", "/a/b")).isTrue();
        assertThat(JpaAdminProperties.overlaps("/a/b", "/a")).isTrue();
        assertThat(JpaAdminProperties.overlaps("/a", "/a")).isTrue();
    }

    @Test
    void aShortSigningKeyStopsStartupAndIsNeverPrinted() {
        JpaAdminProperties properties = valid();
        properties.getUpload().setSigningKey("short-key-value");

        String message = problemsOf(properties);

        assertThat(message).contains("jpa.admin.upload.signing-key").doesNotContain("short-key-value");
    }

    @Test
    void aSigningKeyThatIsAnUnsetPlaceholderStopsStartup() {
        JpaAdminProperties properties = valid();
        properties.getUpload().setSigningKey("${UPLOAD_SIGNING_KEY}");

        assertThat(problemsOf(properties)).contains("jpa.admin.upload.signing-key").contains("no value");
    }

    @Test
    void aLongEnoughKeyIsAcceptedAndABlankOneMeansNone() {
        JpaAdminProperties properties = valid();
        properties.getUpload().setSigningKey(KEY);
        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.signingKey()).isEqualTo(KEY);

        properties.getUpload().setSigningKey("   ");
        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.signingKey()).isNull();
    }

    @Test
    void toStringNeverShowsTheKeyOrAnyUploadValue() {
        JpaAdminProperties properties = valid();
        properties.getUpload().setSigningKey(KEY);

        assertThat(properties.toString()).doesNotContain(KEY).doesNotContain(PASSWORD);
    }

    @Test
    void allProblemsAreReportedTogether() {
        JpaAdminProperties properties = valid();
        JpaAdminProperties.Storage storage = new JpaAdminProperties.Storage();
        storage.setDirectory("");
        storage.setPublicPath("nope");
        properties.getStorage().put("default", storage);
        properties.getUpload().setSigningKey("tiny");

        assertThat(problemsOf(properties)).contains("storage.default.directory", "storage.default.public-path",
                "upload.signing-key");
    }
}
