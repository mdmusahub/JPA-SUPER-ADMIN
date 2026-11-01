package io.github.mdmusahub.jpaadmin.web.view;

import java.util.Objects;

/**
 * One display value in a list row or a detail field.
 *
 * <p>A {@link Type#MASKED} cell carries no value, href or title. Only the fixed {@link #MASK} is shown, whatever the
 * real value or its length. The constructor rejects any other masked cell.
 *
 * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
 *
 * @param type    kind of value, which decides how the template renders it
 * @param display text to show, already formatted
 * @param tone    badge colour, {@link Tone#NONE} for plain values
 * @param href    link target, or {@code null} when the value is not a link; a local path under the base path
 * @param title   hover text, or {@code null} when there is none
 */
public record Cell(Type type, String display, Tone tone, String href, String title) {

    /** Fixed mask shown instead of a sensitive value. Its length never depends on the real value. */
    public static final String MASK = "•".repeat(8);

    /**
     * Kind of value in a cell.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     */
    public enum Type { TEXT, NUMBER, BOOLEAN, ENUM, RELATION, DATE, IMAGE, FILE, MASKED, EMPTY }

    /**
     * Badge colour of a cell.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     */
    public enum Tone { NONE, GREEN, AMBER, RED, GREY }

    public Cell {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(tone, "tone");
        Hrefs.optional("href", href);
        if (type == Type.MASKED && (!MASK.equals(display) || tone != Tone.NONE || href != null || title != null)) {
            throw new IllegalArgumentException("A masked cell carries only the fixed mask, no value, tone, href or title");
        }
    }

    /** Plain text cell. */
    public static Cell text(String display) {
        return new Cell(Type.TEXT, display, Tone.NONE, null, null);
    }

    /** Cell for a sensitive value. It holds the fixed mask and nothing else. */
    public static Cell masked() {
        return new Cell(Type.MASKED, MASK, Tone.NONE, null, null);
    }

    /** Cell for a missing value. The template shows its own placeholder. */
    public static Cell empty() {
        return new Cell(Type.EMPTY, "", Tone.NONE, null, null);
    }
}
