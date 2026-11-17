package io.github.mdmusahub.jpaadmin.service;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of reading the {@code filter.*} parameters of a request: the filters that are valid (in the entity's
 * column order, at most {@value ListQuery#MAX_FILTERS}) and a note for everything that was ignored or changed. It never
 * fails. A relation filter is only checked against the metadata here; whether its target id exists is checked when the
 * page is read (see {@link ListQueryService#list}).
 *
 * <p>Internal, not stable API. Public because the controller uses it.
 *
 * @param filters the valid filters
 * @param notes   what to tell the visitor
 */
public record FilterParse(List<ActiveFilter> filters, List<FilterNote> notes) {

    public FilterParse {
        filters = List.copyOf(Objects.requireNonNull(filters, "filters"));
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
    }
}
