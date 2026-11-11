package io.github.mdmusahub.jpaadmin.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Rules that records enforce beyond null checks. */
class ViewInvariantsTest {

    private static final ListView.Empty NO_RECORDS = new ListView.Empty(ListView.Empty.Reason.NO_RECORDS,
            "Create the first record.");

    private static final List<ListView.Column> COLUMNS = List.of(Samples.nameColumn(), Samples.secretColumn());

    @Test
    void rowMustHaveOneCellPerColumn() {
        ListView.Row shortRow = new ListView.Row("2", List.of(Cell.text("only one")));

        assertThatThrownBy(() -> Samples.list(COLUMNS, List.of(shortRow), null, null, Samples.notEmpty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one cell per column");
    }

    @Test
    void emptyReasonMatchesWhetherThereAreRows() {
        assertThatCode(() -> Samples.list(COLUMNS, List.of(), null, null, NO_RECORDS)).doesNotThrowAnyException();
        assertThatThrownBy(() -> Samples.list(COLUMNS, List.of(), null, null, Samples.notEmpty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Samples.list(COLUMNS, List.of(Samples.row()), null, null, NO_RECORDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sortAndDirectionAreSetTogether() {
        List<ListView.Column> sortedByName = List.of(Samples.nameColumn(ListView.Column.SortState.DESCENDING),
                Samples.secretColumn());

        assertThatCode(() -> Samples.list(sortedByName, List.of(Samples.row()), "name", "desc", Samples.notEmpty()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> Samples.list(COLUMNS, List.of(Samples.row()), "name", null, Samples.notEmpty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Samples.list(COLUMNS, List.of(Samples.row()), null, "asc", Samples.notEmpty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Samples.list(COLUMNS, List.of(Samples.row()), "name", "sideways", Samples.notEmpty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ListView.Paging paging(int page, long total, String first, String prev, String next, String last) {
        return new ListView.Paging(page, 25, total, first, prev, next, last, Samples.sizes(25));
    }

    @Test
    void pagingComputesPagesAndRowRange() {
        ListView.Paging middle = Samples.middlePage();
        ListView.Paging lastOfThree = paging(2, 60, "/a?page=0", "/a?page=1", null, null);

        assertThat(middle.totalPages()).isEqualTo(3);
        assertThat(middle.firstRow()).isEqualTo(26);
        assertThat(middle.lastRow()).isEqualTo(50);
        assertThat(lastOfThree.totalPages()).isEqualTo(3);
        assertThat(lastOfThree.lastRow()).isEqualTo(60);
    }

    @Test
    void pagingWithoutRecordsHasNoRange() {
        ListView.Paging paging = paging(0, 0, null, null, null, null);

        assertThat(paging.totalPages()).isZero();
        assertThat(paging.firstRow()).isZero();
        assertThat(paging.lastRow()).isZero();
    }

    @Test
    void pagingRejectsNegativeOrZeroValues() {
        assertThatThrownBy(() -> paging(-1, 0, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListView.Paging(0, 0, 0, null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paging(0, -1, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void firstPageHasOnlyForwardLinks() {
        assertThatCode(() -> paging(0, 75, null, null, "/a?page=1", "/a?page=2")).doesNotThrowAnyException();
        assertThatThrownBy(() -> paging(0, 75, "/a?page=0", null, "/a?page=1", "/a?page=2"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("previous page");
        assertThatThrownBy(() -> paging(0, 75, null, "/a?page=0", "/a?page=1", "/a?page=2"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("previous page");
        assertThatThrownBy(() -> paging(0, 75, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("next page");
    }

    @Test
    void middlePageHasAllFourLinks() {
        assertThatCode(Samples::middlePage).doesNotThrowAnyException();
        assertThatThrownBy(() -> paging(1, 75, "/a?page=0", null, "/a?page=2", "/a?page=2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paging(1, 75, "/a?page=0", "/a?page=0", "/a?page=2", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paging(1, 75, null, "/a?page=0", "/a?page=2", "/a?page=2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lastPageHasOnlyBackwardLinks() {
        assertThatCode(() -> paging(2, 75, "/a?page=0", "/a?page=1", null, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> paging(2, 75, "/a?page=0", "/a?page=1", "/a?page=3", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("next page");
        assertThatThrownBy(() -> paging(2, 75, "/a?page=0", "/a?page=1", null, "/a?page=2"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("next page");
    }

    @Test
    void singlePageHasNoLinksAtAll() {
        assertThatCode(() -> paging(0, 25, null, null, null, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> paging(0, 25, null, null, "/a?page=1", "/a?page=1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPagePastTheEndStillOffersWayBack() {
        assertThatCode(() -> paging(9, 75, "/a?page=0", "/a?page=2", null, null)).doesNotThrowAnyException();
    }

    @Test
    void exactlyThePageSizeInUseIsCurrent() {
        List<ListView.Paging.SizeOption> noneCurrent = Samples.sizes(0);
        List<ListView.Paging.SizeOption> wrongOne = Samples.sizes(50);

        assertThat(Samples.paging().sizes()).filteredOn(ListView.Paging.SizeOption::current)
                .extracting(ListView.Paging.SizeOption::size).containsExactly(25);
        for (List<ListView.Paging.SizeOption> sizes : List.of(noneCurrent, wrongOne)) {
            assertThatThrownBy(() -> new ListView.Paging(0, 25, 1, null, null, null, null, sizes))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("current");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void aPageSizeMustBePositive(int size) {
        assertThatThrownBy(() -> new ListView.Paging.SizeOption(size, false, "/a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sortLinkIsPresentExactlyForSortableColumns() {
        assertThatCode(Samples::nameColumn).doesNotThrowAnyException();
        assertThatCode(Samples::secretColumn).doesNotThrowAnyException();
        assertThatThrownBy(() -> new ListView.Column("k", "l", Cell.Type.TEXT, true, null, ListView.Column.SortState.NONE,
                ListView.Column.Align.START, true, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sortHref");
        assertThatThrownBy(() -> new ListView.Column("k", "l", Cell.Type.TEXT, false, "/a?sort=k",
                ListView.Column.SortState.NONE, ListView.Column.Align.START, true, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sortHref");
    }

    @Test
    void aColumnThatIsNotSortableHasNoSortDirection() {
        assertThatThrownBy(() -> new ListView.Column("k", "l", Cell.Type.TEXT, false, null,
                ListView.Column.SortState.ASCENDING, ListView.Column.Align.START, true, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void columnSortStatesMatchTheListSortAndDirection() {
        ListView.Column asc = Samples.nameColumn(ListView.Column.SortState.ASCENDING);
        ListView.Column desc = Samples.nameColumn(ListView.Column.SortState.DESCENDING);
        ListView.Column none = Samples.nameColumn();
        List<ListView.Row> rows = List.of(Samples.row());

        assertThatCode(() -> Samples.list(List.of(asc, Samples.secretColumn()), rows, "name", "asc", Samples.notEmpty()))
                .doesNotThrowAnyException();
        assertThatCode(() -> Samples.list(List.of(none, Samples.secretColumn()), rows, null, null, Samples.notEmpty()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> Samples.list(List.of(desc, Samples.secretColumn()), rows, "name", "asc",
                Samples.notEmpty())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sortState");
        assertThatThrownBy(() -> Samples.list(List.of(none, Samples.secretColumn()), rows, "name", "asc",
                Samples.notEmpty())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sortState");
        assertThatThrownBy(() -> Samples.list(List.of(asc, Samples.secretColumn()), rows, null, null,
                Samples.notEmpty())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sortState");
    }

    @Test
    void sortingByAColumnThatIsNotSortableIsRejected() {
        assertThatThrownBy(() -> Samples.list(COLUMNS, List.of(Samples.row()), "secret", "asc", Samples.notEmpty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportLinkIsPresentExactlyWhenExportIsOffered() {
        assertThatCode(() -> new ListView.Actions(true, true, true, "/admin/item/export")).doesNotThrowAnyException();
        assertThatCode(() -> new ListView.Actions(true, false, true, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new ListView.Actions(true, true, true, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exportHref");
        assertThatThrownBy(() -> new ListView.Actions(true, false, true, "/admin/item/export"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exportHref");
    }

    @Test
    void editFormNeedsARecordIdAndCreateFormHasNone() {
        assertThat(Samples.form().recordId()).isEqualTo("1");
        assertThat(Samples.createForm().recordId()).isNull();
        assertThatThrownBy(() -> new FormView(FormView.Mode.EDIT, "t", "e", null, "/a", "/a", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("recordId");
        assertThatThrownBy(() -> new FormView(FormView.Mode.CREATE, "t", "e", "7", "/a", "/a", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("recordId");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 8, 14, 31, 60, 91, 365})
    void chartRejectsUnsupportedRanges(int range) {
        assertThatThrownBy(() -> new ChartSeries(range, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 30, 90})
    void chartAcceptsSupportedRanges(int range) {
        assertThat(new ChartSeries(range, List.of(), List.of()).range()).isEqualTo(range);
    }

    @Test
    void chartDayNeedsOneCountPerEntity() {
        assertThatThrownBy(() -> new ChartSeries(7, List.of("A", "B"),
                List.of(new ChartSeries.Day("2026-11-01", List.of(1L)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chartDayNeedsAnIsoDateAndNonNegativeCounts() {
        assertThatThrownBy(() -> new ChartSeries.Day("01/11/2026", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChartSeries.Day("2026-13-40", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChartSeries.Day("2026-11-01", List.of(-1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "", "/"})
    void chromeRejectsBasePathsThatAreNotAConsolePath(String basePath) {
        assertThatThrownBy(() -> new PageChrome("t", basePath, PageChrome.Theme.SYSTEM, List.of(), null, List.of(),
                List.of(), "admin", Samples.csrf()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chromeAcceptsAnotherConsolePath() {
        assertThat(new PageChrome("t", "/admin-preview", PageChrome.Theme.DARK, List.of(), null, List.of(), List.of(),
                "admin", Samples.csrf()).basePath()).isEqualTo("/admin-preview");
    }

    @Test
    void negativeCountsAreRejected() {
        assertThatThrownBy(() -> new PageChrome.NavItem("e", "l", "i", -1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardView.Stats(-1, 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardView.LargestTable("l", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardView.EntityCard("e", "l", "i", -1L,
                new DashboardView.Permissions(true, true, true, true))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uploadFieldNeedsATicketAndOnlyUploadFieldsHaveOne() {
        assertThatThrownBy(() -> new FormView.Field("file", "File", FormView.Field.InputType.UPLOAD, null, false, null,
                List.of(), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FormView.Field("name", "Name", FormView.Field.InputType.TEXT, null, false, null,
                List.of(), null, "ticket")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Samples.uploadField().uploadTicket()).isEqualTo("sample-ticket");
    }

    @Test
    void uploadResultIsEitherASuccessOrAFailure() {
        UploadResult success = UploadResult.success("/admin/files/a.png", "a.png", 10);
        UploadResult failure = UploadResult.failure("The file is too large. Choose a file under 5 MB.");

        assertThat(success.error()).isNull();
        assertThat(failure.url()).isNull();
        assertThat(failure.name()).isNull();
        assertThatThrownBy(() -> new UploadResult(null, null, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UploadResult("/u", "n", 1, "error")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UploadResult("/u", "n", -1, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 99, 600, -404})
    void errorViewRejectsNonHttpStatus(int status) {
        assertThatThrownBy(() -> new ErrorView(status, "h", "hint")).isInstanceOf(IllegalArgumentException.class);
    }
}
