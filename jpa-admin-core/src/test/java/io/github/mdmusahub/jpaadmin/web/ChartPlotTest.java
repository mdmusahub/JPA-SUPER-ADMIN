package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.web.view.ChartSeries;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView.Bar;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView.Chart;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView.Segment;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The stacked geometry and the labels of the chart: a pure function, so every case is plain data. */
class ChartPlotTest {

    private static ChartSeries.Entity entity(String name, boolean available, long total) {
        return new ChartSeries.Entity(name, name.substring(0, 1).toUpperCase() + name.substring(1), available, total, false);
    }

    /** A series over consecutive days starting at {@code first}; the entity totals are derived from the counts. */
    private static ChartSeries series(String first, List<String> names, long[][] countsPerDay) {
        LocalDate start = LocalDate.parse(first);
        List<ChartSeries.Day> days = new ArrayList<>();
        long[] totals = new long[names.size()];
        for (int d = 0; d < countsPerDay.length; d++) {
            List<Long> counts = new ArrayList<>();
            for (int e = 0; e < names.size(); e++) {
                counts.add(countsPerDay[d][e]);
                totals[e] += countsPerDay[d][e];
            }
            days.add(new ChartSeries.Day(start.plusDays(d).toString(), counts));
        }
        List<ChartSeries.Entity> entities = new ArrayList<>();
        for (int e = 0; e < names.size(); e++) {
            entities.add(entity(names.get(e), true, totals[e]));
        }
        return new ChartSeries(7, entities, days, List.of());
    }

    private static List<Bar> bars(ChartSeries series) {
        return ChartPlot.bars(series, ChartPlot.peak(series));
    }

    @Test
    void thePeakIsTheLargestDailyTotalOverAllEntities() {
        ChartSeries series = series("2026-11-01", List.of("a", "b"), new long[][] {{1, 2}, {5, 4}, {0, 3}});

        assertThat(ChartPlot.peak(series)).isEqualTo(9);
    }

    @Test
    void theTallestDayReachesTheTopAndSegmentsStackFromTheBottomInEntityOrder() {
        ChartSeries series = series("2026-11-01", List.of("a", "b"), new long[][] {{1, 3}, {0, 0}});

        Bar tall = bars(series).get(0);

        assertThat(tall.total()).isEqualTo(4);
        assertThat(tall.segments()).containsExactly(new Segment(0, 750, 250), new Segment(1, 0, 750));
    }

    @Test
    void aSmallerDayIsProportionalToThePeak() {
        ChartSeries series = series("2026-11-01", List.of("a"), new long[][] {{4}, {1}});

        Segment small = bars(series).get(1).segments().get(0);

        assertThat(small).isEqualTo(new Segment(0, 750, 250));
    }

    @Test
    void aSingleRecordNextToThousandsIsStillAVisibleMark() {
        ChartSeries series = series("2026-11-01", List.of("a", "b"), new long[][] {{100_000, 0}, {1, 0}, {0, 1}});

        List<Bar> bars = bars(series);

        assertThat(bars.get(1).segments().get(0).height()).isEqualTo(ChartPlot.MIN_HEIGHT);
        assertThat(bars.get(2).segments().get(0).height()).isEqualTo(ChartPlot.MIN_HEIGHT);
        assertThat(bars.get(2).segments().get(0).y() + ChartPlot.MIN_HEIGHT).isEqualTo(Chart.SCALE);
    }

    @Test
    void anEntityWithNoRecordThatDayHasNoSegmentAndAnUnavailableOneNever() {
        ChartSeries series = new ChartSeries(7, List.of(entity("a", true, 2), entity("b", false, 0)),
                List.of(new ChartSeries.Day("2026-11-01", List.of(2L, 0L))), List.of());

        Bar bar = bars(series).get(0);

        assertThat(bar.segments()).extracting(Segment::entity).containsExactly(0);
        assertThat(bar.summary()).isEqualTo("Nov 1: A 2");
    }

    @Test
    void whenNothingWasCreatedThereIsNoSegmentAndTheBarSaysSo() {
        ChartSeries series = series("2026-11-01", List.of("a", "b"), new long[][] {{0, 0}, {0, 0}});

        List<Bar> bars = bars(series);

        assertThat(ChartPlot.peak(series)).isZero();
        assertThat(bars).allSatisfy(bar -> {
            assertThat(bar.segments()).isEmpty();
            assertThat(bar.total()).isZero();
        });
        assertThat(bars.get(0).summary()).isEqualTo("Nov 1: no records");
    }

    @Test
    void theSummaryNamesEveryEntityThatHasRecordsWithThousandsSeparators() {
        ChartSeries series = series("2026-11-03", List.of("ledger", "parcel"), new long[][] {{1_200, 3}});

        assertThat(bars(series).get(0).summary()).isEqualTo("Nov 3: Ledger 1,200, Parcel 3");
    }

    @Test
    void barsAreOneStepApartOldestFirst() {
        ChartSeries series = series("2026-11-01", List.of("a"), new long[][] {{1}, {2}, {3}});

        assertThat(bars(series)).extracting(Bar::x).containsExactly(0, 10, 20);
        assertThat(bars(series)).extracting(Bar::date).containsExactly("2026-11-01", "2026-11-02", "2026-11-03");
    }

    @Test
    void veryLargeCountsDoNotOverflowOrLeaveTheBox() {
        ChartSeries series = series("2026-11-01", List.of("a", "b"),
                new long[][] {{3_000_000_000_000L, 4_000_000_000_000L}, {1, 1}});

        for (Bar bar : bars(series)) {
            for (Segment segment : bar.segments()) {
                assertThat(segment.y()).isBetween(0, Chart.SCALE);
                assertThat(segment.y() + segment.height()).isLessThanOrEqualTo(Chart.SCALE);
            }
        }
        assertThat(bars(series).get(0).segments().get(1).y()).isZero();
    }

    @Test
    void whateverTheCountsEverySegmentLiesInTheBoxAndSegmentsDoNotOverlap() {
        Random random = new Random(42);
        for (int round = 0; round < 300; round++) {
            int entities = 1 + random.nextInt(6);
            int days = 1 + random.nextInt(20);
            long[][] counts = new long[days][entities];
            long scale = 1L << random.nextInt(30);
            for (long[] day : counts) {
                for (int e = 0; e < entities; e++) {
                    day[e] = random.nextInt(4) == 0 ? 0 : 1 + (long) (random.nextDouble() * scale);
                }
            }
            List<String> names = new ArrayList<>();
            for (int e = 0; e < entities; e++) {
                names.add("e" + e);
            }

            for (Bar bar : bars(series("2026-01-01", names, counts))) {
                int previousTop = Chart.SCALE;
                for (Segment segment : bar.segments()) {
                    assertThat(segment.y()).isBetween(0, Chart.SCALE);
                    assertThat(segment.height()).isPositive();
                    assertThat(segment.y() + segment.height()).as("round " + round).isLessThanOrEqualTo(previousTop);
                    previousTop = segment.y();
                }
            }
        }
    }

    // ---- the axis ---------------------------------------------------------------------------------------------------

    private static ChartSeries daysFrom(String first, int count) {
        long[][] counts = new long[count][1];
        return series(first, List.of("a"), counts);
    }

    @Test
    void theAxisHasTheFirstMiddleAndLastDay() {
        assertThat(ChartPlot.axis(daysFrom("2026-11-04", 7))).containsExactly("Nov 4", "Nov 7", "Nov 10");
        assertThat(ChartPlot.axis(daysFrom("2026-10-12", 30))).containsExactly("Oct 12", "Oct 26", "Nov 10");
    }

    @Test
    void theAxisAddsTheYearWhenTheRangeCrossesNewYear() {
        assertThat(ChartPlot.axis(daysFrom("2025-12-20", 30))).allSatisfy(label -> assertThat(label).containsPattern(
                "20\\d\\d$"));
        assertThat(bars(daysFrom("2025-12-20", 30)).get(0).label()).isEqualTo("Dec 20, 2025");
    }

    @Test
    void aVeryShortSeriesHasTwoLabelsOrOne() {
        assertThat(ChartPlot.axis(daysFrom("2026-11-04", 2))).containsExactly("Nov 4", "Nov 5");
        assertThat(ChartPlot.axis(daysFrom("2026-11-04", 1))).containsExactly("Nov 4");
        assertThat(ChartPlot.axis(new ChartSeries(7, List.of(), List.of(), List.of()))).isEmpty();
    }

    @Test
    void datesAreFormattedInEnglishWhateverTheServerLocale() {
        java.util.Locale before = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertThat(ChartPlot.axis(daysFrom("2026-03-04", 3)).get(0)).isEqualTo("Mar 4");
        } finally {
            java.util.Locale.setDefault(before);
        }
    }
}
