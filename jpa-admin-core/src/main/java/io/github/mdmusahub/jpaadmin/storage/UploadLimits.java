package io.github.mdmusahub.jpaadmin.storage;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fixed numbers of the upload feature and the one place a size written as text ({@code "2MB"}) is read.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module reads it.
 */
public final class UploadLimits {

    /** The storage every installation has without any property. */
    public static final String DEFAULT_STORAGE = "default";

    /** The folder of the {@value #DEFAULT_STORAGE} storage unless a property says otherwise (relative to the working directory). */
    public static final String DEFAULT_DIRECTORY = "./uploads";

    /** The largest file of a storage unless a property says otherwise: 5 MB. */
    public static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;

    /**
     * The global ceiling (UPL-2): no storage and no field accepts a larger file, whatever a property or an annotation says.
     * A setting may lower a limit and never raise it above this (the rule of D-12 for the export).
     */
    public static final long CEILING_BYTES = 100L * 1024 * 1024;

    private static final Pattern SIZE = Pattern.compile("(\\d{1,12})\\s?(B|KB|MB|GB)?", Pattern.CASE_INSENSITIVE);

    private UploadLimits() {
    }

    /**
     * Reads a size such as {@code 512KB}, {@code 2MB} or {@code 2048} (bytes). Units are 1024-based, as in Spring Boot's
     * {@code DataSize}.
     *
     * @return the size in bytes, or empty when the text is not a size or is zero
     */
    public static OptionalLong parseSize(String text) {
        if (text == null) {
            return OptionalLong.empty();
        }
        Matcher matcher = SIZE.matcher(text.strip());
        if (!matcher.matches()) {
            return OptionalLong.empty();
        }
        long number = Long.parseLong(matcher.group(1));
        long unit = switch (matcher.group(2) == null ? "B" : matcher.group(2).toUpperCase(java.util.Locale.ROOT)) {
            case "KB" -> 1024L;
            case "MB" -> 1024L * 1024;
            case "GB" -> 1024L * 1024 * 1024;
            default -> 1L;
        };
        long bytes = number * unit;
        return bytes > 0 ? OptionalLong.of(bytes) : OptionalLong.empty();
    }

    /** A size for a message to a person: {@code 512 KB}, {@code 5 MB}, {@code 1.5 MB}. */
    public static String describe(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return trim(kb) + " KB";
        }
        double mb = kb / 1024.0;
        return trim(mb) + " MB";
    }

    private static String trim(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
