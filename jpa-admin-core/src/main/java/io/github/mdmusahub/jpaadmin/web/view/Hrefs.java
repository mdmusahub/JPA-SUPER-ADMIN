package io.github.mdmusahub.jpaadmin.web.view;

import java.util.Objects;

/**
 * The one rule for every link in the view-model records (D-28). Package-private: it is not a record and not API.
 *
 * <p>A link is a relative path that starts with a single {@code /}. It has no scheme (so no {@code javascript:}), no
 * {@code //} in its path (a protocol-relative URL), no backslash and no whitespace or control character (browsers
 * strip tabs and line breaks, so {@code /<tab>/host} would become {@code //host}). The query and fragment may
 * contain {@code //} and {@code :}, because those are legal data there, but the code that builds a link must
 * percent-encode every parameter value. Error messages name the field and never echo the value.
 */
final class Hrefs {

    private Hrefs() {
    }

    /** Checks a link that must be present. */
    static String require(String name, String href) {
        return check(name, Objects.requireNonNull(href, name));
    }

    /** Checks a link that may be absent. {@code null} passes. */
    static String optional(String name, String href) {
        return href == null ? null : check(name, href);
    }

    /** Whether {@code href} is the base path or lies below it. {@code /admin} does not contain {@code /administrator}. */
    static boolean isUnder(String basePath, String href) {
        return href.equals(basePath) || href.startsWith(basePath + "/") || href.startsWith(basePath + "?")
                || href.startsWith(basePath + "#");
    }

    private static String check(String name, String href) {
        if (!href.startsWith("/")) {
            throw new IllegalArgumentException(name + " must be a relative path that starts with '/'");
        }
        int pathEnd = href.length();
        for (int i = 0; i < href.length(); i++) {
            char c = href.charAt(i);
            if (c == '\\' || Character.isISOControl(c) || Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                throw new IllegalArgumentException(name + " must not contain a backslash, whitespace or a control character");
            }
            if (pathEnd == href.length() && (c == '?' || c == '#')) {
                pathEnd = i;
            }
        }
        if (href.substring(0, pathEnd).contains("//")) {
            throw new IllegalArgumentException(name + " must not contain '//' in its path");
        }
        return href;
    }
}
