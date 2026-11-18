package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Turns the text of one form input into the value of one attribute, or into a plain sentence that says what is wrong. It
 * never throws for a bad text and never repeats the text in its answer (SEC-1, SEC-12). It knows nothing of the web or of a
 * database, so it is tested alone.
 *
 * <p>The rules are strict so that a value has one written form (the same forms as {@link FormText} writes):
 * <ul>
 *   <li><b>Text</b> ({@code String}, {@code char}, {@code Character}, {@link UUID}): stored as given (not trimmed); a text of
 *       spaces only is empty; control characters other than tab and line breaks are refused; at most the column's length.</li>
 *   <li><b>Whole numbers</b>: an optional {@code -} and digits only; a value outside the type's range is refused.
 *       <b>Decimals</b> ({@link BigDecimal}): digits with one decimal point, no exponent, no grouping.
 *       {@code double} and {@code float}: the same with an optional exponent; never infinite.</li>
 *   <li><b>Boolean</b>: exactly {@code true} or {@code false}.</li>
 *   <li><b>Date</b>: {@code yyyy-MM-dd} and a date that exists. <b>Date and time</b>: {@code yyyy-MM-ddTHH:mm[:ss[.fraction]]},
 *       a time that exists; an instant, an offset or zoned time and the legacy date classes are read as UTC (the rule the list
 *       uses to show them, D-55).</li>
 *   <li><b>Enum</b>: the name of one of the constants.</li>
 * </ul>
 * An empty text is {@code null} when the field is optional and "required" when it is not. A relation is not converted here: its
 * text is checked against the target's choices by the service.
 *
 * <p>Package-private: it is a rule, not API.
 */
final class ValueConverter {

    private static final Pattern WHOLE = Pattern.compile("-?[0-9]{1,40}");
    private static final Pattern DECIMAL = Pattern.compile("-?[0-9]{1,30}(\\.[0-9]{1,30})?");
    private static final Pattern FLOATING = Pattern.compile("-?[0-9]{1,30}(\\.[0-9]{1,30})?([eE][+-]?[0-9]{1,3})?");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern DATE_TIME =
            Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2}(\\.[0-9]{1,9})?)?");
    private static final Pattern UUID_LAYOUT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private ValueConverter() {
    }

    /**
     * The outcome of one conversion: a value (which may be {@code null} for an empty optional field) or an error.
     *
     * @param value the converted value; {@code null} when empty or when there is an error
     * @param error a plain sentence, or {@code null} when the conversion worked
     */
    record Result(Object value, String error) {

        static Result of(Object value) {
            return new Result(value, null);
        }

        static Result error(String message) {
            return new Result(null, message);
        }

        boolean failed() {
            return error != null;
        }
    }

    /**
     * @param field     the attribute
     * @param text      the posted text; not {@code null}
     * @param maxLength the longest text the column holds, or {@code 0} for no column limit
     */
    static Result convert(AdminField field, String text, int maxLength) {
        if (text.isBlank()) {
            return field.required() ? Result.error(FieldMessages.required()) : Result.of(null);
        }
        try {
            return switch (field.kind()) {
                case TEXT -> text(field, text, maxLength);
                case NUMBER -> number(field.javaType(), text.strip());
                case BOOLEAN -> bool(text.strip());
                case DATE -> date(field.javaType(), text.strip());
                case DATETIME -> dateTime(field.javaType(), text.strip());
                case ENUM -> enumeration(field, text.strip());
                case TO_ONE, TO_MANY, EMBEDDED, BINARY, OTHER -> Result.error(FieldMessages.cannotBeSet());
            };
        } catch (RuntimeException unexpected) {
            // A text that slipped through a pattern but the type still refuses. Never the exception's text (it holds the value).
            return Result.error(FieldMessages.notAccepted());
        }
    }

    // ---- text ----------------------------------------------------------------------------------------------------------

    private static Result text(AdminField field, String text, int maxLength) {
        Class<?> type = field.javaType();
        int limit = maxLength > 0 ? Math.min(maxLength, FieldMessages.MAX_TEXT_LENGTH) : FieldMessages.MAX_TEXT_LENGTH;
        if (type == UUID.class) {
            String stripped = text.strip();
            return UUID_LAYOUT.matcher(stripped).matches() ? Result.of(UUID.fromString(stripped))
                    : Result.error(FieldMessages.uuid());
        }
        if (text.length() > limit) {
            return Result.error(FieldMessages.tooLong(limit));
        }
        if (hasControlCharacter(text)) {
            return Result.error(FieldMessages.badCharacters());
        }
        if (type == char.class || type == Character.class) {
            return text.length() == 1 ? Result.of(text.charAt(0)) : Result.error(FieldMessages.singleCharacter());
        }
        return Result.of(text);
    }

    private static boolean hasControlCharacter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                return true;
            }
        }
        return false;
    }

    // ---- numbers -------------------------------------------------------------------------------------------------------

    private static Result number(Class<?> type, String text) {
        if (type == BigDecimal.class) {
            return DECIMAL.matcher(text).matches() ? Result.of(new BigDecimal(text)) : Result.error(FieldMessages.number());
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return floating(type, text);
        }
        if (!WHOLE.matcher(text).matches()) {
            return Result.error(FieldMessages.wholeNumber());
        }
        BigInteger whole = new BigInteger(text);
        if (type == BigInteger.class) {
            return Result.of(whole);
        }
        if (type == long.class || type == Long.class) {
            return range(whole, Long.MIN_VALUE, Long.MAX_VALUE, whole.longValue());
        }
        if (type == int.class || type == Integer.class) {
            return range(whole, Integer.MIN_VALUE, Integer.MAX_VALUE, whole.intValue());
        }
        if (type == short.class || type == Short.class) {
            return range(whole, Short.MIN_VALUE, Short.MAX_VALUE, (short) whole.intValue());
        }
        if (type == byte.class || type == Byte.class) {
            return range(whole, Byte.MIN_VALUE, Byte.MAX_VALUE, (byte) whole.intValue());
        }
        return Result.error(FieldMessages.cannotBeSet());
    }

    private static Result range(BigInteger value, long min, long max, Object converted) {
        if (value.compareTo(BigInteger.valueOf(min)) < 0 || value.compareTo(BigInteger.valueOf(max)) > 0) {
            return Result.error(FieldMessages.outOfRange(Long.toString(min), Long.toString(max)));
        }
        return Result.of(converted);
    }

    private static Result floating(Class<?> type, String text) {
        if (!FLOATING.matcher(text).matches()) {
            return Result.error(FieldMessages.number());
        }
        if (type == double.class || type == Double.class) {
            double value = Double.parseDouble(text);
            return Double.isInfinite(value) ? Result.error(FieldMessages.number()) : Result.of(value);
        }
        float value = Float.parseFloat(text);
        return Float.isInfinite(value) ? Result.error(FieldMessages.number()) : Result.of(value);
    }

    // ---- booleans, dates, enums ----------------------------------------------------------------------------------------

    private static Result bool(String text) {
        return switch (text) {
            case "true" -> Result.of(Boolean.TRUE);
            case "false" -> Result.of(Boolean.FALSE);
            default -> Result.error(FieldMessages.yesNo());
        };
    }

    private static Result date(Class<?> type, String text) {
        if (!DATE.matcher(text).matches()) {
            return Result.error(FieldMessages.date());
        }
        try {
            LocalDate date = LocalDate.parse(text);
            return Result.of(type == java.sql.Date.class ? java.sql.Date.valueOf(date) : date);
        } catch (DateTimeParseException impossible) {
            return Result.error(FieldMessages.date());
        }
    }

    private static Result dateTime(Class<?> type, String text) {
        if (!DATE_TIME.matcher(text).matches()) {
            return Result.error(FieldMessages.dateTime());
        }
        LocalDateTime local;
        try {
            local = LocalDateTime.parse(text);
        } catch (DateTimeParseException impossible) {
            return Result.error(FieldMessages.dateTime());
        }
        if (type == LocalDateTime.class) {
            return Result.of(local);
        }
        Instant instant = local.toInstant(ZoneOffset.UTC);
        if (type == Instant.class) {
            return Result.of(instant);
        }
        if (type == OffsetDateTime.class) {
            return Result.of(local.atOffset(ZoneOffset.UTC));
        }
        if (type == ZonedDateTime.class) {
            return Result.of(local.atZone(ZoneOffset.UTC));
        }
        if (type == java.sql.Timestamp.class) {
            return Result.of(java.sql.Timestamp.from(instant));
        }
        if (type == java.util.Date.class) {
            return Result.of(java.util.Date.from(instant));
        }
        return Result.error(FieldMessages.cannotBeSet());
    }

    private static Result enumeration(AdminField field, String text) {
        Object[] constants = field.javaType().getEnumConstants();
        if (constants != null && field.enumValues().contains(text)) {
            for (Object constant : constants) {
                if (((Enum<?>) constant).name().equals(text)) {
                    return Result.of(constant);
                }
            }
        }
        return Result.error(FieldMessages.chooseListed());
    }
}
