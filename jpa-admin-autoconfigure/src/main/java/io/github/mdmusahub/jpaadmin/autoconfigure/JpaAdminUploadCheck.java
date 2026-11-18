package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.storage.StorageProvider;
import io.github.mdmusahub.jpaadmin.storage.StorageSettings;
import io.github.mdmusahub.jpaadmin.storage.UploadStartupCheck;
import io.github.mdmusahub.jpaadmin.storage.UploadTargets;
import jakarta.servlet.MultipartConfigElement;
import java.util.Map;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the upload startup check once, when the application starts (UPL-8). A problem stops startup with one report that lists the
 * offending fields and says why; warnings and the resolved folder of every storage in use are logged once.
 *
 * <p>Created {@code @Lazy(false)}, like the password guard: nothing injects it, so a lazy bean would never run and the check would
 * be skipped silently.
 *
 * <p>Nothing here prints a secret. The absolute folder of a storage is not one, and is logged at INFO so an operator can find
 * the files (and back them up).
 */
final class JpaAdminUploadCheck {

    private static final Logger log = LoggerFactory.getLogger(JpaAdminUploadCheck.class);

    JpaAdminUploadCheck(AdminEntityRegistry registry, JpaAdminProperties properties, StorageProvider provider,
                        MultipartConfigElement multipart) {
        Map<String, StorageSettings> storages = properties.storageSettings();
        UploadStartupCheck.Report report = UploadStartupCheck.run(registry, storages, provider,
                limit(multipart == null ? -1 : multipart.getMaxFileSize()),
                limit(multipart == null ? -1 : multipart.getMaxRequestSize()));
        if (!report.ok()) {
            throw new JpaAdminConfigurationException(report.problems());
        }
        report.notes().forEach(note -> log.info(note));
        report.warnings().forEach(warning -> log.warn(warning));
        if (!UploadTargets.of(registry, storages).isEmpty() && properties.signingKey() == null) {
            log.warn("No upload signing key is set (jpa.admin.upload.signing-key), so a random one is made at every start. "
                    + "Upload forms that are open when the application restarts, or that were opened on another instance, "
                    + "will be refused until the form is opened again. Set the property to a secret of 32 characters or more "
                    + "when you run several instances.");
        }
    }

    /** The servlet API says -1 for "no limit"; that is not a limit to compare with. */
    private static OptionalLong limit(long value) {
        return value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
    }
}
