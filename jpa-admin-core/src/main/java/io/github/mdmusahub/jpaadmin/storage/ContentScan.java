package io.github.mdmusahub.jpaadmin.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * One pass over an uploaded file before anything is stored: it counts the bytes that really arrive (the size a client or a header
 * claims is not trusted) and looks for markup that has no business inside a PNG, a JPEG, a GIF, a WebP or a PDF.
 *
 * <p>The markers are the ones a <em>polyglot</em> needs, a file that is a valid image to a decoder and a page or a script to a
 * browser or a server: {@code <script}, {@code <html}, {@code <?php}, {@code <iframe}, {@code <svg} and
 * {@code <!doctype html}, in any letter case, anywhere in the file. A marker that straddles two reads is found, because the end
 * of each read is kept for the next one. Honest files do not contain them: compressed image data and PDF streams are binary, and
 * a 6 to 14 byte pattern that starts with {@code <} is far too specific to turn up in them by chance. A PDF that holds the plain
 * text of such a tag in an uncompressed stream is refused too, and that is the price of the rule (see the limitations).
 *
 * <p>Internal, not stable API.
 */
final class ContentScan {

    /** What the pass found. {@code bytes} is how many bytes were read, which stops just over the limit when the file is too big. */
    record Result(long bytes, boolean markup, boolean tooLarge) {
    }

    private static final String[] MARKERS = {"script", "html", "?php", "iframe", "svg", "!doctype html"};
    private static final int TAIL = 13;
    private static final int CHUNK = 64 * 1024;

    private ContentScan() {
    }

    /**
     * @param in    the file's bytes (the caller closes it)
     * @param limit the largest size allowed
     */
    static Result scan(InputStream in, long limit) throws IOException {
        byte[] buffer = new byte[TAIL + CHUNK];
        int carry = 0;
        long total = 0;
        int read;
        while ((read = in.read(buffer, carry, CHUNK)) != -1) {
            total += read;
            if (total > limit) {
                return new Result(total, false, true);
            }
            int end = carry + read;
            if (containsMarker(buffer, end)) {
                return new Result(total, true, false);
            }
            carry = Math.min(TAIL, end);
            System.arraycopy(buffer, end - carry, buffer, 0, carry);
        }
        return new Result(total, false, false);
    }

    private static boolean containsMarker(byte[] bytes, int end) {
        for (int i = 0; i < end; i++) {
            if (bytes[i] != '<') {
                continue;
            }
            for (String marker : MARKERS) {
                if (matchesAt(bytes, end, i + 1, marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesAt(byte[] bytes, int end, int from, String marker) {
        if (from + marker.length() > end) {
            return false;
        }
        for (int k = 0; k < marker.length(); k++) {
            if (lower(bytes[from + k]) != marker.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    private static int lower(byte b) {
        return b >= 'A' && b <= 'Z' ? b + 32 : b;
    }
}
