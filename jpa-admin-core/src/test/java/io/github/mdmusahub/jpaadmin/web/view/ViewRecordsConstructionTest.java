package io.github.mdmusahub.jpaadmin.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ViewRecordsConstructionTest {

    @Test
    void everyTopLevelRecordCanBeConstructed() {
        assertThat(Samples.chrome()).isNotNull();
        assertThat(Samples.login()).isNotNull();
        assertThat(Samples.dashboard()).isNotNull();
        assertThat(Samples.chart()).isNotNull();
        assertThat(Samples.list()).isNotNull();
        assertThat(Samples.textField()).isNotNull();
        assertThat(Cell.text("x")).isNotNull();
        assertThat(Samples.detail()).isNotNull();
        assertThat(Samples.form()).isNotNull();
        assertThat(Samples.upload()).isNotNull();
        assertThat(Samples.error()).isNotNull();
    }

    @Test
    void nestedRecordsKeepTheirValues() {
        PageChrome chrome = Samples.chrome();
        assertThat(chrome.nav().get(0).items().get(0).count()).isEqualTo(3);
        assertThat(chrome.breadcrumbs().get(1).href()).isNull();
        assertThat(chrome.csrf().token()).isEqualTo("sample-token");

        ListView list = Samples.list();
        assertThat(list.rows().get(0).cells()).hasSize(list.columns().size());
        assertThat(list.filters().definitions().get(0).kind()).isEqualTo(ListView.Filters.Definition.Kind.ENUM);
        assertThat(list.filters().chips().get(0).removeHref()).isEqualTo("/admin/item");

        assertThat(Samples.dashboard().stats().largestTable().label()).isEqualTo("Items");
        assertThat(Samples.chart().days().get(0).counts()).containsExactly(1L);
        assertThat(Samples.detail().fields().get(0).cell().display()).isEqualTo("Alpha");
        assertThat(Samples.form().fields().get(2).uploadTicket()).isEqualTo("sample-ticket");
    }

    @Test
    void optionalValuesMayBeNull() {
        assertThat(Samples.login().error()).isNull();
        assertThat(new DashboardView.Stats(0, 0, 0, null).largestTable()).isNull();
        assertThat(Cell.text("x").href()).isNull();
        assertThat(Cell.empty().title()).isNull();
    }

    @Test
    void modelAttributeAndTemplateNamesAreFixed() {
        assertThat(PageChrome.ATTRIBUTE).isEqualTo("chrome");
        assertThat(PageChrome.VIEW_ATTRIBUTE).isEqualTo("view");
        assertThat(LoginView.TEMPLATE).isEqualTo("jpa-admin/login");
        assertThat(DashboardView.TEMPLATE).isEqualTo("jpa-admin/dashboard");
        assertThat(ListView.TEMPLATE).isEqualTo("jpa-admin/list");
        assertThat(DetailView.TEMPLATE).isEqualTo("jpa-admin/detail");
        assertThat(FormView.TEMPLATE).isEqualTo("jpa-admin/form");
        assertThat(ErrorView.TEMPLATE).isEqualTo("jpa-admin/error");
    }
}
