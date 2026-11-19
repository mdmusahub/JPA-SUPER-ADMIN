package io.github.mdmusahub.jpaadmin.web.view;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * JSON body of the chart endpoint, and the data of the dashboard's chart: daily counts of created records per entity.
 *
 * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
 *
 * <p>Changed in Phase 10 (D-101, approved): {@code entities} used to be a list of display names, which could not carry the
 * URL name, whether the counts could be read, the total or the cap. It is a list of {@link Entity} now, and the series has
 * {@code notes}. {@link Day} is unchanged.
 *
 * @param range    number of days covered, one of {@link #RANGES}
 * @param entities the entities of the chart, in the order of each day's counts
 * @param days     one entry per day, oldest first
 * @param notes    plain sentences the reader needs to read the numbers correctly (a range that was replaced, an entity
 *                 whose dates could not be read, counts that are incomplete); never a typed value, never an exception text
 */
public record ChartSeries(int range, List<Entity> entities, List<Day> days, List<String> notes) {

    /** Ranges the chart can show. */
    public static final List<Integer> RANGES = List.of(7, 30, 90);

    public ChartSeries {
        if (!RANGES.contains(range)) {
            throw new IllegalArgumentException("range must be one of " + RANGES);
        }
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        days = List.copyOf(Objects.requireNonNull(days, "days"));
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
        for (String note : notes) {
            if (note.isBlank()) {
                throw new IllegalArgumentException("a note must not be blank");
            }
        }
        for (Day day : days) {
            if (day.counts().size() != entities.size()) {
                throw new IllegalArgumentException("each day needs exactly one count per entity");
            }
        }
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            long sum = 0;
            for (Day day : days) {
                sum += day.counts().get(i);
            }
            if (entity.available() ? sum != entity.total() : sum != 0) {
                throw new IllegalArgumentException("the total of an entity must be the sum of its counts, and an "
                        + "unavailable entity has only zeros (entity " + i + ")");
            }
        }
    }

    /**
     * One entity of the chart.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param name      URL name of the entity, as in the console's addresses
     * @param label     display name
     * @param available {@code false} when the creation dates of this entity could not be read; its counts are all zero
     *                  and the page says so instead of drawing a flat line
     * @param total     records created in the range (the sum of its counts); zero when unavailable
     * @param capped    {@code true} when the table held more records in the window than the console reads, so the oldest
     *                  days in the range are incomplete (a note says which); never {@code true} when unavailable
     */
    public record Entity(String name, String label, boolean available, long total, boolean capped) {
        public Entity {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(label, "label");
            if (total < 0) {
                throw new IllegalArgumentException("total must not be negative");
            }
            if (!available && (total != 0 || capped)) {
                throw new IllegalArgumentException("an unavailable entity has no total and is not capped");
            }
        }
    }

    /**
     * Counts for one day.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param date   day in ISO-8601 form, for example {@code 2026-11-01}
     * @param counts created records per entity, in the order of {@link ChartSeries#entities()}
     */
    public record Day(String date, List<Long> counts) {
        public Day {
            Objects.requireNonNull(date, "date");
            counts = List.copyOf(Objects.requireNonNull(counts, "counts"));
            try {
                LocalDate.parse(date);
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("date must be an ISO-8601 date: " + date, e);
            }
            if (counts.stream().anyMatch(count -> count < 0)) {
                throw new IllegalArgumentException("counts must not be negative");
            }
        }
    }
}
