/**
 * The console's annotations: the public API a host application writes in its own entity classes.
 *
 * <p>Unlike the rest of this module, every type here is public, documented, stable API (the ARCH-9 exception for a
 * public package). All of them are optional: the console works without a single annotation. v0.1 has
 * {@link io.github.mdmusahub.jpaadmin.annotation.AdminHidden} and
 * {@link io.github.mdmusahub.jpaadmin.annotation.AdminSensitive}; the other annotations of {@code docs/PLAN.md}
 * section 6 arrive in later phases.
 */
package io.github.mdmusahub.jpaadmin.annotation;
