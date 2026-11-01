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
        return new PageChrome("JPA Admin", basePath, theme, nav, activeEntity, new ArrayList<>(breadcrumbs), flash, CSRF);
    }

    /** Three groups, eight entities. */
    private static List<NavGroup> navigation() {
        return List.of(
                new NavGroup("Content", List.of(
                        new NavItem("article", "Articles", "file", 1204),
                        new NavItem("comment", "Comments", "message", 5430),
                        new NavItem("page", "Pages", "layout", 12))),
                new NavGroup("People", List.of(
                        new NavItem("member", "Members", "user", 318),
                        new NavItem("team", "Teams", "users", 9))),
                new NavGroup("Billing", List.of(
                        new NavItem("invoice", "Invoices", "receipt", 2871),
                        new NavItem("payment", "Payments", "card", 2871),
                        new NavItem("plan", "Plans", "layers", 4))));
    }
}
