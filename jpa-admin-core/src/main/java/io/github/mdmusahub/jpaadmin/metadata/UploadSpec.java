package io.github.mdmusahub.jpaadmin.metadata;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * What {@code @AdminUpload} said about one field, read once at startup (ARCH-1). The values are kept as written (the types
 * lower-cased and stripped); whether they make sense is decided by the startup check, which names the field when they do not,
 * so a wrong value never crashes the scanner.
 *
 * <p>Internal, not stable API. Public because the storage package and the auto-configuration read it.
 *
 * @param storage name of the storage, never blank
 * @param folder  sub-folder as written, possibly empty
 * @param accept  the accepted MIME types, lower case, without duplicates, in the order written
 * @param maxSize the field's size limit as written ({@code "2MB"}), or empty for the storage's limit
 */
public record UploadSpec(String storage, String folder, List<String> accept, String maxSize) {

    public UploadSpec {
        storage = Objects.requireNonNull(storage, "storage").strip();
        folder = Objects.requireNonNull(folder, "folder").strip();
        maxSize = Objects.requireNonNull(maxSize, "maxSize").strip();
        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (String type : Objects.requireNonNull(accept, "accept")) {
            types.add(Objects.requireNonNull(type, "accept entry").strip().toLowerCase(Locale.ROOT));
        }
        accept = List.copyOf(types);
    }
}
