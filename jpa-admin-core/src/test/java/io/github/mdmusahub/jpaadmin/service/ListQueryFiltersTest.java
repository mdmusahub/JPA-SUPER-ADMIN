package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.web.view.ListView.Filters.Definition.Kind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The filters a {@link ListQuery} carries, and what changing them does to the rest of the query. A change of the filter
 * set starts again at page 0, because the old page number would mean other rows; the other parts are kept.
 */
class ListQueryFiltersTest {

    private static final ActiveFilter STATUS = ActiveFilter.of("status", Kind.ENUM, "LOST");
    private static final ActiveFilter WEIGHT = ActiveFilter.range("weight", Kind.NUMBER_RANGE, "1", "9");
    private static final ListQuery ON_PAGE_THREE = new ListQuery("pkg", "weight", "desc", 3, 50, List.of(STATUS, WEIGHT));

    @Test
    void aQueryWithoutFiltersEqualsTheFiveArgumentForm() {
        assertThat(new ListQuery("a", null, null, 2, 25)).isEqualTo(new ListQuery("a", null, null, 2, 25, List.of()));
        assertThat(ListQuery.defaults().filters()).isEmpty();
    }

    @Test
    void theFilterListIsCopiedAndCannotBeChangedFromOutside() {
        List<ActiveFilter> mine = new ArrayList<>(List.of(STATUS));
        ListQuery query = new ListQuery("", null, null, 0, 25, mine);
        mine.add(WEIGHT);

        assertThat(query.filters()).containsExactly(STATUS);
        assertThatThrownBy(() -> query.filters().add(WEIGHT)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void moreThanTheCapIsRefusedAtTheRecordAndExactlyTheCapIsFine() {
        List<ActiveFilter> many = new ArrayList<>();
        for (int i = 0; i < ListQuery.MAX_FILTERS; i++) {
            many.add(ActiveFilter.of("field" + i, Kind.TEXT, "x"));
        }
        assertThat(new ListQuery("", null, null, 0, 25, many).filters()).hasSize(ListQuery.MAX_FILTERS);

        many.add(ActiveFilter.of("one-too-many", Kind.TEXT, "x"));
        assertThatThrownBy(() -> new ListQuery("", null, null, 0, 25, many)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void twoFiltersOnOneFieldAreRefused() {
        assertThatThrownBy(() -> new ListQuery("", null, null, 0, 25, List.of(STATUS, ActiveFilter.of("status", Kind.ENUM, "PENDING"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changingTheFilterSetStartsAgainAtThePageZeroAndKeepsTheRest() {
        ListQuery changed = ON_PAGE_THREE.withFilters(List.of(STATUS));

        assertThat(changed.page()).isZero();
        assertThat(changed.search()).isEqualTo("pkg");
        assertThat(changed.sort()).isEqualTo("weight");
        assertThat(changed.dir()).isEqualTo("desc");
        assertThat(changed.size()).isEqualTo(50);
        assertThat(changed.filters()).containsExactly(STATUS);
    }

    @Test
    void removingOneFilterKeepsTheOthersAndStartsAgainAtThePageZero() {
        ListQuery removed = ON_PAGE_THREE.withoutFilter("status");

        assertThat(removed.filters()).containsExactly(WEIGHT);
        assertThat(removed.page()).isZero();
        assertThat(removed.search()).isEqualTo("pkg");
        assertThat(removed.sort()).isEqualTo("weight");
        assertThat(removed.size()).isEqualTo(50);
        assertThat(ON_PAGE_THREE.withoutFilter("nosuch").filters()).containsExactly(STATUS, WEIGHT);
    }

    @Test
    void clearingEveryFilterKeepsTheSearchTheSortAndTheSizeAndStartsAgainAtThePageZero() {
        ListQuery cleared = ON_PAGE_THREE.withoutFilters();

        assertThat(cleared).isEqualTo(new ListQuery("pkg", "weight", "desc", 0, 50));
    }

    @Test
    void theFiltersReadFromAnAddressKeepThePageOfThatAddress() {
        // The address that carries the filters also carries the page. Only a change made by the visitor resets it.
        assertThat(new ListQuery("", null, null, 4, 25).withParsedFilters(List.of(STATUS)).page()).isEqualTo(4);
    }

    @Test
    void movingToAnotherPageSizeOrSortKeepsTheFilters() {
        assertThat(ON_PAGE_THREE.withPage(1).filters()).containsExactly(STATUS, WEIGHT);
        assertThat(ON_PAGE_THREE.withSize(10).filters()).containsExactly(STATUS, WEIGHT);
        assertThat(ON_PAGE_THREE.withSort("status", "asc").filters()).containsExactly(STATUS, WEIGHT);
        assertThat(ON_PAGE_THREE.withSize(10).page()).isZero();
    }

    @Test
    void anActiveFilterOfEachShapeIsChecked() {
        assertThatThrownBy(() -> ActiveFilter.of("a", Kind.TEXT, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActiveFilter.of("a", Kind.NUMBER_RANGE, "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActiveFilter("a", Kind.TEXT, "x", "1", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActiveFilter("a", Kind.DATE_RANGE, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActiveFilter(null, Kind.TEXT, "x", null, null)).isInstanceOf(NullPointerException.class);
    }
}
