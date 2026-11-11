package io.github.mdmusahub.jpaadmin.metadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Every entity the console manages, built once at startup and immutable afterwards (ARCH-1).
 *
 * <p>Hidden and unsupported entities are <em>not</em> in this registry's lookups. An address that names one is
 * therefore answered exactly like an address that names nothing at all, so the console never reveals that such an
 * entity exists. {@link #unsupported()} exists only so startup can log the reasons once, on the server.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it and the pages read it.
 */
public final class AdminEntityRegistry {

    private static final AdminEntityRegistry EMPTY = new AdminEntityRegistry(List.of(), List.of());

    private final List<AdminEntity> entities;
    private final List<AdminEntity> unsupported;
    private final Map<String, AdminEntity> byUrlName;

    /**
     * @param entities    the entities the console manages, already in display order
     * @param unsupported the entities it cannot manage, for the startup log only
     */
    public AdminEntityRegistry(List<AdminEntity> entities, List<AdminEntity> unsupported) {
        this.entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        this.unsupported = List.copyOf(Objects.requireNonNull(unsupported, "unsupported"));
        Map<String, AdminEntity> index = new LinkedHashMap<>();
        for (AdminEntity entity : this.entities) {
            if (!entity.supported()) {
                throw new IllegalArgumentException("an unsupported entity must not be in the registry's entities");
            }
            if (index.putIfAbsent(entity.urlName(), entity) != null) {
                // Two entities with one address would make one of them unreachable, so this is a bug, not a config
                // problem: the scanner resolves collisions before it gets here.
                throw new IllegalArgumentException("two entities share the console address " + entity.urlName());
            }
        }
        this.byUrlName = Map.copyOf(index);
    }

    /** A registry with nothing in it: no entity manager factory, or every entity hidden. */
    public static AdminEntityRegistry empty() {
        return EMPTY;
    }

    /**
     * The entity with this address, or empty when there is none, when it is hidden, or when it is unsupported.
     *
     * @param urlName the {@code {entity}} part of a request path; untrusted, never echoed (SEC-2)
     */
    public Optional<AdminEntity> find(String urlName) {
        return urlName == null ? Optional.empty() : Optional.ofNullable(byUrlName.get(urlName));
    }

    /** Every entity the console manages, in a stable order (group, then label, then class name). */
    public List<AdminEntity> all() {
        return entities;
    }

    /** The entities the console cannot manage, with their reasons. For the startup log only. */
    public List<AdminEntity> unsupported() {
        return unsupported;
    }

    /** Whether the console found no entity at all. */
    public boolean isEmpty() {
        return entities.isEmpty();
    }

    /** The group names in display order. */
    public List<String> groups() {
        return entities.stream().map(AdminEntity::group).distinct().toList();
    }
}
