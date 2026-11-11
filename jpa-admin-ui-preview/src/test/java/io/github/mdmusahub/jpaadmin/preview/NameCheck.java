package io.github.mdmusahub.jpaadmin.preview;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ARCH-12 as code: the library modules must not contain the demo entity names. Entities are discovered through the
 * JPA metamodel, so a library file that mentions {@code Category}, {@code Product}, {@code Customer}, {@code Order}
 * or {@code Tag} is either a leftover from the sample application or an assumption about the host's model.
 *
 * <p>The match is whole word and case-sensitive, so {@code Ordered} and {@code reorder} are not hits and
 * {@code @Order} is. An allow-list (one file, every entry with a path, the line text and a written reason) covers
 * the legitimate hits; an entry that matches nothing fails the check, so a stale entry cannot hide a later hit, and
 * so does an entry without a reason (D-42).
 *
 * <p>This class is the checker. {@code NameCheckTest} runs it over the real repository and also feeds it made-up
 * input, so a weakened checker fails its own tests (AI-6).
 *
 * <p>It lives in {@code jpa-admin-ui-preview}, next to {@code HtmlRules}, for one reason: this file and its test
 * have to write the forbidden names down, and the preview module is not one of the library modules the check reads.
 */
final class NameCheck {

    /** The sample application's entity names. Not configurable: a shorter list would quietly allow more. */
    static final List<String> FORBIDDEN = List.of("Category", "Product", "Customer", "Order", "Tag");

    /** The modules that are published and must stay entity-agnostic. */
    static final List<String> LIBRARY_MODULES =
            List.of("jpa-admin-core", "jpa-admin-autoconfigure", "jpa-admin-ui", "jpa-admin-starter");

    /** The allow-list, relative to the repository root. */
    static final String ALLOW_LIST = "docs/name-check-allow-list.txt";

    private static final Pattern WORD = Pattern.compile("\\b(" + String.join("|", FORBIDDEN) + ")\\b");

    private NameCheck() {
    }

    /** One hit: a file, a line number, the line's text and the name that was found. */
    record Hit(String path, int line, String text, String name) {

        @Override
        public String toString() {
            return path + ":" + line + " holds the word '" + name + "': " + text;
        }
    }

    /** One allow-list entry. */
    record Allowed(String path, String line, String reason) {
    }

    /**
     * Every problem found, each one a sentence that says what to do. An empty list means the rule holds.
     *
     * @param root    the repository root
     * @param modules the modules to read, as folder names under {@code root}
     * @param allowed the allow-list entries
     */
    static List<String> problems(Path root, List<String> modules, List<Allowed> allowed) {
        return problems(hits(root, modules), allowed);
    }

    /**
     * The same, for hits that are already known. The tests use it to drive the checker with made-up hits.
     *
     * @param hits    every hit found in the modules
     * @param allowed the allow-list entries
     */
    static List<String> problems(List<Hit> hits, List<Allowed> allowed) {
        List<String> problems = new ArrayList<>();
        for (Allowed entry : allowed) {
            if (entry.reason() == null || entry.reason().isBlank()) {
                problems.add("The allow-list entry for " + entry.path() + " has no reason. Every entry needs one "
                        + "(ARCH-12, D-42).");
            }
        }
        List<Allowed> unused = new ArrayList<>(allowed);
        for (Hit hit : hits) {
            Allowed match = allowed.stream().filter(entry -> covers(entry, hit)).findFirst().orElse(null);
            if (match == null) {
                problems.add("A demo entity name is in a library module: " + hit + ". Rename it, or add it to "
                        + ALLOW_LIST + " with a reason if it is a genuine Spring or Java term (ARCH-12).");
            } else {
                unused.remove(match);
            }
        }
        for (Allowed entry : unused) {
            problems.add("The allow-list entry for " + entry.path() + " (" + entry.line() + ") matches nothing any "
                    + "more. Remove it from " + ALLOW_LIST + ", so a stale entry cannot hide a later hit (D-42).");
        }
        return problems;
    }

    private static boolean covers(Allowed entry, Hit hit) {
        return entry.path().equals(hit.path()) && entry.line().equals(hit.text());
    }

    /** Every whole-word, case-sensitive hit in the given modules, with the path written the same way everywhere. */
    static List<Hit> hits(Path root, List<String> modules) {
        List<Hit> hits = new ArrayList<>();
        for (String module : modules) {
            Path folder = root.resolve(module);
            if (!Files.isDirectory(folder)) {
                throw new IllegalStateException("there is no module folder " + folder);
            }
            try (Stream<Path> files = Files.walk(folder)) {
                files.filter(Files::isRegularFile).filter(NameCheck::isReadableSource).sorted()
                        .forEach(file -> hits.addAll(hitsIn(root, file)));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        return hits;
    }

    /** Sources and resources, but not build output and not a compiled file. */
    private static boolean isReadableSource(Path file) {
        String path = file.toString().replace('\\', '/');
        if (path.contains("/target/")) {
            return false;
        }
        return path.contains("/src/") || path.endsWith("pom.xml");
    }

    private static List<Hit> hitsIn(Path root, Path file) {
        List<Hit> hits = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        String path = root.relativize(file).toString().replace('\\', '/');
        for (int number = 1; number <= lines.size(); number++) {
            Matcher matcher = WORD.matcher(lines.get(number - 1));
            while (matcher.find()) {
                hits.add(new Hit(path, number, lines.get(number - 1).strip(), matcher.group(1)));
            }
        }
        return hits;
    }

    /**
     * Reads the allow-list: three lines per entry ({@code path:}, {@code line:}, {@code reason:}), blank lines and
     * {@code #} comments ignored. Anything else is an error, because a file that is read loosely is a file that can
     * silently allow too much.
     */
    static List<Allowed> readAllowList(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return parseAllowList(lines);
    }

    static List<Allowed> parseAllowList(List<String> lines) {
        List<Allowed> entries = new ArrayList<>();
        String path = null;
        String line = null;
        for (int number = 1; number <= lines.size(); number++) {
            String text = lines.get(number - 1);
            String trimmed = text.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("path:")) {
                path = value(trimmed);
                line = null;
            } else if (trimmed.startsWith("line:")) {
                require(path != null, number, "a 'line:' without a 'path:' before it");
                line = value(trimmed);
            } else if (trimmed.startsWith("reason:")) {
                require(path != null && line != null, number, "a 'reason:' without a 'path:' and a 'line:'");
                entries.add(new Allowed(path, line, value(trimmed)));
                path = null;
                line = null;
            } else {
                throw new IllegalStateException("the allow-list has a line that is none of path:, line: or reason: "
                        + "(line " + number + ")");
            }
        }
        require(path == null && line == null, lines.size(), "an entry without a reason at the end of the file");
        return entries;
    }

    private static void require(boolean condition, int number, String what) {
        if (!condition) {
            throw new IllegalStateException("the allow-list has " + what + " (line " + number + ")");
        }
    }

    private static String value(String line) {
        return Objects.requireNonNull(line.substring(line.indexOf(':') + 1).strip());
    }
}
