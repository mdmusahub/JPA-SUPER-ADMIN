package io.github.mdmusahub.jpaadmin.web.view;

import java.util.List;
import java.util.Objects;

/**
 * Data for the dashboard: stat cards and the entity groups.
 *
 * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
 *
 * @param stats          numbers for the stat cards
 * @param groups         entities grouped as in the sidebar, with counts and permissions
 * @param chartAvailable whether any entity has a creation timestamp field, so the chart card is shown
 */
public record DashboardView(Stats stats, List<Group> groups, boolean chartAvailable) {

    /** Template (view) name. */
    public static final String TEMPLATE = "jpa-admin/dashboard";

    public DashboardView {
        Objects.requireNonNull(stats, "stats");
        groups = List.copyOf(Objects.requireNonNull(groups, "groups"));
    }

    /**
     * Numbers for the stat cards.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param totalRecords total number of records in all entities whose count could be read; an entity with an
     *                     unavailable count is left out instead of being guessed at
     * @param entityCount  number of entities
     * @param groupCount   number of groups
     * @param largestTable the entity with most records, or {@code null} when there are no entities, or when no
     *                     count could be read
     */
    public record Stats(long totalRecords, int entityCount, int groupCount, LargestTable largestTable) {
        public Stats {
            if (totalRecords < 0 || entityCount < 0 || groupCount < 0) {
                throw new IllegalArgumentException("stat values must not be negative");
            }
        }
    }

    /**
     * The entity with most records.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param label display name
     * @param count number of records
     */
    public record LargestTable(String label, long count) {
        public LargestTable {
            Objects.requireNonNull(label, "label");
            if (count < 0) {
                throw new IllegalArgumentException("count must not be negative");
            }
        }
    }

    /**
     * A group of entities.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param label    group name
     * @param entities entities in the group
     */
    public record Group(String label, List<EntityCard> entities) {
        public Group {
            Objects.requireNonNull(label, "label");
            entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        }
    }

    /**
     * One entity on the dashboard.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param entity      URL name of the entity
     * @param label       display name
     * @param icon        icon name from the icon sprite
     * @param count       number of records, or {@code null} when the count could not be read (the card then says
     *                    "unavailable" instead of a number, and the entity is left out of the totals)
     * @param permissions what the console allows for this entity
     */
    public record EntityCard(String entity, String label, String icon, Long count, Permissions permissions) {
        public EntityCard {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(permissions, "permissions");
            if (count != null && count < 0) {
                throw new IllegalArgumentException("count must not be negative");
            }
        }
    }

    /**
     * What the console allows for an entity. The flags are also enforced in the service layer (SEC-11).
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param create whether records can be created
     * @param update whether records can be edited
     * @param delete whether records can be deleted
     * @param export whether records can be exported
     */
    public record Permissions(boolean create, boolean update, boolean delete, boolean export) {
    }
}
