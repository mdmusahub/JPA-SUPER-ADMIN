package io.github.mdmusahub.jpaadmin.storage;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What a file really is, decided by its first bytes and never by its name or the type a browser claims (UPL-1).
 *
 * <p>A small, fixed table for the five types the console accepts by default: PNG, JPEG, GIF, WebP and PDF. A type that is not in
 * the table cannot be recognised, so it cannot be accepted: an {@code @AdminUpload} that lists one (including SVG and HTML, which
 * have no reliable signature and can carry script, UPL-5) stops startup. Adding a type means adding a row here, with a test.
 *
 * <p>Internal, not stable API. Public because the web package and the auto-configuration read it.
 */
public final class FileSignatures {

    /** How many leading bytes {@link #detect} needs. */
    public static final int HEAD_BYTES = 16;

    /**
     * A recognised type.
     *
     * @param mime       the MIME type, for example {@code image/png}
     * @param extension  the extension the stored name gets, for example {@code png}
     * @param showInline whether a browser may show it in the page (an image); everything else is served as an attachment (UPL-6)
     */
    public record Detected(String mime, String extension, boolean showInline) {
    }

    private static final Detected PNG = new Detected("image/png", "png", true);
    private static final Detected JPEG = new Detected("image/jpeg", "jpg", true);
    private static final Detected GIF = new Detected("image/gif", "gif", true);
    private static final Detected WEBP = new Detected("image/webp", "webp", true);
    private static final Detected PDF = new Detected("application/pdf", "pdf", false);

    private static final List<Detected> ALL = List.of(PNG, JPEG, GIF, WEBP, PDF);

    private FileSignatures() {
    }

    /** The MIME types this version can recognise, in a fixed order. */
    public static List<String> knownTypes() {
        return ALL.stream().map(Detected::mime).toList();
    }

    /** The row for a MIME type (any letter case), or empty when this version cannot recognise it. */
    public static Optional<Detected> forMime(String mime) {
        if (mime == null) {
            return Optional.empty();
        }
        String wanted = mime.strip().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(candidate -> candidate.mime().equals(wanted)).findFirst();
    }

    /** The row for a stored file's extension, or empty. */
    public static Optional<Detected> forExtension(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        String wanted = extension.strip().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(candidate -> candidate.extension().equals(wanted)).findFirst();
    }

    /**
     * Decides from the first bytes.
     *
     * @param head   the start of the file (at least {@link #HEAD_BYTES} bytes when the file has that many)
     * @param length how many bytes of {@code head} are real
     * @return the type, or empty when the bytes are none of the known types (a program, a page, text, anything else)
     */
    public static Optional<Detected> detect(byte[] head, int length) {
        if (head == null || length <= 0) {
            return Optional.empty();
        }
        int n = Math.min(length, head.length);
        if (isPng(head, n)) {
            return Optional.of(PNG);
        }
        if (n >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (n >= 6 && startsWith(head, "GIF87a") || n >= 6 && startsWith(head, "GIF89a")) {
            return Optional.of(GIF);
        }
        if (n >= 12 && startsWith(head, "RIFF") && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return Optional.of(WEBP);
        }
        if (n >= 5 && startsWith(head, "%PDF-")) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    /** The 8-byte PNG signature, then the first chunk, which must be the header chunk {@code IHDR}. */
    private static boolean isPng(byte[] head, int n) {
        if (n < 16) {
            return false;
        }
        int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < signature.length; i++) {
            if ((head[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return head[12] == 'I' && head[13] == 'H' && head[14] == 'D' && head[15] == 'R';
    }

    private static boolean startsWith(byte[] head, String ascii) {
        for (int i = 0; i < ascii.length(); i++) {
            if (head[i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
