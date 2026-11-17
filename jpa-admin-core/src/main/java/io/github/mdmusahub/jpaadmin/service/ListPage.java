package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.ListView;
import java.util.List;
import java.util.Objects;

/**
 * One page of an entity's list, as display values: the columns, the rows (one cell per column, never an entity),
 * how many records match in all, the query that was really run, the columns a search looks in, the filter controls
 * on offer and what to tell the visitor about the filters.
 *
 * <p>{@link #query()} is the <em>effective</em> query: a requested page past the end has become the last page, a
 * sort the entity cannot do has been dropped, and so has a filter the entity cannot do or whose relation choice does not
 * exist. Links must be built from it, not from the request.
 *
 * <p>{@link #searched()} holds header labels only, never a key or a value. Only visible, non-sensitive text columns
 * are in it, so it can be shown to the visitor to say what a search covers. {@link #controls()} holds only fields that
 * may be filtered; a relation control lists labels of its target's records, never a hidden entity's.
 *
 * <p>Internal, not stable API. Public because the controller and the preview build the list page from it.
 *
 * @param columns  the columns, in display order
 * @param rows     the rows of this page; each has exactly one cell per column
 * @param total    how many records match the search and the filters, over all pages
 * @param query    the query that was run
 * @param searched header labels of the columns a search looks in, in display order; empty when nothing is searchable
 * @param controls the filter controls on offer, in column order
 * @param notes    what the service found out about the filters (a relation choice that does not exist, a relation
 *                 filter that cannot be offered)
 * @param relationIds for each row, for each column, the identifier (as text) of the record a relation cell points at,
 *                 or {@code null}; empty when the page carries none (the pages of the preview that show no link). Used only
 *                 to build links: the factory links a cell only when its column names a target that is in the console
 */
public record ListPage(List<ListColumn> columns, List<ListView.Row> rows, long total, ListQuery query,
                       List<String> searched, List<FilterControl> controls, List<FilterNote> notes,
                       List<List<String>> relationIds) {

    /** A page with no relation ids: its relation cells carry no link. */
    public ListPage(List<ListColumn> columns, List<ListView.Row> rows, long total, ListQuery query,
                    List<String> searched, List<FilterControl> controls, List<FilterNote> notes) {
        this(columns, rows, total, query, searched, controls, notes, List.of());
    }

    public ListPage {
        relationIds = copyOfIds(Objects.requireNonNull(relationIds, "relationIds"));
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        Objects.requireNonNull(query, "query");
        searched = List.copyOf(Objects.requireNonNull(searched, "searched"));
        controls = List.copyOf(Objects.requireNonNull(controls, "controls"));
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        for (ListView.Row row : rows) {
            if (row.cells().size() != columns.size()) {
                throw new IllegalArgumentException("each row needs exactly one cell per column");
            }
        }
        if (!relationIds.isEmpty()) {
            if (relationIds.size() != rows.size()) {
                throw new IllegalArgumentException("relationIds needs one entry per row, or none at all");
            }
            for (List<String> ids : relationIds) {
                if (ids.size() != columns.size()) {
                    throw new IllegalArgumentException("each relationIds entry needs one value per column");
                }
            }
        }
    }

    /** An immutable copy that allows {@code null} values, which {@code List.copyOf} refuses. */
    private static List<List<String>> copyOfIds(List<List<String>> ids) {
        List<List<String>> copy = new java.util.ArrayList<>(ids.size());
        for (List<String> row : ids) {
            copy.add(java.util.Collections.unmodifiableList(new java.util.ArrayList<>(Objects.requireNonNull(row, "row"))));
        }
        return java.util.Collections.unmodifiableList(copy);
    }

    /** A page with no filter controls and no notes. */
    public ListPage(List<ListColumn> columns, List<ListView.Row> rows, long total, ListQuery query,
                    List<String> searched) {
        this(columns, rows, total, query, searched, List.of(), List.of());
    }
}
