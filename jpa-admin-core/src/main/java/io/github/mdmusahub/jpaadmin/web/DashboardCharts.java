package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.service.StatsService;
import io.github.mdmusahub.jpaadmin.web.view.ChartSeries;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView.Chart;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView.RangeLink;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds the dashboard's chart card from the statistics: the series, the range links, the entities that are left out and
 * the drawn bars. It is the only place that makes the links of the range switch (D-74: the backend builds every link that
 * carries state), and the static methods are shared with the preview app so its fixtures are drawn by the real code.
 *
 * <p>It holds no entity and no row (ARCH-3); every value is a display value in a view-model record.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it and the preview app uses it.
 */
public final class DashboardCharts {

    private final BasePath basePath;
    private final StatsService stats;

    /**
     * @param basePath the console path the range links lie under
     * @param stats    the chart statistics
     */
    public DashboardCharts(BasePath basePath, StatsService stats) {
        this.basePath = Objects.requireNonNull(basePath, "basePath");
        this.stats = Objects.requireNonNull(stats, "stats");
    }

    /** A dashboard with no chart at all: no entity has a creation field. */
    public static DashboardCharts none(BasePath basePath) {
        return new DashboardCharts(basePath, StatsService.unavailable(AdminEntityRegistry.empty(), Clock.systemUTC()));
    }

    /**
     * The chart card, or empty when no entity has a creation field (then the dashboard hides the card).
     *
     * @param requestedRange the {@code range} parameter as it came; untrusted, see {@link StatsService#series}
     */
    public Optional<Chart> chart(String requestedRange) {
        if (!stats.hasChart()) {
            return Optional.empty();
        }
        ChartSeries series = stats.series(requestedRange);
        return Optional.of(chartOf(series, stats.leftOut(), rangeLinks(basePath.value(), series.range())));
    }

    /** The links that switch the range: the dashboard with {@code ?range=N}, the current one marked. */
    public static List<RangeLink> rangeLinks(String basePath, int current) {
        List<RangeLink> links = new ArrayList<>();
        for (int days : ChartSeries.RANGES) {
            links.add(new RangeLink(days, days == current, basePath + "?range=" + days));
        }
        return links;
    }

    /** The chart card for a series. Public so the preview app can draw its fixtures with the real geometry. */
    public static Chart chartOf(ChartSeries series, List<String> leftOut, List<RangeLink> ranges) {
        long peak = ChartPlot.peak(series);
        return new Chart(series, ranges, leftOut, peak, series.days().size() * Chart.BAR_STEP, ChartPlot.axis(series),
                ChartPlot.bars(series, peak));
    }
}
