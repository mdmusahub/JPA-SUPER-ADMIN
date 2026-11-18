package io.github.mdmusahub.jpaadmin.preview;

import io.github.mdmusahub.jpaadmin.service.DetailRecord;
import io.github.mdmusahub.jpaadmin.service.ListColumn;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.web.DeleteMessages;
import io.github.mdmusahub.jpaadmin.web.DetailViewFactory;
import io.github.mdmusahub.jpaadmin.web.ListViewFactory;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.DetailView;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.ListView.Column.Align;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Breadcrumb;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The fixtures of task U4 ({@code docs/FIXTURES.md} sections 5 and 6): the detail page, the list with its row links,
 * checkboxes and bulk bar, and the pages that follow a delete. Every one is built by the console's real factories
 * ({@link DetailViewFactory}, {@link ListViewFactory}, {@link DeleteMessages}), so the links, the list state in the
 * delete addresses and the texts of the messages are the real ones. The data is invented (articles, members) and holds
 * no real value; a masked cell holds only the mask even here (SEC-1).
 */
final class RecordFixtures {

    /** A list or a detail page: the chrome variant it uses when the address names none, and how to build it. */
    private record Fixture<T>(String preferredChrome, Function<String, T> build) {
    }

    private static final Map<String, Fixture<ListView>> LISTS = new LinkedHashMap<>();
    private static final Map<String, Fixture<DetailPart>> DETAILS = new LinkedHashMap<>();

    /** A detail page together with the pieces of chrome that depend on it: the list state and the messages. */
    private record DetailPart(DetailView view, ListQuery state, List<FlashMessage> flash) {
    }

    /** Every article links to its page, and an author links to a member. The reviewer's target is hidden: no link. */
    private static final List<ListColumn> LINKED_COLUMNS = List.of(
            new ListColumn("id", "ID", Cell.Type.NUMBER, true, Align.END, null, true),
            new ListColumn("title", "Title", Cell.Type.TEXT, true, Align.START),
            new ListColumn("author", "Author", Cell.Type.RELATION, true, Align.START, "member", false),
            new ListColumn("reviewer", "Reviewer", Cell.Type.RELATION, false, Align.START, null, false),
            new ListColumn("status", "Status", Cell.Type.ENUM, true, Align.START),
            new ListColumn("views", "Views", Cell.Type.NUMBER, true, Align.END));

    static {
        LISTS.put("bulk-delete", list("default", base -> linked(base, "", null, null, 0, 25, true, false)));
        LISTS.put("bulk-delete-state", list("default", base -> linked(base, "guide", "title", "asc", 1, 10, true, false)));
        LISTS.put("row-links", list("default", base -> linked(base, "", null, null, 0, 10, false, true)));
        LISTS.put("bulk-read-only", list("default", base -> linked(base, "", null, null, 0, 25, false, false)));
        LISTS.put("bulk-escaped", list("default", RecordFixtures::escaped));
        LISTS.put("delete-done", list("delete-done", base -> linked(base, "", null, null, 0, 25, true, false)));
        LISTS.put("bulk-done", list("bulk-done", base -> linked(base, "", null, null, 0, 25, true, false)));
        LISTS.put("bulk-partial", list("bulk-partial", base -> linked(base, "", null, null, 0, 25, true, false)));
        LISTS.put("bulk-over-cap", list("bulk-over-cap", base -> linked(base, "", null, null, 2, 25, true, false)));
        LISTS.put("bulk-blocked", list("bulk-blocked", base -> linked(base, "", null, null, 0, 25, true, false)));
        LISTS.put("bulk-nothing-selected", list("bulk-nothing-selected", base -> linked(base, "", null, null, 0, 25, true, false)));
        LISTS.put("bulk-last-page", list("default", base -> linked(base, "", null, null, 3, 25, true, false)));

        DETAILS.put("normal", detail("article-detail", base -> normal(base, new DetailViewFactory.Offers(true, true, true))));
        DETAILS.put("real-actions", detail("article-detail", base -> normal(base, new DetailViewFactory.Offers(false, false, true))));
        DETAILS.put("read-only", detail("article-detail", base -> normal(base, new DetailViewFactory.Offers(false, false, false))));
        DETAILS.put("empty-fields", detail("article-detail", RecordFixtures::emptyFields));
        DETAILS.put("long-text", detail("article-detail", RecordFixtures::longText));
        DETAILS.put("relations", detail("article-detail", RecordFixtures::relations));
        DETAILS.put("masked-field", detail("article-detail", RecordFixtures::maskedField));
        DETAILS.put("with-images", detail("article-detail", RecordFixtures::withImages));
        DETAILS.put("all-cell-types", detail("article-detail", RecordFixtures::allCellTypes));
        DETAILS.put("collection-not-shown", detail("article-detail", RecordFixtures::collectionNotShown));
        DETAILS.put("no-label", detail("article-detail", RecordFixtures::noLabel));
        DETAILS.put("escaped", detail("article-detail", RecordFixtures::escaped2));
        DETAILS.put("with-list-state", detail("article-detail", RecordFixtures::withListState));
        DETAILS.put("delete-blocked", detail("delete-blocked", base -> normal(base, new DetailViewFactory.Offers(false, false, true))));
    }

    private RecordFixtures() {
    }

    private static Fixture<ListView> list(String chrome, Function<String, ListView> build) {
        return new Fixture<>(chrome, build);
    }

    private static Fixture<DetailPart> detail(String chrome, Function<String, DetailPart> build) {
        return new Fixture<>(chrome, build);
    }

    static Set<String> listStates() {
        return LISTS.keySet();
    }

    static Set<String> detailStates() {
        return DETAILS.keySet();
    }

    static Optional<ListView> list(String state, String basePath) {
        Fixture<ListView> fixture = LISTS.get(state);
        return fixture == null ? Optional.empty() : Optional.of(fixture.build().apply(basePath));
    }

    static Optional<DetailView> detail(String state, String basePath) {
        Fixture<DetailPart> fixture = DETAILS.get(state);
        return fixture == null ? Optional.empty() : Optional.of(fixture.build().apply(basePath).view());
    }

    /** The chrome variant a list state uses when the address names none, or {@code null} when it is not one of these. */
    static String preferredListChrome(String state) {
        Fixture<ListView> fixture = LISTS.get(state);
        return fixture == null ? null : fixture.preferredChrome();
    }

    /**
     * The chrome of a detail page: the breadcrumbs lead back to the list with the list state the page was opened with, the
     * messages are the real ones, and everything else is the preview's default shell.
     */
    static Optional<PageChrome> detailChrome(String state, String basePath) {
        Fixture<DetailPart> fixture = DETAILS.get(state);
        if (fixture == null) {
            return Optional.empty();
        }
        DetailPart part = fixture.build().apply(basePath);
        List<Breadcrumb> crumbs = DetailViewFactory.breadcrumbs(basePath, "article", "Articles", part.view().title(),
                part.state());
        List<FlashMessage> flash = messages().getOrDefault(fixture.preferredChrome(), part.flash());
        return Optional.of(ChromeFixtures.custom(basePath, "article", crumbs, flash));
    }

    // ---- chrome variants for the pages that follow a delete ----------------------------------------------------------

    /** The messages a visitor sees after a delete, by the name of the chrome variant. All are the console's real texts. */
    static Map<String, List<FlashMessage>> messages() {
        Map<String, List<FlashMessage>> messages = new LinkedHashMap<>();
        messages.put("delete-done", List.of(DeleteMessages.deleted("Article", "Guide on onboarding 3")));
        messages.put("delete-blocked", List.of(DeleteMessages.inUse("Article")));
        messages.put("bulk-done", List.of(DeleteMessages.bulkDeleted("Articles", 3, 0)));
        messages.put("bulk-partial", List.of(DeleteMessages.bulkDeleted("Articles", 2, 1)));
        messages.put("bulk-over-cap", List.of(DeleteMessages.tooMany()));
        messages.put("bulk-blocked", List.of(DeleteMessages.bulkInUse(4)));
        messages.put("bulk-nothing-selected", List.of(DeleteMessages.nothingSelected()));
        return messages;
    }

    // ---- lists ---------------------------------------------------------------------------------------------------------------

    /**
     * Articles as the console lists them since Phase 7: the identifier cell links to the record (carrying the list state),
     * an author links to a member, a reviewer whose entity is hidden does not. {@code bulkDelete} adds the checkbox column
     * and the bar; {@code compact} shows a few rows with the relation cases (a missing relation, a hidden target).
     */
    private static ListView linked(String basePath, String search, String sort, String dir, int page, int size,
                                   boolean bulkDelete, boolean compact) {
        List<ListFixtures.Article> matching = new ArrayList<>();
        for (ListFixtures.Article article : ListFixtures.allArticles(false)) {
            if (search.isEmpty() || article.title().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) {
                matching.add(article);
            }
        }
        if ("title".equals(sort)) {
            matching.sort(java.util.Comparator.comparing(ListFixtures.Article::title));
        }
        int lastPage = matching.isEmpty() ? 0 : (matching.size() - 1) / size;
        int shown = Math.min(page, lastPage);
        List<ListView.Row> rows = new ArrayList<>();
        List<List<String>> targets = new ArrayList<>();
        for (int i = shown * size; i < Math.min(matching.size(), (shown + 1) * size) && (!compact || rows.size() < 8); i++) {
            ListFixtures.Article article = matching.get(i);
            boolean noAuthor = compact && rows.size() % 4 == 3;
            Cell author = noAuthor ? Cell.empty() : ListFixtures.relation(article.author(), null);
            rows.add(new ListView.Row(String.valueOf(article.id()), List.of(ListFixtures.number(String.valueOf(article.id())),
                    Cell.text(article.title()), author, ListFixtures.relation("#" + (article.id() % 9 + 1), null),
                    ListFixtures.status(article.status()), ListFixtures.number(String.valueOf(article.views())))));
            targets.add(java.util.Arrays.asList(null, null, noAuthor ? null : String.valueOf(article.id() % 6 + 1),
                    String.valueOf(article.id() % 9 + 1), null, null));
        }
        ListQuery query = new ListQuery(search, sort, sort == null ? null : dir, shown, size);
        ListPage listPage = new ListPage(LINKED_COLUMNS, rows, matching.size(), query, List.of("Title"), List.of(), List.of(),
                targets);
        ListView.Actions none = new ListView.Actions(false, false, false, null, null);
        return ListViewFactory.build(basePath, "article", ListFixtures.ARTICLES, listPage, none, bulkDelete);
    }

    /** Row identifiers and titles that look like markup: the checkbox labels and values are escaped (SEC-9). */
    private static ListView escaped(String basePath) {
        String[] ids = {"<script>alert(1)</script>", "\"onfocus=\"alert(1)", "a & b", "plain-1"};
        List<ListView.Row> rows = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            rows.add(new ListView.Row(ids[i], List.of(ListFixtures.number(ids[i]), Cell.text("<b>" + ids[i] + "</b>"),
                    Cell.empty(), Cell.empty(), ListFixtures.status("Draft"), ListFixtures.number("0"))));
        }
        ListPage listPage = new ListPage(LINKED_COLUMNS, rows, rows.size(), ListQuery.defaults(), List.of("Title"));
        return ListViewFactory.build(basePath, "article", ListFixtures.ARTICLES, listPage,
                new ListView.Actions(false, false, false, null, null), true);
    }

    // ---- detail pages ------------------------------------------------------------------------------------------------

    private static DetailRecord.Item plain(String label, Cell cell) {
        return DetailRecord.Item.of(label, cell);
    }

    private static DetailRecord.Item member(String label, String name, String basePath, int id) {
        return new DetailRecord.Item(label, new Cell(Cell.Type.RELATION, name, Cell.Tone.NONE, null, null), "member", String.valueOf(id));
    }

    private static List<DetailRecord.Item> articleItems() {
        return List.of(
                plain("ID", ListFixtures.number("7")),
                plain("Title", Cell.text("Guide on onboarding 7")),
                member("Author", "Ava Stone", "", 3),
                plain("Status", ListFixtures.status("Published")),
                plain("Views", ListFixtures.number("2653")),
                plain("Featured", new Cell(Cell.Type.BOOLEAN, "Yes", Cell.Tone.GREEN, null, null)),
                plain("Published", new Cell(Cell.Type.DATE, "2026-10-16", Cell.Tone.NONE, null, null)),
                plain("Summary", Cell.text("A short summary of the article, in one line.")));
    }

    private static DetailPart part(String basePath, DetailRecord record, ListQuery state, DetailViewFactory.Offers offers,
                                   List<FlashMessage> flash) {
        return new DetailPart(DetailViewFactory.build(basePath, "article", "Article", record, state, offers), state, flash);
    }

    private static DetailPart normal(String basePath, DetailViewFactory.Offers offers) {
        return part(basePath, new DetailRecord("7", "Guide on onboarding 7", articleItems()), ListQuery.defaults(), offers,
                List.of());
    }

    private static DetailPart emptyFields(String basePath) {
        List<DetailRecord.Item> items = new ArrayList<>();
        items.add(plain("ID", ListFixtures.number("12")));
        for (String label : List.of("Title", "Summary", "Author", "Status", "Views", "Published", "Featured")) {
            items.add(plain(label, Cell.empty()));
        }
        return part(basePath, new DetailRecord("12", null, items), ListQuery.defaults(),
                new DetailViewFactory.Offers(false, false, true), List.of());
    }

    private static DetailPart longText(String basePath) {
        String paragraph = "This is a long paragraph of body text. It goes on for a while so that the page has to wrap it "
                + "and offer the rest behind a summary. ";
        String longWord = "Supercalifragilisticexpialidocious".repeat(6);
        String body = (paragraph.repeat(8) + "\n\n" + paragraph.repeat(8) + "\n" + longWord).strip();
        List<DetailRecord.Item> items = new ArrayList<>(articleItems());
        items.add(plain("Body", Cell.text(body)));
        items.add(plain("Medium text", Cell.text("A text of a hundred characters or so that fits in the grid without a summary, "
                + "and keeps\nits line break.")));
        items.add(plain("A column with a very long label that keeps going to see how the label wraps in the grid",
                Cell.text(longWord)));
        return part(basePath, new DetailRecord("7", "Guide on onboarding 7", items), ListQuery.defaults(),
                new DetailViewFactory.Offers(true, true, true), List.of());
    }

    private static DetailPart relations(String basePath) {
        List<DetailRecord.Item> items = new ArrayList<>();
        items.add(plain("ID", ListFixtures.number("7")));
        items.add(plain("Title", Cell.text("Guide on onboarding 7")));
        items.add(member("Author", "Ava Stone", basePath, 3));
        items.add(new DetailRecord.Item("Category (no label)",
                new Cell(Cell.Type.RELATION, "#4", Cell.Tone.NONE, null, null), "category", "4"));
        items.add(plain("Reviewer (hidden entity)", new Cell(Cell.Type.RELATION, "#9", Cell.Tone.NONE, null, null)));
        items.add(plain("Editor (not set)", Cell.empty()));
        items.add(member("Related <b>member</b>", "<script>alert(1)</script> & \"quotes\"", basePath, 5));
        return part(basePath, new DetailRecord("7", "Guide on onboarding 7", items), ListQuery.defaults(),
                new DetailViewFactory.Offers(true, true, true), List.of());
    }

    private static DetailPart maskedField(String basePath) {
        List<DetailRecord.Item> items = new ArrayList<>();
        items.add(plain("ID", ListFixtures.number("2")));
        items.add(plain("Name", Cell.text("Ben Carter")));
        items.add(plain("Email", Cell.text("ben.carter@example.test")));
        items.add(plain("Password", Cell.masked()));
        items.add(plain("API token", Cell.masked()));
        return part(basePath, new DetailRecord("2", "Ben Carter", items), ListQuery.defaults(),
                new DetailViewFactory.Offers(true, true, true), List.of());
    }

    private static DetailPart withImages(String basePath) {
        DetailPart base = normal(basePath, new DetailViewFactory.Offers(true, true, true));
        List<DetailView.Field> fields = new ArrayList<>(base.view().fields());
        fields.add(new DetailView.Field("Cover", new Cell(Cell.Type.IMAGE, "cover.png", Cell.Tone.NONE,
                basePath + "/files/cover.png", null)));
        fields.add(new DetailView.Field("Attachment", new Cell(Cell.Type.FILE, "report.pdf", Cell.Tone.NONE,
                basePath + "/files/report.pdf", null)));
        DetailView view = new DetailView(base.view().title(), base.view().entityLabel(), base.view().id(), fields,
                List.of(new DetailView.Image(basePath + "/files/cover.png", "Cover of the article"),
                        new DetailView.Image(basePath + "/files/second.png", "Second picture <b>escaped</b>"),
                        new DetailView.Image(basePath + "/files/missing.png", "A picture that is not there")),
                base.view().actions());
        return new DetailPart(view, ListQuery.defaults(), List.of());
    }

    private static DetailPart allCellTypes(String basePath) {
        List<DetailRecord.Item> items = List.of(
                plain("Text", Cell.text("Plain text")),
                plain("Number", ListFixtures.number("1199.99")),
                plain("Boolean yes", new Cell(Cell.Type.BOOLEAN, "Yes", Cell.Tone.GREEN, null, null)),
                plain("Boolean no", new Cell(Cell.Type.BOOLEAN, "No", Cell.Tone.NONE, null, null)),
                plain("Enum green", new Cell(Cell.Type.ENUM, "Published", Cell.Tone.GREEN, null, null)),
                plain("Enum amber", new Cell(Cell.Type.ENUM, "Pending", Cell.Tone.AMBER, null, null)),
                plain("Enum red", new Cell(Cell.Type.ENUM, "Cancelled", Cell.Tone.RED, null, null)),
                plain("Enum grey", new Cell(Cell.Type.ENUM, "In transit", Cell.Tone.GREY, null, null)),
                member("Relation", "Ava Stone", basePath, 3),
                plain("Date", new Cell(Cell.Type.DATE, "2026-11-01", Cell.Tone.NONE, null, "2026-11-01T10:15:30")),
                plain("Masked", Cell.masked()),
                plain("Empty", Cell.empty()),
                plain("Not shown", Cell.text("(not shown)")));
        DetailPart part = part(basePath, new DetailRecord("1", "Everything", items), ListQuery.defaults(),
                new DetailViewFactory.Offers(true, true, true), List.of());
        List<DetailView.Field> fields = new ArrayList<>(part.view().fields());
        fields.add(new DetailView.Field("Image", new Cell(Cell.Type.IMAGE, "cover.png", Cell.Tone.NONE,
                basePath + "/files/cover.png", null)));
        fields.add(new DetailView.Field("File", new Cell(Cell.Type.FILE, "report.pdf", Cell.Tone.NONE,
                basePath + "/files/report.pdf", null)));
        return new DetailPart(new DetailView(part.view().title(), part.view().entityLabel(), part.view().id(), fields,
                List.of(), part.view().actions()), part.state(), part.flash());
    }

    private static DetailPart collectionNotShown(String basePath) {
        List<DetailRecord.Item> items = new ArrayList<>(articleItems());
        items.add(plain("Comments", Cell.text("(not shown)")));
        items.add(plain("Tags", Cell.text("(not shown)")));
        items.add(plain("Cover bytes", Cell.text("(not shown)")));
        return part(basePath, new DetailRecord("7", "Guide on onboarding 7", items), ListQuery.defaults(),
                new DetailViewFactory.Offers(false, false, true), List.of());
    }

    private static DetailPart noLabel(String basePath) {
        return part(basePath, new DetailRecord("7", null, articleItems()), ListQuery.defaults(),
                new DetailViewFactory.Offers(false, false, true), List.of());
    }

    private static DetailPart escaped2(String basePath) {
        String markup = "<script>alert(1)</script> & \"quotes\" & 'single' <img src=x onerror=alert(1)>";
        List<DetailRecord.Item> items = new ArrayList<>();
        items.add(plain("ID", ListFixtures.number("7")));
        items.add(plain("Title " + "<b>bold label</b>", Cell.text(markup)));
        items.add(plain("Unicode", Cell.text("Ürün kategorileri — Платёжные поручения — 配送先住所 — Παραγγελίες — 😀")));
        items.add(plain("Long markup", Cell.text(markup.repeat(8))));
        return part(basePath, new DetailRecord("7", markup, items), ListQuery.defaults(),
                new DetailViewFactory.Offers(false, false, true), List.of());
    }

    private static DetailPart withListState(String basePath) {
        ListQuery state = new ListQuery("guide & more", "views", "desc", 2, 10);
        return part(basePath, new DetailRecord("7", "Guide on onboarding 7", articleItems()), state,
                new DetailViewFactory.Offers(false, false, true), List.of());
    }
}
