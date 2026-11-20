package io.github.mdmusahub.jpaadmin.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Chooses the colour of the badge that shows the value of an enum or {@code String} field in the console's lists and detail
 * pages. Without it, an enum constant gets a colour from a small list of everyday words (a constant named {@code PUBLISHED} is
 * green, {@code PENDING} amber, {@code CANCELLED} red, everything else grey).
 *
 * <p>Each entry is written {@code VALUE=tone}. The tone is one of four names, in any letter case: {@code green},
 * {@code amber}, {@code red} and {@code grey}. Nothing else is accepted, so a colour code or a style cannot be given; the
 * console maps the name to its own badge style, which follows the light and dark theme.
 *
 * <pre>{@code
 * @Entity
 * public class Article {
 *     @Enumerated(EnumType.STRING)
 *     @AdminBadge({"PUBLISHED=green", "DRAFT=amber", "ARCHIVED=grey"})
 *     private ArticleState state;          // an enum: VALUE is the name of a constant
 *
 *     @AdminBadge({"ok=green", "late=red"})
 *     private String shipment;             // a String: VALUE is the text as stored, upper and lower case matter
 * }
 * }</pre>
 *
 * <p>On an enum field a value that is not a constant of the enum stops startup; on a {@code String} field a value that is
 * stored but not listed gets the grey badge, and so does an enum constant that is not listed (the host has taken charge of the
 * colours). An annotated {@code String} field is shown as a badge with the stored text. Anything else stops startup with a
 * message that names the field and the entry's position and never repeats the entry: a field that is neither an enum nor a
 * {@code String}, an entry without {@code =}, a blank value, a value listed twice, or a tone outside the four.
 *
 * <p>A sensitive field is always masked, so this annotation has no effect on it. Put the annotation on the field or on its
 * getter; both are read.
 *
 * <p>Part of the console's public API (ARCH-9).
 *
 * @see AdminSensitive
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AdminBadge {

    /**
     * The entries, each written {@code VALUE=tone} (for example {@code "PUBLISHED=green"}). The tone is the part after the
     * last {@code =}. At least one entry is expected; none at all does nothing.
     */
    String[] value();
}
