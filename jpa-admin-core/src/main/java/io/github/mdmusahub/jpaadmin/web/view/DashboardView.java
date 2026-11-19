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
 * @param chart          the chart card, present exactly when {@code chartAvailable} is true (Phase 10, D-101)
 */
public record DashboardView(Stats stats, List<Group> groups, boolean chartAvailable, Chart chart) {

    /** Template (view) name. */
    public static final String TEMPLATE = "jpa-admin/dashboard";

    public DashboardView {
        Objects.requireNonNull(stats, "stats");
        groups = List.copyOf(Objects.requireNonNull(groups, "groups"));
        if (chartAvailable != (chart != null)) {
            throw new IllegalArgumentException("chart is present exactly when chartAvailable is true");
        }
    }

    /**
     * The chart card: the series, the links that switch the range, the entities that are not in the chart, and the
     * stacked bars ready to draw. The backend computes the geometry once, because a template cannot sum.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param series  the data of the chart; the same record the JSON endpoint answers with
     * @param ranges  one link per range, exactly one of them current and equal to {@code series.range()}
     * @param leftOut display names of the entities that have no creation date field and so are not in the chart
     * @param peak    the largest daily total over the available entities (the height of the tallest bar)
     * @param width   width of the drawing box, {@code bars.size() * BAR_STEP}
     * @param axis    up to three date labels for the time axis (first, middle, last day)
     * @param bars    one stacked bar per day, oldest first
     */
    public record Chart(ChartSeries series, List<RangeLink> ranges, List<String> leftOut, long peak, int width,
                        List<String> axis, List<Bar> bars) {

        /** Height of the drawing box; every segment lies inside {@code 0..SCALE}. */
        public static final int SCALE = 1000;
        /** Distance between the left edges of two bars. */
        public static final int BAR_STEP = 10;
        /** Width of one bar; the gap is {@code BAR_STEP - BAR_WIDTH}. */
        public static final int BAR_WIDTH = 8;
        /** Number of colour classes before they repeat. */
        public static final int TONES = 8;

        public Chart {
            Objects.requireNonNull(series, "series");
            ranges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
            leftOut = List.copyOf(Objects.requireNonNull(leftOut, "leftOut"));
            axis = List.copyOf(Objects.requireNonNull(axis, "axis"));
            bars = List.copyOf(Objects.requireNonNull(bars, "bars"));
            if (peak < 0) {
                throw new IllegalArgumentException("peak must not be negative");
            }
            if (width != bars.size() * BAR_STEP) {
                throw new IllegalArgumentException("width must be the number of bars times BAR_STEP");
            }
            if (bars.size() != series.days().size()) {
                throw new IllegalArgumentException("there must be one bar per day");
            }
            long current = ranges.stream().filter(RangeLink::current).count();
            if (!ranges.isEmpty() && (current != 1 || ranges.stream()
                    .noneMatch(link -> link.current() && link.days() == series.range()))) {
                throw new IllegalArgumentException("exactly one range link is current, and it is the series' range");
            }
            for (Bar bar : bars) {
                for (Segment segment : bar.segments()) {
                    if (segment.entity() >= series.entities().size()) {
                        throw new IllegalArgumentException("a segment names an entity that is not in the series");
                    }
                }
            }
        }

        /** Whether the creation dates of at least one entity could be read (otherwise the card says so). */
        public boolean anyAvailable() {
            return series.entities().stream().anyMatch(ChartSeries.Entity::available);
        }
    }

    /**
     * A link that switches the chart to another range.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param days    one of {@link ChartSeries#RANGES}
     * @param current whether this is the range on show
     * @param href    the dashboard with that range, built by the backend (the link rule of D-28)
     */
    public record RangeLink(int days, boolean current, String href) {
        public RangeLink {
            if (!ChartSeries.RANGES.contains(days)) {
                throw new IllegalArgumentException("days must be one of " + ChartSeries.RANGES);
            }
            Hrefs.require("href", href);
        }
    }

    /**
     * One day's stacked bar.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param date     the day in ISO-8601 form
     * @param label    short date for people, for example {@code Nov 3}
     * @param summary  one sentence with the day's counts per available entity, for the tooltip and the alternative text
     * @param total    records created that day over the available entities
     * @param x        left edge in the drawing box
     * @param segments the coloured parts, in the order of the entities; an entity with no record that day has none
     */
    public record Bar(String date, String label, String summary, long total, int x, List<Segment> segments) {
        public Bar {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(summary, "summary");
            segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
            if (total < 0 || x < 0) {
                throw new IllegalArgumentException("total and x must not be negative");
            }
        }
    }

    /**
     * One coloured part of a bar.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param entity index of the entity in {@link ChartSeries#entities()}; the colour class is this index modulo
     *               {@link Chart#TONES}
     * @param y      top edge in the drawing box (0 is the top)
     * @param height height in the drawing box; {@code y + height} is at most {@link Chart#SCALE}
     */
    public record Segment(int entity, int y, int height) {
        public Segment {
            if (entity < 0 || y < 0 || height < 0 || y + height > Chart.SCALE) {
                throw new IllegalArgumentException("a segment must lie inside the drawing box");
            }
        }
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
