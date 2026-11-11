package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Breadcrumb;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.List;
import java.util.Objects;

/**
 * Builds the {@link PageChrome} that every console page shares: title, base path, initial theme, sidebar,
 * breadcrumbs, flash messages, the signed-in user and the CSRF token.
 *
 * <p>The sidebar comes from {@link ConsoleNavigation}, so it is the same on every page and no page can invent its
 * own. The only thing read from the request is who is signed in, through the Servlet API
 * ({@link HttpServletRequest#getUserPrincipal()}), which is {@code null} for a visitor who is not signed in; this
 * module therefore needs no dependency on Spring Security. Nothing a visitor typed ever reaches the chrome.
 *
 * <p>Internal, not stable API. Public only because the auto-configuration module creates it.
 */
public final class ConsoleChromeFactory {

    private final BasePath basePath;
    private final ConsoleSettings settings;
    private final CsrfTokens csrfTokens;
    private final ConsoleNavigation navigation;

    public ConsoleChromeFactory(BasePath basePath, ConsoleSettings settings, CsrfTokens csrfTokens,
                                ConsoleNavigation navigation) {
        this.basePath = Objects.requireNonNull(basePath, "basePath");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.csrfTokens = Objects.requireNonNull(csrfTokens, "csrfTokens");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
    }

    /**
     * The chrome of a page with the given breadcrumbs, with no entity marked as the current one. Every breadcrumb
     * link must lie under the base path (the record checks it).
     */
    public PageChrome chrome(HttpServletRequest request, List<Breadcrumb> breadcrumbs) {
        return chrome(request, breadcrumbs, null, List.of());
    }

    /**
     * The chrome of an entity's page: the entity is marked as the current one in the sidebar, and the page may add
     * one-time messages.
     *
     * @param activeEntity URL name of the entity being shown, or {@code null} outside an entity
     * @param flash        one-time messages for this response
     */
    public PageChrome chrome(HttpServletRequest request, List<Breadcrumb> breadcrumbs, String activeEntity,
                             List<FlashMessage> flash) {
        return new PageChrome(settings.title(), basePath.value(), PageChrome.Theme.SYSTEM, navigation.sidebar(),
                activeEntity, breadcrumbs, flash, signedInUser(request), csrfTokens.forRequest(request));
    }

    /**
     * The chrome of the sign-in page: an empty sidebar, no breadcrumbs and no user (the page is not part of the
     * shell, and whoever sees it is not signed in).
     */
    public PageChrome loginChrome(HttpServletRequest request) {
        return new PageChrome(settings.title(), basePath.value(), PageChrome.Theme.SYSTEM, List.of(), null,
                List.of(), List.of(), null, csrfTokens.forRequest(request));
    }

    /** The name of the signed-in user, or {@code null} when nobody is signed in. */
    private static String signedInUser(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        String name = principal == null ? null : principal.getName();
        return name == null || name.isBlank() ? null : name;
    }
}
