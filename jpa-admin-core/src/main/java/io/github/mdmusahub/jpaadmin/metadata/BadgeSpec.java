package io.github.mdmusahub.jpaadmin.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What {@code @AdminBadge} said about one field, read once at startup (ARCH-1). The entries are kept as written; which of them
 * are usable is decided here, by one rule, and a wrong entry never crashes the scanner: it is reported by
 * {@link AnnotationProblems} (startup stops, naming the field) and, if the console ran anyway, it is simply not used.
 *
 * <p>An entry is {@code VALUE=tone}: the tone is the text after the last {@code =}, compared without case and without
 * surrounding space, and it must be one of the four names in {@link #PALETTE}. The value is the text before the last
 * {@code =}, stripped, and is compared as written (upper and lower case matter). A tone that is not in the palette (a colour
 * code, a style, an unknown word) is never usable, so no colour of the host's choosing can reach a page: the tone only ever
 * leads to one of the console's own badge styles.
 *
 * <p>Internal, not stable API. Public because the services and the auto-configuration read it.
 *
 * @param entries the entries as written, in order
 */
public record BadgeSpec(List<String> entries) {

    /** The tones an entry may name: the badge styles the console has, in the order the documentation lists them. */
    public static final List<String> PALETTE = List.of("green", "amber", "red", "grey");

    public BadgeSpec {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        entries.forEach(entry -> Objects.requireNonNull(entry, "entry"));
    }

    /** The tone of a stored value ({@link #PALETTE} name), or empty when no usable entry names the value. */
    public Optional<String> toneOf(String value) {
        return value == null ? Optional.empty() : Optional.ofNullable(usable().get(value));
    }

    /** The usable entries as value to tone, first entry wins when a value is listed twice. Never holds a tone outside the palette. */
    public Map<String, String> usable() {
        Map<String, String> tones = new LinkedHashMap<>();
        for (String entry : entries) {
            Parsed parsed = parse(entry);
            if (parsed.problem() == null) {
                tones.putIfAbsent(parsed.value(), parsed.tone());
            }
        }
        return tones;
    }

    /**
     * Why entries cannot be used, one sentence for each, without repeating what was written (the entry may be anything).
     * Only the shape and the palette are judged here; whether a value is a constant of the field's enum is the check of
     * {@link AnnotationProblems}, which knows the field.
     */
    List<String> problems() {
        List<String> problems = new ArrayList<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            Parsed parsed = parse(entries.get(i));
            String number = "entry " + (i + 1);
            if (parsed.problem() != null) {
                problems.add(number + " " + parsed.problem());
            } else if (seen.putIfAbsent(parsed.value(), i + 1) != null) {
                problems.add(number + " lists a value that entry " + seen.get(parsed.value()) + " already lists");
            }
        }
        return problems;
    }

    /** The values the usable entries list, in order. */
    List<String> values() {
        return List.copyOf(usable().keySet());
    }

    private record Parsed(String value, String tone, String problem) {
    }

    private static Parsed parse(String entry) {
        int at = entry.lastIndexOf('=');
        if (at < 0) {
            return new Parsed(null, null, "is not written VALUE=tone (there is no '=')");
        }
        String value = entry.substring(0, at).strip();
        String tone = entry.substring(at + 1).strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return new Parsed(null, null, "has no value before the '='");
        }
        if (!PALETTE.contains(tone)) {
            return new Parsed(null, null, "has a tone that is not one of " + String.join(", ", PALETTE)
                    + "; only those names are accepted, never a colour or a style");
        }
        return new Parsed(value, tone, null);
    }
}
