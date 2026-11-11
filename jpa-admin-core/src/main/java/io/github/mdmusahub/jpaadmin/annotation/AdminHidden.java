package io.github.mdmusahub.jpaadmin.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Removes an entity or a field from the console completely.
 *
 * <p>On an entity class, the entity is not in the sidebar, not on the dashboard and not reachable by URL: its
 * address answers exactly like an address that was never an entity, so the console does not reveal that the entity
 * exists. On a field, the field is not shown, searched, sorted, filtered or exported.
 *
 * <p>Put it on the field or on its getter; both are read, so it works whether the entity uses field access or
 * property access.
 *
 * <pre>{@code
 * @Entity
 * @AdminHidden                       // the whole table never appears in the console
 * public class InternalJob { ... }
 *
 * @Entity
 * public class Member {
 *     @AdminHidden                   // this one field never appears
 *     private String internalNote;
 * }
 * }</pre>
 *
 * <p>The property {@code jpa.admin.excluded-entities} hides entities in the same way without changing their code.
 *
 * <p>Part of the console's public API.
 *
 * @see AdminSensitive
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface AdminHidden {
}
