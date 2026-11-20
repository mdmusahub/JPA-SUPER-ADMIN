package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.NavGroup;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.NavItem;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Turns the entity metadata and the record counts into the two things the pages show: the sidebar and the
 * dashboard. It is the only place that knows how entities become groups, so the chrome factory and the dashboard
 * cannot disagree.
 *
 * <p>It holds no entity and no row (ARCH-3): every value it produces is a display value in a view-model record.
 * Hidden and unsupported entities never reach it, because they are not in the registry.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it.
 */
public final class ConsoleNavigation {

    private final AdminEntityRegistry registry;
    private final CountService counts;
    private final boolean exportEnabled;

    /**
     * @param registry the entities the console manages
     * @param counts   the record counts
     */
    public ConsoleNavigation(AdminEntityRegistry registry, CountService counts) {
        this(registry, counts, false);
    }

    /**
     * @param registry      the entities the console manages
     * @param counts        the record counts
     * @param exportEnabled whether the CSV export is switched on ({@code jpa.admin.export-enabled}); the dashboard's
     *                      {@code Permissions.export} is this and the entity's own {@code exportable} option
     */
    public ConsoleNavigation(AdminEntityRegistry registry, CountService counts, boolean exportEnabled) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.counts = Objects.requireNonNull(counts, "counts");
        this.exportEnabled = exportEnabled;
    }

    /** A console with no entities: no entity manager factory, or every entity hidden. */
    public static ConsoleNavigation empty() {
        return new ConsoleNavigation(AdminEntityRegistry.empty(), CountService.unavailable(Clock.systemUTC()));
    }

    /**
     * The entity with this address, or empty when there is none, it is hidden, or it is unsupported (SEC-2).
     *
     * @param urlName the {@code {entity}} part of a request path; untrusted
     */
    public Optional<AdminEntity> find(String urlName) {
        return registry.find(urlName);
    }

    /** The sidebar: one group per entity group, each entity with its icon and count. */
    public List<NavGroup> sidebar() {
        Map<String, OptionalLong> counted = count();
        Map<String, List<NavItem>> groups = new LinkedHashMap<>();
        for (AdminEntity entity : registry.all()) {
            groups.computeIfAbsent(entity.group(), group -> new ArrayList<>())
                    .add(new NavItem(entity.urlName(), entity.plural(), entity.icon(),
                            number(counted.get(entity.urlName()))));
        }
        return groups.entrySet().stream().map(group -> new NavGroup(group.getKey(), group.getValue())).toList();
    }

    /** The dashboard without a chart: the stat cards and one card per group. */
    public DashboardView dashboard() {
        return dashboard(null);
    }

    /**
     * The dashboard: the stat cards, one card per group, and the chart card when there is one.
     *
     * @param chart the chart card, or {@code null} when no entity has a creation field (the card is then hidden)
     */
    public DashboardView dashboard(DashboardView.Chart chart) {
        Map<String, OptionalLong> counted = count();
        Map<String, List<DashboardView.EntityCard>> groups = new LinkedHashMap<>();
        long total = 0;
        DashboardView.LargestTable largest = null;
        for (AdminEntity entity : registry.all()) {
            OptionalLong count = counted.getOrDefault(entity.urlName(), OptionalLong.empty());
            groups.computeIfAbsent(entity.group(), group -> new ArrayList<>())
                    .add(new DashboardView.EntityCard(entity.urlName(), entity.plural(), entity.icon(),
                            number(count), permissions(entity)));
            if (count.isPresent()) {
                // An entity whose count could not be read is left out instead of being counted as zero.
                total += count.getAsLong();
                if (largest == null || count.getAsLong() > largest.count()) {
                    largest = new DashboardView.LargestTable(entity.plural(), count.getAsLong());
                }
            }
        }
        DashboardView.Stats stats = new DashboardView.Stats(total, registry.all().size(), groups.size(), largest);
        List<DashboardView.Group> cards = groups.entrySet().stream()
                .map(group -> new DashboardView.Group(group.getKey(), group.getValue())).toList();
        return new DashboardView(stats, cards, chart != null, chart);
    }

    private Map<String, OptionalLong> count() {
        return counts.countAll(registry.all());
    }

    private static Long number(OptionalLong count) {
        return count == null || count.isEmpty() ? null : count.getAsLong();
    }

    /**
     * What the console offers for an entity: creating, changing, deleting and exporting, when {@code @AdminResource} and the
     * properties allow it (each flag is also enforced in its service, SEC-11). Exporting is offered when it is switched on and
     * the entity is exportable; the list page additionally needs a column to export ({@code ExportService#offers}).
     */
    private DashboardView.Permissions permissions(AdminEntity entity) {
        return new DashboardView.Permissions(entity.canCreate(), entity.canUpdate(), entity.canDelete(),
                exportEnabled && entity.canExport());
    }
}
