package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.Cell;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Turns one value read from the database into the text of a CSV cell (D-105, D-109). Machine-readable, not the display text
 * of the list: a person reads the list, a spreadsheet or a script reads the file.
 *
 * <ul>
 *   <li><b>Numbers</b> are plain: no grouping, a decimal point, no exponent ({@code BigDecimal#toPlainString}).</li>
 *   <li><b>Dates</b> are ISO-8601 in the zone of the stored value, never converted: a {@code LocalDate} is
 *       {@code yyyy-MM-dd}, a {@code LocalDateTime} {@code yyyy-MM-ddTHH:mm:ss}, an {@code OffsetDateTime} or a
 *       {@code ZonedDateTime} carries its offset, an {@code Instant} (and a {@code Timestamp}, {@code Date} or
 *       {@code Calendar}, which are instants) ends in {@code Z}.</li>
 *   <li><b>Booleans</b> are {@code true} and {@code false}; an <b>enum</b> is its constant name.</li>
 *   <li><b>Text</b> is complete (not cut, line breaks kept); the CSV writer quotes it.</li>
 *   <li>A <b>relation</b> is the related record's label as the list shows it, else its plain identifier.</li>
 *   <li>{@code null} is {@code null}, which the writer turns into an empty cell.</li>
 * </ul>
 *
 * <p>Package-private: it is a rule, not API. It never receives a sensitive value: a sensitive column is not selected.
 */
final class ExportValues {

    private ExportValues() {
    }

    /** The text of a non-relation value of a column of this kind; {@code null} for {@code null}. */
    static String text(Cell.Type type, Object value) {
        if (value == null) {
            return null;
        }
        return switch (type) {
            case NUMBER -> number(value);
            case BOOLEAN -> value instanceof Boolean ? value.toString() : other(value);
            case ENUM -> value instanceof Enum<?> constant ? constant.name() : other(value);
            case DATE -> temporal(value);
            default -> value instanceof CharSequence ? value.toString() : other(value);
        };
    }

    /** A relation cell: the label when there is a usable one, else the plain identifier; {@code null} when there is no target. */
    static String relation(Object label, Object id) {
        if (id == null) {
            return null;
        }
        if (label != null && !label.toString().isBlank()) {
            return label.toString();
        }
        return String.valueOf(id);
    }

    private static String number(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Float single) {
            return single.isNaN() || single.isInfinite() ? single.toString() : new BigDecimal(single.toString()).toPlainString();
        }
        if (value instanceof Double number) {
            return number.isNaN() || number.isInfinite() ? number.toString() : BigDecimal.valueOf(number).toPlainString();
        }
        if (value instanceof BigInteger || value instanceof Number) {
            return value.toString();
        }
        return other(value);
    }

    private static String temporal(Object value) {
        if (value instanceof LocalDate date) {
            return DateTimeFormatter.ISO_LOCAL_DATE.format(date);
        }
        if (value instanceof java.sql.Date sqlDate) {
            return DateTimeFormatter.ISO_LOCAL_DATE.format(sqlDate.toLocalDate());
        }
        if (value instanceof LocalDateTime dateTime) {
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(dateTime);
        }
        if (value instanceof OffsetDateTime dateTime) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime);
        }
        if (value instanceof ZonedDateTime dateTime) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime.toOffsetDateTime());
        }
        if (value instanceof Instant instant) {
            return instant(instant);
        }
        if (value instanceof Timestamp stamp) {
            return instant(stamp.toInstant());
        }
        if (value instanceof Date date) {
            return instant(Instant.ofEpochMilli(date.getTime()));
        }
        if (value instanceof Calendar calendar) {
            return instant(calendar.toInstant());
        }
        return other(value);
    }

    private static String instant(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    /** Text for a value of an unknown type. A collection, a map or an array is never expanded (ARCH-4): it is empty. */
    private static String other(Object value) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?> || value.getClass().isArray()) {
            return null;
        }
        return value.toString();
    }
}
