package io.github.mdmusahub.jpaadmin.web;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The console's base path, for example {@code /admin}. Every console link and route is built from it.
 *
 * <p>The path follows one strict rule (SEC-14): it starts with {@code /}, is never just {@code /}, has no trailing
 * slash, and holds one or more segments separated by single slashes. A segment starts with a letter or digit and
 * continues with letters, digits, {@code -} or {@code _}. At most {@value #MAX_LENGTH} characters. Anything else is
 * rejected, never rewritten: "normalized" here means the canonical form, so {@code /admin/} is an error, not
 * {@code /admin}. The rule is a subset of the link rule in {@code web.view} (a local path, no scheme, no {@code //},
 * no backslash, no whitespace), so {@link #value()} can always be a link.
 *
 * <p>Internal, not stable API. Public only because the auto-configuration module builds it.
 */
public final class BasePath {

    /** The console path when {@code jpa.admin.path} is not set. */
    public static final String DEFAULT = "/admin";

    /** Longest accepted path, in characters. */
    public static final int MAX_LENGTH = 100;

    private static final String ASSETS_SEGMENT = "/assets";
    private static final String SEGMENT = "[A-Za-z0-9][A-Za-z0-9_-]*";
    private static final Pattern VALID = Pattern.compile("/" + SEGMENT + "(?:/" + SEGMENT + ")*");
    private static final Pattern RELATIVE = Pattern.compile("(?:/[A-Za-z0-9._~-]+)*");

    private final String value;

    private BasePath(String value) {
        this.value = value;
    }

    /** Whether {@code path} is an acceptable console path. {@code null} is not. */
    public static boolean isValid(String path) {
        return path != null && path.length() <= MAX_LENGTH && VALID.matcher(path).matches();
    }

    /**
     * Wraps a console path.
     *
     * @throws IllegalArgumentException if the path breaks the rule; the message never repeats the input
     */
    public static BasePath of(String path) {
        if (!isValid(path)) {
            throw new IllegalArgumentException("The console path is not valid. Use '/' followed by letters, digits, "
                    + "'-' or '_' (more segments are separated by single slashes), for example " + DEFAULT);
        }
        return new BasePath(path);
    }

    /** The console path, for example {@code /admin}. */
    public String value() {
        return value;
    }

    /**
     * A fixed route below the console path. {@code relative} is empty or starts with {@code /} and holds only
     * letters, digits, {@code .}, {@code _}, {@code ~} and {@code -} between slashes, with no {@code .} or
     * {@code ..} segment. It carries no query: code that adds query values must percent-encode them.
     *
     * @throws IllegalArgumentException if {@code relative} breaks that rule; the message never repeats the input
     */
    public String link(String relative) {
        Objects.requireNonNull(relative, "relative");
        if (!RELATIVE.matcher(relative).matches() || hasDotSegment(relative)) {
            throw new IllegalArgumentException("A console route must be empty or start with '/' and hold only "
                    + "letters, digits, '.', '_', '~' and '-' between slashes, with no '.' or '..' segment");
        }
        return value + relative;
    }

    /** The URL pattern of the console's own assets, {@code {path}/assets/**}. */
    public String assetsPattern() {
        return value + ASSETS_SEGMENT + "/**";
    }

    private static boolean hasDotSegment(String relative) {
        for (String segment : relative.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BasePath that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
