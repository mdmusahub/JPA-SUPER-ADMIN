package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.Cell;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The rules for hiding columns of an entity's list (Phase 12, D-112). One class decides them, so the parser, the query
 * service and the page cannot disagree.
 *
 * <ul>
 *   <li><b>What can be hidden:</b> a column of the list that is not masked. A hidden ({@code @AdminHidden}) attribute is
 *       not a column at all, and a sensitive field is a masked column that is never accepted: a name is ignored exactly
 *       like an unknown one, so hiding cannot be used to learn whether a name is sensitive (SEC-1, SEC-2). The
 *       identifier can be hidden.</li>
 *   <li><b>Untrusted input:</b> every value is checked on its own. An unknown, empty, over-long or repeated name is
 *       ignored without a message. At most {@value #MAX_VALUES_READ} values are read, so a request cannot ask for unbounded
 *       work.</li>
 *   <li><b>At least one column stays:</b> when the accepted names cover every column that can be hidden, the whole
 *       {@code hide} is ignored and {@link HiddenParse#everyColumnHidden()} says so, so the page can tell the visitor. A
 *       masked column never counts as the column that stays.</li>
 *   <li><b>Sequence:</b> the result is in column order, never in request order, and has no repeat.</li>
 * </ul>
 *
 * <p>Package-private: it is a rule, not API.
 */
final class HiddenColumns {

    /** The most {@code hide} values read from one request; the rest are ignored. */
    static final int MAX_VALUES_READ = 200;

    /** The longest name looked at; a longer value cannot be a column key and is ignored. */
    static final int MAX_NAME_LENGTH = 100;

    private HiddenColumns() {
    }

    /** The keys of the columns that may be hidden: every column that is not masked, in column order. */
    static List<String> hideable(List<ListColumn> columns) {
        List<String> keys = new ArrayList<>(columns.size());
        for (ListColumn column : columns) {
            if (column.type() != Cell.Type.MASKED) {
                keys.add(column.key());
            }
        }
        return List.copyOf(keys);
    }

    /**
     * Reads the {@code hide} values of a request. Never throws.
     *
     * @param columns the entity's list columns
     * @param raw     the values of the repeated {@code hide} parameter, or {@code null}
     */
    static HiddenParse parse(List<ListColumn> columns, List<String> raw) {
        Objects.requireNonNull(columns, "columns");
        if (raw == null || raw.isEmpty()) {
            return HiddenParse.NONE;
        }
        Set<String> asked = new HashSet<>();
        int read = 0;
        for (String value : raw) {
            if (read++ >= MAX_VALUES_READ) {
                break;
            }
            if (value != null && !value.isEmpty() && value.length() <= MAX_NAME_LENGTH) {
                asked.add(value);
            }
        }
        List<String> hideable = hideable(columns);
        List<String> accepted = new ArrayList<>();
        for (String key : hideable) {
            if (asked.contains(key)) {
                accepted.add(key);
            }
        }
        if (accepted.isEmpty()) {
            return HiddenParse.NONE;
        }
        if (accepted.size() >= hideable.size()) {
            return new HiddenParse(List.of(), true);
        }
        return new HiddenParse(accepted, false);
    }

    /**
     * The second check, used by the query service on a {@link ListQuery} that may have been built by hand: only names of
     * columns that may be hidden stay, in column order, and the last column that can stay is never hidden.
     */
    static List<String> checked(List<ListColumn> columns, List<String> hidden) {
        if (hidden.isEmpty()) {
            return hidden;
        }
        return parse(columns, hidden).hidden();
    }
}
