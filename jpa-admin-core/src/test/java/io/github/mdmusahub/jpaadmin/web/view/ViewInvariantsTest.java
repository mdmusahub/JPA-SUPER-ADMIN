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
        assertThatCode(() -> Samples.list(COLUMNS, List.of(Samples.row()), "name", "desc", Samples.notEmpty()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> Samples.list(COLUMNS, List.of(Samples.row()), "name", null, Samples.notEmpty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Samples.list(COLUMNS, List.of(Samples.row()), null, "asc", Samples.notEmpty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Samples.list(COLUMNS, List.of(Samples.row()), "name", "sideways", Samples.notEmpty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pagingComputesPagesAndRowRange() {
        ListView.Paging paging = new ListView.Paging(1, 25, 60, List.of(25));

        assertThat(paging.totalPages()).isEqualTo(3);
        assertThat(paging.firstRow()).isEqualTo(26);
        assertThat(paging.lastRow()).isEqualTo(50);
        assertThat(new ListView.Paging(2, 25, 60, List.of(25)).lastRow()).isEqualTo(60);
    }

    @Test
    void pagingWithoutRecordsHasNoRange() {
        ListView.Paging paging = new ListView.Paging(0, 25, 0, List.of(25));

        assertThat(paging.totalPages()).isZero();
        assertThat(paging.firstRow()).isZero();
        assertThat(paging.lastRow()).isZero();
    }

    @Test
    void pagingRejectsNegativeOrZeroValues() {
        assertThatThrownBy(() -> new ListView.Paging(-1, 25, 0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListView.Paging(0, 0, 0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListView.Paging(0, 25, -1, List.of())).isInstanceOf(IllegalArgumentException.class);
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
                List.of(), Samples.csrf()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chromeAcceptsAnotherConsolePath() {
        assertThat(new PageChrome("t", "/admin-preview", PageChrome.Theme.DARK, List.of(), null, List.of(), List.of(),
                Samples.csrf()).basePath()).isEqualTo("/admin-preview");
    }

    @Test
    void negativeCountsAreRejected() {
        assertThatThrownBy(() -> new PageChrome.NavItem("e", "l", "i", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardView.Stats(-1, 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardView.LargestTable("l", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DashboardView.EntityCard("e", "l", "i", -1,
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
