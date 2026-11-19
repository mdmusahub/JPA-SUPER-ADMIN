package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.mdmusahub.jpaadmin.service.ListColumn;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Hidden columns in the page and its links (Phase 12, D-112, D-113): the {@code visible} flag, the {@code hide} parameters
 * in every link that carries the list state (percent-encoded, passing the link rule), the reset to the first page when the
 * hidden set changes, the row link when the identifier is hidden, and the notice.
 */
class ListViewFactoryHiddenTest {

    private static final ListView.EntityInfo INFO = new ListView.EntityInfo("Parcel", "Parcels", "table");
    private static final Pattern SAFE_LINK = Pattern.compile("/[a-z0-9/.-]+(\\?[A-Za-z0-9_.%=&-]*)?");

    private static final List<ListColumn> COLUMNS = List.of(
            new ListColumn("id", "ID", Cell.Type.NUMBER, true, ListView.Column.Align.END, null, true),
            new ListColumn("reference", "Reference", Cell.Type.TEXT, true, ListView.Column.Align.START),
            new ListColumn("weight", "Weight", Cell.Type.NUMBER, true, ListView.Column.Align.END),
            new ListColumn("status", "Status", Cell.Type.ENUM, true, ListView.Column.Align.START),
            new ListColumn("accessToken", "Access token", Cell.Type.MASKED, false, ListView.Column.Align.START));

    private static List<ListView.Row> rows(int count) {
        List<ListView.Row> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(new ListView.Row(String.valueOf(i), List.of(Cell.text(String.valueOf(i)), Cell.text("PKG-" + i),
                    Cell.text("1.5"), Cell.empty(), Cell.masked())));
        }
        return rows;
    }

    private static ListView view(ListQuery query, long total, int rowCount, ListView.Actions actions, boolean bulk,
                                 boolean export) {
        return ListViewFactory.build("/admin", "parcel", INFO, new ListPage(COLUMNS, rows(rowCount), total, query,
                List.of("Reference")), actions, bulk, export);
    }

    private static ListView view(ListQuery query, long total, int rowCount) {
        return view(query, total, rowCount, new ListView.Actions(false, false, false, null, null), false, false);
    }

    private static ListQuery hiding(String... keys) {
        return new ListQuery("", null, null, 0, 25, List.of(), List.of(keys));
    }

    /** The decoded values of every {@code hide} parameter of a link, in order. */
    private static List<String> hides(String link) {
        List<String> values = new ArrayList<>();
        int at = link.indexOf('?');
        if (at >= 0) {
            for (String pair : link.substring(at + 1).split("&")) {
                if (pair.startsWith("hide=")) {
                    values.add(URLDecoder.decode(pair.substring(5), StandardCharsets.UTF_8));
                }
            }
        }
        return values;
    }

    private static List<String> everyLink(ListView view) {
        List<String> links = new ArrayList<>();
        view.columns().stream().map(ListView.Column::sortHref).filter(link -> link != null).forEach(links::add);
        ListView.Paging paging = view.paging();
        for (String link : new String[] {paging.firstHref(), paging.prevHref(), paging.nextHref(), paging.lastHref()}) {
            if (link != null) {
                links.add(link);
            }
        }
        paging.sizes().forEach(size -> links.add(size.href()));
        return links;
    }

    // ---- the page -----------------------------------------------------------------------------------------------

    @Test
    void aHiddenColumnIsNotVisibleAndTheOthersAre() {
        ListView list = view(hiding("weight", "status"), 3, 3);

        assertThat(list.columns()).extracting(ListView.Column::key, ListView.Column::visible).containsExactly(
                org.assertj.core.groups.Tuple.tuple("id", true), org.assertj.core.groups.Tuple.tuple("reference", true),
                org.assertj.core.groups.Tuple.tuple("weight", false), org.assertj.core.groups.Tuple.tuple("status", false),
                org.assertj.core.groups.Tuple.tuple("accessToken", true));
    }

    @Test
    void withNothingHiddenEveryColumnIsVisible() {
        assertThat(view(ListQuery.defaults(), 3, 3).columns()).allMatch(ListView.Column::visible);
    }

    @Test
    void aRowStillHasOneCellPerColumnHiddenOnesIncluded() {
        ListView list = view(hiding("weight"), 3, 3);

        assertThat(list.rows()).allSatisfy(row -> assertThat(row.cells()).hasSize(list.columns().size()));
    }

    // ---- links ----------------------------------------------------------------------------------------------

    @Test
    void everyLinkThatCarriesTheListStateCarriesTheHiddenColumns() {
        ListView list = view(new ListQuery("", "reference", "asc", 1, 10, List.of(), List.of("weight", "status")), 100, 10);

        assertThat(everyLink(list)).isNotEmpty().allSatisfy(link -> assertThat(hides(link)).as(link).containsExactly("weight", "status"));
    }

    @Test
    void theExportLinkCarriesTheHiddenColumnsButNotThePageOrTheSize() {
        ListView list = view(new ListQuery("guide", "reference", "desc", 4, 50, List.of(), List.of("weight")), 400, 50,
                new ListView.Actions(false, false, false, null, null), false, true);

        assertThat(list.actions().exportHref()).isEqualTo("/admin/parcel/export.csv?q=guide&hide=weight&sort=reference&dir=desc");
    }

    @Test
    void theBulkDeleteAddressCarriesTheHiddenColumnsSoTheAnswerReturnsToTheSameList() {
        ListView list = view(new ListQuery("", null, null, 2, 25, List.of(), List.of("weight", "status")), 100, 25,
                new ListView.Actions(false, false, false, null, null), true, false);

        assertThat(list.actions().bulkDeleteHref()).isEqualTo("/admin/parcel/bulk-delete?hide=weight&hide=status&page=2");
    }

    @Test
    void theRowLinkCarriesTheHiddenColumns() {
        ListView list = view(hiding("weight"), 3, 3);

        assertThat(list.rows().get(0).cells().get(0).href()).isEqualTo("/admin/parcel/1?hide=weight");
    }

    @Test
    void theOrderIsSearchFiltersHiddenSortDirPageSize() {
        ListLinks links = new ListLinks("/admin", "parcel");

        assertThat(links.to(new ListQuery("x", "reference", "desc", 3, 50, List.of(), List.of("weight", "status"))))
                .isEqualTo("/admin/parcel?q=x&hide=weight&hide=status&sort=reference&dir=desc&page=3&size=50");
    }

    @Test
    void aViewLinkHasNoPageSoApplyingAViewStartsAtThePageOne() {
        ListLinks links = new ListLinks("/admin", "parcel");
        ListQuery onPageFive = new ListQuery("x", null, null, 5, 25, List.of(), List.of());

        assertThat(links.to(onPageFive.withHidden(List.of("weight")))).as("a change of the view resets the page")
                .isEqualTo("/admin/parcel?q=x&hide=weight").doesNotContain("page=");
        assertThat(links.to(onPageFive.withoutHidden())).doesNotContain("page=");
        assertThat(links.to(onPageFive.withParsedHidden(List.of("weight")))).as("the same address keeps its page")
                .contains("page=5");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a b", "a/b/c", "it's", "say \"hi\"", "http://x", "https://evil.example/a?b=c#d", "100%", "%41",
            "a&b=c", "#frag", "x#y?z", "a+b", "ünï", "東京", "😀 emoji", "<script>alert(1)</script>", "a\\b", "*", "~", "a;b",
            "a,b", "javascript:alert(1)", "//evil.example", "..", "x'; drop table t; --", "tab\there", "line\nbreak",
            "‮override"})
    void everyHiddenNameIsPercentEncodedPassesTheLinkRuleAndComesBackAsGiven(String name) {
        ListQuery query = new ListQuery("", "reference", "asc", 1, 10, List.of(), List.of(name));

        // Building the view runs every record's link rule; an unencoded space, backslash, tab or line feed would throw.
        assertThatCode(() -> view(query, 100, 10, new ListView.Actions(false, false, false, null, null), true, true))
                .doesNotThrowAnyException();
        ListView list = view(query, 100, 10, new ListView.Actions(false, false, false, null, null), true, true);
        List<String> links = new ArrayList<>(everyLink(list));
        links.add(list.actions().exportHref());
        links.add(list.actions().bulkDeleteHref());
        for (String link : links) {
            assertThat(link).as(link).matches(SAFE_LINK).startsWith("/admin/parcel");
            assertThat(link).doesNotContain(" ", "\"", "'", "<", ">", "\\", "\n", "\t", "+", "://");
            assertThat(hides(link)).as(link).containsExactly(name);
        }
    }

    // ---- the row link -----------------------------------------------------------------------------------------

    @Test
    void theIdentifierCellIsTheRowLinkWhenTheIdentifierIsShown() {
        ListView list = view(hiding("weight"), 3, 3);

        assertThat(list.rows().get(0).cells().get(0).href()).isNotNull();
        assertThat(list.rows().get(0).cells().get(1).href()).isNull();
    }

    @Test
    void whenTheIdentifierIsHiddenTheRowLinkMovesToTheFirstVisibleTextOrNumberCell() {
        ListView list = view(hiding("id"), 3, 3);

        ListView.Row row = list.rows().get(1);
        assertThat(row.cells().get(0).href()).as("the hidden identifier's cell").isNull();
        assertThat(row.cells().get(1).href()).as("the reference becomes the link").isEqualTo("/admin/parcel/2?hide=id");
        assertThat(row.cells().get(2).href()).isNull();
        assertThat(row.cells().get(3).href()).isNull();
        assertThat(row.cells().get(4).href()).as("a masked cell is never a link").isNull();
    }

    @Test
    void whenTheIdentifierAndTheTextColumnAreHiddenTheNumberCellIsTheLink() {
        ListView list = view(hiding("id", "reference"), 3, 3);

        assertThat(list.rows().get(0).cells().get(2).href()).isEqualTo("/admin/parcel/1?hide=id&hide=reference");
        assertThat(list.rows().get(0).cells().get(1).href()).isNull();
    }

    @Test
    void aPageWithNoIdentifierColumnHasNoRowLinkWhateverIsHidden() {
        List<ListColumn> noIdentifier = List.of(
                new ListColumn("reference", "Reference", Cell.Type.TEXT, true, ListView.Column.Align.START),
                new ListColumn("weight", "Weight", Cell.Type.NUMBER, true, ListView.Column.Align.END));
        List<ListView.Row> rows = List.of(new ListView.Row("1", List.of(Cell.text("PKG-1"), Cell.text("1.5"))));

        ListView list = ListViewFactory.build("/admin", "parcel", INFO,
                new ListPage(noIdentifier, rows, 1, hiding("weight"), List.of()),
                new ListView.Actions(false, false, false, null, null));

        assertThat(list.rows().get(0).cells()).allSatisfy(cell -> assertThat(cell.href()).isNull());
    }

    // ---- the notice -------------------------------------------------------------------------------------------

    @Test
    void theEveryColumnHiddenNoticeIsAPlainWarningThatRepeatsNothingTyped() {
        PageChrome.FlashMessage notice = ListViewFactory.everyColumnHiddenNotice();

        assertThat(notice.level()).isEqualTo(PageChrome.FlashMessage.Level.WARNING);
        assertThat(notice.text()).isEqualTo("Every column was hidden, so all columns are shown. A list needs at least one column.");
        assertThat(notice.text()).doesNotContain("<", ">", "&", "hide=");
    }
}
