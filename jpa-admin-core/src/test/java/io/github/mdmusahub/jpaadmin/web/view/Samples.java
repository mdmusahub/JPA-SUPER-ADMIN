package io.github.mdmusahub.jpaadmin.web.view;

import java.util.List;

/**
 * Valid sample instances of every view-model record, used only by these tests. Not the preview fixtures, and no
 * entity names from any host project.
 */
final class Samples {

    private Samples() {
    }

    static PageChrome.Csrf csrf() {
        return new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", "sample-token");
    }

    static PageChrome chrome() {
        return new PageChrome("Items", "/admin", PageChrome.Theme.SYSTEM,
                List.of(new PageChrome.NavGroup("Catalog", List.of(new PageChrome.NavItem("item", "Items", "box", 3)))),
                "item",
                List.of(new PageChrome.Breadcrumb("Home", "/admin"), new PageChrome.Breadcrumb("Items", null)),
                List.of(new PageChrome.FlashMessage(PageChrome.FlashMessage.Level.SUCCESS, "Saved")),
                csrf());
    }

    static LoginView login() {
        return new LoginView("JPA Admin", null, false);
    }

    static DashboardView dashboard() {
        return new DashboardView(
                new DashboardView.Stats(3, 1, 1, new DashboardView.LargestTable("Items", 3)),
                List.of(new DashboardView.Group("Catalog", List.of(new DashboardView.EntityCard(
                        "item", "Items", "box", 3, new DashboardView.Permissions(true, true, true, true))))),
                true);
    }

    static ChartSeries chart() {
        return new ChartSeries(7, List.of("Items"), List.of(
                new ChartSeries.Day("2026-10-30", List.of(1L)),
                new ChartSeries.Day("2026-10-31", List.of(0L))));
    }

    static ListView.Column nameColumn() {
        return new ListView.Column("name", "Name", Cell.Type.TEXT, true, ListView.Column.Align.START, true, true);
    }

    static ListView.Column secretColumn() {
        return new ListView.Column("secret", "Secret", Cell.Type.MASKED, false, ListView.Column.Align.START, true,
                false);
    }

    static ListView.Row row() {
        return new ListView.Row("1", List.of(Cell.text("Alpha"), Cell.masked()));
    }

    static ListView.Filters filters() {
        return new ListView.Filters(
                List.of(new ListView.Filters.Definition("state", "State", ListView.Filters.Definition.Kind.ENUM,
                        List.of(new FormView.Option("OPEN", "Open")), "OPEN", null, null)),
                List.of(new ListView.Filters.Chip("state", "State", "Open", "/admin/item")),
                "/admin/item");
    }

    static ListView.Paging paging() {
        return new ListView.Paging(0, 25, 1, List.of(10, 25, 50, 100));
    }

    static ListView.Empty notEmpty() {
        return new ListView.Empty(ListView.Empty.Reason.NONE, "");
    }

    static ListView list() {
        return list(List.of(nameColumn(), secretColumn()), List.of(row()), null, null, notEmpty());
    }

    static ListView list(List<ListView.Column> columns, List<ListView.Row> rows, String sort, String dir,
                         ListView.Empty empty) {
        return new ListView(new ListView.EntityInfo("Item", "Items", "box"), columns, rows, "", sort, dir, filters(),
                paging(), new ListView.Actions(true, true, true), empty);
    }

    static DetailView detail() {
        return new DetailView("Alpha", "Item", "1",
                List.of(new DetailView.Field("Name", Cell.text("Alpha")), new DetailView.Field("Secret", Cell.masked())),
                List.of(new DetailView.Image("/admin/files/a.png", "Alpha")),
                new DetailView.Actions(true, true, true));
    }

    static FormView.Field textField() {
        return new FormView.Field("name", "Name", FormView.Field.InputType.TEXT, "Alpha", true, "Shown in lists",
                List.of(), null, null);
    }

    static FormView.Field maskedField() {
        return new FormView.Field("secret", "Secret", FormView.Field.InputType.MASKED, null, false,
                "Leave blank to keep the current value", List.of(), null, null);
    }

    static FormView.Field uploadField() {
        return new FormView.Field("file", "File", FormView.Field.InputType.UPLOAD, null, false, null, List.of(), null,
                "sample-ticket");
    }

    static FormView form() {
        return new FormView(FormView.Mode.EDIT, List.of(textField(), maskedField(), uploadField()),
                List.of("Fix the errors below."));
    }

    static UploadResult upload() {
        return UploadResult.success("/admin/files/a.png", "a.png", 10);
    }

    static ErrorView error() {
        return new ErrorView(404, "Page not found", "Check the address or go back to the dashboard.");
    }
}
