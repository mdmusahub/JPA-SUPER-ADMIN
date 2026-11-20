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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The state of the page, the "Show all columns" link and the "Clear search" link (Phase 13, D-118), and the page size the
 * links leave out (D-119): what {@code ListView.state} holds for every shape of query.
 */
class ListViewFactoryStateTest {

    private static final ListView.EntityInfo INFO = new ListView.EntityInfo("Parcel", "Parcels", "table");

    private static final List<ListColumn> COLUMNS = List.of(
            new ListColumn("id", "ID", Cell.Type.NUMBER, true, ListView.Column.Align.END, null, true),
            new ListColumn("reference", "Reference", Cell.Type.TEXT, true, ListView.Column.Align.START),
            new ListColumn("weight", "Weight", Cell.Type.NUMBER, true, ListView.Column.Align.END),
            new ListColumn("status", "Status", Cell.Type.ENUM, true, ListView.Column.Align.START),
            new ListColumn("accessToken", "Access token", Cell.Type.MASKED, false, ListView.Column.Align.START));

    private static ListView view(ListQuery query) {
        return view(COLUMNS, query);
    }

    private static ListView view(List<ListColumn> columns, ListQuery query) {
        List<Cell> cells = new ArrayList<>();
        for (ListColumn column : columns) {
            cells.add(column.type() == Cell.Type.MASKED ? Cell.masked() : Cell.text("x"));
        }
        return ListViewFactory.build("/admin", "parcel", INFO, new ListPage(columns,
                List.of(new ListView.Row("1", cells)), 300, query, List.of("Reference")),
                new ListView.Actions(false, false, false, null, null));
    }

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

    // ---- the state address --------------------------------------------------------------------------------------------

    @Test
    void theStateAddressCarriesTheAcceptedStateAndNeverAPage() {
        ListQuery query = new ListQuery("guide & more", "reference", "desc", 3, 50, List.of(), List.of("weight"));

        assertThat(view(query).state().href())
                .isEqualTo("/admin/parcel?q=guide%20%26%20more&hide=weight&sort=reference&dir=desc&size=50");
    }

    @Test
    void theStateAddressOfAPlainListIsTheListAddress() {
        assertThat(view(ListQuery.defaults()).state().href()).isEqualTo("/admin/parcel");
    }

    @Test
    void theStateAddressIsTheAddressOfTheCurrentPageSizeOption() {
        // The script used to read the state from that option; the new field must say the same thing (D-115 became D-118).
        ListQuery query = new ListQuery("x", "weight", "asc", 2, 10, List.of(), List.of("status"));

        ListView list = view(query);

        assertThat(list.paging().sizes()).filteredOn(ListView.Paging.SizeOption::current)
                .singleElement().extracting(ListView.Paging.SizeOption::href).isEqualTo(list.state().href());
    }

    // ---- show all columns ---------------------------------------------------------------------------------------------

    @Test
    void thereIsNoShowAllLinkWhenNoColumnIsHidden() {
        assertThat(view(ListQuery.defaults()).state().showAllColumnsHref()).isNull();
        assertThat(view(new ListQuery("a", "weight", "asc", 0, 10)).state().showAllColumnsHref()).isNull();
    }

    @Test
    void theShowAllLinkIsThereWhenAColumnIsHiddenAndKeepsTheRestOfTheStateExceptThePage() {
        ListQuery query = new ListQuery("guide", "reference", "asc", 4, 10, List.of(), List.of("weight", "status"));

        String link = view(query).state().showAllColumnsHref();

        assertThat(link).isEqualTo("/admin/parcel?q=guide&sort=reference&dir=asc&size=10");
        assertThat(hides(link)).isEmpty();
    }

    // ---- clear search -------------------------------------------------------------------------------------------------

    @Test
    void clearSearchKeepsTheHiddenColumnsAndNothingElse() {
        ListQuery query = new ListQuery("guide", "reference", "asc", 2, 50, List.of(), List.of("weight", "status"));

        String link = view(query).state().clearSearchHref();

        assertThat(link).isEqualTo("/admin/parcel?hide=weight&hide=status");
        assertThat(link).doesNotContain("q=").doesNotContain("sort=").doesNotContain("size=").doesNotContain("page=");
    }

    @Test
    void clearSearchWithNothingHiddenIsThePlainAddress() {
        assertThat(view(new ListQuery("guide", null, null, 0, 25)).state().clearSearchHref()).isEqualTo("/admin/parcel");
    }

    // ---- the page size a link leaves out ------------------------------------------------------------------------------

    @Test
    void theDefaultSizeIsTheQuerysOwn() {
        assertThat(view(ListQuery.defaults()).state().defaultSize()).isEqualTo(25);
        assertThat(view(ListQuery.defaults(50)).state().defaultSize()).isEqualTo(50);
    }

    @Test
    void underADefaultOfFiftyASizeOfFiftyIsLeftOutAndASizeOfTwentyFiveIsSaid() {
        ListQuery query = new ListQuery("", "reference", "asc", 0, 50, List.of(), List.of(), 50);

        ListView list = view(query);

        assertThat(list.state().href()).isEqualTo("/admin/parcel?sort=reference&dir=asc");
        assertThat(list.paging().sizes()).extracting(ListView.Paging.SizeOption::size, ListView.Paging.SizeOption::href)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10, "/admin/parcel?sort=reference&dir=asc&size=10"),
                        org.assertj.core.groups.Tuple.tuple(25, "/admin/parcel?sort=reference&dir=asc&size=25"),
                        org.assertj.core.groups.Tuple.tuple(50, "/admin/parcel?sort=reference&dir=asc"),
                        org.assertj.core.groups.Tuple.tuple(100, "/admin/parcel?sort=reference&dir=asc&size=100"));
    }

    @Test
    void underTheUsualDefaultTheLinksAreExactlyWhatTheyWereBefore() {
        ListView list = view(new ListQuery("", "reference", "asc", 1, 25));

        assertThat(list.paging().sizes()).extracting(ListView.Paging.SizeOption::href).containsExactly(
                "/admin/parcel?sort=reference&dir=asc&size=10", "/admin/parcel?sort=reference&dir=asc",
                "/admin/parcel?sort=reference&dir=asc&size=50", "/admin/parcel?sort=reference&dir=asc&size=100");
    }

    @Test
    void theExportLinkLeavesTheSizeOutUnderAnyDefault() {
        ListQuery query = new ListQuery("guide", null, null, 3, 100, List.of(), List.of(), 50);

        ListView list = ListViewFactory.build("/admin", "parcel", INFO, new ListPage(COLUMNS,
                List.of(new ListView.Row("1", List.of(Cell.text("1"), Cell.text("a"), Cell.text("1"), Cell.text("s"),
                        Cell.masked()))), 300, query, List.of("Reference")),
                new ListView.Actions(false, false, false, null, null), false, true);

        assertThat(list.actions().exportHref()).isEqualTo("/admin/parcel/export.csv?q=guide");
    }

    // ---- awkward column names -----------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"a b", "a&b=c", "a\"b", "a'b", "a/b", "a\\b", "a#b", "a%b", "a+b", "http://x", "éè",
            "日本", "a?b", "../x"})
    void aColumnNameThatIsNotPlainIsEncodedInTheShowAllAndClearSearchLinks(String key) {
        List<ListColumn> columns = List.of(
                new ListColumn("id", "ID", Cell.Type.NUMBER, true, ListView.Column.Align.END, null, true),
                new ListColumn(key, "Odd", Cell.Type.TEXT, true, ListView.Column.Align.START),
                new ListColumn("other", "Other", Cell.Type.TEXT, true, ListView.Column.Align.START));
        ListQuery query = new ListQuery("", null, null, 0, 25, List.of(), List.of(key));

        ListView list = view(columns, query);

        // The record constructor already applied the link rule; the decoded name must come back exactly.
        assertThat(hides(list.state().clearSearchHref())).containsExactly(key);
        assertThat(hides(list.state().href())).containsExactly(key);
        assertThat(list.state().showAllColumnsHref()).isEqualTo("/admin/parcel");
        assertThat(list.state().clearSearchHref()).startsWith("/admin/parcel?hide=").doesNotContain(" ").doesNotContain("\"");
    }

    @Test
    void everyLinkOfTheStateLiesUnderTheBasePath() {
        ListView.State state = view(new ListQuery("a", "weight", "asc", 2, 10, List.of(), List.of("status"))).state();

        assertThat(state.href()).startsWith("/admin/parcel");
        assertThat(state.showAllColumnsHref()).startsWith("/admin/parcel");
        assertThat(state.clearSearchHref()).startsWith("/admin/parcel");
    }
}
