package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns one value read from the database into the text a list cell shows (ARCH-3: a row never holds an entity, only
 * display values). It never receives a sensitive value: a masked column is never selected, and its cell is built by
 * {@link Cell#masked()} without looking at anything (SEC-1).
 *
 * <p>The formats are fixed and do not depend on the server's locale, so the same row looks the same everywhere:
 * numbers as stored (no grouping), dates as {@code yyyy-MM-dd}, date-times as {@code yyyy-MM-dd HH:mm} (an instant
 * in UTC), booleans as Yes and No. Text is shown on one line and cut at {@value #MAX_DISPLAY} characters, with the
 * full text (up to {@value #MAX_TITLE}) as the hover text.
 *
 * <p>Package-private: it is a rule, not API.
 */
final class CellFormatter {

    static final int MAX_DISPLAY = 120;
    static final int MAX_TITLE = 1000;
    /** The longest text the detail page prints for one field, in characters. */
    static final int MAX_DETAIL_TEXT = 5000;
    private static final String ELLIPSIS = "…";
    private static final String HIDDEN_VALUE = "(not shown)";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    // Words in an enum constant that suggest a tone. The default for an enum without @AdminBadge (Phase 13, D-120): a
    // field with the annotation takes its tones from the host and never from this list.
    private static final Set<String> GREEN = Set.of("active", "published", "paid", "shipped", "delivered", "complete",
            "completed", "approved", "done", "success", "succeeded", "enabled", "open", "available", "confirmed");
    private static final Set<String> AMBER = Set.of("pending", "draft", "new", "processing", "review", "waiting",
            "scheduled", "hold", "partial", "unpaid");
    private static final Set<String> RED = Set.of("cancelled", "canceled", "failed", "rejected", "error", "blocked",
            "banned", "deleted", "refunded", "expired", "archived", "disabled", "suspended", "closed");

    private CellFormatter() {
    }

    /** The cell for a value of a non-relation, non-masked column. {@code null} is an empty cell. */
    static Cell format(Cell.Type type, Object value) {
        if (value == null) {
            return Cell.empty();
        }
        return switch (type) {
            case NUMBER -> number(value);
            case BOOLEAN -> bool(value);
            case ENUM -> enumeration(value);
            case DATE -> temporal(value);
            default -> text(value instanceof CharSequence ? value.toString() : other(value));
        };
    }

    /**
 * The cell for a value of a field that may carry an {@code @AdminBadge}. Without a usable badge it is
 * {@link #format(Cell.Type, Object)}. With one, the value is shown as a badge whose tone the host chose: the tone of the
 * entry that lists the value, else grey (the host has taken charge of the colours, so the word list is not asked). An enum
 * constant is shown humanized as always; a {@code String} is shown as stored, on one line and cut like any text.
 */
static Cell format(Cell.Type type, Object value, AdminField field) {
    if (value == null || !field.hasBadge() || type != Cell.Type.ENUM) {
        return format(type, value);
    }
    String name = value instanceof Enum<?> constant ? constant.name() : value.toString();
    Cell.Tone tone = field.badge().toneOf(name).map(CellFormatter::toneNamed).orElse(Cell.Tone.GREY);
    if (value instanceof Enum<?>) {
        return new Cell(Cell.Type.ENUM, humanize(name), tone, null, null);
    }
    Cell text = text(name);
    return text.type() == Cell.Type.EMPTY ? text : new Cell(Cell.Type.ENUM, text.display(), tone, null, text.title());
}

/**
 * The tone a palette name stands for. A fixed switch over the four names, so nothing the host wrote can become a tone
 * other than one of the console's own (D-120); any other text, which the metadata already refuses, is grey.
 */
static Cell.Tone toneNamed(String name) {
    return switch (name) {
        case "green" -> Cell.Tone.GREEN;
        case "amber" -> Cell.Tone.AMBER;
        case "red" -> Cell.Tone.RED;
        default -> Cell.Tone.GREY;
    };
}

/** A relation cell: the target's label (or {@code #id} when it has none). The link arrives with Phase 7. */
    static Cell relation(Object label, Object id) {
        if (id == null) {
            return Cell.empty();
        }
        String shown = label == null ? "#" + id : label.toString();
        Cell text = text(shown);
        return new Cell(Cell.Type.RELATION, text.type() == Cell.Type.EMPTY ? "#" + id : text.display(), Cell.Tone.NONE,
                null, text.title());
    }

    /** The cell of a field the detail page does not print: a collection, an embedded value, binary data (ARCH-4). */
    static Cell notShown() {
        return Cell.text(HIDDEN_VALUE);
    }

    /**
     * Text for the detail page: unlike a list cell it keeps line breaks (the page prints them with {@code pre-wrap}) and
     * is cut at {@value #MAX_DETAIL_TEXT} characters. Control characters other than the line break become spaces, runs of
     * more than two line breaks become two, and bidirectional overrides are dropped, as in a list cell.
     */
    static Cell detailText(String raw) {
        StringBuilder text = new StringBuilder(Math.min(raw.length(), 2 * MAX_DETAIL_TEXT));
        int lineBreaks = 0;
        for (int i = 0; i < raw.length(); ) {
            int codePoint = raw.codePointAt(i);
            i += Character.charCount(codePoint);
            if (isBidirectionalControl(codePoint)) {
                continue;
            }
            if (codePoint == '\r') {
                if (i < raw.length() && raw.charAt(i) == '\n') {
                    continue; // the \n of this \r\n is the break
                }
                codePoint = '\n';
            }
            if (codePoint == '\n') {
                if (++lineBreaks <= 2) {
                    text.append('\n');
                }
                continue;
            }
            lineBreaks = 0;
            text.appendCodePoint(Character.isISOControl(codePoint) ? ' ' : codePoint);
            if (text.length() > 2 * MAX_DETAIL_TEXT) {
                break;
            }
        }
        String shown = text.toString().strip();
        if (shown.isEmpty()) {
            return Cell.empty();
        }
        return shown.codePointCount(0, shown.length()) <= MAX_DETAIL_TEXT ? Cell.text(shown)
                : Cell.text(cut(shown, MAX_DETAIL_TEXT) + ELLIPSIS);
    }

    /** The identifier as text for the row. */
    static String id(Object id) {
        return String.valueOf(id);
    }

    static Cell text(String raw) {
        String line = singleLine(raw);
        if (line.isEmpty()) {
            return Cell.empty();
        }
        if (line.codePointCount(0, line.length()) <= MAX_DISPLAY) {
            return Cell.text(line);
        }
        return new Cell(Cell.Type.TEXT, cut(line, MAX_DISPLAY) + ELLIPSIS, Cell.Tone.NONE, null,
                line.codePointCount(0, line.length()) <= MAX_TITLE ? line : cut(line, MAX_TITLE) + ELLIPSIS);
    }

    private static Cell number(Object value) {
        String shown;
        if (value instanceof BigDecimal decimal) {
            shown = decimal.toPlainString();
        } else if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            shown = Double.isFinite(number) ? BigDecimal.valueOf(number).toPlainString() : String.valueOf(number);
        } else if (value instanceof BigInteger || value instanceof Number) {
            shown = value.toString();
        } else {
            return text(other(value));
        }
        return new Cell(Cell.Type.NUMBER, shown, Cell.Tone.NONE, null, null);
    }

    private static Cell bool(Object value) {
        if (!(value instanceof Boolean yes)) {
            return text(other(value));
        }
        return new Cell(Cell.Type.BOOLEAN, yes ? "Yes" : "No", yes ? Cell.Tone.GREEN : Cell.Tone.NONE, null, null);
    }

    private static Cell enumeration(Object value) {
        String name = value instanceof Enum<?> constant ? constant.name() : value.toString();
        return new Cell(Cell.Type.ENUM, humanize(name), tone(name), null, null);
    }

    /** {@code OUT_OF_STOCK} becomes {@code Out of stock}. */
    static String humanize(String constant) {
        String words = constant.replace('_', ' ').strip().toLowerCase(Locale.ROOT);
        return words.isEmpty() ? constant : words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }

    static Cell.Tone tone(String constant) {
        for (String word : constant.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (GREEN.contains(word)) {
                return Cell.Tone.GREEN;
            }
            if (RED.contains(word)) {
                return Cell.Tone.RED;
            }
            if (AMBER.contains(word)) {
                return Cell.Tone.AMBER;
            }
        }
        return Cell.Tone.GREY;
    }

    private static Cell temporal(Object value) {
        String display;
        String title;
        if (value instanceof LocalDate date) {
            display = DATE.format(date);
            title = null;
        } else if (value instanceof java.sql.Date sqlDate) {
            display = DATE.format(sqlDate.toLocalDate());
            title = null;
        } else if (value instanceof LocalDateTime dateTime) {
            display = DATE_TIME.format(dateTime);
            title = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(dateTime);
        } else if (value instanceof OffsetDateTime dateTime) {
            display = DATE_TIME.format(dateTime);
            title = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime);
        } else if (value instanceof ZonedDateTime dateTime) {
            display = DATE_TIME.format(dateTime);
            title = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime.toOffsetDateTime());
        } else if (value instanceof Instant instant) {
            return instant(instant);
        } else if (value instanceof Timestamp stamp) {
            return instant(stamp.toInstant());
        } else if (value instanceof Date date) {
            return instant(Instant.ofEpochMilli(date.getTime()));
        } else if (value instanceof Calendar calendar) {
            return instant(calendar.toInstant());
        } else {
            return text(other(value));
        }
        return new Cell(Cell.Type.DATE, display, Cell.Tone.NONE, null, title);
    }

    private static Cell instant(Instant instant) {
        OffsetDateTime utc = instant.atOffset(ZoneOffset.UTC);
        return new Cell(Cell.Type.DATE, DATE_TIME.format(utc), Cell.Tone.NONE, null,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(utc));
    }

    /** Text for a value of an unknown type. A collection, a map or an array is never expanded (ARCH-4). */
    private static String other(Object value) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?> || value.getClass().isArray()) {
            return HIDDEN_VALUE;
        }
        return value.toString();
    }

    /**
     * One line of plain text: every control character and every run of white space becomes one space, and the
     * characters that reorder text on screen (bidirectional overrides and isolates) are dropped, so a stored value
     * cannot make the table show something other than what it holds.
     */
    private static String singleLine(String raw) {
        StringBuilder line = new StringBuilder(Math.min(raw.length(), 2 * MAX_TITLE));
        boolean pendingSpace = false;
        for (int i = 0; i < raw.length(); ) {
            int codePoint = raw.codePointAt(i);
            i += Character.charCount(codePoint);
            if (isBidirectionalControl(codePoint)) {
                continue;
            }
            if (Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)
                    || Character.isSpaceChar(codePoint)) {
                pendingSpace = !line.isEmpty();
                continue;
            }
            if (pendingSpace) {
                line.append(' ');
                pendingSpace = false;
            }
            line.appendCodePoint(codePoint);
            if (line.length() > 2 * MAX_TITLE) {
                // More than the longest hover text plus the cut: there is no need to look at the rest.
                break;
            }
        }
        return line.toString();
    }

    private static boolean isBidirectionalControl(int codePoint) {
        return (codePoint >= 0x202A && codePoint <= 0x202E) || (codePoint >= 0x2066 && codePoint <= 0x2069)
                || codePoint == 0x200E || codePoint == 0x200F || codePoint == 0x061C;
    }

    private static String cut(String text, int codePoints) {
        return text.substring(0, text.offsetByCodePoints(0, codePoints)).stripTrailing();
    }
}
