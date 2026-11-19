package io.github.mdmusahub.jpaadmin.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The rules of the records that carry the chart (Phase 10, D-101): what each constructor refuses. */
class ChartRecordsTest {

    private static ChartSeries.Entity entity(String name, boolean available, long total, boolean capped) {
        return new ChartSeries.Entity(name, name.toUpperCase(), available, total, capped);
    }

    // ---- ChartSeries ------------------------------------------------------------------------------------------------

    @Test
    void anEntityTotalMustBeTheSumOfItsCounts() {
        List<ChartSeries.Day> days = List.of(new ChartSeries.Day("2026-11-01", List.of(2L)),
                new ChartSeries.Day("2026-11-02", List.of(3L)));

        assertThat(new ChartSeries(7, List.of(entity("a", true, 5, false)), days, List.of()).entities().get(0).total())
                .isEqualTo(5);
        assertThatThrownBy(() -> new ChartSeries(7, List.of(entity("a", true, 4, false)), days, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sum");
    }

    @Test
    void anUnavailableEntityHasOnlyZeros() {
        List<ChartSeries.Day> days = List.of(new ChartSeries.Day("2026-11-01", List.of(2L)));

        assertThatThrownBy(() -> new ChartSeries(7, List.of(entity("a", false, 0, false)), days, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ChartSeries(7, List.of(entity("a", false, 0, false)),
                List.of(new ChartSeries.Day("2026-11-01", List.of(0L))), List.of()).entities().get(0).available())
                .isFalse();
    }

    @Test
    void anUnavailableEntityHasNoTotalAndIsNotCapped() {
        assertThatThrownBy(() -> entity("a", false, 1, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entity("a", false, 0, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entity("a", true, -1, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNoteMustHaveText() {
        assertThatThrownBy(() -> new ChartSeries(7, List.of(), List.of(), List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChartSeries(7, List.of(), List.of(), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void theNotesAreCopied() {
        List<String> notes = new java.util.ArrayList<>(List.of("one"));
        ChartSeries series = new ChartSeries(30, List.of(), List.of(), notes);

        notes.add("two");

        assertThat(series.notes()).containsExactly("one");
    }

    // ---- DashboardView.Chart ----------------------------------------------------------------------------------------

    @Test
    void theChartIsPresentExactlyWhenTheDashboardSaysItIsAvailable() {
        DashboardView.Stats stats = new DashboardView.Stats(0, 0, 0, null);

        assertThatThrownBy(() -> new DashboardView(stats, List.of(), true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardView(stats, List.of(), false, Samples.dashboardChart()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new DashboardView(stats, List.of(), false, null).chart()).isNull();
        assertThat(Samples.dashboard().chartAvailable()).isTrue();
    }

    @Test
    void exactlyOneRangeLinkIsCurrentAndItIsTheSeriesRange() {
        ChartSeries series = Samples.chart();
        List<DashboardView.RangeLink> noneCurrent = List.of(new DashboardView.RangeLink(7, false, "/admin?range=7"));
        List<DashboardView.RangeLink> wrongCurrent = List.of(new DashboardView.RangeLink(30, true, "/admin?range=30"));

        for (List<DashboardView.RangeLink> ranges : List.of(noneCurrent, wrongCurrent)) {
            assertThatThrownBy(() -> new DashboardView.Chart(series, ranges, List.of(), 1, 20, List.of(), Samples
                    .dashboardChart().bars())).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void thereIsOneBarPerDayAndTheWidthFollowsThem() {
        DashboardView.Chart chart = Samples.dashboardChart();

        assertThatThrownBy(() -> new DashboardView.Chart(chart.series(), chart.ranges(), List.of(), 1, 30,
                chart.axis(), chart.bars())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("width");
        assertThatThrownBy(() -> new DashboardView.Chart(chart.series(), chart.ranges(), List.of(), 1, 10,
                chart.axis(), chart.bars().subList(0, 1))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one bar per day");
    }

    @Test
    void aSegmentMustNameAnEntityOfTheSeries() {
        DashboardView.Chart chart = Samples.dashboardChart();
        List<DashboardView.Bar> bars = List.of(
                new DashboardView.Bar("2026-10-30", "Oct 30", "s", 1, 0, List.of(new DashboardView.Segment(1, 0, 10))),
                chart.bars().get(1));

        assertThatThrownBy(() -> new DashboardView.Chart(chart.series(), chart.ranges(), List.of(), 1, 20, chart.axis(),
                bars)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entity");
    }

    @Test
    void aSegmentMustLieInsideTheBox() {
        assertThatThrownBy(() -> new DashboardView.Segment(0, -1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardView.Segment(0, 0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardView.Segment(-1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardView.Segment(0, 995, 6)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DashboardView.Segment(0, 996, 4).height()).isEqualTo(4);
    }

    @Test
    void aRangeLinkMustBeOneOfTheRanges() {
        assertThatThrownBy(() -> new DashboardView.RangeLink(45, false, "/admin?range=45"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theChartListsAreCopied() {
        DashboardView.Chart chart = Samples.dashboardChart();
        List<String> leftOut = new java.util.ArrayList<>(List.of("Tags"));
        DashboardView.Chart copy = new DashboardView.Chart(chart.series(), chart.ranges(), leftOut, 1, 20, chart.axis(),
                chart.bars());

        leftOut.add("Other");

        assertThat(copy.leftOut()).containsExactly("Tags");
    }
}
