/**
 * The console's annotations: the public API a host application writes in its own entity classes.
 *
 * <p>Unlike the rest of this module, every type here is public, documented, stable API (the ARCH-9 exception for a
 * public package). All of them are optional: the console works without a single annotation. v0.1 has
 * {@link io.github.mdmusahub.jpaadmin.annotation.AdminHidden},
 * {@link io.github.mdmusahub.jpaadmin.annotation.AdminSensitive},
 * {@link io.github.mdmusahub.jpaadmin.annotation.AdminUpload},
 * {@link io.github.mdmusahub.jpaadmin.annotation.AdminBadge},
 * {@link io.github.mdmusahub.jpaadmin.annotation.AdminLookup} and
 * {@link io.github.mdmusahub.jpaadmin.annotation.AdminResource} (access flags and the plural name so far); the other
 * annotations and options of {@code docs/PLAN.md} section 6 arrive in later phases.
 */
package io.github.mdmusahub.jpaadmin.annotation;
