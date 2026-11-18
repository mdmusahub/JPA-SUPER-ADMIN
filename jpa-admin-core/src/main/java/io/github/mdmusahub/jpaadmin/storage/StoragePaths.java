package io.github.mdmusahub.jpaadmin.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The name formats of the upload feature, in one place: a storage's name, a folder, and the key of a stored file
 * ({@code folder/yyyy/MM/<32 hex>.<ext>}). Traversal is impossible by construction because no accepted part can hold a
 * dot (other than the one in the file name), a slash, a backslash, a colon or a control character (UPL-4); the local
 * provider still resolves and checks the final path as a second line of defence.
 *
 * <p>Internal, not stable API. Public because the web package and the auto-configuration read it.
 */
public final class StoragePaths {

    /** At most this many parts in an {@code @AdminUpload} folder. */
    public static final int MAX_FOLDER_PARTS = 4;

    private static final Pattern STORAGE_NAME = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final Pattern FOLDER_PART = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,31}");
    private static final Pattern YEAR = Pattern.compile("[0-9]{4}");
    private static final Pattern MONTH = Pattern.compile("0[1-9]|1[0-2]");
    private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f]{32}\\.[a-z0-9]{2,4}");

    /** Names that Windows treats as devices, in any letter case, with or without an extension. */
    private static final Set<String> DEVICES = Set.of("con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5",
            "com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private StoragePaths() {
    }

    /** Whether {@code name} can be the name of a storage: a lower-case word of letters, digits and hyphens, at most 32 characters. */
    public static boolean validStorageName(String name) {
        return name != null && STORAGE_NAME.matcher(name).matches();
    }

    /**
     * Why {@code folder} cannot be an upload folder, or empty when it can. The empty folder is fine (the storage's root).
     * The message never repeats the folder.
     */
    public static Optional<String> folderProblem(String folder) {
        if (folder == null) {
            return Optional.of("the folder is missing");
        }
        if (folder.isEmpty()) {
            return Optional.empty();
        }
        String[] parts = folder.split("/", -1);
        if (parts.length > MAX_FOLDER_PARTS) {
            return Optional.of("the folder has more than " + MAX_FOLDER_PARTS + " parts");
        }
        for (String part : parts) {
            if (!FOLDER_PART.matcher(part).matches()) {
                return Optional.of("each part of the folder must be letters, digits, '-' and '_' (starting with a letter or "
                        + "digit, at most 32 characters) separated by single '/', with no '.', no leading or trailing '/', "
                        + "and no space");
            }
            if (DEVICES.contains(part.toLowerCase(Locale.ROOT))) {
                return Optional.of("a part of the folder is a reserved device name on Windows");
            }
        }
        return Optional.empty();
    }

    /** The parts of a folder that passed {@link #folderProblem}; empty for the root. */
    public static List<String> folderParts(String folder) {
        return folder.isEmpty() ? List.of() : List.of(folder.split("/"));
    }

    /**
     * Splits a key ({@code folder/yyyy/MM/name}) into its parts, or empty when it is not exactly that shape. A key that
     * passes holds no dot except the one in the file name, so it cannot climb out of a folder.
     */
    public static Optional<List<String>> keyParts(String key) {
        if (key == null || key.isEmpty() || key.length() > 300) {
            return Optional.empty();
        }
        String[] parts = key.split("/", -1);
        if (parts.length < 3 || parts.length > 3 + MAX_FOLDER_PARTS) {
            return Optional.empty();
        }
        int last = parts.length - 1;
        if (!FILE_NAME.matcher(parts[last]).matches() || !MONTH.matcher(parts[last - 1]).matches()
                || !YEAR.matcher(parts[last - 2]).matches()) {
            return Optional.empty();
        }
        List<String> all = new ArrayList<>(parts.length);
        for (int i = 0; i < last - 2; i++) {
            if (!FOLDER_PART.matcher(parts[i]).matches() || DEVICES.contains(parts[i].toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
        }
        all.addAll(List.of(parts));
        return Optional.of(List.copyOf(all));
    }

    /** Whether {@code key} is exactly {@code folder/yyyy/MM/<32 hex>.<ext>}. */
    public static boolean validKey(String key) {
        return keyParts(key).isPresent();
    }
}
