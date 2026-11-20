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
 * order ({@code q}, the {@code filter.*} parameters in column order, one {@code hide} for each hidden column in column order,
 * {@code sort}, {@code dir}, {@code page}, {@code size}). The default page size (the query's own
 * {@link ListQuery#defaultSize()}, {@code jpa.admin.page-size}) and page 0 are left out, so the first page of an unsorted
 * list is the plain address. A value is encoded with every character outside
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
        return link(path, query);
    }

    /**
     * The address of the CSV export of the list in {@code state}: the entity's {@code export.csv} with the search, the
     * filters and the sort, and nothing else. The page and the page size are left out because the file is not one page: it
     * holds every matching record up to the cap. Every value is percent-encoded by the same writer as the other links. The
     * hidden columns travel as well (Phase 12, D-113): the file holds exactly the columns the list shows.
     */
    String export(ListQuery state) {
        return link(path + "/export.csv", new ListQuery(state.search(), state.sort(), state.dir(), 0,
                state.defaultSize(), state.filters(), state.hidden(), state.defaultSize()));
    }

    // ---- links of the page state (Phase 13, D-118): same writer, same encoding -------------------------------------------

    /**
     * The address of this list in the state the server accepted: search, filters, hidden columns, sort and size, and never a
     * page (the saved views script stores and rebuilds exactly this; a page number would mean other rows).
     */
    String state(ListQuery query) {
        return to(query.withPage(0));
    }

    /** The address that shows every column again and keeps the rest of the state, from the first page. */
    String showAllColumns(ListQuery query) {
        return to(query.withoutHidden());
    }

    /**
     * The address "Clear search" and "Clear search and filters" lead to: no search, no filter, no sort and the default page size,
     * and the same hidden columns (D-118: a visitor who chose the columns did not ask to lose that choice by clearing a search).
     */
    String clearSearch(ListQuery query) {
        return to(ListQuery.defaults(query.defaultSize()).withHidden(query.hidden()));
    }

    // ---- links of the detail and delete pages (Phase 7, D-65): same writer, same encoding ---------------------------

    /**
     * The detail page of one record, carrying the list state it was opened from so that the page can lead back to the
     * same list. {@code null} when the id cannot be written into an address (see {@link #addressable}): the cell is then
     * shown without a link.
     */
    String detail(String id, ListQuery state) {
        return addressable(id) ? link(path + "/" + encode(id), state) : null;
    }

    /** The address the delete form of one record posts to, carrying the list state. {@code null} as for {@link #detail}. */
    String delete(String id, ListQuery state) {
        return addressable(id) ? link(path + "/" + encode(id) + "/delete", state) : null;
    }

    /** The address the bulk-delete form posts to, carrying the list state. */
    String bulkDelete(ListQuery state) {
        return link(path + "/bulk-delete", state);
    }

    /**
     * Where the visitor goes after a delete: the list they came from, with the same search, filters, sort and page size.
     * When at least one record was deleted the list's content changed, so it starts again at the first page (the rule of
     * D-62 for any change of what the list holds); when nothing was deleted the page stays. The list itself clamps a page
     * past the end (D-51).
     */
    String afterDelete(ListQuery state, boolean deletedSomething) {
        return to(deletedSomething ? state.withPage(0) : state);
    }

    // ---- links of the create and edit forms (Phase 8): same writer, same encoding ---------------------------------

    /** The name of the query parameter that carries the version a form was opened with (see {@link #update}). */
    static final String VERSION_PARAMETER = "jpa-admin-version";

    /** The address the create form posts to: the entity's collection. */
    String create() {
        return path;
    }

    /**
     * The address the edit form posts to: the record, and for an entity with a {@code @Version} the version the form was
     * opened with, so a save can tell that the form is stale. {@code null} as for {@link #detail} when the id has no address.
     */
    String update(String id, String version) {
        if (!addressable(id)) {
            return null;
        }
        String record = path + "/" + encode(id);
        return version == null ? record : record + "?" + VERSION_PARAMETER + "=" + encode(version);
    }

    // ---- links back to a form after an upload (Phase 9a): same writer, same encoding -----------------------------------

    /** The create form of this entity. */
    String newForm() {
        return path + "/new";
    }

    /** The edit form of one record. {@code null} as for {@link #detail} when the id has no address. */
    String editForm(String id) {
        return addressable(id) ? path + "/" + encode(id) + "/edit" : null;
    }

    /**
     * Where the visitor goes after a file was uploaded with a plain form (option A, no script): back to the form the upload
     * was for, the edit form when {@code recordId} is an id with an address and the create form otherwise. Built here, from the
     * entity of the console's own metadata and an id that passed {@link #addressable}; nothing in a request is ever a redirect
     * target (D-65).
     */
    String afterUpload(String recordId) {
        String edit = recordId == null ? null : editForm(recordId);
        return edit != null ? edit : newForm();
    }

    /**
     * Where the visitor goes after a record was created: the list, from its first page (what the list holds changed, the
     * rule of D-62), with the search, filters, sort and page size of {@code state}. Never taken from the request (D-65).
     */
    String afterSave(ListQuery state) {
        return to(state.withPage(0));
    }

    /** Where the visitor goes after a record was changed: its page, or the list when the id has no address. */
    String afterEdit(String id) {
        String record = detail(id, ListQuery.defaults());
        return record != null ? record : to(ListQuery.defaults());
    }

    /**
     * Whether an id can be written into a path segment that a server will deliver back unchanged: not empty, no slash
     * (Tomcat refuses an encoded one), not {@code .} or {@code ..}, no control character. An id for which this is false
     * has no link; the record is still listed.
     */
    static boolean addressable(String id) {
        if (id == null || id.isEmpty() || id.length() > 255 || id.equals(".") || id.equals("..")) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '/' || c == '\\' || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }

    private static String link(String path, ListQuery query) {
        List<String> parameters = new ArrayList<>(5);
        if (!query.search().isEmpty()) {
            parameters.add("q=" + encode(query.search()));
        }
        for (ActiveFilter filter : query.filters()) {
            addFilter(parameters, filter);
        }
        for (String column : query.hidden()) {
            parameters.add("hide=" + encode(column));
        }
        if (query.sort() != null) {
            parameters.add("sort=" + encode(query.sort()));
            parameters.add("dir=" + encode(query.dir()));
        }
        if (query.page() > 0) {
            parameters.add("page=" + query.page());
        }
        if (query.size() != query.defaultSize()) {
            // The default is the console's setting (Phase 13, D-119), not the constant 25: with a default of 50, a link for
            // 25 rows has to say so, and a link for 50 rows needs no size at all.
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
