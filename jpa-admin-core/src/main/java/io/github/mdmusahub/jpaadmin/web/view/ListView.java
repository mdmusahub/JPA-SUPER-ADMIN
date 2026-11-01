package io.github.mdmusahub.jpaadmin.web.view;

import java.util.List;
import java.util.Objects;

/**
 * Data for the list page of one entity: columns, rows, search, sort, filters, paging and actions.
 *
 * <p>Each row has exactly one cell per column, in column order. Hidden columns still have cells; the template skips
 * columns that are not {@link Column#visible()}.
 *
 * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
 *
 * @param entity  names of the entity
 * @param columns columns in display order
 * @param rows    rows of the current page
 * @param search  current search text, empty when there is none
 * @param sort    key of the sorted column, or {@code null} when unsorted
 * @param dir     {@code asc} or {@code desc}; {@code null} exactly when {@code sort} is {@code null}
 * @param filters filter definitions and active chips
 * @param paging  page position and page sizes
 * @param actions what the list offers
 * @param empty   why the list is empty; {@link Empty.Reason#NONE} exactly when there are rows
 */
public record ListView(EntityInfo entity, List<Column> columns, List<Row> rows, String search, String sort,
                       String dir, Filters filters, Paging paging, Actions actions, Empty empty) {

    /** Template (view) name. */
    public static final String TEMPLATE = "jpa-admin/list";

    public ListView {
        Objects.requireNonNull(entity, "entity");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(filters, "filters");
        Objects.requireNonNull(paging, "paging");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(empty, "empty");
        if ((sort == null) != (dir == null)) {
            throw new IllegalArgumentException("sort and dir must both be set or both be null");
        }
        if (dir != null && !dir.equals("asc") && !dir.equals("desc")) {
            throw new IllegalArgumentException("dir must be 'asc' or 'desc'");
        }
        for (Row row : rows) {
            if (row.cells().size() != columns.size()) {
                throw new IllegalArgumentException("each row needs exactly one cell per column");
            }
        }
        if (rows.isEmpty() == (empty.reason() == Empty.Reason.NONE)) {
            throw new IllegalArgumentException("empty.reason must be NONE exactly when there are rows");
        }
    }

    /**
     * Names of the entity.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param label  singular display name
     * @param plural plural display name
     * @param icon   icon name from the icon sprite
     */
    public record EntityInfo(String label, String plural, String icon) {
        public EntityInfo {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(plural, "plural");
            Objects.requireNonNull(icon, "icon");
        }
    }

    /**
     * Column header description.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param key        column key, a name from the entity metadata
     * @param label      header text
     * @param type       kind of value in the column
     * @param sortable   whether the header sorts the list
     * @param align      text alignment
     * @param visible    whether the column is shown
     * @param filterable whether the column has a filter
     */
    public record Column(String key, String label, Cell.Type type, boolean sortable, Align align, boolean visible,
                         boolean filterable) {
        public Column {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(align, "align");
        }

        /**
         * Text alignment.
         *
         * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
         */
        public enum Align { START, END, CENTER }
    }

    /**
     * One row of the list.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param id    record id as text, used in links and for row selection
     * @param cells one cell per column, in column order
     */
    public record Row(String id, List<Cell> cells) {
        public Row {
            Objects.requireNonNull(id, "id");
            cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        }
    }

    /**
     * Filter definitions and the chips for active filters.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param definitions   filters the user can set
     * @param chips         active filters, each removable
     * @param clearAllHref  link that removes every filter, or {@code null} when no filter is active
     */
    public record Filters(List<Definition> definitions, List<Chip> chips, String clearAllHref) {
        public Filters {
            definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
            chips = List.copyOf(Objects.requireNonNull(chips, "chips"));
        }

        /**
         * One filter control.
         *
         * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
         *
         * @param key     filter key, a name from the entity metadata
         * @param label   control label
         * @param kind    kind of control
         * @param options choices for enum, boolean and relation filters, empty for the others
         * @param value   current value for single-value filters, or {@code null}
         * @param from    current lower bound for range filters, or {@code null}
         * @param to      current upper bound for range filters, or {@code null}
         */
        public record Definition(String key, String label, Kind kind, List<FormView.Option> options, String value,
                                 String from, String to) {
            public Definition {
                Objects.requireNonNull(key, "key");
                Objects.requireNonNull(label, "label");
                Objects.requireNonNull(kind, "kind");
                options = List.copyOf(Objects.requireNonNull(options, "options"));
            }

            /**
             * Kind of filter control.
             *
             * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
             */
            public enum Kind { TEXT, ENUM, BOOLEAN, RELATION, NUMBER_RANGE, DATE_RANGE }
        }

        /**
         * Chip for one active filter.
         *
         * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
         *
         * @param key        filter key
         * @param label      filter label
         * @param display    value text to show
         * @param removeHref link that removes only this filter
         */
        public record Chip(String key, String label, String display, String removeHref) {
            public Chip {
                Objects.requireNonNull(key, "key");
                Objects.requireNonNull(label, "label");
                Objects.requireNonNull(display, "display");
                Objects.requireNonNull(removeHref, "removeHref");
            }
        }
    }

    /**
     * Page position and the page sizes on offer. The page number starts at 0, as in the URL.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param page  current page, starting at 0
     * @param size  rows per page
     * @param total total number of matching records
     * @param sizes page sizes the user can choose
     */
    public record Paging(int page, int size, long total, List<Integer> sizes) {
        public Paging {
            if (page < 0 || size <= 0 || total < 0) {
                throw new IllegalArgumentException("page and total must not be negative and size must be positive");
            }
            sizes = List.copyOf(Objects.requireNonNull(sizes, "sizes"));
        }

        /** Number of pages, 0 when there are no matching records. */
        public long totalPages() {
            return (total + size - 1) / size;
        }

        /** Number of the first row on this page, starting at 1, or 0 when there are no rows. */
        public long firstRow() {
            return total == 0 ? 0 : Math.min(total, (long) page * size + 1);
        }

        /** Number of the last row on this page, or 0 when there are no rows. */
        public long lastRow() {
            return total == 0 ? 0 : Math.min(total, (long) (page + 1) * size);
        }
    }

    /**
     * What the list offers the user.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param canCreate     whether "New record" is offered
     * @param canExport     whether CSV export is offered
     * @param canBulkDelete whether row selection and bulk delete are offered
     */
    public record Actions(boolean canCreate, boolean canExport, boolean canBulkDelete) {
    }

    /**
     * Empty state of the list.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param reason why the list is empty, {@link Reason#NONE} when it is not
     * @param hint   what the user can do next, empty text when the list is not empty
     */
    public record Empty(Reason reason, String hint) {
        public Empty {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(hint, "hint");
        }

        /**
         * Why a list is empty.
         *
         * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
         */
        public enum Reason { NONE, NO_RECORDS, NO_MATCHES }
    }
}
