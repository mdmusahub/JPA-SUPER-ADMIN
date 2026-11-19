package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.web.view.ChartSeries;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView.Bar;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView.Chart;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView.Segment;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a {@link ChartSeries} into stacked bars in an integer drawing box, so the page only prints numbers (a template
 * cannot sum, and a rounding rule belongs in one tested place). A pure function: no clock, no database, no entity.
 *
 * <p>The box is {@link Chart#SCALE} high; the tallest day reaches the top. A day with records always gets at least
 * {@link #MIN_HEIGHT} units per entity, so one record next to thousands is still a visible mark. Bars are drawn from
 * the bottom in the order of the entities. An unavailable entity has only zeros and so no segment.
 *
 * <p>Package-private: a rule, not API; {@link DashboardCharts} is the entry point.
 */
final class ChartPlot {

    /** The smallest drawn height of a segment, in drawing units (0.4% of the box). */
    static final int MIN_HEIGHT = 4;

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_WITH_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private ChartPlot() {
    }

    /** The largest daily total of the series. */
    static long peak(ChartSeries series) {
        long peak = 0;
        for (ChartSeries.Day day : series.days()) {
            peak = Math.max(peak, day.counts().stream().mapToLong(Long::longValue).sum());
        }
        return peak;
    }

    /** One stacked bar per day, oldest first. */
    static List<Bar> bars(ChartSeries series, long peak) {
        List<Bar> bars = new ArrayList<>();
        boolean withYear = spansYears(series);
        int index = 0;
        for (ChartSeries.Day day : series.days()) {
            LocalDate date = LocalDate.parse(day.date());
            bars.add(bar(series, day, date, peak, index * Chart.BAR_STEP, withYear));
            index++;
        }
        return bars;
    }

    /** Up to three date labels: the first day, the middle one and the last. */
    static List<String> axis(ChartSeries series) {
        List<ChartSeries.Day> days = series.days();
        if (days.isEmpty()) {
            return List.of();
        }
        boolean withYear = spansYears(series);
        List<Integer> positions = days.size() < 3 ? List.of(0, days.size() - 1)
                : List.of(0, (days.size() - 1) / 2, days.size() - 1);
        List<String> labels = new ArrayList<>();
        for (int position : positions.stream().distinct().toList()) {
            labels.add(label(LocalDate.parse(days.get(position).date()), withYear));
        }
        return labels;
    }

    private static Bar bar(ChartSeries series, ChartSeries.Day day, LocalDate date, long peak, int x, boolean withYear) {
        List<Segment> segments = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        long total = 0;
        // The top edge of what is drawn so far. A segment starts where the one below it really ended, so growing a tiny
        // segment to its minimum height can never make it overlap the next one.
        int floor = Chart.SCALE;
        for (int entity = 0; entity < day.counts().size(); entity++) {
            long count = day.counts().get(entity);
            if (count == 0) {
                continue;
            }
            total += count;
            parts.add(series.entities().get(entity).label() + " " + String.format(Locale.ROOT, "%,d", count));
            int top = Math.min(floor, Chart.SCALE - scaled(total, peak));
            if (floor - top < MIN_HEIGHT) {
                top = Math.max(0, floor - MIN_HEIGHT);
            }
            // Only when the stack already touches the top of the box is there no room left; the count is still in the
            // summary, the totals and the table.
            if (floor > top) {
                segments.add(new Segment(entity, top, floor - top));
                floor = top;
            }
        }
        String label = label(date, withYear);
        String summary = label + ": " + (parts.isEmpty() ? "no records" : String.join(", ", parts));
        return new Bar(day.date(), label, summary, total, x, segments);
    }

    private static int scaled(long value, long peak) {
        return peak == 0 ? 0 : (int) Math.round(value * (double) Chart.SCALE / peak);
    }

    private static boolean spansYears(ChartSeries series) {
        List<ChartSeries.Day> days = series.days();
        return !days.isEmpty() && LocalDate.parse(days.get(0).date()).getYear()
                != LocalDate.parse(days.get(days.size() - 1).date()).getYear();
    }

    private static String label(LocalDate date, boolean withYear) {
        return (withYear ? SHORT_WITH_YEAR : SHORT).format(date);
    }
}
