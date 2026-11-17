package io.github.mdmusahub.jpaadmin.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Says what the console may do with the records of an entity. Without it, an entity can be deleted like any other.
 *
 * <p>The flags are enforced in the console's service layer, not only by hiding a button: a request that asks for
 * something the flags forbid is refused even when the page never offered it (SEC-11).
 *
 * <pre>{@code
 * @Entity
 * @AdminResource(readOnly = true)        // the console only shows these records
 * public class AuditTrailEntry { ... }
 *
 * @Entity
 * @AdminResource(deletable = false)      // can be looked at (and, from a later version, edited) but never deleted
 * public class Invoice { ... }
 * }</pre>
 *
 * <p>This version of the console reads two options. The others named in the project plan (display names, group, icon,
 * order, creatable, updatable, exportable, default sort, page size) arrive with the phases that use them.
 *
 * <p>Part of the console's public API.
 *
 * @see AdminHidden
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AdminResource {

    /**
     * Whether the console only shows this entity's records: no delete, and (from a later version) no create or edit.
     * Default {@code false}. A read-only entity is never deletable, whatever {@link #deletable()} says.
     */
    boolean readOnly() default false;

    /**
     * Whether records of this entity can be deleted in the console. Default {@code true}. Ignored when
     * {@link #readOnly()} is {@code true}.
     */
    boolean deletable() default true;
}
