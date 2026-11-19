package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * How a {@link ListQuery} carries the hidden columns (Phase 12, D-112): every way of changing the query keeps them, and
 * only a <em>change</em> of the hidden set starts again at page 0 (decision 9: a change of the view resets the page).
 */
class ListQueryHiddenTest {

    private static final ListQuery BASE = new ListQuery("guide", "title", "desc", 3, 50, List.of(), List.of("weight", "pieces"));

    @Test
    void aQueryHidesNothingUnlessToldTo() {
        assertThat(ListQuery.defaults().hidden()).isEmpty();
        assertThat(new ListQuery("", null, null, 0, 25).hidden()).isEmpty();
        assertThat(new ListQuery("", null, null, 0, 25, List.of()).hidden()).isEmpty();
    }

    @Test
    void changingTheHiddenColumnsStartsAgainAtThePageZero() {
        ListQuery changed = BASE.withHidden(List.of("pieces"));

        assertThat(changed.page()).as("the old page would mean other rows").isZero();
        assertThat(changed.hidden()).containsExactly("pieces");
        assertThat(changed.search()).isEqualTo("guide");
        assertThat(changed.sort()).isEqualTo("title");
        assertThat(changed.dir()).isEqualTo("desc");
        assertThat(changed.size()).isEqualTo(50);
    }

    @Test
    void showingEveryColumnAgainAlsoStartsAgainAtThePageZero() {
        ListQuery shown = BASE.withoutHidden();

        assertThat(shown.hidden()).isEmpty();
        assertThat(shown.page()).isZero();
    }

    @Test
    void theHiddenColumnsReadFromTheSameAddressKeepThatAddressesPage() {
        // The address that carries the hide also carries its page: a next-page link and Back must keep working.
        assertThat(BASE.withParsedHidden(List.of("weight")).page()).isEqualTo(3);
        assertThat(BASE.withParsedHidden(List.of("weight")).hidden()).containsExactly("weight");
    }

    @Test
    void everyOtherChangeKeepsTheHiddenColumns() {
        assertThat(BASE.withPage(5).hidden()).containsExactly("weight", "pieces");
        assertThat(BASE.withSize(10).hidden()).containsExactly("weight", "pieces");
        assertThat(BASE.withSort("pieces", "asc").hidden()).containsExactly("weight", "pieces");
        assertThat(BASE.withFilters(List.of()).hidden()).containsExactly("weight", "pieces");
        assertThat(BASE.withoutFilters().hidden()).containsExactly("weight", "pieces");
        assertThat(BASE.withParsedFilters(List.of()).hidden()).containsExactly("weight", "pieces");
        assertThat(BASE.withoutFilter("anything").hidden()).containsExactly("weight", "pieces");
    }

    @Test
    void aSortAPageSizeAndAFilterChangeStillResetThePageAsBefore() {
        assertThat(BASE.withSize(10).page()).isZero();
        assertThat(BASE.withSort("pieces", "asc").page()).isZero();
        assertThat(BASE.withFilters(List.of()).page()).isZero();
        assertThat(BASE.withPage(2).page()).isEqualTo(2);
    }

    @Test
    void aColumnCannotBeHiddenTwice() {
        assertThatThrownBy(() -> BASE.withHidden(List.of("weight", "weight"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("weight");
    }

    @Test
    void theHiddenListMayNotBeNullAndCannotBeChangedAfterwards() {
        assertThatThrownBy(() -> BASE.withHidden(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BASE.hidden().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theFivePartParserNeverHidesAnythingBecauseHidingIsReadByItsOwnParser() {
        ListQuery parsed = ListQuery.parse(parcel(), registry(),"q", "reference", "asc", "2", "50");

        assertThat(parsed.hidden()).isEmpty();
    }

    private static io.github.mdmusahub.jpaadmin.metadata.AdminEntity parcel() {
        return registry().find("parcel").orElseThrow();
    }

    private static io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry registry() {
        return io.github.mdmusahub.jpaadmin.metadata.EntityScanner.scan(
                io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence.factory().getMetamodel(),
                io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings.defaults());
    }
}
