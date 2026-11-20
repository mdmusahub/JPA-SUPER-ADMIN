package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a list request asks for, after every part of it has been checked: the search text, the sort, the page and the
 * page size. Everything in the address is untrusted (SEC-2), so {@link #parse} is the only way in from a request, and
 * it never fails: a part that is not acceptable falls back to its default and the page still renders.
 *
 * <p>The rules, in one place:
 * <ul>
 *   <li><b>Search</b> ({@code q}): control characters become spaces, the text is trimmed and cut to
 *       {@value #MAX_SEARCH_LENGTH} characters (code points, so a character is never cut in half). Empty means no
 *       search.</li>
 *   <li><b>Sort</b> ({@code sort}, {@code dir}): the key must be a sortable column of the entity (visible, not
 *       sensitive; a relation only when its target has a label, D-60). Any other key means "no sort". With a valid
 *       key, {@code dir} is {@code asc} or
 *       {@code desc} in any letter case; anything else (or nothing) means {@code asc}. A direction without a key is
 *       ignored.</li>
 *   <li><b>Page</b> ({@code page}, zero-based): a whole number of digits only. Anything else (empty, negative,
 *       non-numeric) means page 0. A number too big for an {@code int} counts as {@link Integer#MAX_VALUE}, which the
 *       service then clamps to the last page. The page returned by the service is always a real page.</li>
 *   <li><b>Size</b> ({@code size}): one of {@link #PAGE_SIZES}. Anything else means the default size, which is
 *       {@value #DEFAULT_SIZE} unless the host set {@code jpa.admin.page-size} (Phase 13, D-119); the query carries it as
 *       {@link #defaultSize()} so that a link can leave {@code size} out exactly when it equals the default.</li>
 *   <li><b>Filters</b> ({@code filter.*}): read by {@code ListQueryService#parseFilters}, not by {@link #parse}; the
 *       checked result is carried here as {@link ActiveFilter}s, at most {@value #MAX_FILTERS}, one per field.
 *       Every change of the filter set starts again at page 0 (the old page number would mean other rows).</li>
 * </ul>
 *
 * <p>Internal, not stable API. Public because the controller and the preview build it.
 *
 * @param search the checked search text, empty for none
 * @param sort   key of the sort column, or {@code null} for the default order (by identifier)
 * @param dir    {@code asc} or {@code desc}; {@code null} exactly when {@code sort} is {@code null}
 * @param page   requested page, zero-based, not negative
 * @param size   rows per page, one of {@link #PAGE_SIZES}
 * @param filters the filters in force, in column order, at most {@value #MAX_FILTERS}
 * @param hidden  the keys of the columns the list does not show, in column order, without repeats (Phase 12, D-112); the
 *                names are checked against the entity's columns by {@code ListQueryService}, never trusted here
 * @param defaultSize the page size an address without {@code size} uses, one of {@link #PAGE_SIZES}: the console's setting
 *                {@code jpa.admin.page-size}, {@value #DEFAULT_SIZE} when the host did not set it (Phase 13, D-119). Not a
 *                request value; the older constructors set it to {@value #DEFAULT_SIZE}
 */
public record ListQuery(String search, String sort, String dir, int page, int size, List<ActiveFilter> filters,
                        List<String> hidden, int defaultSize) {

    /** The page sizes the console offers (FR-10). */
    public static final List<Integer> PAGE_SIZES = List.of(10, 25, 50, 100);

    /** The page size when none, or an unacceptable one, is asked for and the host did not set {@code jpa.admin.page-size}. */
    public static final int DEFAULT_SIZE = 25;

    /** The longest search text, in characters. Longer text is cut, not refused. */
    public static final int MAX_SEARCH_LENGTH = 100;

    /** The most filters that can be in force at once (a range with two bounds is one filter). */
    public static final int MAX_FILTERS = 10;

    /** Ascending order. */
    public static final String ASC = "asc";

    /** Descending order. */
    public static final String DESC = "desc";

    /** A query with no filters and no hidden column. */
    public ListQuery(String search, String sort, String dir, int page, int size) {
        this(search, sort, dir, page, size, List.of(), List.of(), DEFAULT_SIZE);
    }

    /** A query with filters and no hidden column. */
    public ListQuery(String search, String sort, String dir, int page, int size, List<ActiveFilter> filters) {
        this(search, sort, dir, page, size, filters, List.of(), DEFAULT_SIZE);
    }

    /** A query with filters and hidden columns, and the console's own default page size of {@value #DEFAULT_SIZE}. */
    public ListQuery(String search, String sort, String dir, int page, int size, List<ActiveFilter> filters,
                     List<String> hidden) {
        this(search, sort, dir, page, size, filters, hidden, DEFAULT_SIZE);
    }

    public ListQuery {
        Objects.requireNonNull(search, "search");
        filters = List.copyOf(Objects.requireNonNull(filters, "filters"));
        hidden = List.copyOf(Objects.requireNonNull(hidden, "hidden"));
        if (hidden.stream().distinct().count() != hidden.size()) {
            throw new IllegalArgumentException("a column is hidden once");
        }
        if (filters.size() > MAX_FILTERS) {
            throw new IllegalArgumentException("at most " + MAX_FILTERS + " filters");
        }
        if (filters.stream().map(ActiveFilter::key).distinct().count() != filters.size()) {
            throw new IllegalArgumentException("one filter per field");
        }
        if ((sort == null) != (dir == null)) {
            throw new IllegalArgumentException("sort and dir must both be set or both be null");
        }
        if (dir != null && !dir.equals(ASC) && !dir.equals(DESC)) {
            throw new IllegalArgumentException("dir must be 'asc' or 'desc'");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (!PAGE_SIZES.contains(size)) {
            throw new IllegalArgumentException("size must be one of " + PAGE_SIZES);
        }
        if (!PAGE_SIZES.contains(defaultSize)) {
            throw new IllegalArgumentException("defaultSize must be one of " + PAGE_SIZES);
        }
    }

    /** A request for the first page with every default and no search. */
    public static ListQuery defaults() {
        return new ListQuery("", null, null, 0, DEFAULT_SIZE);
    }

    /** A request for the first page with every default, the page size being the console's configured one. */
    public static ListQuery defaults(int defaultSize) {
        if (!PAGE_SIZES.contains(defaultSize)) {
            throw new IllegalArgumentException("defaultSize must be one of " + PAGE_SIZES);
        }
        return new ListQuery("", null, null, 0, defaultSize, List.of(), List.of(), defaultSize);
    }

    /**
     * Checks the five request parameters of an entity's list page. Never throws.
     *
     * @param entity   the entity being listed; its columns decide which sort keys are acceptable
     * @param registry the entities the console manages; a relation column is sortable only when its target is in it
     *                 and has a label
     * @param q        the {@code q} parameter, or {@code null}
     * @param sort     the {@code sort} parameter, or {@code null}
     * @param dir      the {@code dir} parameter, or {@code null}
     * @param page     the {@code page} parameter, or {@code null}
     * @param size     the {@code size} parameter, or {@code null}
     */
    public static ListQuery parse(AdminEntity entity, AdminEntityRegistry registry, String q, String sort, String dir,
                                  String page, String size) {
        return parse(entity, registry, q, sort, dir, page, size, DEFAULT_SIZE);
    }

    /**
     * As {@link #parse(AdminEntity, AdminEntityRegistry, String, String, String, String, String)}, for a console whose
     * default page size is {@code defaultSize} (Phase 13, D-119): a missing or unacceptable {@code size} means that size.
     *
     * @param defaultSize one of {@link #PAGE_SIZES}
     * @throws IllegalArgumentException if {@code defaultSize} is not one of {@link #PAGE_SIZES}
     */
    public static ListQuery parse(AdminEntity entity, AdminEntityRegistry registry, String q, String sort, String dir,
                                  String page, String size, int defaultSize) {
        Objects.requireNonNull(entity, "entity");
        String checkedSort = sort != null && sortableKeys(entity, registry).contains(sort) ? sort : null;
        return new ListQuery(normalizeSearch(q), checkedSort, checkedSort == null ? null : direction(dir),
                parsePage(page), parseSize(size, defaultSize), List.of(), List.of(), defaultSize);
    }

    /** The same query on another page. */
    public ListQuery withPage(int newPage) {
        return new ListQuery(search, sort, dir, newPage, size, filters, hidden, defaultSize);
    }

    /** The same query with another page size, back on the first page (the old page number would mean other rows). */
    public ListQuery withSize(int newSize) {
        return new ListQuery(search, sort, dir, 0, newSize, filters, hidden, defaultSize);
    }

    /** The same search sorted by {@code key} and {@code direction}, back on the first page. */
    public ListQuery withSort(String key, String direction) {
        return new ListQuery(search, key, direction, 0, size, filters, hidden, defaultSize);
    }

    /** The same query with these filters instead, back on the first page (the old page number would mean other rows). */
    public ListQuery withFilters(List<ActiveFilter> newFilters) {
        return new ListQuery(search, sort, dir, 0, size, newFilters, hidden, defaultSize);
    }

    /**
     * The same query with another search text, back on the first page. The text is taken as it is (the caller has cleaned it).
     * Used to build the address that clears the search (Phase 13).
     */
    public ListQuery withSearch(String newSearch) {
        return new ListQuery(newSearch, sort, dir, 0, size, filters, hidden, defaultSize);
    }

    /**
     * The same query with the filters that were read from the same address. Unlike {@link #withFilters}, the page stays:
     * the address that carries these filters also carries that page, and a link built for it (next page, Back) must keep
     * working. Only a <em>change</em> of the filter set, made through {@link #withFilters}, starts again at page 0.
     */
    public ListQuery withParsedFilters(List<ActiveFilter> parsed) {
        return new ListQuery(search, sort, dir, page, size, parsed, hidden, defaultSize);
    }

    /**
     * The same query hiding these columns instead, back on the first page: a change of the columns is a change of what the
     * list shows, and the old page number would mean other rows (decision 9 of Phase 12, D-112).
     */
    public ListQuery withHidden(List<String> newHidden) {
        return new ListQuery(search, sort, dir, 0, size, filters, newHidden, defaultSize);
    }

    /** The same query showing every column again, back on the first page. */
    public ListQuery withoutHidden() {
        return withHidden(List.of());
    }

    /**
     * The same query with the hidden columns that were read from the same address. Unlike {@link #withHidden}, the page stays:
     * the address that carries these columns also carries that page, and a link built for it (next page, Back) must keep
     * working. Only a <em>change</em> of the hidden set, made through {@link #withHidden}, starts again at page 0.
     */
    public ListQuery withParsedHidden(List<String> parsed) {
        return new ListQuery(search, sort, dir, page, size, filters, parsed, defaultSize);
    }

    /** The same query without the filter on {@code key}, back on the first page. */
    public ListQuery withoutFilter(String key) {
        return withFilters(filters.stream().filter(filter -> !filter.key().equals(key)).toList());
    }

    /** The same query without any filter, back on the first page. */
    public ListQuery withoutFilters() {
        return withFilters(List.of());
    }

    private static Set<String> sortableKeys(AdminEntity entity, AdminEntityRegistry registry) {
        return ListColumns.of(entity, registry).stream().filter(ListColumn::sortable).map(ListColumn::key)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String direction(String raw) {
        return raw != null && raw.strip().equalsIgnoreCase(DESC) ? DESC : ASC;
    }

    static String normalizeSearch(String raw) {
        String stripped = cleaned(raw);
        if (stripped.codePointCount(0, stripped.length()) <= MAX_SEARCH_LENGTH) {
            return stripped;
        }
        int end = stripped.offsetByCodePoints(0, MAX_SEARCH_LENGTH);
        return stripped.substring(0, end).strip();
    }

    /**
     * Whether {@link #parse} cut the search text because it was longer than {@value #MAX_SEARCH_LENGTH} characters.
     * It follows the same cleaning as the parser (control characters become spaces, the ends are trimmed), so a text
     * that only looked long because of spaces around it is not reported as cut.
     *
     * @param raw the {@code q} parameter, or {@code null}
     */
    public static boolean searchWasCut(String raw) {
        String stripped = cleaned(raw);
        return stripped.codePointCount(0, stripped.length()) > MAX_SEARCH_LENGTH;
    }

    private static String cleaned(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(Math.min(raw.length(), 4 * MAX_SEARCH_LENGTH));
        raw.codePoints().forEach(codePoint -> cleaned.appendCodePoint(Character.isISOControl(codePoint) ? ' ' : codePoint));
        return cleaned.toString().strip();
    }

    static int parsePage(String raw) {
        if (raw == null) {
            return 0;
        }
        String digits = raw.strip();
        if (digits.isEmpty() || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return 0;
        }
        // Ten digits can already overflow an int, so anything longer than nine is "far past the end".
        return digits.length() > 9 ? Integer.MAX_VALUE : Integer.parseInt(digits);
    }

    static int parseSize(String raw) {
        return parseSize(raw, DEFAULT_SIZE);
    }

    static int parseSize(String raw, int defaultSize) {
        if (!PAGE_SIZES.contains(defaultSize)) {
            throw new IllegalArgumentException("defaultSize must be one of " + PAGE_SIZES);
        }
        if (raw == null) {
            return defaultSize;
        }
        String digits = raw.strip();
        if (digits.isEmpty() || digits.length() > 3 || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return defaultSize;
        }
        int size = Integer.parseInt(digits);
        return PAGE_SIZES.contains(size) ? size : defaultSize;
    }
}
