package io.github.mdmusahub.jpaadmin.web.view;

import java.util.Objects;

/**
 * JSON body of the upload endpoint. Either a success (url and name set, no error) or a failure (error set, no url
 * and no name). The constructor rejects anything else.
 *
 * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
 *
 * @param url   link to the stored file, {@code null} on failure
 * @param name  original file name for display only, never used as a path (UPL-3), {@code null} on failure
 * @param size  size in bytes, 0 on failure
 * @param error message that says what went wrong and how to fix it, {@code null} on success
 */
public record UploadResult(String url, String name, long size, String error) {

    public UploadResult {
        boolean success = error == null;
        if (success && (url == null || name == null)) {
            throw new IllegalArgumentException("A successful upload needs a url and a name");
        }
        if (!success && (url != null || name != null)) {
            throw new IllegalArgumentException("A failed upload carries an error only, no url or name");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }

    /** Result of a stored file. */
    public static UploadResult success(String url, String name, long size) {
        return new UploadResult(Objects.requireNonNull(url, "url"), Objects.requireNonNull(name, "name"), size, null);
    }

    /** Result of a rejected file. */
    public static UploadResult failure(String error) {
        return new UploadResult(null, null, 0, Objects.requireNonNull(error, "error"));
    }
}
