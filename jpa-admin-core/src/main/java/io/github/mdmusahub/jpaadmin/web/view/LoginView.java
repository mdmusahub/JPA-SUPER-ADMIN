package io.github.mdmusahub.jpaadmin.web.view;

import java.util.Objects;

/**
 * Data for the login page. The page also receives a {@link PageChrome} (with an empty sidebar) for the base path,
 * theme and CSRF token.
 *
 * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
 *
 * @param title        title shown above the form
 * @param error        message after a failed sign-in, or {@code null}; it never says which field was wrong
 * @param logoutNotice whether to show "You have been signed out"
 */
public record LoginView(String title, String error, boolean logoutNotice) {

    /** Template (view) name. */
    public static final String TEMPLATE = "jpa-admin/login";

    public LoginView {
        Objects.requireNonNull(title, "title");
    }
}
