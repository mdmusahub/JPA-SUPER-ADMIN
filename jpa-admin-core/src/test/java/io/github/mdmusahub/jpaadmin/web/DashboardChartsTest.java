package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.service.CreationDates;
import io.github.mdmusahub.jpaadmin.service.StatsService;
import io.github.mdmusahub.jpaadmin.testsupport.StatsTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.statsmodel.StatsEntities;
import io.github.mdmusahub.jpaadmin.web.view.ChartSeries;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView.Chart;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView.RangeLink;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The dashboard's chart card (Phase 10): built from real metadata and real rows, with the range links under the base path
 * (D-29, D-74), the fallback for a range that is not offered, the entities that are left out, and no chart at all when no
 * entity has a creation field.
 */
class DashboardChartsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    private AdminEntityRegistry registry;
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        StatsTestPersistence.clear();
        registry = StatsTestPersistence.registry();
        entityManager = StatsTestPersistence.entityManager();
    }

    private DashboardCharts charts(String base) {
        return new DashboardCharts(BasePath.of(base), StatsService.over(CreationDates.over(entityManager), registry, CLOCK));
    }

    private static StatsEntities.Journal journal(String at) {
        StatsEntities.Journal row = new StatsEntities.Journal();
        row.createdAt = LocalDateTime.parse(at);
        return row;
    }

    @Test
    void theCardHasTheSeriesTheLinksTheLeftOutEntitiesAndTheBars() {
        StatsTestPersistence.persist(journal("2026-11-10T08:00:00"), journal("2026-11-10T09:00:00"),
                journal("2026-11-09T09:00:00"));

        Chart chart = charts("/admin").chart("7").orElseThrow();

        assertThat(chart.series().range()).isEqualTo(7);
        assertThat(chart.series().days()).hasSize(7);
        assertThat(chart.bars()).hasSize(7);
        assertThat(chart.width()).isEqualTo(70);
        assertThat(chart.peak()).isEqualTo(2);
        assertThat(chart.axis()).containsExactly("Nov 4", "Nov 7", "Nov 10");
        assertThat(chart.leftOut()).containsExactly("Masked", "Plain", "Veiled");
        assertThat(chart.bars().get(6).summary()).isEqualTo("Nov 10: Journal 2");
    }

    @Test
    void theRangeLinksAreThreeUnderTheBasePathWithTheCurrentOneMarked() {
        Chart chart = charts("/admin").chart("90").orElseThrow();

        assertThat(chart.ranges()).containsExactly(new RangeLink(7, false, "/admin?range=7"),
                new RangeLink(30, false, "/admin?range=30"), new RangeLink(90, true, "/admin?range=90"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/ops/back-office", "/a_b-1", "/x/y/z"})
    void theRangeLinksFollowAnyConfiguredBasePath(String base) {
        Chart chart = charts(base).chart(null).orElseThrow();

        assertThat(chart.ranges()).extracting(RangeLink::href).containsExactly(base + "?range=7", base + "?range=30",
                base + "?range=90");
        assertThat(chart.ranges()).filteredOn(RangeLink::current).extracting(RangeLink::days).containsExactly(30);
    }

    @ParameterizedTest
    @ValueSource(strings = {"45", "abc", "", "7;30", "../../etc/passwd", "<script>", "javascript:alert(1)", "%00", "999"})
    void aRangeThatIsNotOfferedFallsBackToThirtyAndSaysSoWithoutRepeatingIt(String range) {
        Chart chart = charts("/admin").chart(range).orElseThrow();

        assertThat(chart.series().range()).isEqualTo(30);
        assertThat(chart.series().notes()).hasSize(1);
        assertThat(chart.series().notes().get(0)).doesNotContain("script", "passwd", "javascript");
        assertThat(chart.ranges()).filteredOn(RangeLink::current).extracting(RangeLink::days).containsExactly(30);
        assertThat(chart.ranges()).extracting(RangeLink::href).allSatisfy(href -> assertThat(href)
                .startsWith("/admin?range=").doesNotContain(range.isEmpty() ? "\u0000" : range));
    }

    @Test
    void thereIsNoCardWhenNoEntityHasACreationField() {
        List<AdminEntity> none = registry.all().stream().filter(entity -> entity.creationField().isEmpty()).toList();
        DashboardCharts charts = new DashboardCharts(BasePath.of("/admin"), StatsService.over(
                CreationDates.over(entityManager), new AdminEntityRegistry(none, List.of()), CLOCK));

        assertThat(charts.chart("7")).isEmpty();
        assertThat(charts.chart(null)).isEmpty();
    }

    @Test
    void aConsoleWithNothingInItHasNoCard() {
        assertThat(DashboardCharts.none(BasePath.of("/admin")).chart("7")).isEmpty();
    }

    @Test
    void theCardHoldsNoEntityNoRowAndNoSecret() {
        StatsEntities.Journal row = journal("2026-11-10T08:00:00");
        row.title = "a title that must not travel";
        StatsTestPersistence.persist(row);

        Chart chart = charts("/admin").chart("7").orElseThrow();

        assertThat(chart.toString()).doesNotContain("a title that must not travel").doesNotContain("StatsEntities");
    }

    @Test
    void anUnavailableEntityIsInTheCardWithoutBars() {
        StatsEntities.Journal row = journal("2026-11-10T08:00:00");
        StatsTestPersistence.persist(row);
        DashboardCharts broken = new DashboardCharts(BasePath.of("/admin"), StatsService.unavailable(registry, CLOCK));

        Chart chart = broken.chart("7").orElseThrow();

        assertThat(chart.series().entities()).isNotEmpty().noneMatch(ChartSeries.Entity::available);
        assertThat(chart.peak()).isZero();
        assertThat(chart.bars()).allSatisfy(bar -> assertThat(bar.segments()).isEmpty());
    }

    @Test
    void theNavigationPutsTheCardIntoTheDashboardRecord() {
        ConsoleNavigation navigation = new ConsoleNavigation(registry, io.github.mdmusahub.jpaadmin.service.CountService
                .unavailable(CLOCK));
        Chart chart = charts("/admin").chart("30").orElseThrow();

        DashboardView with = navigation.dashboard(chart);
        DashboardView without = navigation.dashboard();

        assertThat(with.chartAvailable()).isTrue();
        assertThat(with.chart()).isSameAs(chart);
        assertThat(without.chartAvailable()).isFalse();
        assertThat(without.chart()).isNull();
    }
}
