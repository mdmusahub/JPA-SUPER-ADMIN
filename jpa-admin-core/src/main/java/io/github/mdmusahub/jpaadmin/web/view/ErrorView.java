package io.github.mdmusahub.jpaadmin.web.view;

import java.util.Objects;

/**
 * Data for error pages. The text says what went wrong and how to fix it, and never contains SQL, table names or
 * stack traces (SEC-12).
 *
 * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
 *
 * @param status  HTTP status code, 100 to 599
 * @param heading short title
 * @param hint    what the user can do next
 */
public record ErrorView(int status, String heading, String hint) {

    /** Template (view) name. */
    public static final String TEMPLATE = "jpa-admin/error";

    public ErrorView {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status must be an HTTP status code");
        }
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(hint, "hint");
    }
}
