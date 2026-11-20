package io.github.mdmusahub.jpaadmin.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Chooses which property of the related entity names a record in the console wherever a to-one relation is shown: in the list
 * and the detail page, in the relation filter and in the choice list of a form. Without it, the console uses the first text
 * property of the related entity called {@code name}, {@code title}, {@code label}, {@code fullName}, {@code displayName},
 * {@code username}, {@code email} or {@code code}, else its first text property, else the record's number ({@code #12}).
 *
 * <pre>{@code
 * @Entity
 * public class Invoice {
 *     @ManyToOne
 *     @AdminLookup("email")                // show the client by e-mail address instead of by name
 *     private Client client;
 * }
 * }</pre>
 *
 * <p>The property must exist in the related entity and must be a visible, non-sensitive text ({@code String}) property that
 * is not the identifier; otherwise startup stops with a message that names the relation field. A sensitive property can never
 * be a label, whatever the annotation says. When the related record has no value for the property, the console shows the
 * record's number. When the related entity is not in the console (hidden or unsupported), the relation shows the number only
 * and the annotation has no effect.
 *
 * <p>Put the annotation on the relation field or on its getter; both are read. It only applies to a to-one relation
 * ({@code @ManyToOne} or {@code @OneToOne}); on anything else startup stops.
 *
 * <p>Part of the console's public API (ARCH-9).
 *
 * @see AdminSensitive
 * @see AdminHidden
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AdminLookup {

    /** The name of the property of the related entity that labels its records, exactly as it is written in the class. */
    String value();
}
