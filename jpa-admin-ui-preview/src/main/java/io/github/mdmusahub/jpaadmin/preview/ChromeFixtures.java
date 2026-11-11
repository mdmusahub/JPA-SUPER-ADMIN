package io.github.mdmusahub.jpaadmin.preview;

import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Breadcrumb;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Csrf;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.NavGroup;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.NavItem;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Theme;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The chrome variants of {@code docs/FIXTURES.md} section 1 that task U0 needs: the shell around every page. Each one is
 * a fake {@link PageChrome} built through the real record constructor, so a link that breaks the link rule cannot be
 * written here. Entity names are invented (Article, Member, Invoice), never the sample app's (ARCH-12).
 *
 * <p>Fake data only: the CSRF token is a made-up string and no variant holds a real secret.
 */
final class ChromeFixtures {

    /** The preview's own console path. */
    static final String BASE = "/admin-preview";

    /** A second console path, for the {@code custom-base-path} variant. The preview serves assets under it too. */
    static final String ALTERNATE_BASE = "/preview-alt";

    private static final Csrf CSRF = new Csrf("_csrf", "X-CSRF-TOKEN", "preview-fake-token-not-a-secret");

    /** The signed-in name the user chip shows. A made-up name, like every other value here. */
    private static final String USER = "admin";

    private static final Map<String, Supplier<PageChrome>> VARIANTS = new LinkedHashMap<>();

    static {
        VARIANTS.put("default", () -> chrome(BASE, Theme.SYSTEM, navigation(), "article",
                List.of(new Breadcrumb("Dashboard", BASE), new Breadcrumb("Articles", null)), List.of()));
        VARIANTS.put("empty-sidebar", () -> chrome(BASE, Theme.SYSTEM, List.of(), null,
                List.of(new Breadcrumb("Dashboard", null)), List.of()));
        VARIANTS.put("no-active-entity", () -> chrome(BASE, Theme.SYSTEM, navigation(), null,
                List.of(new Breadcrumb("Dashboard", null)), List.of()));
        VARIANTS.put("deep-breadcrumbs", () -> chrome(BASE, Theme.SYSTEM, navigation(), "article", List.of(
                new Breadcrumb("Dashboard", BASE),
                new Breadcrumb("Content management and editorial workflow", BASE + "/article"),
                new Breadcrumb("Articles waiting for a second review before publication", BASE + "/article"),
                new Breadcrumb("An article with a very long title that goes on and on", BASE + "/article/1"),
                new Breadcrumb("Edit the article and its translations", null)), List.of()));
        VARIANTS.put("flash-all-levels", () -> chrome(BASE, Theme.SYSTEM, navigation(), null,
                List.of(new Breadcrumb("Dashboard", null)), List.of(
                        new FlashMessage(FlashMessage.Level.SUCCESS, "The article was saved."),
                        new FlashMessage(FlashMessage.Level.INFO, "You have been signed in as the console administrator."),
                        new FlashMessage(FlashMessage.Level.WARNING, "This record is also open in another window."),
                        new FlashMessage(FlashMessage.Level.ERROR, "The record could not be deleted because other records refer to it."),
                        new FlashMessage(FlashMessage.Level.INFO, "A very long message that keeps going to check wrapping on a phone: "
                                + "the quick brown fox jumps over the lazy dog, ".repeat(6)
                                + "AVeryLongWordWithoutAnySpacesAtAllToSeeWhetherItBreaksInsteadOfOverflowingTheScreen" + "x".repeat(60)))));
        VARIANTS.put("theme-dark", () -> chrome(BASE, Theme.DARK, navigation(), null,
                List.of(new Breadcrumb("Dashboard", null)), List.of()));
        VARIANTS.put("theme-light", () -> chrome(BASE, Theme.LIGHT, navigation(), null,
                List.of(new Breadcrumb("Dashboard", null)), List.of()));
        VARIANTS.put("theme-system", () -> chrome(BASE, Theme.SYSTEM, navigation(), null,
                List.of(new Breadcrumb("Dashboard", null)), List.of()));
        VARIANTS.put("custom-base-path", () -> chrome(ALTERNATE_BASE, Theme.SYSTEM, navigation(), "article", List.of(
                new Breadcrumb("Dashboard", ALTERNATE_BASE), new Breadcrumb("Articles", null)), List.of()));
        VARIANTS.put("long-sidebar", () -> chrome(BASE, Theme.SYSTEM, longNavigation(), "entity-11",
                List.of(new Breadcrumb("Dashboard", BASE), new Breadcrumb("Ürün kategorileri", null)), List.of()));
        VARIANTS.put("no-user", () -> chrome(BASE, Theme.SYSTEM, navigation(), null,
                List.of(new Breadcrumb("Dashboard", null)), List.of(), null));
        // The list of another entity: "Members" is the active item, as the masked-column list fixture needs.
        VARIANTS.put("member-active", () -> chrome(BASE, Theme.SYSTEM, navigation(), "member",
                List.of(new Breadcrumb("Dashboard", BASE), new Breadcrumb("Members", null)), List.of()));
    }

    private ChromeFixtures() {
    }

    /** The variant called {@code name}, or empty if there is none. */
    static Optional<PageChrome> find(String name) {
        Supplier<PageChrome> variant = VARIANTS.get(name);
        return variant == null ? Optional.empty() : Optional.of(variant.get());
    }

    static Set<String> names() {
        return VARIANTS.keySet();
    }

    private static PageChrome chrome(String basePath, Theme theme, List<NavGroup> nav, String activeEntity,
                                     List<Breadcrumb> breadcrumbs, List<FlashMessage> flash) {
        return chrome(basePath, theme, nav, activeEntity, breadcrumbs, flash, USER);
    }

    private static PageChrome chrome(String basePath, Theme theme, List<NavGroup> nav, String activeEntity,
                                     List<Breadcrumb> breadcrumbs, List<FlashMessage> flash, String user) {
        return new PageChrome("JPA Admin", basePath, theme, nav, activeEntity, new ArrayList<>(breadcrumbs), flash,
                user, CSRF);
    }

    /** The same three groups the default sidebar shows, so the dashboard fixtures agree with it. */
    static List<NavGroup> navigationForDashboard() {
        return navigation();
    }

    /** Three groups, eight entities. */
    private static List<NavGroup> navigation() {
        return List.of(
                new NavGroup("Content", List.of(
                        new NavItem("article", "Articles", "file", 1204L),
                        new NavItem("comment", "Comments", "file", 5430L),
                        new NavItem("page", "Pages", "file", 12L))),
                new NavGroup("People", List.of(
                        new NavItem("member", "Members", "users", 318L),
                        new NavItem("team", "Teams", "users", 9L))),
                new NavGroup("Billing", List.of(
                        new NavItem("invoice", "Invoices", "layers", 2871L),
                        new NavItem("payment", "Payments", "layers", 2871L),
                        new NavItem("plan", "Plans", "layers", 4L))));
    }

    /**
     * Eight groups and sixty entities, with the hard cases in one place: a count of zero, a count in the millions,
     * a count the console could not read ({@code null}), a very long name, and names in other scripts.
     */
    static List<NavGroup> longNavigation() {
        List<NavGroup> groups = new ArrayList<>();
        String[] names = {"Archive", "Billing", "Catalogue", "Content", "Logistics", "Messaging", "People",
                "Reporting"};
        long[] counts = {0L, 7L, 48L, 312L, 2_048L, 54_321L, 987_654L, 1_000_000L};
        int entity = 0;
        for (int g = 0; g < names.length; g++) {
            // 60 entities over 8 groups: the first four hold one more than the others.
            int size = LONG_NAVIGATION_ENTITIES / names.length + (g < LONG_NAVIGATION_ENTITIES % names.length ? 1 : 0);
            List<NavItem> items = new ArrayList<>();
            for (int i = 0; i < size; i++, entity++) {
                items.add(new NavItem("entity-" + entity, longNavigationLabel(entity),
                        ICONS[entity % ICONS.length], longNavigationCount(entity, counts)));
            }
            groups.add(new NavGroup(names[g], items));
        }
        return List.copyOf(groups);
    }

    private static String longNavigationLabel(int entity) {
        return switch (entity) {
            case 3 -> "Scheduled newsletter delivery attempts and their bounce reports";
            case 11 -> "Ürün kategorileri";
            case 19 -> "Платёжные поручения";
            case 27 -> "配送先住所";
            case 35 -> "Παραγγελίες πελατών";
            default -> "Entity " + (entity + 1);
        };
    }

    /** Entity 5 has no count at all: that is what a failed count looks like. */
    private static Long longNavigationCount(int entity, long[] counts) {
        return entity == 5 ? null : counts[entity % counts.length];
    }

    /** How many entities the long sidebar holds. */
    static final int LONG_NAVIGATION_ENTITIES = 60;

    private static final String[] ICONS = {"table", "file", "users", "box", "layers", "unknown-icon-name"};
}
