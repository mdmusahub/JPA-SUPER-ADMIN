package io.github.mdmusahub.jpaadmin.storage;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.metadata.UploadSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * Every upload field of the console, resolved once at startup against the configured storages: which storage, which folder,
 * which types, and the largest file (ARCH-1). The upload service and the startup check both read this class, so the answer to
 * "may this field take this file" cannot differ between them. A field whose declaration is wrong is not a target; the startup
 * check reports it and stops the application, so it never reaches the upload code.
 *
 * <p>Internal, not stable API. Public because the web package and the auto-configuration read it.
 */
public final class UploadTargets {

    private static final Pattern MIME = Pattern.compile("[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*");

    /**
     * One upload field, resolved.
     *
     * @param entity   the entity's address name ({@code AdminEntity#urlName})
     * @param field    the attribute name
     * @param storage  the storage's name
     * @param folder   the folder inside the storage, possibly empty
     * @param accept   the accepted MIME types (all recognisable by {@link FileSignatures})
     * @param maxBytes the largest file: the field's own limit or the storage's, never above the storage's
     */
    public record Target(String entity, String field, String storage, String folder, List<String> accept, long maxBytes) {

        public Target {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(folder, "folder");
            accept = List.copyOf(accept);
        }
    }

    private final Map<String, Target> byKey;
    private final List<Target> all;

    private UploadTargets(List<Target> targets) {
        this.all = List.copyOf(targets);
        Map<String, Target> index = new LinkedHashMap<>();
        for (Target target : targets) {
            index.put(key(target.entity(), target.field()), target);
        }
        this.byKey = Map.copyOf(index);
    }

    /** No upload field at all. */
    public static UploadTargets none() {
        return new UploadTargets(List.of());
    }

    /**
     * Resolves every valid upload field of the registry's entities. A hidden field is not an upload field for the console.
     *
     * @param storages the configured storages by name (the {@code default} one included)
     */
    public static UploadTargets of(AdminEntityRegistry registry, Map<String, StorageSettings> storages) {
        List<Target> targets = new ArrayList<>();
        for (AdminEntity entity : registry.all()) {
            for (AdminField field : entity.visibleFields()) {
                if (field.isUpload() && problems(field, field.upload(), storages).isEmpty()) {
                    targets.add(resolve(entity, field, storages));
                }
            }
        }
        return new UploadTargets(targets);
    }

    /** The target for an entity address and a field name, or empty when there is no such upload field. */
    public Optional<Target> find(String entity, String field) {
        if (entity == null || field == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(key(entity, field)));
    }

    /** Every target, in the order of the registry. */
    public List<Target> all() {
        return all;
    }

    /** Whether the console has any upload field. */
    public boolean isEmpty() {
        return all.isEmpty();
    }

    private static String key(String entity, String field) {
        return entity + "\u0000" + field;
    }

    private static Target resolve(AdminEntity entity, AdminField field, Map<String, StorageSettings> storages) {
        UploadSpec spec = field.upload();
        StorageSettings storage = storages.get(spec.storage());
        long own = spec.maxSize().isEmpty() ? storage.maxBytes() : UploadLimits.parseSize(spec.maxSize()).orElseThrow();
        long limit = Math.min(Math.min(own, storage.maxBytes()), UploadLimits.CEILING_BYTES);
        return new Target(entity.urlName(), field.name(), spec.storage(), spec.folder(), spec.accept(), limit);
    }

    /**
     * Why an upload field cannot be used, one sentence each (empty when it can). The sentences name the declaration, never a
     * value a visitor typed, and they are what the startup check prints.
     */
    static List<String> problems(AdminField field, UploadSpec spec, Map<String, StorageSettings> storages) {
        List<String> problems = new ArrayList<>();
        if (field.javaType() != String.class) {
            problems.add("it is not a String, so it cannot hold the address of a file. Declare it as a String");
        }
        if (!StoragePaths.validStorageName(spec.storage())) {
            problems.add("the storage name is not valid (a lower-case word of letters, digits and '-')");
        } else if (!storages.containsKey(spec.storage())) {
            problems.add("it names the storage '" + spec.storage() + "', which is not configured. Configure it with "
                    + "jpa.admin.storage." + spec.storage() + ".directory, or use the storage 'default'");
        }
        StoragePaths.folderProblem(spec.folder()).ifPresent(problem -> problems.add("its folder is not usable: " + problem));
        if (spec.accept().isEmpty()) {
            problems.add("it accepts no type. List at least one in accept, or leave accept out for the defaults");
        }
        for (String type : spec.accept()) {
            if (!MIME.matcher(type).matches()) {
                problems.add("accept holds an entry that is not a MIME type (like image/png)");
            } else if (FileSignatures.forMime(type).isEmpty()) {
                problems.add("accept lists '" + type + "', which the console cannot recognise by its content and so never "
                        + "accepts (SVG and HTML are never accepted, because they can carry script). The types it can "
                        + "recognise are " + String.join(", ", FileSignatures.knownTypes()));
            }
        }
        if (!spec.maxSize().isEmpty()) {
            OptionalLong size = UploadLimits.parseSize(spec.maxSize());
            if (size.isEmpty()) {
                problems.add("maxSize is not a size. Write it like 512KB or 2MB");
            } else if (size.getAsLong() > UploadLimits.CEILING_BYTES) {
                problems.add("maxSize is above the ceiling of " + UploadLimits.describe(UploadLimits.CEILING_BYTES));
            }
        }
        return problems;
    }
}
