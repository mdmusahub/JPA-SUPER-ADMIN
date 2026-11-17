package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.ListView.Filters.Definition.Kind;
import java.util.Objects;

/**
 * One filter that has been checked and is in force: the key of a filterable field of the entity, the kind of filter,
 * and its value in a canonical text form. Nothing in it is trusted text any more: the key is a name the metadata gave
 * out, and the value has been parsed and written back (a number as plain digits, a date as {@code yyyy-MM-dd}, an enum
 * as the name of one of its constants, a boolean as {@code true} or {@code false}). The service turns it into a bound
 * parameter; {@code ListLinks} writes it back into an address, percent-encoded.
 *
 * <p>Single-value kinds ({@code TEXT}, {@code ENUM}, {@code BOOLEAN}, {@code RELATION}) use {@code value}. Range kinds
 * ({@code NUMBER_RANGE}, {@code DATE_RANGE}) use {@code from} (the minimum or the first day) and {@code to} (the maximum
 * or the last day); at least one is set.
 *
 * <p>Internal, not stable API. Public because the controller and the preview build it.
 *
 * @param key   attribute name of the filtered field
 * @param kind  kind of filter
 * @param value the value of a single-value filter, otherwise {@code null}
 * @param from  the lower bound of a range filter, otherwise {@code null}
 * @param to    the upper bound of a range filter, otherwise {@code null}
 */
public record ActiveFilter(String key, Kind kind, String value, String from, String to) {

    public ActiveFilter {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(kind, "kind");
        boolean range = kind == Kind.NUMBER_RANGE || kind == Kind.DATE_RANGE;
        if (range) {
            if (value != null || (from == null && to == null)) {
                throw new IllegalArgumentException("a range filter needs a lower or an upper bound and no single value");
            }
        } else if (value == null || value.isBlank() || from != null || to != null) {
            throw new IllegalArgumentException("a single-value filter needs a value and no bounds");
        }
    }

    /** A filter with one value. */
    public static ActiveFilter of(String key, Kind kind, String value) {
        return new ActiveFilter(key, kind, value, null, null);
    }

    /** A range filter; either bound may be {@code null}, not both. */
    public static ActiveFilter range(String key, Kind kind, String from, String to) {
        return new ActiveFilter(key, kind, null, from, to);
    }
}
