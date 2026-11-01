package io.github.mdmusahub.jpaadmin.web.view;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * JSON body of the chart endpoint: daily counts of created records per entity.
 *
 * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
 *
 * @param range    number of days covered, one of {@link #RANGES}
 * @param entities display names of the entities, in the order of each day's counts
 * @param days     one entry per day
 */
public record ChartSeries(int range, List<String> entities, List<Day> days) {

    /** Ranges the chart can show. */
    public static final List<Integer> RANGES = List.of(7, 30, 90);

    public ChartSeries {
        if (!RANGES.contains(range)) {
            throw new IllegalArgumentException("range must be one of " + RANGES);
        }
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        days = List.copyOf(Objects.requireNonNull(days, "days"));
        for (Day day : days) {
            if (day.counts().size() != entities.size()) {
                throw new IllegalArgumentException("each day needs exactly one count per entity");
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
