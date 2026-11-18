package io.github.mdmusahub.jpaadmin.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;

/**
 * A stored value as the text a form input holds. One place for the rule, so the edit form, the duplicate form, the
 * defaults of the create form and the "did this field change?" check of an update all agree (an update compares
 * {@code FormText.of(current)} with the posted text and writes only what differs, so a value that the form cannot show
 * exactly is never rewritten by an edit that did not touch it).
 *
 * <p>Numbers are written without grouping or an exponent ({@link BigDecimal#toPlainString}), an enum by its constant name,
 * a boolean as {@code true} or {@code false}, a date as {@code yyyy-MM-dd}, and a date with a time as the value of an
 * {@code <input type="datetime-local">}: {@code yyyy-MM-ddTHH:mm[:ss[.SSS]]}, at most to the millisecond (the most that
 * input holds). An instant, an offset or zoned time and the legacy date classes are shown in UTC (the rule of the list,
 * D-55). A value is never trimmed or altered otherwise.
 *
 * <p>Package-private: it is a rule, not API.
 */
final class FormText {

    private FormText() {
    }

    /** The form text of {@code value}, or {@code null} for {@code null}. */
    static String of(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof LocalDateTime time) {
            return dateTime(time);
        }
        if (value instanceof Instant instant) {
            return dateTime(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
        }
        if (value instanceof OffsetDateTime time) {
            return dateTime(time.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
        }
        if (value instanceof ZonedDateTime time) {
            return dateTime(time.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
        }
        if (value instanceof java.sql.Timestamp stamp) {
            return dateTime(LocalDateTime.ofInstant(stamp.toInstant(), ZoneOffset.UTC));
        }
        if (value instanceof Date date) {
            return dateTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneOffset.UTC));
        }
        if (value instanceof Calendar calendar) {
            return dateTime(LocalDateTime.ofInstant(calendar.toInstant(), ZoneOffset.UTC));
        }
        return value.toString();
    }

    private static String dateTime(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.MILLIS).toString();
    }
}
