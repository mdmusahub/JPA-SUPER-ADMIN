package io.github.mdmusahub.jpaadmin.service;

import java.util.Objects;

/**
 * Something the visitor should be told about the filters in the address: a filter that was ignored and why, a value
 * that was cut, or a filter that cannot be offered. The text is plain; the template escapes it. It names a filter by
 * its label from the metadata and never repeats what was typed, and a name that the console does not filter (unknown,
 * hidden or sensitive) is only counted, so those three cannot be told apart.
 *
 * <p>Internal, not stable API. Public because the controller and the preview build the page from it.
 *
 * @param text    the message
 * @param applied whether the filter is still in force (a cut value is applied; an ignored filter is not)
 */
public record FilterNote(String text, boolean applied) {

    public FilterNote {
        Objects.requireNonNull(text, "text");
    }
}
