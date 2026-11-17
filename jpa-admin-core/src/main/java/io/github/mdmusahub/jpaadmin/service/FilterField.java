package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.ListView.Filters.Definition.Kind;
import java.util.List;
import java.util.Objects;

/**
 * A field of an entity that may be filtered, with the kind of filter it gets. Decided from the metadata alone
 * ({@link ListFilters#fields}); a field that is hidden, sensitive or of a kind with no filter has none.
 *
 * <p>Package-private: it is a rule, not API.
 *
 * @param key          attribute name
 * @param label        humanized name
 * @param kind         kind of filter
 * @param javaType     Java type of the attribute (the bounds of a range are converted to it)
 * @param enumValues   constants of an enum field, in declaration order; empty otherwise
 * @param targetEntity URL name of a relation's target, {@code null} for other kinds
 */
record FilterField(String key, String label, Kind kind, Class<?> javaType, List<String> enumValues,
                   String targetEntity) {

    FilterField {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(javaType, "javaType");
        enumValues = List.copyOf(Objects.requireNonNull(enumValues, "enumValues"));
    }
}
