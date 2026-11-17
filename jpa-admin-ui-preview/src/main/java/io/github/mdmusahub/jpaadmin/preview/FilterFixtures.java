package io.github.mdmusahub.jpaadmin.preview;

import io.github.mdmusahub.jpaadmin.service.ActiveFilter;
import io.github.mdmusahub.jpaadmin.service.FilterControl;
import io.github.mdmusahub.jpaadmin.service.FilterNote;
import io.github.mdmusahub.jpaadmin.service.ListColumn;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.web.ListViewFactory;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.ListView.Column.Align;
import io.github.mdmusahub.jpaadmin.web.view.ListView.Filters.Definition.Kind;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The filter fixtures of task U3 (see {@code docs/FIXTURES.md}): each is a fake list page with filter controls and active
 * filters, turned into a {@code ListView} by the real {@link ListViewFactory}, so the chips, the remove links, the
 * clear-all link and the filter-aware page links are built by the real link rules. The fake rows are filtered here by the
 * same meaning the console gives each kind (contains, exact, range), only to make the page look right; the data is
 * invented and holds no real value.
 *
 * <p>What the panel may show follows the console: no control for a masked column (the members fixture has a password
 * column and no password filter), none for a relation to a hidden entity (the reviewer column in the hidden-entity
 * fixture), and a relation control lists labels only.
 */
final class FilterFixtures {

    private static final String[] STATUS_VALUES = {"PUBLISHED", "DRAFT", "ARCHIVED", "IN_REVIEW"};

    /** The article columns plus a yes/no column, so every kind of filter has a column to act on. */
    private static final List<ListColumn> COLUMNS = List.of(
            new ListColumn("id", "ID", Cell.Type.NUMBER, true, Align.END),
            new ListColumn("title", "Title", Cell.Type.TEXT, true, Align.START),
            new ListColumn("author", "Author", Cell.Type.RELATION, true, Align.START),
            new ListColumn("status", "Status", Cell.Type.ENUM, true, Align.START),
            new ListColumn("views", "Views", Cell.Type.NUMBER, true, Align.END),
            new ListColumn("published", "Published", Cell.Type.DATE, true, Align.START),
            new ListColumn("featured", "Featured", Cell.Type.BOOLEAN, true, Align.START));

    private static final List<FormView.Option> YES_NO = List.of(new FormView.Option("true", "Yes"),
            new FormView.Option("false", "No"));

    private FilterFixtures() {
    }

    // ---- the controls ----------------------------------------------------------------------------------------------

    private static List<FormView.Option> authors() {
        List<FormView.Option> options = new ArrayList<>();
        for (int i = 0; i < ListFixtures.AUTHORS.length; i++) {
            options.add(new FormView.Option(String.valueOf(i + 1), ListFixtures.AUTHORS[i]));
        }
        return options;
    }

    private static List<FormView.Option> statuses() {
        List<FormView.Option> options = new ArrayList<>();
        for (int i = 0; i < STATUS_VALUES.length; i++) {
            options.add(new FormView.Option(STATUS_VALUES[i], ListFixtures.STATUSES[i]));
        }
        return options;
    }

    private static List<FilterControl> controls(List<FormView.Option> authorOptions) {
        return List.of(
                new FilterControl("title", "Title", Kind.TEXT, List.of()),
                new FilterControl("author", "Author", Kind.RELATION, authorOptions),
                new FilterControl("status", "Status", Kind.ENUM, statuses()),
                new FilterControl("views", "Views", Kind.NUMBER_RANGE, List.of()),
                new FilterControl("published", "Published", Kind.DATE_RANGE, List.of()),
                new FilterControl("featured", "Featured", Kind.BOOLEAN, YES_NO));
    }

    // ---- the pages -------------------------------------------------------------------------------------------------

    /** Articles with the controls above, narrowed by {@code filters} the way the console would. */
    static ListView articles(String basePath, String search, String sort, String dir, int page, int size,
                             List<ActiveFilter> filters) {
        return articles(basePath, controls(authors()), search, sort, dir, page, size, filters);
    }

    static ListView articles(String basePath, List<FilterControl> controls, String search, String sort, String dir,
                             int page, int size, List<ActiveFilter> filters) {
        List<ListFixtures.Article> matching = new ArrayList<>();
        for (ListFixtures.Article article : ListFixtures.allArticles(false)) {
            boolean searched = search.isEmpty() || article.title().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
            if (searched && filters.stream().allMatch(filter -> matches(article, filter))) {
                matching.add(article);
            }
        }
        matching.sort(order(sort, dir));
        int lastPage = matching.isEmpty() ? 0 : (matching.size() - 1) / size;
        int shown = Math.min(page, lastPage);
        List<ListView.Row> rows = new ArrayList<>();
        for (int i = shown * size; i < Math.min(matching.size(), (shown + 1) * size); i++) {
            ListFixtures.Article article = matching.get(i);
            boolean featured = featured(article);
            rows.add(new ListView.Row(String.valueOf(article.id()), List.of(ListFixtures.number(String.valueOf(article.id())),
                    Cell.text(article.title()), ListFixtures.relation(article.author(), null), ListFixtures.status(article.status()),
                    ListFixtures.number(String.format(Locale.ROOT, "%,d", article.views())),
                    new Cell(Cell.Type.DATE, article.published().toString(), Cell.Tone.NONE, null, null),
                    new Cell(Cell.Type.BOOLEAN, featured ? "Yes" : "No", featured ? Cell.Tone.GREEN : Cell.Tone.NONE, null, null))));
        }
        ListQuery query = new ListQuery(search, sort, dir, shown, size, filters);
        return ListViewFactory.build(basePath, "article", ListFixtures.ARTICLES, new ListPage(COLUMNS, rows, matching.size(),
                query, List.of("Title"), controls, List.of()), new ListView.Actions(false, false, false, null));
    }

    private static boolean featured(ListFixtures.Article article) {
        return article.id() % 3 == 0;
    }

    private static boolean matches(ListFixtures.Article article, ActiveFilter filter) {
        return switch (filter.key()) {
            case "title" -> article.title().toLowerCase(Locale.ROOT).contains(filter.value().toLowerCase(Locale.ROOT));
            case "author" -> ListFixtures.AUTHORS[Integer.parseInt(filter.value()) - 1].equals(article.author());
            case "status" -> STATUS_VALUES[indexOf(article.status())].equals(filter.value());
            case "featured" -> featured(article) == Boolean.parseBoolean(filter.value());
            case "views" -> (filter.from() == null || article.views() >= Double.parseDouble(filter.from()))
                    && (filter.to() == null || article.views() <= Double.parseDouble(filter.to()));
            case "published" -> (filter.from() == null || !article.published().isBefore(LocalDate.parse(filter.from())))
                    && (filter.to() == null || !article.published().isAfter(LocalDate.parse(filter.to())));
            default -> true;
        };
    }

    private static int indexOf(String status) {
        for (int i = 0; i < ListFixtures.STATUSES.length; i++) {
            if (ListFixtures.STATUSES[i].equals(status)) {
                return i;
            }
        }
        return 0;
    }

    private static Comparator<ListFixtures.Article> order(String sort, String dir) {
        Comparator<ListFixtures.Article> byId = Comparator.comparingInt(ListFixtures.Article::id);
        if (sort == null) {
            return byId;
        }
        Comparator<ListFixtures.Article> primary = switch (sort) {
            case "views" -> Comparator.comparingInt(ListFixtures.Article::views);
            case "title" -> Comparator.comparing(ListFixtures.Article::title);
            default -> byId;
        };
        return ("desc".equals(dir) ? primary.reversed() : primary).thenComparing(byId);
    }

    // ---- shapes ----------------------------------------------------------------------------------------------------

    /** A relation control with forty labels: long names, unicode and markup, to check the dropdown and that it escapes. */
    static ListView relationDropdown(String basePath) {
        return relationDropdown(basePath, 40);
    }

    /** The same with {@code count} choices; 200 is the most the console ever lists. */
    static ListView relationDropdown(String basePath, int count) {
        List<FormView.Option> many = new ArrayList<>();
        many.add(new FormView.Option("1", ListFixtures.AUTHORS[0]));
        many.add(new FormView.Option("2", "<b>Bold</b> & \"quoted\" <script>alert(1)</script>"));
        many.add(new FormView.Option("3", "Ünïcödé 東京 Платёж"));
        many.add(new FormView.Option("4", "An author with a surprisingly long display name that goes on and on and on and on"));
        for (int i = 5; i <= count; i++) {
            many.add(new FormView.Option(String.valueOf(i), "Author number " + i));
        }
        return articles(basePath, controls(many), "", null, null, 0, 25, List.of(
                ActiveFilter.of("author", Kind.RELATION, "2")));
    }

    /**
     * A table whose columns include a relation to an entity that is hidden from the console (Reviewer, shown as an
     * identifier) and a masked column: neither has a filter control, and nothing of the hidden entity is listed.
     */
    static ListView hiddenEntityAbsent(String basePath) {
        List<ListColumn> columns = List.of(
                new ListColumn("id", "ID", Cell.Type.NUMBER, true, Align.END),
                new ListColumn("title", "Title", Cell.Type.TEXT, true, Align.START),
                new ListColumn("author", "Author", Cell.Type.RELATION, true, Align.START),
                new ListColumn("reviewer", "Reviewer", Cell.Type.RELATION, false, Align.START),
                new ListColumn("apiKey", "Api key", Cell.Type.MASKED, false, Align.START));
        List<ListView.Row> rows = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            rows.add(new ListView.Row(String.valueOf(i), List.of(ListFixtures.number(String.valueOf(i)), Cell.text("Article " + i),
                    ListFixtures.relation(ListFixtures.AUTHORS[i % ListFixtures.AUTHORS.length], null),
                    ListFixtures.relation("#" + (100 + i), null), Cell.masked())));
        }
        List<FilterControl> controls = List.of(new FilterControl("title", "Title", Kind.TEXT, List.of()),
                new FilterControl("author", "Author", Kind.RELATION, authors()));
        return ListViewFactory.build(basePath, "article", ListFixtures.ARTICLES, new ListPage(columns, rows, rows.size(),
                ListQuery.defaults(), List.of("Title"), controls, List.of()), new ListView.Actions(false, false, false, null));
    }

    /** Members: a masked password column and no password filter; name and email are text filters, joined a date range. */
    static ListView sensitiveAbsent(String basePath) {
        String[] names = {"Ava Stone", "Ben Carter", "Chloe Reed", "Dan Moore", "Eva Lane", "Finn Hale"};
        List<ListView.Row> rows = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            rows.add(new ListView.Row(String.valueOf(i + 1), List.of(ListFixtures.number(String.valueOf(i + 1)), Cell.text(names[i]),
                    Cell.text(names[i].toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.test"), Cell.masked(),
                    new Cell(Cell.Type.DATE, "2026-0" + (i + 1) + "-15", Cell.Tone.NONE, null, null))));
        }
        List<FilterControl> controls = List.of(new FilterControl("name", "Name", Kind.TEXT, List.of()),
                new FilterControl("email", "Email", Kind.TEXT, List.of()),
                new FilterControl("joined", "Joined", Kind.DATE_RANGE, List.of()));
        ListQuery query = new ListQuery("", null, null, 0, 25, List.of(ActiveFilter.range("joined", Kind.DATE_RANGE,
                "2026-01-01", "2026-06-30")));
        return ListViewFactory.build(basePath, "member", ListFixtures.MEMBERS, new ListPage(ListFixtures.MEMBER_COLUMNS, rows,
                rows.size(), query, List.of("Name", "Email"), controls, List.of()), new ListView.Actions(false, false, false, null));
    }

    /** The notices the console raises for a request full of bad filters: built by the real factory from real notes. */
    static List<PageChrome.FlashMessage> ignoredNotices() {
        return ListViewFactory.filterNotices(List.of(
                new FilterNote("The Views filter's minimum was ignored: it is not a number. Use digits, with a point for decimals.", false),
                new FilterNote("The Published filter was ignored: the 'from' date is after the 'to' date.", false),
                new FilterNote("The Status filter was ignored: that is not one of its values.", false),
                new FilterNote("The Title filter was cut to 100 characters.", true),
                new FilterNote("2 filters in the address do not match any filter of this table and were ignored.", false)));
    }

    static List<ActiveFilter> everyKind() {
        return List.of(ActiveFilter.of("title", Kind.TEXT, "report"), ActiveFilter.of("author", Kind.RELATION, "2"),
                ActiveFilter.of("status", Kind.ENUM, "PUBLISHED"), ActiveFilter.range("views", Kind.NUMBER_RANGE, "100", "8000"),
                ActiveFilter.range("published", Kind.DATE_RANGE, "2026-01-01", null),
                ActiveFilter.of("featured", Kind.BOOLEAN, "false"));
    }
}
