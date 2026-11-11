/**
 * Entity metadata: what the console knows about the host's entities.
 *
 * <p>The metadata is read once at startup from the JPA metamodel and held in immutable records (ARCH-1). No entity
 * name is ever hard-coded (ARCH-12). Hidden and unsupported entities are not in the registry's lookups, so they
 * cannot be reached by URL and the console does not reveal that they exist.
 *
 * <p>Internal, not stable API, except the annotations in {@code io.github.mdmusahub.jpaadmin.annotation}.
 */
package io.github.mdmusahub.jpaadmin.metadata;
