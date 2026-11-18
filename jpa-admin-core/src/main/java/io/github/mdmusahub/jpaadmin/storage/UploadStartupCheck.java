package io.github.mdmusahub.jpaadmin.storage;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The upload startup check (UPL-8). It reads the registry and the configured storages and answers, in one pass, everything that
 * would otherwise fail later and confusingly: a field that names a storage that is not configured, a storage of a type this
 * version does not have, a field that is not a {@code String}, a folder or a type or a size that makes no sense, and a storage
 * whose folder cannot be created or written to.
 *
 * <p>The report lists every offending field (or storage) with the reason, so the operator fixes them in one pass. A host
 * with no upload field passes without touching the disk, and a host that only uses the defaults passes too: the {@code default}
 * storage exists without a property. The folder of a storage is checked, and created, only when an upload field names that
 * storage.
 *
 * <p>This class is a pure function of its arguments (the provider does the file work), so every case is tested without a
 * running application. It names fields as {@code package.Class.field}; a class name is not a secret, and no entity name is
 * hard-coded here (ARCH-12).
 *
 * <p>Internal, not stable API. Public because the auto-configuration module runs it.
 */
public final class UploadStartupCheck {

    /**
     * What the check found.
     *
     * @param problems one sentence per problem; any problem stops startup
     * @param warnings things worth knowing that do not stop startup
     * @param notes    plain facts for the log, for example where a storage keeps its files
     */
    public record Report(List<String> problems, List<String> warnings, List<String> notes) {

        public Report {
            problems = List.copyOf(problems);
            warnings = List.copyOf(warnings);
            notes = List.copyOf(notes);
        }

        /** Whether startup may go on. */
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private UploadStartupCheck() {
    }

    /**
     * Runs the check.
     *
     * @param registry         the console's entities
     * @param storages         the configured storages by name ({@code default} included)
     * @param provider         the storage provider
     * @param hostMaxFileSize  the host's multipart limit for one file, or empty when it is not known or not limited
     * @param hostMaxRequest   the host's multipart limit for a whole request, or empty
     */
    public static Report run(AdminEntityRegistry registry, Map<String, StorageSettings> storages, StorageProvider provider,
                             OptionalLong hostMaxFileSize, OptionalLong hostMaxRequest) {
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Map<String, Set<String>> fieldsByStorage = new LinkedHashMap<>();

        for (AdminEntity entity : registry.all()) {
            for (AdminField field : entity.visibleFields()) {
                if (!field.isUpload()) {
                    continue;
                }
                String name = entity.className() + "." + field.name();
                fieldsByStorage.computeIfAbsent(field.upload().storage(), key -> new LinkedHashSet<>()).add(name);
                List<String> own = UploadTargets.problems(field, field.upload(), storages);
                for (String problem : own) {
                    problems.add("Upload field " + name + ": " + problem + ".");
                }
                if (own.isEmpty()) {
                    limitWarnings(name, field, storages, hostMaxFileSize, hostMaxRequest, warnings);
                }
            }
        }

        for (Map.Entry<String, StorageSettings> entry : storages.entrySet()) {
            StorageSettings storage = entry.getValue();
            Set<String> users = fieldsByStorage.getOrDefault(entry.getKey(), Set.of());
            if (!storage.supportedType()) {
                problems.add("Storage '" + storage.name() + "' (jpa.admin.storage." + storage.name() + ".type) has a type that "
                        + "this version does not support. The only type is 'local'; S3 is planned for v0.2."
                        + usedBy(users));
                continue;
            }
            if (users.isEmpty()) {
                continue;
            }
            if (!provider.supports(storage.name())) {
                problems.add("Storage '" + storage.name() + "' is configured, but the storage provider in use does not serve it."
                        + usedBy(users));
                continue;
            }
            List<String> storageProblems = provider.verify(storage.name());
            for (String problem : storageProblems) {
                problems.add("Storage '" + storage.name() + "': " + problem + usedBy(users));
            }
            if (storageProblems.isEmpty()) {
                String location = provider.location(storage.name());
                notes.add("File uploads: storage '" + storage.name() + "' keeps its files in "
                        + (location == null ? "its own place" : location) + " (largest file "
                        + UploadLimits.describe(storage.maxBytes()) + ", used by " + users.size() + " upload field"
                        + (users.size() == 1 ? "" : "s") + ").");
            }
        }
        return new Report(problems, warnings, notes);
    }

    private static String usedBy(Set<String> users) {
        return users.isEmpty() ? "" : " Used by: " + String.join(", ", users) + ".";
    }

    private static void limitWarnings(String name, AdminField field, Map<String, StorageSettings> storages,
                                      OptionalLong hostFile, OptionalLong hostRequest, List<String> warnings) {
        StorageSettings storage = storages.get(field.upload().storage());
        long wanted = field.upload().maxSize().isEmpty() ? storage.maxBytes()
                : UploadLimits.parseSize(field.upload().maxSize()).orElse(storage.maxBytes());
        if (wanted > storage.maxBytes()) {
            warnings.add("Upload field " + name + " asks for a larger file than its storage allows; the storage's limit of "
                    + UploadLimits.describe(storage.maxBytes()) + " applies (a field may lower a limit, never raise it).");
        }
        long effective = Math.min(wanted, storage.maxBytes());
        if (hostFile.isPresent() && effective > hostFile.getAsLong()) {
            warnings.add("Upload field " + name + " allows files up to " + UploadLimits.describe(effective) + ", but the "
                    + "host's multipart limit for one file is " + UploadLimits.describe(hostFile.getAsLong())
                    + ", and that limit applies first. To accept larger files raise spring.servlet.multipart.max-file-size "
                    + "and spring.servlet.multipart.max-request-size in the host (the console never changes them).");
        } else if (hostRequest.isPresent() && effective > hostRequest.getAsLong()) {
            warnings.add("Upload field " + name + " allows files up to " + UploadLimits.describe(effective) + ", but the "
                    + "host's multipart limit for a request is " + UploadLimits.describe(hostRequest.getAsLong())
                    + ", and that limit applies first. To accept larger files raise "
                    + "spring.servlet.multipart.max-request-size in the host (the console never changes it).");
        }
    }
}
