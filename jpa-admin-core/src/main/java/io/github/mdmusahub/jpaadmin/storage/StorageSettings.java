package io.github.mdmusahub.jpaadmin.storage;

import java.util.Objects;

/**
 * One storage as configured, handed over by the auto-configuration so this module does not have to know the property
 * names. The values were already checked by the property validation (a name, a size within the ceiling, a path shape); the
 * startup check looks at what only the file system can answer.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module builds it.
 *
 * @param name       the storage's name as an {@code @AdminUpload} field writes it (lower case)
 * @param type       the provider type as configured, lower case; only {@value #LOCAL} is supported in v0.1
 * @param directory  the folder of a local storage as configured (may be relative to the working directory)
 * @param maxBytes   the largest file of this storage, within {@link UploadLimits#CEILING_BYTES}
 * @param publicPath the path at which the files are also served without a session, or {@code null} (the default)
 */
public record StorageSettings(String name, String type, String directory, long maxBytes, String publicPath) {

    /** The only provider type of v0.1. */
    public static final String LOCAL = "local";

    public StorageSettings {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(directory, "directory");
        if (maxBytes <= 0 || maxBytes > UploadLimits.CEILING_BYTES) {
            throw new IllegalArgumentException("maxBytes must be between 1 and the ceiling");
        }
    }

    /** The storage that exists without any property. */
    public static StorageSettings defaults() {
        return new StorageSettings(UploadLimits.DEFAULT_STORAGE, LOCAL, UploadLimits.DEFAULT_DIRECTORY,
                UploadLimits.DEFAULT_MAX_BYTES, null);
    }

    /** Whether the type is one this version can serve. */
    public boolean supportedType() {
        return LOCAL.equals(type);
    }
}
