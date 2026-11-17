package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.ListView;
import java.util.List;
import java.util.Objects;

/**
 * One page of an entity's list, as display values: the columns, the rows (one cell per column, never an entity),
 * how many records match in all, the query that was really run, and the columns a search looks in.
 *
 * <p>{@link #query()} is the <em>effective</em> query: a requested page past the end has become the last page, and a
 * sort the entity cannot do has been dropped. Links must be built from it, not from the request.
 *
 * <p>{@link #searched()} holds header labels only, never a key or a value. Only visible, non-sensitive text columns
 * are in it, so it can be shown to the visitor to say what a search covers.
 *
 * <p>Internal, not stable API. Public because the controller and the preview build the list page from it.
 *
 * @param columns  the columns, in display order
 * @param rows     the rows of this page; each has exactly one cell per column
 * @param total    how many records match the search, over all pages
 * @param query    the query that was run
 * @param searched header labels of the columns a search looks in, in display order; empty when nothing is searchable
 */
public record ListPage(List<ListColumn> columns, List<ListView.Row> rows, long total, ListQuery query,
                       List<String> searched) {

    public ListPage {
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        Objects.requireNonNull(query, "query");
        searched = List.copyOf(Objects.requireNonNull(searched, "searched"));
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        for (ListView.Row row : rows) {
            if (row.cells().size() != columns.size()) {
                throw new IllegalArgumentException("each row needs exactly one cell per column");
            }
        }
    }
}
