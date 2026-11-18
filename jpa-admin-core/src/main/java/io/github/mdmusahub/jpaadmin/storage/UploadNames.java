package io.github.mdmusahub.jpaadmin.storage;

/**
 * Makes a client's file name fit for <em>display</em> and nothing else. The name is never a path, never part of the stored name and
 * never logged (UPL-3); it is shown back to the person who chose the file, so it is cut down to a plain last component without
 * control or direction-changing characters, and the page escapes it on output (SEC-9).
 *
 * <p>Internal, not stable API.
 */
public final class UploadNames {

    /** The name shown when the client sent none, or one that is nothing once cleaned. */
    public static final String FALLBACK = "file";

    private static final int MAX_CHARACTERS = 100;

    private UploadNames() {
    }

    /** @return a non-empty name of at most 100 characters; never a path */
    public static String display(String clientName) {
        if (clientName == null) {
            return FALLBACK;
        }
        String name = clientName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder clean = new StringBuilder();
        name.codePoints().filter(UploadNames::acceptable).forEach(clean::appendCodePoint);
        String stripped = clean.toString().strip();
        if (stripped.isEmpty() || stripped.equals(".") || stripped.equals("..")) {
            return FALLBACK;
        }
        if (stripped.codePointCount(0, stripped.length()) > MAX_CHARACTERS) {
            stripped = stripped.substring(0, stripped.offsetByCodePoints(0, MAX_CHARACTERS)).strip();
        }
        return stripped.isEmpty() ? FALLBACK : stripped;
    }

    /** No control character, no line or paragraph separator, no zero-width or direction character, no byte order mark. */
    private static boolean acceptable(int codePoint) {
        if (Character.isISOControl(codePoint) || codePoint == 0x2028 || codePoint == 0x2029 || codePoint == 0xFEFF
                || codePoint == 0x061C || (codePoint >= 0x200B && codePoint <= 0x200F)
                || (codePoint >= 0x202A && codePoint <= 0x202E) || (codePoint >= 0x2060 && codePoint <= 0x2064)
                || (codePoint >= 0x2066 && codePoint <= 0x2069)) {
            return false;
        }
        return Character.isDefined(codePoint) && Character.getType(codePoint) != Character.SURROGATE
                && Character.getType(codePoint) != Character.PRIVATE_USE;
    }
}
