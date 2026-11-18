package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.mdmusahub.jpaadmin.autoconfigure.testmodel.Gizmo;
import io.github.mdmusahub.jpaadmin.autoconfigure.uploadmodel.bad.Parchment;
import io.github.mdmusahub.jpaadmin.autoconfigure.uploadmodel.good.Portrait;
import io.github.mdmusahub.jpaadmin.storage.LocalStorageProvider;
import io.github.mdmusahub.jpaadmin.storage.StorageProvider;
import io.github.mdmusahub.jpaadmin.storage.StoredContent;
import io.github.mdmusahub.jpaadmin.storage.StoredFile;
import jakarta.servlet.MultipartConfigElement;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The upload startup check on real Spring contexts with Boot 4.1's own auto-configurations and a real Hibernate factory (UPL-8):
 * unknown storage, unknown type, an unusable folder, several offenders in one report, the defaults passing, the absolute folder
 * logged once, a host without upload fields left alone, and a replaceable provider.
 */
class UploadStartupTest {

    @TempDir
    Path temp;

    private ListAppender<ILoggingEvent> logged;
    private Logger checkLog;

    @BeforeEach
    void captureLog() {
        checkLog = (Logger) LoggerFactory.getLogger(JpaAdminUploadCheck.class);
        logged = new ListAppender<>();
        logged.start();
        checkLog.addAppender(logged);
        checkLog.setLevel(Level.INFO);
    }

    @AfterEach
    void releaseLog() {
        checkLog.detachAppender(logged);
    }

    @EntityScan(basePackageClasses = Portrait.class)
    @Configuration(proxyBeanMethods = false)
    static class GoodEntities {
    }

    @EntityScan(basePackageClasses = Parchment.class)
    @Configuration(proxyBeanMethods = false)
    static class BadEntities {
    }

    @EntityScan(basePackageClasses = Gizmo.class)
    @Configuration(proxyBeanMethods = false)
    static class NoUploadEntities {
    }

    private static int databases;

    private WebApplicationContextRunner host(Class<?> entities, String... properties) {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class, JpaAdminAutoConfiguration.class,
                        DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
                        HibernateJpaAutoConfiguration.class))
                .withPropertyValues("jpa.admin.username=console-admin", "jpa.admin.password=console-pw-Qx7-not-a-real-secret",
                        "spring.jpa.hibernate.ddl-auto=create-drop",
                        "spring.datasource.url=jdbc:h2:mem:upload-startup-" + (databases++) + ";DB_CLOSE_DELAY=-1")
                .withPropertyValues(properties).withUserConfiguration(entities);
    }

    private String dir(String name) {
        return "jpa.admin.storage.default.directory=" + temp.resolve(name).toString().replace('\\', '/');
    }

    private List<String> infoLines() {
        return logged.list.stream().filter(event -> event.getLevel() == Level.INFO).map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private List<String> warnLines() {
        return logged.list.stream().filter(event -> event.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    // ---- passes ----------------------------------------------------------------------------------------------------------------

    @Test
    void defaultsAlonePassAndTheFolderIsCreatedAndLoggedOnce() throws IOException {
        Path defaultFolder = Path.of("uploads").toAbsolutePath().normalize();
        boolean existed = Files.exists(defaultFolder);
        try {
            host(GoodEntities.class).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(StorageProvider.class);
                assertThat(context.getBean(StorageProvider.class)).isInstanceOf(LocalStorageProvider.class);
            });

            assertThat(defaultFolder).isDirectory();
            assertThat(infoLines()).hasSize(1);
            assertThat(infoLines().get(0)).contains("'default'", defaultFolder.toString(), "5 MB", "1 upload field");
        } finally {
            if (!existed) {
                deleteTree(defaultFolder);
            }
        }
    }

    @Test
    void theAbsoluteConfiguredFolderIsLoggedExactlyOnceAndCreated() {
        host(GoodEntities.class, dir("up")).run(context -> assertThat(context).hasNotFailed());

        Path expected = temp.resolve("up").toAbsolutePath().normalize();
        assertThat(expected).isDirectory();
        assertThat(infoLines()).hasSize(1);
        assertThat(infoLines().get(0)).contains(expected.toString());
    }

    @Test
    void aHostWithNoUploadFieldPassesAndGetsNoFolder() {
        host(NoUploadEntities.class, dir("never")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StorageProvider.class);
        });

        assertThat(temp.resolve("never")).doesNotExist();
        assertThat(infoLines()).isEmpty();
        assertThat(warnLines()).isEmpty();
    }

    // ---- fails ------------------------------------------------------------------------------------------------------------------

    @Test
    void severalOffendingFieldsAreListedInOneStartupFailure() {
        host(BadEntities.class, dir("up")).run(context -> {
            assertThat(context).hasFailed();
            Throwable cause = context.getStartupFailure();
            while (cause.getCause() != null && !(cause instanceof JpaAdminConfigurationException)) {
                cause = cause.getCause();
            }
            assertThat(cause).isInstanceOf(JpaAdminConfigurationException.class);
            JpaAdminConfigurationException failure = (JpaAdminConfigurationException) cause;
            assertThat(failure.problems()).hasSize(2);
            String all = String.join("\n", failure.problems());
            assertThat(all).contains("Parchment.contract", "'vault'", "not configured").contains("Parchment.broken", "not a String")
                    .doesNotContain("Parchment.cover");
        });
    }

    @Test
    void aStorageOfAnUnknownTypeStopsStartupAndListsTheFieldsThatUseIt() {
        host(BadEntities.class, dir("up"), "jpa.admin.storage.vault.type=s3").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().rootCause().isInstanceOf(JpaAdminConfigurationException.class)
                    .hasMessageContaining("Storage 'vault'").hasMessageContaining("jpa.admin.storage.vault.type")
                    .hasMessageContaining("Used by: " + Parchment.class.getName() + ".contract");
        });
    }

    @Test
    void aFolderThatCannotBeCreatedStopsStartupAndNamesTheProperty() throws IOException {
        Path file = temp.resolve("a-file");
        Files.writeString(file, "x");

        host(GoodEntities.class, "jpa.admin.storage.default.directory=" + file.toString().replace('\\', '/') + "/below")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().rootCause().isInstanceOf(JpaAdminConfigurationException.class)
                            .hasMessageContaining("cannot be created").hasMessageContaining("jpa.admin.storage.default.directory")
                            .hasMessageContaining(Portrait.class.getName() + ".photo");
                });
    }

    @Test
    void aFolderInsideTheWebRootStopsStartup() {
        Path classes = Path.of("target/classes/uploads-test").toAbsolutePath().normalize();

        host(GoodEntities.class, "jpa.admin.storage.default.directory=" + classes.toString().replace('\\', '/')).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().rootCause().hasMessageContaining("inside the web root");
        });

        assertThat(classes).doesNotExist();
    }

    // ---- warnings -----------------------------------------------------------------------------------------------------------------

    @Test
    void aMissingSigningKeyIsWarnedAboutOnlyWhenAnUploadFieldExists() {
        host(GoodEntities.class, dir("up")).run(context -> assertThat(context).hasNotFailed());
        assertThat(warnLines()).anyMatch(line -> line.contains("jpa.admin.upload.signing-key") && line.contains("restart"));
        logged.list.clear();

        host(GoodEntities.class, dir("up2"), "jpa.admin.upload.signing-key=0123456789-abcdefghijklmnopqrstuvwxyz-KEY")
                .run(context -> assertThat(context).hasNotFailed());
        assertThat(warnLines()).isEmpty();
        logged.list.clear();

        host(NoUploadEntities.class, dir("up3")).run(context -> assertThat(context).hasNotFailed());
        assertThat(warnLines()).isEmpty();
    }

    @Test
    void theWarningNeverPrintsTheKey() {
        String key = "0123456789-abcdefghijklmnopqrstuvwxyz-KEY";
        host(GoodEntities.class, dir("up"), "jpa.admin.upload.signing-key=" + key).run(context -> assertThat(context).hasNotFailed());

        assertThat(logged.list).noneMatch(event -> event.getFormattedMessage().contains(key));
    }

    @Test
    void aHostMultipartLimitBelowTheFieldLimitIsWarnedAboutAndNeverChanged() {
        host(GoodEntities.class, dir("up")).withBean("hostMultipart", MultipartConfigElement.class,
                () -> new MultipartConfigElement("", 1024 * 1024, 10 * 1024 * 1024, 0)).run(context -> {
                    assertThat(context).hasNotFailed();
                    MultipartConfigElement host = context.getBean(MultipartConfigElement.class);
                    assertThat(host.getMaxFileSize()).isEqualTo(1024 * 1024);
                    assertThat(host.getMaxRequestSize()).isEqualTo(10 * 1024 * 1024);
                });

        assertThat(warnLines()).anyMatch(line -> line.contains("spring.servlet.multipart.max-file-size")
                && line.contains("never changes"));
    }

    // ---- replaceable provider ------------------------------------------------------------------------------------------------------

    @Configuration(proxyBeanMethods = false)
    static class OwnProvider {

        @Bean
        StorageProvider hostStorageProvider() {
            return new StorageProvider() {
                @Override
                public boolean supports(String storage) {
                    return true;
                }

                @Override
                public StoredFile store(String storage, String folder, String extension, InputStream content, long maxBytes) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void delete(StoredFile file) {
                }

                @Override
                public Optional<StoredContent> open(String storage, String key) {
                    return Optional.empty();
                }

                @Override
                public String url(StoredFile file) {
                    return "/host/" + file.key();
                }
            };
        }
    }

    @Test
    void aHostCanReplaceTheProviderAndTheConsoleUsesIt() {
        host(GoodEntities.class, dir("up")).withUserConfiguration(OwnProvider.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StorageProvider.class);
            assertThat(context).hasBean("hostStorageProvider");
            assertThat(context.getBean(StorageProvider.class)).isNotInstanceOf(LocalStorageProvider.class);
        });

        // The host's provider did not ask the console to create the local folder.
        assertThat(temp.resolve("up")).doesNotExist();
    }

    private static void deleteTree(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
