package io.github.mdmusahub.jpaadmin.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@code ListView.State} (Phase 13, D-118): the rules its constructor and its list enforce. */
class ListViewStateTest {

    private static ListView.Column column(String key, boolean visible) {
        return new ListView.Column(key, key, Cell.Type.TEXT, false, null, ListView.Column.SortState.NONE,
                ListView.Column.Align.START, visible, false);
    }

    private static ListView list(ListView.State state, List<ListView.Column> columns) {
        return new ListView(new ListView.EntityInfo("Item", "Items", "box"), columns, List.of(), "", null, null,
                Samples.filters(), Samples.paging(), Samples.actions(),
                new ListView.Empty(ListView.Empty.Reason.NO_RECORDS, "h"), state);
    }

    @Test
    void aStateWithoutAShowAllLinkIsFineWhenEveryColumnIsVisible() {
        assertThatCode(() -> list(new ListView.State("/admin/item", 25, null, "/admin/item"),
                List.of(column("a", true), column("b", true)))).doesNotThrowAnyException();
    }

    @Test
    void theShowAllLinkIsRequiredExactlyWhenAColumnIsHidden() {
        List<ListView.Column> oneHidden = List.of(column("a", true), column("b", false));

        assertThatCode(() -> list(new ListView.State("/admin/item", 25, "/admin/item", "/admin/item?hide=b"), oneHidden))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> list(new ListView.State("/admin/item", 25, null, "/admin/item"), oneHidden))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("showAllColumnsHref");
        assertThatThrownBy(() -> list(new ListView.State("/admin/item", 25, "/admin/item", "/admin/item"),
                List.of(column("a", true), column("b", true)))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("showAllColumnsHref");
    }

    @Test
    void theStateIsRequired() {
        assertThatThrownBy(() -> list(null, List.of())).isInstanceOf(NullPointerException.class).hasMessageContaining("state");
    }

    @Test
    void theDefaultSizeIsPositive() {
        assertThatThrownBy(() -> new ListView.State("/admin/item", 0, null, "/admin/item"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultSize");
        assertThatThrownBy(() -> new ListView.State("/admin/item", -25, null, "/admin/item"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAddressAndTheClearSearchLinkAreRequired() {
        assertThatThrownBy(() -> new ListView.State(null, 25, null, "/admin/item")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ListView.State("/admin/item", 25, null, null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "//evil.example/x", "https://evil.example/", "/a\\b", "/a b", "/a\tb",
            "admin/item", "", "/admin//item", "data:text/html,x", "/a\nb"})
    void everyLinkOfTheStateFollowsTheLinkRule(String bad) {
        assertThatThrownBy(() -> new ListView.State(bad, 25, null, "/admin/item")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("href").hasMessageNotContaining("evil");
        assertThatThrownBy(() -> new ListView.State("/admin/item", 25, bad, "/admin/item"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("showAllColumnsHref");
        assertThatThrownBy(() -> new ListView.State("/admin/item", 25, null, bad)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clearSearchHref");
    }

    @Test
    void aQueryWithSlashesAndColonsIsFineBecauseThoseAreDataThere() {
        assertThat(new ListView.State("/admin/item?q=http%3A%2F%2Fx&hide=a", 25, "/admin/item?q=http://x", "/admin/item"))
                .isNotNull();
    }
}
