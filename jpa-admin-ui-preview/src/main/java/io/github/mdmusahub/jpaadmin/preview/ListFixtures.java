package io.github.mdmusahub.jpaadmin.preview;

import io.github.mdmusahub.jpaadmin.service.ListColumn;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.web.ListViewFactory;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.ListView.Column.Align;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The list-page fixtures of {@code docs/FIXTURES.md} section 5 that task U2 needs. Each fixture is a fake page of
 * display values, turned into a {@link ListView} by the same {@link ListViewFactory} the console uses, so the sort,
 * page and page-size links in the preview are built by the real link rules, under the base path of the chrome in use.
 *
 * <p>The data is invented (articles, members) and holds no real value. A masked cell holds only the mask, even here
 * (SEC-1). The links point at addresses that only the real console serves, so in the preview they are for looking at;
 * the fake data does not react to them.
 */
final class ListFixtures {

    private static final ListView.EntityInfo ARTICLES = new ListView.EntityInfo("Article", "Articles", "file");
    private static final ListView.EntityInfo MEMBERS = new ListView.EntityInfo("Member", "Members", "users");

    /** 94 articles: pages of 25 are 25, 25, 25 and 19, so the last page is partial. */
    private static final int ARTICLE_COUNT = 94;

    private static final String[] AUTHORS = {"Ava Stone", "Ben Carter", "Chloe Reed", "Dan Moore", "Eva Lane", "Finn Hale"};
    private static final String[] STATUSES = {"Published", "Draft", "Archived", "In review"};
    private static final String[] WORDS = {"Guide", "Notes", "Review", "Report", "Update", "Tutorial", "Summary", "Plan"};
    private static final String[] TOPICS = {"onboarding", "billing", "search", "security", "releases", "reporting",
            "accessibility", "backups", "themes", "exports", "imports", "permissions"};

    private static final List<ListColumn> ARTICLE_COLUMNS = List.of(
            new ListColumn("id", "ID", Cell.Type.NUMBER, true, Align.END),
            new ListColumn("title", "Title", Cell.Type.TEXT, true, Align.START),
            new ListColumn("author", "Author", Cell.Type.RELATION, false, Align.START),
            new ListColumn("status", "Status", Cell.Type.ENUM, true, Align.START),
            new ListColumn("views", "Views", Cell.Type.NUMBER, true, Align.END),
            new ListColumn("published", "Published", Cell.Type.DATE, true, Align.START));

    private static final ListView.Actions NO_ACTIONS = new ListView.Actions(false, false, false, null);

    private record Article(int id, String title, String author, String status, int views, LocalDate published) {
    }

    private record Fixture(String preferredChrome, Function<String, ListView> build) {
    }

    private static final Map<String, Fixture> FIXTURES = new LinkedHashMap<>();

    static {
        FIXTURES.put("normal", fixture("default", base -> articles(base, "", null, null, 0, 25, true)));
        FIXTURES.put("middle-page", fixture("default", base -> articles(base, "", null, null, 1, 25, true)));
        FIXTURES.put("last-page", fixture("default", base -> articles(base, "", null, null, 3, 25, true)));
        FIXTURES.put("single-page", fixture("default", base -> articles(base, "plan", null, null, 0, 25, true)));
        FIXTURES.put("page-size-10", fixture("default", base -> articles(base, "", null, null, 2, 10, true)));
        FIXTURES.put("page-size-50", fixture("default", base -> articles(base, "", null, null, 0, 50, true)));
        FIXTURES.put("sorted-asc", fixture("default", base -> articles(base, "", "title", "asc", 0, 25, true)));
        FIXTURES.put("sorted-desc", fixture("default", base -> articles(base, "", "views", "desc", 0, 25, true)));
        FIXTURES.put("search-active", fixture("default", base -> articles(base, "guide", "title", "asc", 0, 25, true)));
        FIXTURES.put("empty-no-records", fixture("default", ListFixtures::emptyNoRecords));
        FIXTURES.put("empty-no-matches", fixture("default", base -> articles(base, "no article has these words", null,
                null, 0, 25, true)));
        FIXTURES.put("read-only", fixture("default", base -> articles(base, "", null, null, 0, 25, false)));
        FIXTURES.put("masked-column", fixture("member-active", ListFixtures::maskedColumn));
        FIXTURES.put("relation-cells", fixture("default", ListFixtures::relationCells));
        FIXTURES.put("long-text", fixture("default", ListFixtures::longText));
        FIXTURES.put("unicode", fixture("default", ListFixtures::unicode));
        FIXTURES.put("many-columns", fixture("default", ListFixtures::manyColumns));
        FIXTURES.put("all-cell-types", fixture("default", ListFixtures::allCellTypes));
    }

    private ListFixtures() {
    }

    private static Fixture fixture(String chrome, Function<String, ListView> build) {
        return new Fixture(chrome, build);
    }

    static Set<String> states() {
        return FIXTURES.keySet();
    }

    /** The list for {@code state}, with every link under {@code basePath}. */
    static Optional<ListView> list(String state, String basePath) {
        Fixture fixture = FIXTURES.get(state);
        return fixture == null ? Optional.empty() : Optional.of(fixture.build().apply(basePath));
    }

    /** The chrome variant that fits the state when the address names none. */
    static String preferredChrome(String state) {
        Fixture fixture = FIXTURES.get(state);
        return fixture == null ? "default" : fixture.preferredChrome();
    }

    // ---- articles: the fake data set, searched, sorted and paged like the console would ----------------------------

    private static List<Article> allArticles() {
        List<Article> articles = new ArrayList<>();
        for (int i = 1; i <= ARTICLE_COUNT; i++) {
            articles.add(new Article(i, WORDS[i % WORDS.length] + " on " + TOPICS[(i * 5) % TOPICS.length] + " " + i,
                    AUTHORS[i % AUTHORS.length], STATUSES[i % STATUSES.length], (i * 379) % 9000,
                    LocalDate.of(2026, 10, 30).minusDays(i * 2L)));
        }
        return articles;
    }

    private static ListView articles(String basePath, String search, String sort, String dir, int page, int size,
                                     boolean offersActions) {
        List<Article> matching = new ArrayList<>();
        for (Article article : allArticles()) {
            if (search.isEmpty() || article.title().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) {
                matching.add(article);
            }
        }
        matching.sort(comparator(sort, dir));
        int lastPage = matching.isEmpty() ? 0 : (matching.size() - 1) / size;
        int shown = Math.min(page, lastPage);
        List<ListView.Row> rows = new ArrayList<>();
        for (int i = shown * size; i < Math.min(matching.size(), (shown + 1) * size); i++) {
            Article article = matching.get(i);
            rows.add(new ListView.Row(String.valueOf(article.id()), List.of(number(String.valueOf(article.id())),
                    Cell.text(article.title()), relation(article.author(), null), status(article.status()),
                    number(String.format(Locale.ROOT, "%,d", article.views())),
                    new Cell(Cell.Type.DATE, article.published().toString(), Cell.Tone.NONE, null, null))));
        }
        return build(basePath, "article", ARTICLES, ARTICLE_COLUMNS, rows, matching.size(),
                new ListQuery(search, sort, dir, shown, size), actions(basePath, offersActions));
    }

    private static Comparator<Article> comparator(String sort, String dir) {
        Comparator<Article> byId = Comparator.comparingInt(Article::id);
        if (sort == null) {
            return byId;
        }
        Comparator<Article> primary = switch (sort) {
            case "title" -> Comparator.comparing(Article::title);
            case "views" -> Comparator.comparingInt(Article::views);
            case "status" -> Comparator.comparing(Article::status);
            case "published" -> Comparator.comparing(Article::published);
            default -> byId;
        };
        return ("desc".equals(dir) ? primary.reversed() : primary).thenComparing(byId);
    }

    private static ListView emptyNoRecords(String basePath) {
        return build(basePath, "article", ARTICLES, ARTICLE_COLUMNS, List.of(), 0, ListQuery.defaults(),
                actions(basePath, true));
    }

    private static ListView.Actions actions(String basePath, boolean offered) {
        return offered ? new ListView.Actions(true, true, false, basePath + "/article/export.csv") : NO_ACTIONS;
    }

    // ---- other shapes -------------------------------------------------------------------------------------------

    private static ListView maskedColumn(String basePath) {
        List<ListColumn> columns = List.of(
                new ListColumn("id", "ID", Cell.Type.NUMBER, true, Align.END),
                new ListColumn("name", "Name", Cell.Type.TEXT, true, Align.START),
                new ListColumn("email", "Email", Cell.Type.TEXT, true, Align.START),
                new ListColumn("password", "Password", Cell.Type.MASKED, false, Align.START),
                new ListColumn("joined", "Joined", Cell.Type.DATE, true, Align.START));
        String[] names = {"Ava Stone", "Ben Carter", "Chloe Reed", "Dan Moore", "Eva Lane", "Finn Hale"};
        List<ListView.Row> rows = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            rows.add(new ListView.Row(String.valueOf(i + 1), List.of(number(String.valueOf(i + 1)), Cell.text(names[i]),
                    Cell.text(names[i].toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.test"), Cell.masked(),
                    new Cell(Cell.Type.DATE, "2026-0" + (i + 1) + "-15", Cell.Tone.NONE, null, null))));
        }
        return build(basePath, "member", MEMBERS, columns, rows, rows.size(), ListQuery.defaults(), NO_ACTIONS);
    }

    private static ListView relationCells(String basePath) {
        List<ListView.Row> rows = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            Cell author = i % 4 == 0 ? Cell.empty()
                    : relation(AUTHORS[i % AUTHORS.length], basePath + "/member/" + i);
            rows.add(new ListView.Row(String.valueOf(i), List.of(number(String.valueOf(i)), Cell.text("Article " + i),
                    author, status(STATUSES[i % STATUSES.length]), number(String.valueOf(i * 100)),
                    new Cell(Cell.Type.DATE, "2026-10-0" + i, Cell.Tone.NONE, null, null))));
        }
        return build(basePath, "article", ARTICLES, ARTICLE_COLUMNS, rows, rows.size(), ListQuery.defaults(), NO_ACTIONS);
    }

    private static ListView longText(String basePath) {
        String sentence = "This text is much longer than any table cell should be, and it goes on and on so that the "
                + "table has to cut it and offer the rest as hover text. ";
        String longWord = "Supercalifragilisticexpialidocious".repeat(5);
        List<ListColumn> columns = List.of(
                new ListColumn("id", "ID", Cell.Type.NUMBER, true, Align.END),
                new ListColumn("title", "Title", Cell.Type.TEXT, true, Align.START),
                new ListColumn("summary", "A summary column with a very long label that keeps going to see how headers wrap",
                        Cell.Type.TEXT, true, Align.START),
                new ListColumn("author", "Author", Cell.Type.RELATION, false, Align.START));
        List<ListView.Row> rows = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String full = sentence.repeat(3).strip();
            Cell cut = new Cell(Cell.Type.TEXT, full.substring(0, 120).strip() + "…", Cell.Tone.NONE, null, full);
            Cell word = new Cell(Cell.Type.TEXT, longWord.substring(0, 120) + "…", Cell.Tone.NONE, null, longWord);
            rows.add(new ListView.Row(String.valueOf(i), List.of(number(String.valueOf(i)), i % 2 == 0 ? word : cut,
                    i % 2 == 0 ? cut : word, relation("A contributor with a surprisingly long display name " + i, null))));
        }
        return build(basePath, "article", ARTICLES, columns, rows, rows.size(), ListQuery.defaults(), NO_ACTIONS);
    }

    private static ListView unicode(String basePath) {
        String[] titles = {"Ürün kategorileri güncellemesi", "Платёжные поручения за ноябрь", "配送先住所の変更",
                "Παραγγελίες πελατών", "مقال عن التصميم", "😀 Emoji and é combining mark", "Ünïcödé ✓ — – … “quotes”", "<script>alert(1)</script> & \"quotes\" & 'single'"};
        List<ListView.Row> rows = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            rows.add(new ListView.Row(String.valueOf(i + 1), List.of(number(String.valueOf(i + 1)), Cell.text(titles[i]),
                    relation(titles[(i + 1) % titles.length], null), status(STATUSES[i % STATUSES.length]),
                    number(String.valueOf(i * 111)), new Cell(Cell.Type.DATE, "2026-11-0" + (i + 1), Cell.Tone.NONE,
                            null, null))));
        }
        return build(basePath, "article", ARTICLES, ARTICLE_COLUMNS, rows, rows.size(), ListQuery.defaults(), NO_ACTIONS);
    }

    private static ListView manyColumns(String basePath) {
        List<ListColumn> columns = new ArrayList<>();
        columns.add(new ListColumn("id", "ID", Cell.Type.NUMBER, true, Align.END));
        for (int c = 1; c < 20; c++) {
            boolean numeric = c % 3 == 0;
            columns.add(new ListColumn("field" + c, "Field number " + c + (c % 5 == 0 ? " with a longer label" : ""),
                    numeric ? Cell.Type.NUMBER : Cell.Type.TEXT, true, numeric ? Align.END : Align.START));
        }
        List<ListView.Row> rows = new ArrayList<>();
        for (int r = 1; r <= 10; r++) {
            List<Cell> cells = new ArrayList<>();
            cells.add(number(String.valueOf(r)));
            for (int c = 1; c < 20; c++) {
                cells.add(c % 3 == 0 ? number(String.valueOf(r * c * 17)) : Cell.text("Value " + r + "." + c));
            }
            rows.add(new ListView.Row(String.valueOf(r), cells));
        }
        return build(basePath, "article", ARTICLES, columns, rows, rows.size(), ListQuery.defaults(), NO_ACTIONS);
    }

    private static ListView allCellTypes(String basePath) {
        List<ListColumn> columns = List.of(
                new ListColumn("text", "Text", Cell.Type.TEXT, true, Align.START),
                new ListColumn("number", "Number", Cell.Type.NUMBER, true, Align.END),
                new ListColumn("yes", "Boolean yes", Cell.Type.BOOLEAN, true, Align.START),
                new ListColumn("no", "Boolean no", Cell.Type.BOOLEAN, true, Align.START),
                new ListColumn("green", "Enum green", Cell.Type.ENUM, true, Align.START),
                new ListColumn("amber", "Enum amber", Cell.Type.ENUM, true, Align.START),
                new ListColumn("red", "Enum red", Cell.Type.ENUM, true, Align.START),
                new ListColumn("grey", "Enum grey", Cell.Type.ENUM, true, Align.START),
                new ListColumn("none", "Enum none", Cell.Type.ENUM, true, Align.START),
                new ListColumn("related", "Relation", Cell.Type.RELATION, false, Align.START),
                new ListColumn("plain", "Relation, no link", Cell.Type.RELATION, false, Align.START),
                new ListColumn("date", "Date", Cell.Type.DATE, true, Align.START),
                new ListColumn("image", "Image", Cell.Type.IMAGE, false, Align.START),
                new ListColumn("file", "File", Cell.Type.FILE, false, Align.START),
                new ListColumn("secret", "Masked", Cell.Type.MASKED, false, Align.START),
                new ListColumn("empty", "Empty", Cell.Type.EMPTY, false, Align.START));
        List<Cell> cells = List.of(Cell.text("Plain text"), number("1,199.99"),
                new Cell(Cell.Type.BOOLEAN, "Yes", Cell.Tone.GREEN, null, null),
                new Cell(Cell.Type.BOOLEAN, "No", Cell.Tone.NONE, null, null),
                new Cell(Cell.Type.ENUM, "Published", Cell.Tone.GREEN, null, null),
                new Cell(Cell.Type.ENUM, "Pending", Cell.Tone.AMBER, null, null),
                new Cell(Cell.Type.ENUM, "Cancelled", Cell.Tone.RED, null, null),
                new Cell(Cell.Type.ENUM, "In transit", Cell.Tone.GREY, null, null),
                new Cell(Cell.Type.ENUM, "Unknown", Cell.Tone.NONE, null, null),
                relation("Ava Stone", basePath + "/member/1"), relation("Ben Carter", null),
                new Cell(Cell.Type.DATE, "2026-11-01", Cell.Tone.NONE, null, "2026-11-01T10:15:30"),
                new Cell(Cell.Type.IMAGE, "cover.png", Cell.Tone.NONE, basePath + "/files/cover.png", null),
                new Cell(Cell.Type.FILE, "report.pdf", Cell.Tone.NONE, basePath + "/files/report.pdf", null),
                Cell.masked(), Cell.empty());
        return build(basePath, "article", ARTICLES, columns, List.of(new ListView.Row("1", cells)), 1,
                ListQuery.defaults(), NO_ACTIONS);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private static ListView build(String basePath, String urlName, ListView.EntityInfo info, List<ListColumn> columns,
                                  List<ListView.Row> rows, long total, ListQuery query, ListView.Actions actions) {
        return ListViewFactory.build(basePath, urlName, info, new ListPage(columns, rows, total, query), actions);
    }

    private static Cell number(String text) {
        return new Cell(Cell.Type.NUMBER, text, Cell.Tone.NONE, null, null);
    }

    private static Cell relation(String label, String href) {
        return new Cell(Cell.Type.RELATION, label, Cell.Tone.NONE, href, null);
    }

    private static Cell status(String status) {
        Cell.Tone tone = switch (status) {
            case "Published" -> Cell.Tone.GREEN;
            case "Draft" -> Cell.Tone.AMBER;
            case "Archived" -> Cell.Tone.RED;
            default -> Cell.Tone.GREY;
        };
        return new Cell(Cell.Type.ENUM, status, tone, null, null);
    }
}
