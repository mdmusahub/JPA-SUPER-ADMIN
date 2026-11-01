package io.github.mdmusahub.jpaadmin.web.view;

import java.util.List;
import java.util.Objects;

/**
 * Shell data used by every page: title, base path, sidebar, breadcrumbs, flash messages and the CSRF token.
 *
 * <p>Templates read it as the model attribute {@value #ATTRIBUTE}. The page's own record is the model attribute
 * {@value #VIEW_ATTRIBUTE}.
 *
 * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
 *
 * @param title        page title shown in the header and the browser tab
 * @param basePath     console path such as {@code /admin}, starts with a slash and is never the site root (SEC-14)
 * @param theme        initial theme; the browser may override it from {@code localStorage}
 * @param nav          sidebar groups
 * @param activeEntity URL name of the entity being shown, or {@code null} outside an entity
 * @param breadcrumbs  trail shown above the content
 * @param flash        one-time messages for this response
 * @param csrf         name and value of the CSRF token for forms and scripts (SEC-5)
 */
public record PageChrome(String title, String basePath, Theme theme, List<NavGroup> nav, String activeEntity,
                         List<Breadcrumb> breadcrumbs, List<FlashMessage> flash, Csrf csrf) {

    /** Model attribute name of the {@code PageChrome}. */
    public static final String ATTRIBUTE = "chrome";

    /** Model attribute name of the page's own record ({@code LoginView}, {@code ListView} and so on). */
    public static final String VIEW_ATTRIBUTE = "view";

    public PageChrome {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(basePath, "basePath");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(csrf, "csrf");
        if (!basePath.startsWith("/") || basePath.equals("/")) {
            throw new IllegalArgumentException("basePath must start with '/' and must not be the site root");
        }
        nav = List.copyOf(Objects.requireNonNull(nav, "nav"));
        breadcrumbs = List.copyOf(Objects.requireNonNull(breadcrumbs, "breadcrumbs"));
        flash = List.copyOf(Objects.requireNonNull(flash, "flash"));
    }

    /**
     * Initial theme choice.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     */
    public enum Theme { SYSTEM, LIGHT, DARK }

    /**
     * Sidebar group with its entities.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param label group name
     * @param items entities in the group
     */
    public record NavGroup(String label, List<NavItem> items) {
        public NavGroup {
            Objects.requireNonNull(label, "label");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    /**
     * Sidebar entry for one entity.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param entity URL name of the entity
     * @param label  display name
     * @param icon   icon name from the icon sprite
     * @param count  number of records
     */
    public record NavItem(String entity, String label, String icon, long count) {
        public NavItem {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(icon, "icon");
            if (count < 0) {
                throw new IllegalArgumentException("count must not be negative");
            }
        }
    }

    /**
     * One step of the breadcrumb trail.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param label text to show
     * @param href  link target, or {@code null} for the current page
     */
    public record Breadcrumb(String label, String href) {
        public Breadcrumb {
            Objects.requireNonNull(label, "label");
        }
    }

    /**
     * One-time message such as "Record saved".
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param level kind of message
     * @param text  message text, already escaped by the template on output
     */
    public record FlashMessage(Level level, String text) {
        public FlashMessage {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(text, "text");
        }

        /**
         * Kind of flash message.
         *
         * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
         */
        public enum Level { SUCCESS, INFO, WARNING, ERROR }
    }

    /**
     * CSRF token for forms (hidden field) and scripts (header).
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1); the token is
     * the request's own CSRF token, never a stored secret.
     *
     * @param parameterName form field name
     * @param headerName    request header name for scripts
     * @param token         token value
     */
    public record Csrf(String parameterName, String headerName, String token) {
        public Csrf {
            Objects.requireNonNull(parameterName, "parameterName");
            Objects.requireNonNull(headerName, "headerName");
            Objects.requireNonNull(token, "token");
        }
    }
}
