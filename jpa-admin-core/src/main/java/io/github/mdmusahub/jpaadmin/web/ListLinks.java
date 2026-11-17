package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.service.ActiveFilter;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the links of the list page: sort, page, first/previous/next/last and page size (D-28, D-29). It is the only
 * place that writes a query string, so the rule that <em>every value is percent-encoded</em> is kept in one spot.
 *
 * <p>A link is the entity's address plus the parts of the query that differ from the defaults, always in the same
 * order ({@code q}, the {@code filter.*} parameters in column order, {@code sort}, {@code dir}, {@code page}, {@code size}). The default page size and page 0 are left
 * out, so the first page of an unsorted list is the plain address. A value is encoded with every character outside
 * {@code A-Z a-z 0-9 - _ .} written as {@code %XX} bytes of UTF-8 (a space is {@code %20}, never a raw space or a
 * {@code +}), so a quote, a slash, a {@code #}, an {@code &}, a unicode letter or a pasted {@code http://x} can never
 * end the value, start another parameter, or break out of an HTML attribute.
 *
 * <p>Package-private: it is a rule, not API.
 */
final class ListLinks {

    private final String path;

    /**
     * @param basePath the console path, for example {@code /admin}
     * @param urlName  the entity's address segment (letters, digits and hyphens only, see the scanner)
     */
    ListLinks(String basePath, String urlName) {
        this.path = basePath + "/" + urlName;
    }

    /** The link for {@code query}. */
    String to(ListQuery query) {
        List<String> parameters = new ArrayList<>(5);
        if (!query.search().isEmpty()) {
            parameters.add("q=" + encode(query.search()));
        }
        for (ActiveFilter filter : query.filters()) {
            addFilter(parameters, filter);
        }
        if (query.sort() != null) {
            parameters.add("sort=" + encode(query.sort()));
            parameters.add("dir=" + encode(query.dir()));
        }
        if (query.page() > 0) {
            parameters.add("page=" + query.page());
        }
        if (query.size() != ListQuery.DEFAULT_SIZE) {
            parameters.add("size=" + query.size());
        }
        return parameters.isEmpty() ? path : path + "?" + String.join("&", parameters);
    }

    /**
     * {@code filter.key=value}, or for a range {@code filter.key.min} and {@code .max} (numbers) or {@code .from} and
     * {@code .to} (dates). The key is a name from the metadata and is encoded all the same; so is every value.
     */
    private static void addFilter(List<String> parameters, ActiveFilter filter) {
        String name = "filter." + encode(filter.key());
        switch (filter.kind()) {
            case NUMBER_RANGE -> {
                addIfPresent(parameters, name + ".min", filter.from());
                addIfPresent(parameters, name + ".max", filter.to());
            }
            case DATE_RANGE -> {
                addIfPresent(parameters, name + ".from", filter.from());
                addIfPresent(parameters, name + ".to", filter.to());
            }
            case TEXT, ENUM, BOOLEAN, RELATION -> parameters.add(name + "=" + encode(filter.value()));
        }
    }

    private static void addIfPresent(List<String> parameters, String name, String value) {
        if (value != null) {
            parameters.add(name + "=" + encode(value));
        }
    }

    /** The link a column header uses: sorted by {@code key}, ascending first, and reversed when it is already sorted. */
    String sortBy(ListQuery current, String key) {
        boolean ascendingNow = key.equals(current.sort()) && ListQuery.ASC.equals(current.dir());
        return to(current.withSort(key, ascendingNow ? ListQuery.DESC : ListQuery.ASC));
    }

    /** Percent-encodes every byte of the UTF-8 form of {@code value} except letters, digits and {@code - _ .}. */
    static String encode(String value) {
        // URLEncoder writes a space as '+' and leaves '*' alone; '%20' and an explicit '%2A' are unambiguous.
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A");
    }
}
