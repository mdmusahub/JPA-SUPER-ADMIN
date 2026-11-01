package io.github.mdmusahub.jpaadmin.web.view;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** A null where a value is required is a bug in the caller, so the records reject it at construction. */
class ViewNullRejectionTest {

    private static final PageChrome.Csrf CSRF = Samples.csrf();

    private static Arguments rejects(String what, Executable construction) {
        return Arguments.of(what, construction);
    }

    static Stream<Arguments> nullsThatMustBeRejected() {
        List<PageChrome.NavGroup> nav = Samples.chrome().nav();
        return Stream.of(
                rejects("PageChrome.title", () -> new PageChrome(null, "/admin", PageChrome.Theme.SYSTEM, nav, null,
                        List.of(), List.of(), CSRF)),
                rejects("PageChrome.basePath", () -> new PageChrome("t", null, PageChrome.Theme.SYSTEM, nav, null,
                        List.of(), List.of(), CSRF)),
                rejects("PageChrome.theme", () -> new PageChrome("t", "/admin", null, nav, null, List.of(), List.of(),
                        CSRF)),
                rejects("PageChrome.nav", () -> new PageChrome("t", "/admin", PageChrome.Theme.SYSTEM, null, null,
                        List.of(), List.of(), CSRF)),
                rejects("PageChrome.breadcrumbs", () -> new PageChrome("t", "/admin", PageChrome.Theme.SYSTEM, nav,
                        null, null, List.of(), CSRF)),
                rejects("PageChrome.flash", () -> new PageChrome("t", "/admin", PageChrome.Theme.SYSTEM, nav, null,
                        List.of(), null, CSRF)),
                rejects("PageChrome.csrf", () -> new PageChrome("t", "/admin", PageChrome.Theme.SYSTEM, nav, null,
                        List.of(), List.of(), null)),
                rejects("PageChrome.nav element", () -> new PageChrome("t", "/admin", PageChrome.Theme.SYSTEM,
                        java.util.Arrays.asList((PageChrome.NavGroup) null), null, List.of(), List.of(), CSRF)),
                rejects("NavGroup.label", () -> new PageChrome.NavGroup(null, List.of())),
                rejects("NavItem.entity", () -> new PageChrome.NavItem(null, "l", "i", 0)),
                rejects("Breadcrumb.label", () -> new PageChrome.Breadcrumb(null, null)),
                rejects("FlashMessage.level", () -> new PageChrome.FlashMessage(null, "t")),
                rejects("Csrf.token", () -> new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", null)),
                rejects("LoginView.title", () -> new LoginView(null, null, false)),
                rejects("DashboardView.stats", () -> new DashboardView(null, List.of(), false)),
                rejects("DashboardView.groups", () -> new DashboardView(new DashboardView.Stats(0, 0, 0, null), null,
                        false)),
                rejects("EntityCard.permissions", () -> new DashboardView.EntityCard("e", "l", "i", 0, null)),
                rejects("ChartSeries.entities", () -> new ChartSeries(7, null, List.of())),
                rejects("ChartSeries.days", () -> new ChartSeries(7, List.of(), null)),
                rejects("ChartSeries.Day.date", () -> new ChartSeries.Day(null, List.of())),
                rejects("ChartSeries.Day.counts", () -> new ChartSeries.Day("2026-11-01", null)),
                rejects("Cell.type", () -> new Cell(null, "x", Cell.Tone.NONE, null, null)),
                rejects("Cell.display", () -> new Cell(Cell.Type.TEXT, null, Cell.Tone.NONE, null, null)),
                rejects("Cell.tone", () -> new Cell(Cell.Type.TEXT, "x", null, null, null)),
                rejects("ListView.entity", () -> new ListView(null, List.of(), List.of(), "", null, null, Samples.filters(),
                        Samples.paging(), new ListView.Actions(true, true, true),
                        new ListView.Empty(ListView.Empty.Reason.NO_RECORDS, "h"))),
                rejects("ListView.columns", () -> Samples.list(null, List.of(), null, null,
                        new ListView.Empty(ListView.Empty.Reason.NO_RECORDS, "h"))),
                rejects("ListView.rows", () -> Samples.list(List.of(), null, null, null,
                        new ListView.Empty(ListView.Empty.Reason.NO_RECORDS, "h"))),
                rejects("ListView.empty", () -> Samples.list(List.of(Samples.nameColumn(), Samples.secretColumn()),
                        List.of(Samples.row()), null, null, null)),
                rejects("Column.key", () -> new ListView.Column(null, "l", Cell.Type.TEXT, false,
                        ListView.Column.Align.START, true, false)),
                rejects("Column.type", () -> new ListView.Column("k", "l", null, false, ListView.Column.Align.START,
                        true, false)),
                rejects("Row.id", () -> new ListView.Row(null, List.of())),
                rejects("Row.cells", () -> new ListView.Row("1", null)),
                rejects("Filters.definitions", () -> new ListView.Filters(null, List.of(), null)),
                rejects("Filters.chips", () -> new ListView.Filters(List.of(), null, null)),
                rejects("Chip.removeHref", () -> new ListView.Filters.Chip("k", "l", "d", null)),
                rejects("Paging.sizes", () -> new ListView.Paging(0, 25, 0, null)),
                rejects("DetailView.fields", () -> new DetailView("t", "e", "1", null, List.of(),
                        new DetailView.Actions(true, true, true))),
                rejects("DetailView.actions", () -> new DetailView("t", "e", "1", List.of(), List.of(), null)),
                rejects("DetailView.Field.cell", () -> new DetailView.Field("l", null)),
                rejects("FormView.mode", () -> new FormView(null, List.of(), List.of())),
                rejects("FormView.fields", () -> new FormView(FormView.Mode.CREATE, null, List.of())),
                rejects("FormView.formErrors", () -> new FormView(FormView.Mode.CREATE, List.of(), null)),
                rejects("FormView.Field.inputType", () -> new FormView.Field("k", "l", null, null, false, null,
                        List.of(), null, null)),
                rejects("FormView.Field.options", () -> new FormView.Field("k", "l", FormView.Field.InputType.TEXT,
                        null, false, null, null, null, null)),
                rejects("Option.label", () -> new FormView.Option("v", null)),
                rejects("UploadResult.failure error", () -> UploadResult.failure(null)),
                rejects("UploadResult.success url", () -> UploadResult.success(null, "n", 1)),
                rejects("ErrorView.heading", () -> new ErrorView(404, null, "h")),
                rejects("ErrorView.hint", () -> new ErrorView(404, "h", null)));
    }

    @ParameterizedTest(name = "{0} rejects null")
    @MethodSource("nullsThatMustBeRejected")
    void rejectsNull(String what, Executable construction) {
        assertThatThrownBy(construction::execute).isInstanceOf(NullPointerException.class);
    }
}
