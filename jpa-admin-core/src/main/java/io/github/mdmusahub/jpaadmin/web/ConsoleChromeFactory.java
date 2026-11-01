package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Breadcrumb;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;

/**
 * Builds the {@link PageChrome} that every console page shares: title, base path, initial theme, sidebar, breadcrumbs,
 * flash messages and the CSRF token.
 *
 * <p>The sidebar is empty until entity discovery arrives (Phase 3), and no flash message exists yet. Nothing here
 * reads a request parameter or a stored value, so nothing a visitor typed can reach the chrome.
 *
 * <p>Internal, not stable API. Public only because the auto-configuration module creates it.
 */
public final class ConsoleChromeFactory {

    private final BasePath basePath;
    private final ConsoleSettings settings;
    private final CsrfTokens csrfTokens;

    public ConsoleChromeFactory(BasePath basePath, ConsoleSettings settings, CsrfTokens csrfTokens) {
        this.basePath = Objects.requireNonNull(basePath, "basePath");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.csrfTokens = Objects.requireNonNull(csrfTokens, "csrfTokens");
    }

    /**
     * The chrome of a page with the given breadcrumbs. Every breadcrumb link must lie under the base path (the record
     * checks it).
     */
    public PageChrome chrome(HttpServletRequest request, List<Breadcrumb> breadcrumbs) {
        return new PageChrome(settings.title(), basePath.value(), PageChrome.Theme.SYSTEM, List.of(), null,
                breadcrumbs, List.of(), csrfTokens.forRequest(request));
    }

    /** The chrome of the sign-in page: an empty sidebar and no breadcrumbs (the page is not part of the shell). */
    public PageChrome loginChrome(HttpServletRequest request) {
        return chrome(request, List.of());
    }
}
