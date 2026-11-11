package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.service.ListColumn;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The links of the list page (D-28, D-29) and the way a {@link ListView} is put together from a page of display values.
 * Every link must lie under the base path, carry the search/sort/size that were asked for, and have every value
 * percent-encoded: spaces, slashes, quotes, unicode and a literal {@code http://x} included.
 */
class ListViewFactoryTest {

    private static final ListView.EntityInfo INFO = new ListView.EntityInfo("Parcel", "Parcels", "table");

    /** Letters, digits and - _ ., percent-escapes, and the two separators: nothing else may be in a query. */
    private static final Pattern SAFE_LINK = Pattern.compile("/[a-z0-9/-]+(\\?[A-Za-z0-9_.%=&-]*)?");

    private static final List<ListColumn> COLUMNS = List.of(
            new ListColumn("id", "ID", Cell.Type.NUMBER, true, ListView.Column.Align.END),
            new ListColumn("reference", "Reference", Cell.Type.TEXT, true, ListView.Column.Align.START),
            new ListColumn("depot", "Depot", Cell.Type.RELATION, false, ListView.Column.Align.START),
            new ListColumn("accessToken", "Access token", Cell.Type.MASKED, false, ListView.Column.Align.START));

    private static List<ListView.Row> rows(int count) {
        List<ListView.Row> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(new ListView.Row(String.valueOf(i), List.of(Cell.text(String.valueOf(i)), Cell.text("PKG-" + i),
                    Cell.empty(), Cell.masked())));
        }
        return rows;
    }

    private static ListView view(String basePath, ListQuery query, long total, int rowCount) {
        return ListViewFactory.build(basePath, "parcel", INFO, new ListPage(COLUMNS, rows(rowCount), total, query),
                new ListView.Actions(false, false, false, null));
    }

    private static ListView view(ListQuery query, long total, int rowCount) {
        return view("/admin", query, total, rowCount);
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

    private static Map<String, String> parameters(String link) {
        Map<String, String> parameters = new LinkedHashMap<>();
        int at = link.indexOf('?');
        if (at < 0) {
            return parameters;
        }
        for (String pair : link.substring(at + 1).split("&")) {
            String[] keyAndValue = pair.split("=", 2);
            parameters.put(keyAndValue[0], URLDecoder.decode(keyAndValue[1], StandardCharsets.UTF_8));
        }
        return parameters;
    }

    // ---- encoding ---------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"a b", "  lead and trail  ", "a/b/c", "it's", "say \"hi\"", "http://x", "https://evil.example/a?b=c#d",
            "100%", "%41", "a&b=c", "#frag", "x#y?z", "a+b", "ünï", "東京", "😀 emoji", "<script>alert(1)</script>",
            "a\\b", "*", "~", "a;b", "a,b", "javascript:alert(1)", "//evil.example", "..", "x'; drop table t; --"})
    void everyValueIsPercentEncodedAndComesBackAsTyped(String search) {
        ListView list = view(new ListQuery(search, "reference", "asc", 1, 10), 100, 10);

        for (String link : everyLink(list)) {
            assertThat(link).as(link).matches(SAFE_LINK).startsWith("/admin/parcel");
            assertThat(link).doesNotContain(" ", "\"", "'", "<", ">", "\\", "\n", "\t", "+", "://");
            assertThat(parameters(link)).containsEntry("q", search);
        }
    }

    @Test
    void aSpaceIsPercentTwentyNeverARawSpaceOrAPlus() {
        ListView list = view(new ListQuery("a b", null, null, 0, 25), 100, 25);

        assertThat(list.paging().nextHref()).isEqualTo("/admin/parcel?q=a%20b&page=1");
        assertThat(list.paging().nextHref()).doesNotContain(" ", "+");
    }

    @Test
    void aLiteralUrlInTheSearchStaysDataInTheQuery() {
        ListView list = view(new ListQuery("http://x", null, null, 0, 10), 100, 10);

        assertThat(list.paging().nextHref()).isEqualTo("/admin/parcel?q=http%3A%2F%2Fx&page=1&size=10");
        assertThat(everyLink(list)).allSatisfy(link -> assertThat(link).doesNotContain("://").doesNotContain("//x"));
    }

    @Test
    void encodingIsUtf8PercentBytes() {
        assertThat(ListLinks.encode("é")).isEqualTo("%C3%A9");
        assertThat(ListLinks.encode("東")).isEqualTo("%E6%9D%B1");
        assertThat(ListLinks.encode("😀")).isEqualTo("%F0%9F%98%80");
        assertThat(ListLinks.encode("A-z_0.9")).as("letters, digits and - _ . stay").isEqualTo("A-z_0.9");
        assertThat(ListLinks.encode("~")).as("everything else is escaped, a tilde too").isEqualTo("%7E");
        assertThat(ListLinks.encode("*")).isEqualTo("%2A");
        assertThat(ListLinks.encode(" ")).isEqualTo("%20");
        assertThat(ListLinks.encode("'\"<>&=#?/\\")).doesNotContain("'", "\"", "<", ">", "&", "=", "#", "?", "/", "\\");
    }

    @Test
    void aNonDefaultBasePathIsUsedInEveryLink() {
        ListView list = view("/ops/console", new ListQuery("x", "reference", "asc", 1, 10), 100, 10);

        assertThat(everyLink(list)).isNotEmpty().allSatisfy(link -> assertThat(link).startsWith("/ops/console/parcel"));
    }

    @Test
    void theDefaultStateIsThePlainAddress() {
        ListView list = view(ListQuery.defaults(), 10, 10);

        assertThat(list.paging().sizes().stream().filter(ListView.Paging.SizeOption::current).findFirst().orElseThrow().href())
                .isEqualTo("/admin/parcel");
    }

    // ---- sort -------------------------------------------------------------------------------------------------

    @Test
    void aSortableHeaderLinksToAscendingFirstAndReversesWhenAlreadySorted() {
        ListView unsorted = view(ListQuery.defaults(), 100, 25);
        ListView ascending = view(new ListQuery("", "reference", "asc", 0, 25), 100, 25);
        ListView descending = view(new ListQuery("", "reference", "desc", 0, 25), 100, 25);

        assertThat(column(unsorted, "reference").sortHref()).isEqualTo("/admin/parcel?sort=reference&dir=asc");
        assertThat(column(ascending, "reference").sortHref()).isEqualTo("/admin/parcel?sort=reference&dir=desc");
        assertThat(column(descending, "reference").sortHref()).isEqualTo("/admin/parcel?sort=reference&dir=asc");
    }

    @Test
    void theSortStateOfEachColumnMatchesTheSortAndOnlyTheSortedColumnHasOne() {
        ListView ascending = view(new ListQuery("", "reference", "asc", 0, 25), 100, 25);
        ListView descending = view(new ListQuery("", "reference", "desc", 0, 25), 100, 25);

        assertThat(column(ascending, "reference").sortState()).isEqualTo(ListView.Column.SortState.ASCENDING);
        assertThat(column(descending, "reference").sortState()).isEqualTo(ListView.Column.SortState.DESCENDING);
        assertThat(column(ascending, "id").sortState()).isEqualTo(ListView.Column.SortState.NONE);
        assertThat(ascending.sort()).isEqualTo("reference");
        assertThat(ascending.dir()).isEqualTo("asc");
    }

    @Test
    void aColumnThatCannotBeSortedHasNoSortLink() {
        ListView list = view(ListQuery.defaults(), 100, 25);

        assertThat(column(list, "depot").sortable()).isFalse();
        assertThat(column(list, "depot").sortHref()).isNull();
        assertThat(column(list, "accessToken").sortable()).isFalse();
        assertThat(column(list, "accessToken").sortHref()).as("no link that could name a sensitive field").isNull();
        assertThat(String.join(" ", everyLink(list))).doesNotContain("accessToken", "depot");
    }

    @Test
    void sortingKeepsTheSearchAndSizeButGoesBackToTheFirstPage() {
        ListView list = view(new ListQuery("pkg", null, null, 2, 50), 500, 50);

        assertThat(column(list, "reference").sortHref()).isEqualTo("/admin/parcel?q=pkg&sort=reference&dir=asc&size=50");
    }

    // ---- paging -----------------------------------------------------------------------------------------------

    @Test
    void theFirstPageHasNoFirstOrPreviousLink() {
        ListView.Paging paging = view(ListQuery.defaults(), 55, 25).paging();

        assertThat(paging.firstHref()).isNull();
        assertThat(paging.prevHref()).isNull();
        assertThat(paging.nextHref()).isEqualTo("/admin/parcel?page=1");
        assertThat(paging.lastHref()).isEqualTo("/admin/parcel?page=2");
        assertThat(paging.totalPages()).isEqualTo(3);
        assertThat(paging.firstRow()).isEqualTo(1);
        assertThat(paging.lastRow()).isEqualTo(25);
    }

    @Test
    void aMiddlePageHasAllFourLinks() {
        ListView.Paging paging = view(new ListQuery("", null, null, 1, 25), 55, 25).paging();

        assertThat(paging.firstHref()).isEqualTo("/admin/parcel");
        assertThat(paging.prevHref()).isEqualTo("/admin/parcel");
        assertThat(paging.nextHref()).isEqualTo("/admin/parcel?page=2");
        assertThat(paging.lastHref()).isEqualTo("/admin/parcel?page=2");
        assertThat(paging.firstRow()).isEqualTo(26);
        assertThat(paging.lastRow()).isEqualTo(50);
    }

    @Test
    void theLastPageHasNoNextOrLastLinkAndItsRangeEndsAtTheTotal() {
        ListView.Paging paging = view(new ListQuery("", null, null, 2, 25), 55, 5).paging();

        assertThat(paging.nextHref()).isNull();
        assertThat(paging.lastHref()).isNull();
        assertThat(paging.firstHref()).isEqualTo("/admin/parcel");
        assertThat(paging.prevHref()).isEqualTo("/admin/parcel?page=1");
        assertThat(paging.firstRow()).isEqualTo(51);
        assertThat(paging.lastRow()).isEqualTo(55);
    }

    @Test
    void aSinglePageHasNoPageLinks() {
        ListView.Paging paging = view(ListQuery.defaults(), 7, 7).paging();

        assertThat(paging.firstHref()).isNull();
        assertThat(paging.prevHref()).isNull();
        assertThat(paging.nextHref()).isNull();
        assertThat(paging.lastHref()).isNull();
    }

    @Test
    void thePageSizesAreTheFourOnOfferEachWithALinkAndOnlyTheCurrentOneMarked() {
        ListView.Paging paging = view(new ListQuery("x", "reference", "desc", 3, 50), 500, 50).paging();

        assertThat(paging.sizes().stream().map(ListView.Paging.SizeOption::size)).containsExactly(10, 25, 50, 100);
        assertThat(paging.sizes().stream().filter(ListView.Paging.SizeOption::current).map(ListView.Paging.SizeOption::size))
                .containsExactly(50);
        assertThat(paging.sizes().get(0).href()).as("a new size goes back to the first page")
                .isEqualTo("/admin/parcel?q=x&sort=reference&dir=desc&size=10");
        assertThat(paging.sizes().get(1).href()).isEqualTo("/admin/parcel?q=x&sort=reference&dir=desc");
    }

    @Test
    void pagingLinksKeepSearchSortAndSize() {
        ListView.Paging paging = view(new ListQuery("a b", "pieces", "desc", 1, 10), 100, 10).paging();

        assertThat(paging.nextHref()).isEqualTo("/admin/parcel?q=a%20b&sort=pieces&dir=desc&page=2&size=10");
        assertThat(paging.prevHref()).isEqualTo("/admin/parcel?q=a%20b&sort=pieces&dir=desc&size=10");
    }

    // ---- the rest of the view ---------------------------------------------------------------------------------

    @Test
    void noActionIsOfferedAndNoFilterExistsYet() {
        ListView list = view(ListQuery.defaults(), 25, 25);

        assertThat(list.actions()).isEqualTo(new ListView.Actions(false, false, false, null));
        assertThat(list.filters().definitions()).isEmpty();
        assertThat(list.filters().chips()).isEmpty();
        assertThat(list.filters().clearAllHref()).isNull();
        assertThat(list.columns()).allSatisfy(column -> assertThat(column.filterable()).isFalse());
    }

    @Test
    void anEmptyListWithoutASearchSaysThereAreNoRecords() {
        ListView list = view(ListQuery.defaults(), 0, 0);

        assertThat(list.empty().reason()).isEqualTo(ListView.Empty.Reason.NO_RECORDS);
        assertThat(list.empty().hint()).contains("no parcels yet");
    }

    @Test
    void anEmptyListWithASearchSaysNothingMatches() {
        ListView list = view(new ListQuery("zzz", null, null, 0, 25), 0, 0);

        assertThat(list.empty().reason()).isEqualTo(ListView.Empty.Reason.NO_MATCHES);
        assertThat(list.empty().hint()).contains("match your search").contains("clear the search");
        assertThat(list.search()).isEqualTo("zzz");
    }

    @Test
    void aListWithRowsIsNotEmpty() {
        assertThat(view(ListQuery.defaults(), 25, 25).empty().reason()).isEqualTo(ListView.Empty.Reason.NONE);
    }

    @Test
    void theMaskedColumnIsInTheViewButItsCellsHoldOnlyTheMask() {
        ListView list = view(ListQuery.defaults(), 3, 3);

        assertThat(column(list, "accessToken").type()).isEqualTo(Cell.Type.MASKED);
        assertThat(list.rows()).allSatisfy(row -> assertThat(row.cells().get(3)).isEqualTo(Cell.masked()));
    }

    // ---- plurals ----------------------------------------------------------------------------------------------

    @Test
    void thePluralFollowsThePlainEnglishRules() {
        assertThat(Plurals.of("Invoice")).isEqualTo("Invoices");
        assertThat(Plurals.of("Box")).isEqualTo("Boxes");
        assertThat(Plurals.of("Address")).isEqualTo("Addresses");
        assertThat(Plurals.of("Batch")).isEqualTo("Batches");
        assertThat(Plurals.of("Dish")).isEqualTo("Dishes");
        assertThat(Plurals.of("Entry")).isEqualTo("Entries");
        assertThat(Plurals.of("Key")).as("a vowel before the y").isEqualTo("Keys");
        assertThat(Plurals.of("Http log entry")).isEqualTo("Http log entries");
        assertThat(Plurals.of("Y")).isEqualTo("Ys");
        assertThat(Plurals.of("")).isEmpty();
    }

    private static ListView.Column column(ListView list, String key) {
        return list.columns().stream().filter(column -> column.key().equals(key)).findFirst().orElseThrow();
    }
}
