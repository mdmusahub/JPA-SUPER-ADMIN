package io.github.mdmusahub.jpaadmin.service;

import java.util.List;
import java.util.Objects;

/**
 * The values a "Duplicate" form would start from: a copy of one record's fields, minus everything that must not be
 * copied. It is what Phase 8's create form will be pre-filled with; Phase 7 builds and tests the rule, not the form.
 *
 * <p>Never copied: the identifier and the version (a copy is a new record), a sensitive field (its value is never read,
 * SEC-1), a hidden field, a collection, an embedded value and binary data. A to-one relation is copied as the target's
 * identifier, and only when the target is in the console. When uploads arrive (Phase 9) an upload field's stored file
 * is left out too, so two records never share one file; that rule lives in {@code DetailQueryService#copyable}.
 *
 * <p>Internal, not stable API.
 *
 * @param sourceId the identifier of the record that was copied, as text
 * @param values   the values to start from, in display order; a field with no value is not listed
 */
public record CopySeed(String sourceId, List<Value> values) {

    public CopySeed {
        Objects.requireNonNull(sourceId, "sourceId");
        values = List.copyOf(Objects.requireNonNull(values, "values"));
    }

    /**
     * One field's starting value.
     *
     * @param key  the attribute name
     * @param text the value as plain text: numbers without grouping, enums by constant name, dates in ISO form, a
     *             relation as the target's identifier
     */
    public record Value(String key, String text) {

        public Value {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(text, "text");
        }
    }
}
