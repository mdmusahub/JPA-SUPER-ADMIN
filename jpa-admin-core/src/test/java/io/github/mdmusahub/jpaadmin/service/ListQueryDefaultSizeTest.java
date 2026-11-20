package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The page size an address without {@code size} uses, and where it travels (Phase 13, D-119): {@code ListQuery.defaultSize}.
 */
class ListQueryDefaultSizeTest {

    @Test
    void theOlderConstructorsKeepTwentyFive() {
        assertThat(new ListQuery("", null, null, 0, 25).defaultSize()).isEqualTo(25);
        assertThat(new ListQuery("", null, null, 0, 50, List.of()).defaultSize()).isEqualTo(25);
        assertThat(new ListQuery("", null, null, 0, 10, List.of(), List.of("a")).defaultSize()).isEqualTo(25);
        assertThat(ListQuery.defaults().defaultSize()).isEqualTo(25).isEqualTo(ListQuery.DEFAULT_SIZE);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 25, 50, 100})
    void everySizeOnOfferCanBeTheDefault(int size) {
        ListQuery query = ListQuery.defaults(size);

        assertThat(query.defaultSize()).isEqualTo(size);
        assertThat(query.size()).isEqualTo(size);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 7, 24, 26, 99, 101, 1000, Integer.MAX_VALUE})
    void aSizeThatIsNotOnOfferCannotBeTheDefault(int size) {
        assertThatThrownBy(() -> ListQuery.defaults(size)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultSize");
        assertThatThrownBy(() -> new ListQuery("", null, null, 0, 25, List.of(), List.of(), size))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingOrUnacceptableSizeMeansTheDefaultOfThisConsole() {
        for (String raw : new String[] {null, "", " ", "abc", "7", "-1", "1000", "25.0", "0x19", "٢٥", "99999999999"}) {
            assertThat(ListQuery.parseSize(raw, 50)).as("size=%s", raw).isEqualTo(50);
            assertThat(ListQuery.parseSize(raw, 10)).as("size=%s", raw).isEqualTo(10);
            assertThat(ListQuery.parseSize(raw)).as("size=%s", raw).isEqualTo(25);
        }
    }

    @Test
    void aSizeOnOfferIsKeptWhateverTheDefaultIs() {
        assertThat(ListQuery.parseSize("25", 50)).isEqualTo(25);
        assertThat(ListQuery.parseSize(" 100 ", 10)).isEqualTo(100);
        assertThat(ListQuery.parseSize("10", 100)).isEqualTo(10);
    }

    @Test
    void parseSizeRefusesADefaultThatIsNotOnOffer() {
        assertThatThrownBy(() -> ListQuery.parseSize("25", 7)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyChangeOfTheQueryKeepsTheDefault() {
        ListQuery query = new ListQuery("a", "x", "asc", 3, 25, List.of(), List.of("h"), 50);

        assertThat(query.withPage(1).defaultSize()).isEqualTo(50);
        assertThat(query.withSize(10).defaultSize()).isEqualTo(50);
        assertThat(query.withSort("y", "desc").defaultSize()).isEqualTo(50);
        assertThat(query.withFilters(List.of()).defaultSize()).isEqualTo(50);
        assertThat(query.withParsedFilters(List.of()).defaultSize()).isEqualTo(50);
        assertThat(query.withHidden(List.of()).defaultSize()).isEqualTo(50);
        assertThat(query.withoutHidden().defaultSize()).isEqualTo(50);
        assertThat(query.withParsedHidden(List.of("g")).defaultSize()).isEqualTo(50);
        assertThat(query.withoutFilters().defaultSize()).isEqualTo(50);
        assertThat(query.withSearch("b").defaultSize()).isEqualTo(50);
    }

    @Test
    void withSearchStartsAgainAtTheFirstPageAndChangesNothingElse() {
        ListQuery query = new ListQuery("a", "x", "asc", 3, 50, List.of(), List.of("h"), 25);

        ListQuery changed = query.withSearch("");

        assertThat(changed.search()).isEmpty();
        assertThat(changed.page()).isZero();
        assertThat(changed.sort()).isEqualTo("x");
        assertThat(changed.size()).isEqualTo(50);
        assertThat(changed.hidden()).containsExactly("h");
        assertThatCode(() -> query.withSearch(null)).isInstanceOf(NullPointerException.class);
    }
}
