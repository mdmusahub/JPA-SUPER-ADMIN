package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Turns the text of a record's identifier, as it arrives in an address or a form, into the identifier's Java type. It is
 * the one door for every id that comes from outside: the detail page, delete, bulk delete and the duplicate route.
 *
 * <p>The type comes from the metadata ({@link AdminEntity#idAttribute()}'s field), never from the text. The conversion is
 * a <em>shape check</em>, not the defence against injection (the value always reaches a query as a bound parameter,
 * SEC-3); its job is to give every text that is not the id of a record the same answer, which is "nothing", so the
 * caller answers the same console 404 it gives for an id that has no record (SEC-2).
 *
 * <p>The rules are strict on purpose, so that a record has one address:
 * <ul>
 *   <li>{@code Long}, {@code Integer}, {@code Short}, {@code Byte} (and the primitives): an optional {@code -}, digits
 *       only, at most 19 characters, and the number written back in its canonical form must equal the text, so
 *       {@code 007}, {@code +1}, {@code -0}, {@code 1 } and {@code 1e3} are all nothing. A number outside the type's
 *       range is nothing.</li>
 *   <li>{@link BigInteger}: the same shape, at most 40 digits.</li>
 *   <li>{@link UUID}: exactly 36 characters in the 8-4-4-4-12 hexadecimal layout ({@link UUID#fromString} alone would
 *       also accept {@code 1-1-1-1-1}).</li>
 *   <li>{@link String}: not empty, at most {@value #MAX_TEXT_LENGTH} characters, no control character, no {@code /} or
 *       {@code \}, and not {@code .} or {@code ..} (a path segment of dots means something else to a browser and a
 *       server). Such an id has no address (Tomcat refuses an encoded slash), so it is not accepted from a form either.</li>
 *   <li>Any other identifier type (a character, a date, a floating-point number): nothing. Such an entity's detail and
 *       delete answer 404; this is listed under the limitations.</li>
 * </ul>
 *
 * <p>Package-private: it is a rule, not API.
 */
final class IdConverter {

    /** The longest text id accepted. */
    static final int MAX_TEXT_LENGTH = 255;

    private static final Pattern INTEGER = Pattern.compile("-?[0-9]{1,19}");
    private static final Pattern BIG_INTEGER = Pattern.compile("-?[0-9]{1,40}");
    private static final Pattern UUID_LAYOUT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private IdConverter() {
    }

    /**
     * The identifier of {@code entity} that {@code text} stands for.
     *
     * @param entity a supported entity
     * @param text   the id as text from a request; untrusted, may be {@code null}
     * @return the id as the entity's identifier type, or empty when the text is not the shape of such an id
     */
    static Optional<Object> convert(AdminEntity entity, String text) {
        Objects.requireNonNull(entity, "entity");
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        Optional<AdminField> idField = entity.idField();
        if (idField.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(convert(idField.get().javaType(), text));
    }

    /** The id's Java type for a bound parameter: the wrapper of a primitive. */
    static Class<?> parameterType(AdminEntity entity) {
        Class<?> type = entity.idField().map(AdminField::javaType).orElse(Object.class);
        if (type == long.class) {
            return Long.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        return type;
    }

    private static Object convert(Class<?> type, String text) {
        try {
            if (type == Long.class || type == long.class) {
                return canonical(INTEGER, text) ? Long.valueOf(text) : null;
            }
            if (type == Integer.class || type == int.class) {
                return canonical(INTEGER, text) ? Integer.valueOf(text) : null;
            }
            if (type == Short.class || type == short.class) {
                return canonical(INTEGER, text) ? Short.valueOf(text) : null;
            }
            if (type == Byte.class || type == byte.class) {
                return canonical(INTEGER, text) ? Byte.valueOf(text) : null;
            }
            if (type == BigInteger.class) {
                return canonical(BIG_INTEGER, text) ? new BigInteger(text) : null;
            }
            if (type == UUID.class) {
                return UUID_LAYOUT.matcher(text).matches() ? UUID.fromString(text) : null;
            }
            if (type == String.class) {
                return validText(text) ? text : null;
            }
        } catch (NumberFormatException outOfRange) {
            return null;
        }
        return null;
    }

    /** The shape is right and the text is the number's canonical form: no leading zero, no plus sign, no negative zero. */
    private static boolean canonical(Pattern shape, String text) {
        if (!shape.matcher(text).matches()) {
            return false;
        }
        String digits = text.startsWith("-") ? text.substring(1) : text;
        boolean leadingZero = digits.length() > 1 && digits.charAt(0) == '0';
        boolean negativeZero = text.equals("-0");
        return !leadingZero && !negativeZero;
    }

    private static boolean validText(String text) {
        if (text.length() > MAX_TEXT_LENGTH || text.equals(".") || text.equals("..")) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // A slash or backslash cannot be written into a path segment that comes back unchanged, so such an id has no
            // address (the same rule as ListLinks#addressable); it must not be accepted from a form or a query either.
            if (Character.isISOControl(c) || c == '/' || c == '\\') {
                return false;
            }
        }
        return true;
    }
}
