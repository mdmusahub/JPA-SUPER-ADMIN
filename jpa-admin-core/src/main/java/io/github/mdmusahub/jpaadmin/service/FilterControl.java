package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.ListView.Filters.Definition.Kind;
import java.util.List;
import java.util.Objects;

/**
 * One filter control the list offers: which field, what kind, and the choices of an enum, boolean or relation filter.
 * It is built from the metadata (and, for a relation, from the labels of the target's records), never from the request,
 * and a field that may not be filtered has no control. It holds no active value; those are in the query.
 *
 * <p>The options of a relation filter are the target's {@code id} and its label attribute only (the label is never a
 * sensitive attribute and the target is never a hidden entity), at most {@value ListQueryService#MAX_RELATION_OPTIONS} of
 * them. A target with more records gets no control at all, so a list is never silently cut short.
 *
 * <p>Internal, not stable API. Public because the controller and the preview build the filter panel from it.
 *
 * @param key     attribute name of the field
 * @param label   control label
 * @param kind    kind of filter
 * @param options choices for enum, boolean and relation filters, empty for the others
 */
public record FilterControl(String key, String label, Kind kind, List<FormView.Option> options) {

    public FilterControl {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(kind, "kind");
        options = List.copyOf(Objects.requireNonNull(options, "options"));
    }
}
