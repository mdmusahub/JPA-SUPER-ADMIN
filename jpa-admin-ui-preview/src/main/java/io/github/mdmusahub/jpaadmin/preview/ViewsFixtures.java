package io.github.mdmusahub.jpaadmin.preview;

import io.github.mdmusahub.jpaadmin.service.ActiveFilter;
import io.github.mdmusahub.jpaadmin.service.ListColumn;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.web.ListViewFactory;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.ListView.Column.Align;
import io.github.mdmusahub.jpaadmin.web.view.ListView.Filters.Definition.Kind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The fixtures of task U8 for column visibility and saved views (Phase 12, {@code docs/FIXTURES.md}).
 *
 * <p>The server-rendered states (a hidden column, the Columns panel, the notice) are list fixtures built by the real
 * {@link ListViewFactory}. What a saved view is made of lives in the visitor's browser, so the preview cannot render it from
 * a record. It serves instead, at {@code /admin-preview/stored-views?state=}, the exact text a browser would hold under the
 * key of the views of the article list ({@link #STORAGE_KEY}), so a person or a script can put it in {@code localStorage}
 * and open {@code list/saved-views}: several views, the empty menu, the views that are dropped quietly, the full store. The
 * keys it names are the article list's: columns {@code id title author status views published featured}, filters
 * {@code title author status views published featured}.
 *
 * <p>The data is invented and holds no real value. Nothing here writes browser storage.
 */
final class ViewsFixtures {

    /** The key {@code views.js} uses for the article list under the preview's console path. */
    static final String STORAGE_KEY = "jpa-admin.views." + ChromeFixtures.BASE + "/article";

    private static final Map<String, String> STORED = new LinkedHashMap<>();

    static {
        // Four valid views, with names that need escaping and a name in another script.
        STORED.put("several", """
                [
                  {"name":"Published guides, most viewed","q":"guide","filters":{"filter.status":"PUBLISHED"},"sort":"views","dir":"desc","pageSize":50,"hiddenColumns":["published","featured"]},
                  {"name":"Drafts","q":"","filters":{"filter.status":"DRAFT"},"sort":null,"dir":null,"pageSize":25,"hiddenColumns":[]},
                  {"name":"<b>Bold</b> & \\"quoted\\" <script>alert(1)</script>","q":"a b/c&d=e","filters":{"filter.views.min":"1000","filter.views.max":"5000"},"sort":"title","dir":"asc","pageSize":10,"hiddenColumns":["author"]},
                  {"name":"\\u6771\\u4eac \\u2013 \\u00dcn\\u00efc\\u00f6d\\u00e9","q":"","filters":{},"sort":"published","dir":"desc","pageSize":100,"hiddenColumns":["id","views","featured"]}
                ]
                """.strip());
        STORED.put("empty", "[]");
        // The same store with one valid view and eight that must be dropped without a word (decision 8): unknown column,
        // unknown filter, a name of 61 characters, a direction that is not asc or desc, a direction without a sort, a page size
        // that is not on offer, something that is not an object, and a repeated name.
        STORED.put("dropped", """
                [
                  {"name":"Kept","q":"plan","filters":{"filter.status":"ARCHIVED"},"sort":"title","dir":"asc","pageSize":25,"hiddenColumns":["views"]},
                  {"name":"Unknown column","hiddenColumns":["nothing"]},
                  {"name":"Unknown filter","filters":{"filter.apiKey":"x"}},
                  {"name":"%s"},
                  {"name":"Bad direction","sort":"title","dir":"sideways"},
                  {"name":"Direction without a sort","sort":null,"dir":"asc"},
                  {"name":"Bad page size","pageSize":30},
                  "not an object",
                  {"name":"kept"}
                ]
                """.formatted("N".repeat(61)).strip());
        STORED.put("invalid-json", "{this is not json");
        STORED.put("hides-every-column", "[{\"name\":\"Everything hidden\",\"hiddenColumns\":[\"id\",\"title\",\"author\",\"status\",\"views\","
                + "\"published\",\"featured\"]}]");
        // Twenty valid views fill the store; the next one is refused. Twenty-two stored ones: the last two are dropped.
        STORED.put("full", views(20));
        STORED.put("over-the-cap", views(22));
    }

    private ViewsFixtures() {
    }

    private static String views(int count) {
        List<String> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            items.add("{\"name\":\"View " + String.format("%02d", i) + "\",\"q\":\"\",\"filters\":{},\"sort\":null,\"dir\":null,"
                    + "\"pageSize\":25,\"hiddenColumns\":[]}");
        }
        return "[" + String.join(",", items) + "]";
    }

    static Set<String> storedStates() {
        return STORED.keySet();
    }

    /** The text a browser would hold for {@code state}, or empty for an unknown state. */
    static Optional<String> stored(String state) {
        return Optional.ofNullable(STORED.get(state));
    }

    /**
     * The state of {@code docs/FIXTURES.md}: a search, a filter, a sort, a page size of 50 and two hidden columns are in force, and
     * every link of the page (sort, paging, sizes, the export file) keeps them, so "Save current view" stores exactly this.
     */
    static ListView savedViews(String basePath) {
        return FilterFixtures.articlesHidden(basePath, "guide", "views", "desc", 0, 50,
                List.of(ActiveFilter.of("status", Kind.ENUM, "PUBLISHED")), List.of("published", "featured"), true);
    }

    /**
     * The identifier column is hidden: the row link moves to the first visible text cell, so a row still opens its record. The
     * columns carry the identifier flag, as the console's do.
     */
    static ListView hiddenIdentifier(String basePath) {
        List<ListColumn> columns = List.of(
                new ListColumn("id", "ID", Cell.Type.NUMBER, true, Align.END, null, true),
                new ListColumn("title", "Title", Cell.Type.TEXT, true, Align.START),
                new ListColumn("views", "Views", Cell.Type.NUMBER, true, Align.END));
        List<ListView.Row> rows = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            rows.add(new ListView.Row(String.valueOf(i), List.of(Cell.empty(), Cell.text("Article " + i),
                    ListFixtures.number(String.valueOf(i * 100)))));
        }
        ListQuery query = new ListQuery("", null, null, 0, 25, List.of(), List.of("id"));
        return ListViewFactory.build(basePath, "article", ListFixtures.ARTICLES, new ListPage(columns, rows, rows.size(), query,
                List.of("Title")), new ListView.Actions(false, false, false, null, null));
    }
}
