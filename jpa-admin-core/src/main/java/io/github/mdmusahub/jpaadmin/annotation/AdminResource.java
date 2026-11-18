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
 * @AdminResource(deletable = false)      // can be looked at, created and edited, but never deleted
 * public class Invoice { ... }
 * }</pre>
 *
 * <pre>{@code
 * @Entity
 * @AdminResource(creatable = false)      // records can be looked at, changed and deleted, but not created in the console
 * public class Invoice { ... }
 * }</pre>
 *
 * <p>This version of the console reads four options. The others named in the project plan (display names, group, icon,
 * order, exportable, default sort, page size) arrive with the phases that use them.
 *
 * <p>Part of the console's public API (ARCH-9): the options are small, documented and have defaults, so adding the annotation
 * never changes an entity that does not use it.
 *
 * @see AdminHidden
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AdminResource {

    /**
     * Whether the console only shows this entity's records: no create, no edit and no delete. Default {@code false}. A
     * read-only entity is never creatable, updatable or deletable, whatever the other options say.
     */
    boolean readOnly() default false;

    /**
     * Whether new records of this entity can be created in the console (the "New record" button, the create form and
     * "Duplicate"). Default {@code true}. Ignored when {@link #readOnly()} is {@code true}. A request that creates a record
     * of an entity that is not creatable is refused by the service even when the page never offered the button (SEC-11).
     */
    boolean creatable() default true;

    /**
     * Whether existing records of this entity can be changed in the console (the "Edit record" button and the edit form).
     * Default {@code true}. Ignored when {@link #readOnly()} is {@code true}. A request that changes a record of an entity
     * that is not updatable is refused by the service even when the page never offered the button (SEC-11).
     */
    boolean updatable() default true;

    /**
     * Whether records of this entity can be deleted in the console. Default {@code true}. Ignored when
     * {@link #readOnly()} is {@code true}.
     */
    boolean deletable() default true;
}
