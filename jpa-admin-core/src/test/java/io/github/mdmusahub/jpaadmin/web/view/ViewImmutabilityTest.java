package io.github.mdmusahub.jpaadmin.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ViewImmutabilityTest {

    static Stream<Arguments> lists() {
        PageChrome chrome = Samples.chrome();
        ListView list = Samples.list();
        FormView form = Samples.form();
        return Stream.of(
                Arguments.of("PageChrome.nav", chrome.nav()),
                Arguments.of("NavGroup.items", chrome.nav().get(0).items()),
                Arguments.of("PageChrome.breadcrumbs", chrome.breadcrumbs()),
                Arguments.of("PageChrome.flash", chrome.flash()),
                Arguments.of("DashboardView.groups", Samples.dashboard().groups()),
                Arguments.of("DashboardView.Group.entities", Samples.dashboard().groups().get(0).entities()),
                Arguments.of("ChartSeries.entities", Samples.chart().entities()),
                Arguments.of("ChartSeries.days", Samples.chart().days()),
                Arguments.of("ChartSeries.Day.counts", Samples.chart().days().get(0).counts()),
                Arguments.of("ListView.columns", list.columns()),
                Arguments.of("ListView.rows", list.rows()),
                Arguments.of("ListView.Row.cells", list.rows().get(0).cells()),
                Arguments.of("Filters.definitions", list.filters().definitions()),
                Arguments.of("Filters.Definition.options", list.filters().definitions().get(0).options()),
                Arguments.of("Filters.chips", list.filters().chips()),
                Arguments.of("Paging.sizes", list.paging().sizes()),
                Arguments.of("DetailView.fields", Samples.detail().fields()),
                Arguments.of("DetailView.images", Samples.detail().images()),
                Arguments.of("FormView.fields", form.fields()),
                Arguments.of("FormView.formErrors", form.formErrors()),
                Arguments.of("FormView.Field.options", form.fields().get(0).options()));
    }

    @ParameterizedTest(name = "{0} cannot be modified")
    @MethodSource("lists")
    @SuppressWarnings("unchecked")
    void listsAreImmutable(String name, List<?> list) {
        assertThatThrownBy(() -> ((List<Object>) list).add(new Object()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void recordsCopyTheListsTheyAreGiven() {
        List<ListView.Row> rows = new ArrayList<>(List.of(Samples.row()));
        ListView list = Samples.list(List.of(Samples.nameColumn(), Samples.secretColumn()), rows, null, null,
                Samples.notEmpty());

        rows.clear();

        assertThat(list.rows()).hasSize(1);
    }

    @Test
    void chartCountsAreCopied() {
        List<Long> counts = new ArrayList<>(List.of(4L));
        ChartSeries.Day day = new ChartSeries.Day("2026-11-01", counts);

        counts.add(9L);

        assertThat(day.counts()).containsExactly(4L);
    }

    @Test
    void formFieldsAreCopied() {
        List<FormView.Field> fields = new ArrayList<>(List.of(Samples.textField()));
        FormView form = new FormView(FormView.Mode.CREATE, fields, List.of());

        fields.add(Samples.maskedField());

        assertThat(form.fields()).hasSize(1);
    }
}
