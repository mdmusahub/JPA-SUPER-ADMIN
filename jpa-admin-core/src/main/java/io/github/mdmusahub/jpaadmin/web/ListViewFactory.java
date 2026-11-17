package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.service.ActiveFilter;
import io.github.mdmusahub.jpaadmin.service.FilterControl;
import io.github.mdmusahub.jpaadmin.service.FilterNote;
import io.github.mdmusahub.jpaadmin.service.ListColumn;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the {@link ListView} of one page from the display values the query service produced: the columns with their
 * sort links and sort state, the rows, the paging links, the page-size links and the empty state. It adds no data of
 * its own, so nothing here can leak a value the service did not hand over.
 *
 * <p>It also builds the filter panel (a definition for every control, with the value now in force), the chips of the active
 * filters and the clear-all link. A chip's remove link and the clear-all link keep the search, the sort and the page
 * size and start again at the first page, because the old page number would mean other rows.
 *
 * <p>The preview app calls this same class with fake pages, so what the preview shows is built by the real link and
 * paging rules.
 *
 * <p>Internal, not stable API. Public because the controller and the preview use it.
 */
public final class ListViewFactory {

    /** The most column names the "nothing matched" message lists; the rest are counted. */
    private static final int MAX_NAMED_COLUMNS = 10;

    private ListViewFactory() {
    }

    /**
     * @param basePath the console path, for example {@code /admin}
     * @param urlName  the entity's address segment
     * @param entity   names of the entity
     * @param page     the page read from the database; its query is the effective one
     * @param actions  what the list offers
     */
    public static ListView build(String basePath, String urlName, ListView.EntityInfo entity, ListPage page,
                                 ListView.Actions actions) {
        return build(basePath, urlName, entity, page, actions, false);
    }

    /**
     * As {@link #build(String, String, ListView.EntityInfo, ListPage, ListView.Actions)}, and when {@code bulkDelete} is
     * true also offers bulk delete: the {@code actions} you pass must not offer it yet, and the factory adds the flag
     * together with the address the form posts to, which carries the list state of this page (D-65).
     *
     * <p>Every row's identifier cell links to the record's detail page, carrying the same list state, and a relation cell
     * links to the related record's detail page exactly when its column names a target that is in the console and the
     * relation is set (D-54, D-70). The links are built here, not in the query service, which does not know the base
     * path; a cell whose id cannot be written into an address has no link.
     */
    public static ListView build(String basePath, String urlName, ListView.EntityInfo entity, ListPage page,
                                 ListView.Actions actions, boolean bulkDelete) {
        Objects.requireNonNull(basePath, "basePath");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(actions, "actions");
        ListLinks links = new ListLinks(basePath, urlName);
        ListQuery query = page.query();
        ListView.Actions offered = bulkDelete && !actions.canBulkDelete()
                ? new ListView.Actions(actions.canCreate(), actions.canExport(), true, actions.exportHref(),
                        links.bulkDelete(query))
                : actions;
        return new ListView(entity, columns(page, query, links), linked(basePath, page, links, query), query.search(),
                query.sort(), query.dir(), filters(page, query, links), paging(page, query, links), offered,
                empty(page, entity));
    }

    /** The rows with their links: the identifier cell and the relation cells (see {@code build}). */
    private static List<ListView.Row> linked(String basePath, ListPage page, ListLinks links, ListQuery query) {
        List<ListView.Row> rows = new ArrayList<>(page.rows().size());
        for (int n = 0; n < page.rows().size(); n++) {
            ListView.Row row = page.rows().get(n);
            List<Cell> cells = new ArrayList<>(row.cells());
            for (int i = 0; i < cells.size(); i++) {
                ListColumn column = page.columns().get(i);
                Cell cell = cells.get(i);
                String href = null;
                if (column.identifier() && cell.type() != Cell.Type.MASKED && cell.type() != Cell.Type.EMPTY) {
                    href = links.detail(row.id(), query);
                } else if (column.targetEntity() != null && cell.type() == Cell.Type.RELATION
                        && n < page.relationIds().size() && page.relationIds().get(n).get(i) != null) {
                    // The link carries no list state: it leaves this entity's list for another entity's record.
                    href = new ListLinks(basePath, column.targetEntity()).detail(page.relationIds().get(n).get(i),
                            ListQuery.defaults());
                }
                if (href != null) {
                    cells.set(i, new Cell(cell.type(), cell.display(), cell.tone(), href, cell.title()));
                }
            }
            rows.add(new ListView.Row(row.id(), cells));
        }
        return rows;
    }

    private static ListView.Filters filters(ListPage page, ListQuery query, ListLinks links) {
        Map<String, ActiveFilter> active = new HashMap<>();
        query.filters().forEach(filter -> active.put(filter.key(), filter));
        Map<String, FilterControl> controls = new HashMap<>();
        page.controls().forEach(control -> controls.put(control.key(), control));
        List<ListView.Filters.Definition> definitions = new ArrayList<>();
        for (FilterControl control : page.controls()) {
            ActiveFilter filter = active.get(control.key());
            definitions.add(new ListView.Filters.Definition(control.key(), control.label(), control.kind(), control.options(),
                    filter == null ? null : filter.value(), filter == null ? null : filter.from(),
                    filter == null ? null : filter.to()));
        }
        List<ListView.Filters.Chip> chips = new ArrayList<>();
        for (ActiveFilter filter : query.filters()) {
            FilterControl control = controls.get(filter.key());
            if (control != null) {
                chips.add(new ListView.Filters.Chip(filter.key(), control.label(), display(control, filter),
                        links.to(query.withoutFilter(filter.key()))));
            }
        }
        return new ListView.Filters(definitions, chips, chips.isEmpty() ? null : links.to(query.withoutFilters()));
    }

    /** The text of a chip: the label of a choice for a choice, the number or date range in words for a range. */
    private static String display(FilterControl control, ActiveFilter filter) {
        return switch (filter.kind()) {
            case TEXT -> filter.value();
            case ENUM, BOOLEAN, RELATION -> control.options().stream().filter(option -> option.value().equals(filter.value()))
                    .map(option -> option.label()).findFirst().orElse(filter.value());
            case NUMBER_RANGE -> filter.from() != null && filter.to() != null ? filter.from() + " to " + filter.to()
                    : filter.from() != null ? "at least " + filter.from() : "at most " + filter.to();
            case DATE_RANGE -> filter.from() != null && filter.to() != null ? filter.from() + " to " + filter.to()
                    : filter.from() != null ? "from " + filter.from() : "until " + filter.to();
        };
    }

    private static List<ListView.Column> columns(ListPage page, ListQuery query, ListLinks links) {
        List<ListView.Column> columns = new ArrayList<>(page.columns().size());
        Set<String> filterable = page.controls().stream().map(FilterControl::key).collect(Collectors.toSet());
        for (ListColumn column : page.columns()) {
            ListView.Column.SortState state = !column.key().equals(query.sort()) ? ListView.Column.SortState.NONE
                    : ListQuery.ASC.equals(query.dir()) ? ListView.Column.SortState.ASCENDING
                    : ListView.Column.SortState.DESCENDING;
            columns.add(new ListView.Column(column.key(), column.label(), column.type(), column.sortable(),
                    column.sortable() ? links.sortBy(query, column.key()) : null, state, column.align(), true,
                    filterable.contains(column.key())));
        }
        return columns;
    }

    private static ListView.Paging paging(ListPage page, ListQuery query, ListLinks links) {
        long pages = (page.total() + query.size() - 1) / query.size();
        boolean hasPrevious = query.page() > 0;
        boolean hasNext = query.page() + 1L < pages;
        List<ListView.Paging.SizeOption> sizes = new ArrayList<>();
        for (int size : ListQuery.PAGE_SIZES) {
            sizes.add(new ListView.Paging.SizeOption(size, size == query.size(), links.to(query.withSize(size))));
        }
        return new ListView.Paging(query.page(), query.size(), page.total(),
                hasPrevious ? links.to(query.withPage(0)) : null,
                hasPrevious ? links.to(query.withPage(query.page() - 1)) : null,
                hasNext ? links.to(query.withPage(query.page() + 1)) : null,
                hasNext ? links.to(query.withPage((int) (pages - 1))) : null, sizes);
    }

    private static ListView.Empty empty(ListPage page, ListView.EntityInfo entity) {
        if (!page.rows().isEmpty()) {
            return new ListView.Empty(ListView.Empty.Reason.NONE, "");
        }
        String plural = entity.plural().toLowerCase(java.util.Locale.ROOT);
        boolean filtered = !page.query().filters().isEmpty();
        if (page.query().search().isEmpty() && filtered) {
            return new ListView.Empty(ListView.Empty.Reason.NO_MATCHES, "No " + plural + " match the filters. Remove a "
                    + "filter, or clear all filters, to see more records.");
        }
        if (!page.query().search().isEmpty()) {
            String start = "No " + plural + (filtered ? " match your search and filters. " : " match your search. ");
            if (page.searched().isEmpty()) {
                return new ListView.Empty(ListView.Empty.Reason.NO_MATCHES, start
                        + "This table has no text column that a search can look in, so a search cannot find anything. "
                        + (filtered ? "Clear the search, or remove a filter, to see more records."
                        : "Clear the search to see every record."));
            }
            return new ListView.Empty(ListView.Empty.Reason.NO_MATCHES, start + scope(page.searched())
                    + (filtered ? " Check the spelling, or clear the search or a filter, to see more records."
                    : " Check the spelling, or clear the search to see every record."));
        }
        return new ListView.Empty(ListView.Empty.Reason.NO_RECORDS, "There are no " + plural
                + " yet. They appear here as soon as your application saves one.");
    }

    /**
     * Says which columns a search looked in: header labels only, in display order, at most
     * {@value #MAX_NAMED_COLUMNS} of them. It is plain text; the template escapes it on output (SEC-9).
     */
    private static String scope(List<String> searched) {
        String names = String.join(", ", searched.subList(0, Math.min(searched.size(), MAX_NAMED_COLUMNS)));
        int more = searched.size() - MAX_NAMED_COLUMNS;
        return "The search looked in these text columns: " + names + (more > 0 ? " and " + more + " more" : "") + ".";
    }

    /** The most filter notes shown at once; the rest are counted. */
    private static final int MAX_FILTER_NOTES = 5;

    /**
     * The notices about the filters: what was ignored and why, what was cut, what cannot be offered. A note that says a
     * filter is still in force is INFO, one that says it was ignored is WARNING. At most {@value #MAX_FILTER_NOTES} are
     * listed, then one message counts the rest. The texts are plain; the page escapes them on output.
     */
    public static List<PageChrome.FlashMessage> filterNotices(List<FilterNote> notes) {
        List<PageChrome.FlashMessage> messages = new ArrayList<>();
        for (FilterNote note : notes.subList(0, Math.min(notes.size(), MAX_FILTER_NOTES))) {
            messages.add(new PageChrome.FlashMessage(note.applied() ? PageChrome.FlashMessage.Level.INFO
                    : PageChrome.FlashMessage.Level.WARNING, note.text()));
        }
        int more = notes.size() - MAX_FILTER_NOTES;
        if (more > 0) {
            messages.add(new PageChrome.FlashMessage(PageChrome.FlashMessage.Level.WARNING,
                    more + (more == 1 ? " more note about the filters is not shown." : " more notes about the filters are not shown.")));
        }
        return messages;
    }

    /**
     * The notice for a search that was longer than {@value ListQuery#MAX_SEARCH_LENGTH} characters and was cut. The
     * controller puts it in the page's flash messages only when {@link ListQuery#searchWasCut} says so.
     */
    public static PageChrome.FlashMessage searchCutNotice() {
        return new PageChrome.FlashMessage(PageChrome.FlashMessage.Level.INFO,
                "Your search was longer than " + ListQuery.MAX_SEARCH_LENGTH + " characters, so only the first "
                        + ListQuery.MAX_SEARCH_LENGTH + " were used.");
    }
}
