package io.github.mdmusahub.jpaadmin.web.view;

import java.util.List;
import java.util.Objects;

/**
 * Data for the list page of one entity: columns, rows, search, sort, filters, paging and actions.
 *
 * <p>Each row has exactly one cell per column, in column order. Hidden columns still have cells; the template skips
 * columns that are not {@link Column#visible()}.
 *
 * <p>The backend builds every link (sort, page, page size, filter removal, export). Templates only print them. Each
 * link is a local path under the base path (see {@code Hrefs}) and keeps the current search, filters and sort.
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
        for (Column column : columns) {
            Column.SortState expected = !column.key().equals(sort) ? Column.SortState.NONE
                    : dir.equals("asc") ? Column.SortState.ASCENDING : Column.SortState.DESCENDING;
            if (column.sortState() != expected) {
                throw new IllegalArgumentException("each column's sortState must match sort and dir");
            }
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
     * @param sortHref   link that sorts by this column (the next direction), keeping search, filters and page size;
     *                   present exactly when {@code sortable} is true
     * @param sortState  how the list is sorted by this column now; {@link SortState#NONE} unless {@code sortable}.
     *                   It matches {@link ListView#sort()} and {@link ListView#dir()}
     * @param align      text alignment
     * @param visible    whether the column is shown
     * @param filterable whether the column has a filter
     */
    public record Column(String key, String label, Cell.Type type, boolean sortable, String sortHref,
                         SortState sortState, Align align, boolean visible, boolean filterable) {
        public Column {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(sortState, "sortState");
            Objects.requireNonNull(align, "align");
            Hrefs.optional("sortHref", sortHref);
            if (sortable != (sortHref != null)) {
                throw new IllegalArgumentException("sortHref is required exactly for sortable columns");
            }
            if (!sortable && sortState != SortState.NONE) {
                throw new IllegalArgumentException("a column that is not sortable cannot be sorted");
            }
        }

        /**
         * Direction indicator of a column header, named after the ARIA {@code aria-sort} values.
         *
         * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
         */
        public enum SortState { NONE, ASCENDING, DESCENDING }

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
            Hrefs.optional("clearAllHref", clearAllHref);
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
                Hrefs.require("removeHref", removeHref);
            }
        }
    }

    /**
     * Page position and the page sizes on offer. The page number starts at 0, as in the URL.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param page      current page, starting at 0
     * @param size      rows per page
     * @param total     total number of matching records
     * @param firstHref link to the first page; {@code null} on the first page
     * @param prevHref  link to the previous page; {@code null} on the first page
     * @param nextHref  link to the next page; {@code null} on the last page and when there are no records
     * @param lastHref  link to the last page; {@code null} on the last page and when there are no records
     * @param sizes     page sizes the user can choose, each with its link
     */
    public record Paging(int page, int size, long total, String firstHref, String prevHref, String nextHref,
                         String lastHref, List<SizeOption> sizes) {
        public Paging {
            if (page < 0 || size <= 0 || total < 0) {
                throw new IllegalArgumentException("page and total must not be negative and size must be positive");
            }
            Hrefs.optional("firstHref", firstHref);
            Hrefs.optional("prevHref", prevHref);
            Hrefs.optional("nextHref", nextHref);
            Hrefs.optional("lastHref", lastHref);
            sizes = List.copyOf(Objects.requireNonNull(sizes, "sizes"));
            boolean hasPrevious = page > 0;
            boolean hasNext = page + 1L < pagesFor(total, size);
            if (hasPrevious != (firstHref != null) || hasPrevious != (prevHref != null)) {
                throw new IllegalArgumentException(
                        "firstHref and prevHref are present exactly when there is a previous page");
            }
            if (hasNext != (nextHref != null) || hasNext != (lastHref != null)) {
                throw new IllegalArgumentException(
                        "nextHref and lastHref are present exactly when there is a next page");
            }
            for (SizeOption option : sizes) {
                if (option.current() != (option.size() == size)) {
                    throw new IllegalArgumentException("exactly the page size in use is marked current");
                }
            }
        }

        private static long pagesFor(long total, int size) {
            return (total + size - 1) / size;
        }

        /** Number of pages, 0 when there are no matching records. */
        public long totalPages() {
            return pagesFor(total, size);
        }

        /** Number of the first row on this page, starting at 1, or 0 when there are no rows. */
        public long firstRow() {
            return total == 0 ? 0 : Math.min(total, (long) page * size + 1);
        }

        /** Number of the last row on this page, or 0 when there are no rows. */
        public long lastRow() {
            return total == 0 ? 0 : Math.min(total, (long) (page + 1) * size);
        }

        /**
         * A page size the user can choose.
         *
         * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
         *
         * @param size    rows per page
         * @param current whether this is the page size in use now
         * @param href    link that switches to this page size, keeping search, filters and sort
         */
        public record SizeOption(int size, boolean current, String href) {
            public SizeOption {
                if (size <= 0) {
                    throw new IllegalArgumentException("size must be positive");
                }
                Hrefs.require("href", href);
            }
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
     * @param exportHref    CSV export link carrying the current search, filters, sort and visible columns; present
     *                      exactly when {@code canExport} is true
     */
    public record Actions(boolean canCreate, boolean canExport, boolean canBulkDelete, String exportHref) {
        public Actions {
            Hrefs.optional("exportHref", exportHref);
            if (canExport != (exportHref != null)) {
                throw new IllegalArgumentException("exportHref is required exactly when export is offered");
            }
        }
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
