package io.github.mdmusahub.jpaadmin.service;

import java.util.List;
import java.util.Objects;

/**
 * What the {@code hide} parameters of a list request came to, after every value was checked (see {@link HiddenColumns}).
 * Holds column keys only, never a typed value that was refused.
 *
 * <p>Internal, not stable API. Public because the controller reads it.
 *
 * @param hidden          the keys of the columns to hide, in column order, without repeats; empty when nothing is hidden
 * @param everyColumnHidden whether the request asked to hide every column that can be hidden. {@code hidden} is then empty
 *                        (the request is ignored as a whole) and the page tells the visitor why
 */
public record HiddenParse(List<String> hidden, boolean everyColumnHidden) {

    /** Nothing asked, or nothing acceptable. */
    static final HiddenParse NONE = new HiddenParse(List.of(), false);

    public HiddenParse {
        hidden = List.copyOf(Objects.requireNonNull(hidden, "hidden"));
        if (everyColumnHidden && !hidden.isEmpty()) {
            throw new IllegalArgumentException("a request that hides every column hides none");
        }
    }
}
