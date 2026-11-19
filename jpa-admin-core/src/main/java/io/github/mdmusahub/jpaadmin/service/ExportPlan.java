package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import java.util.List;
import java.util.Objects;

/**
 * What {@link ListQueryService#exportPlan} worked out once for one export: the entity, the columns that go into the file, how
 * many records match, the query that was really run (its sort and filters are the ones the entity can do) and the filter
 * conditions to bind again for every page.
 *
 * <p>The columns are the list's visible columns without the masked ones, in list order. The plan holds no row and no
 * value of a record.
 *
 * <p>Internal, not stable API. Public because {@link ListQueryService}, which a transactional proxy subclasses, hands it out.
 */
public final class ExportPlan {

    private final AdminEntity entity;
    private final List<ListColumn> columns;
    private final long total;
    private final ListQuery query;
    private final List<FilterPredicates.Spec> specs;

    ExportPlan(AdminEntity entity, List<ListColumn> columns, long total, ListQuery query,
               List<FilterPredicates.Spec> specs) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        this.total = total;
        this.query = Objects.requireNonNull(query, "query");
        this.specs = List.copyOf(Objects.requireNonNull(specs, "specs"));
    }

    /** The entity being exported. */
    public AdminEntity entity() {
        return entity;
    }

    /** The columns of the file, in order. Never a masked, hidden, collection, embedded or binary one. */
    public List<ListColumn> columns() {
        return columns;
    }

    /** How many records match the search and the filters, over all pages. */
    public long total() {
        return total;
    }

    /** The query that was run: the sort and filters the entity can do, with the page and size of the request. */
    public ListQuery query() {
        return query;
    }

    List<FilterPredicates.Spec> specs() {
        return specs;
    }
}
