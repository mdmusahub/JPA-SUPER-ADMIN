package io.github.mdmusahub.jpaadmin.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as sensitive, so the console masks it everywhere.
 *
 * <p>A sensitive value never reaches a list, a record page, a form, an export, a log or an error message: the
 * console shows dots and no value at all, and the field cannot be searched, sorted or filtered (SEC-1, SEC-2).
 *
 * <p>Put it on the field or on its getter; both are read, so it works whether the entity uses field access or
 * property access.
 *
 * <pre>{@code
 * @Entity
 * public class Member {
 *     @AdminSensitive
 *     private String recoveryAnswer;   // masked, although its name says nothing about secrets
 * }
 * }</pre>
 *
 * <p>The property {@code jpa.admin.sensitive-fields} masks fields by name as well ({@code password}, {@code secret},
 * {@code token} and {@code apikey} by default). This annotation adds to that list; it never takes anything off it.
 *
 * <p>Part of the console's public API.
 *
 * @see AdminHidden
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AdminSensitive {
}
