package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.service.ListColumn;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
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
            return new ListView.Empty(ListView.Empty.Reason.NO_MATCHES, "No " + plural
                    + " match your search. Check the spelling, or clear the search to see every record.");
        }
        return new ListView.Empty(ListView.Empty.Reason.NO_RECORDS, "There are no " + plural
                + " yet. They appear here as soon as your application saves one.");
    }
}
