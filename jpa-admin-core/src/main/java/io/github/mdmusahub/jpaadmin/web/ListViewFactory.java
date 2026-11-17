package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.service.ListColumn;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the {@link ListView} of one page from the display values the query service produced: the columns with their
 * sort links and sort state, the rows, the paging links, the page-size links and the empty state. It adds no data of
 * its own, so nothing here can leak a value the service did not hand over.
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
        Objects.requireNonNull(basePath, "basePath");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(actions, "actions");
        ListLinks links = new ListLinks(basePath, urlName);
        ListQuery query = page.query();
        return new ListView(entity, columns(page, query, links), page.rows(), query.search(), query.sort(),
                query.dir(), new ListView.Filters(List.of(), List.of(), null), paging(page, query, links), actions,
                empty(page, entity));
    }

    private static List<ListView.Column> columns(ListPage page, ListQuery query, ListLinks links) {
        List<ListView.Column> columns = new ArrayList<>(page.columns().size());
        for (ListColumn column : page.columns()) {
            ListView.Column.SortState state = !column.key().equals(query.sort()) ? ListView.Column.SortState.NONE
                    : ListQuery.ASC.equals(query.dir()) ? ListView.Column.SortState.ASCENDING
                    : ListView.Column.SortState.DESCENDING;
            columns.add(new ListView.Column(column.key(), column.label(), column.type(), column.sortable(),
                    column.sortable() ? links.sortBy(query, column.key()) : null, state, column.align(), true, false));
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
        if (!page.query().search().isEmpty()) {
            String start = "No " + plural + " match your search. ";
            if (page.searched().isEmpty()) {
                return new ListView.Empty(ListView.Empty.Reason.NO_MATCHES, start
                        + "This table has no text column that a search can look in, so a search cannot find anything. "
                        + "Clear the search to see every record.");
            }
            return new ListView.Empty(ListView.Empty.Reason.NO_MATCHES, start + scope(page.searched())
                    + " Check the spelling, or clear the search to see every record.");
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
