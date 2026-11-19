package io.github.mdmusahub.jpaadmin.preview;

import io.github.mdmusahub.jpaadmin.web.DashboardCharts;
import io.github.mdmusahub.jpaadmin.web.view.ChartSeries;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * The chart card fixtures of {@code docs/FIXTURES.md} section 4 (task U7). Every card is built by the real
 * {@link DashboardCharts#chartOf} (the same geometry, the same range links), from numbers made up here; the entities are
 * invented and no sample entity is named. The last day of every series is {@link #LAST_DAY}, so the pages never change
 * with the date.
 */
final class ChartFixtures {

    static final LocalDate LAST_DAY = LocalDate.of(2026, 11, 18);
    private static final String BASE = "/admin-preview/dashboard";

    private static final Map<String, DashboardView.Chart> CHARTS = new LinkedHashMap<>();

    /** The three entities of the usual cards: name and label. */
    private static final List<String[]> THREE = List.of(new String[] {"comments", "Comments"},
            new String[] {"articles", "Articles"}, new String[] {"members", "Members"});

    static {
        CHARTS.put("normal", card(30, THREE, ChartFixtures::wave, none(), List.of(), List.of("Tags")));
        CHARTS.put("range-7", card(7, THREE, ChartFixtures::wave, none(), List.of(), List.of()));
        CHARTS.put("range-90", card(90, THREE, ChartFixtures::wave, none(), List.of(), List.of("Tags")));
        CHARTS.put("single-entity", card(30, THREE.subList(0, 1), ChartFixtures::wave, none(), List.of(), List.of()));
        CHARTS.put("one-unavailable", card(30, THREE, ChartFixtures::wave, new boolean[] {false, true, false},
                List.of("The creation dates of Articles could not be read, so it is not in the chart."), List.of()));
        CHARTS.put("all-unavailable", card(30, THREE, ChartFixtures::wave, new boolean[] {true, true, true},
                List.of("The creation dates of Comments could not be read, so it is not in the chart.",
                        "The creation dates of Articles could not be read, so it is not in the chart.",
                        "The creation dates of Members could not be read, so it is not in the chart."), List.of()));
        CHARTS.put("all-zero", card(30, THREE, (day, entity) -> 0L, none(), List.of(), List.of()));
        CHARTS.put("spike", card(30, THREE, (day, entity) -> day == 20 && entity == 0 ? 48_000L : (day + entity) % 4,
                none(), List.of(), List.of()));
        CHARTS.put("large-counts", card(30, THREE, (day, entity) -> 1_000_000L * (entity + 1) + day * 12_345L, none(),
                List.of(), List.of()));
        CHARTS.put("long-labels", card(30, List.of(
                new String[] {"customer-support-conversation-transcripts",
                        "Customer support conversation transcripts from the regional operations team"},
                new String[] {"supercalifragilisticexpialidocious-records",
                        "Supercalifragilisticexpialidocious_records_with_an_unbreakable_name"},
                new String[] {"short", "Short"}), ChartFixtures::wave, none(), List.of(),
                List.of("Customer support conversation transcripts from the regional operations team and more")));
        CHARTS.put("range-fallback", card(30, THREE, ChartFixtures::wave, none(),
                List.of("That range is not available, so the last 30 days are shown. Choose 7, 30 or 90 days."),
                List.of()));
        CHARTS.put("capped", card(90, THREE, ChartFixtures::wave, none(), List.of(), List.of(), new boolean[] {true, false,
                false}, List.of("The counts for Comments up to and including 2026-10-02 are incomplete: it has more than "
                + "50,000 records in the last 90 days, and only the newest 50,000 are counted.")));
        CHARTS.put("many-entities", card(30, manyEntities(12), ChartFixtures::wave, none(), List.of(), List.of()));
        CHARTS.put("new-year", card(30, THREE, ChartFixtures::wave, none(), List.of(), List.of(), none(), List.of(),
                LocalDate.of(2027, 1, 8)));
    }

    private ChartFixtures() {
    }

    /** The chart card of a state, or {@code null} when there is none. */
    static DashboardView.Chart chart(String state) {
        return CHARTS.get(state);
    }

    static Iterable<String> states() {
        return CHARTS.keySet();
    }

    private static boolean[] none() {
        return new boolean[12];
    }

    private static List<String[]> manyEntities(int count) {
        List<String[]> entities = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            entities.add(new String[] {"entity-" + i, "Entity " + i});
        }
        return entities;
    }

    /** A made-up daily count: small, different for every day and entity, sometimes zero. */
    private static long wave(int day, int entity) {
        return (day * 7L + entity * 13L + (day % 5) * entity) % 9;
    }

    private static DashboardView.Chart card(int range, List<String[]> entities, BiFunction<Integer, Integer, Long> counts,
                                            boolean[] unavailable, List<String> notes, List<String> leftOut) {
        return card(range, entities, counts, unavailable, notes, leftOut, none(), List.of(), LAST_DAY);
    }

    private static DashboardView.Chart card(int range, List<String[]> entities, BiFunction<Integer, Integer, Long> counts,
                                            boolean[] unavailable, List<String> notes, List<String> leftOut,
                                            boolean[] capped, List<String> cappedNotes) {
        return card(range, entities, counts, unavailable, notes, leftOut, capped, cappedNotes, LAST_DAY);
    }

    private static DashboardView.Chart card(int range, List<String[]> entities, BiFunction<Integer, Integer, Long> counts,
                                            boolean[] unavailable, List<String> notes, List<String> leftOut,
                                            boolean[] capped, List<String> cappedNotes, LocalDate last) {
        List<ChartSeries.Day> days = new ArrayList<>();
        long[] totals = new long[entities.size()];
        for (int d = 0; d < range; d++) {
            List<Long> row = new ArrayList<>();
            for (int e = 0; e < entities.size(); e++) {
                long count = unavailable[e] ? 0 : counts.apply(d, e);
                row.add(count);
                totals[e] += count;
            }
            days.add(new ChartSeries.Day(last.minusDays(range - 1L - d).toString(), row));
        }
        List<ChartSeries.Entity> series = new ArrayList<>();
        for (int e = 0; e < entities.size(); e++) {
            series.add(new ChartSeries.Entity(entities.get(e)[0], entities.get(e)[1], !unavailable[e], totals[e],
                    !unavailable[e] && capped[e]));
        }
        List<String> allNotes = new ArrayList<>(notes);
        allNotes.addAll(cappedNotes);
        return DashboardCharts.chartOf(new ChartSeries(range, series, days, allNotes), leftOut,
                DashboardCharts.rangeLinks(BASE, range));
    }
}
