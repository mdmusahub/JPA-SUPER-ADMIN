package io.github.mdmusahub.jpaadmin.engines;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.service.FilterParse;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The list page on a real database (Phase 14a, D-128): paging, search, sort, null ordering, the identifier tie-breaker, filters and
 * hidden columns. The expected values are computed in Java from the parcels that were stored, exactly as the H2 list tests do
 * ({@code ListQueryServiceTest}, {@code ListQueryServiceFilterTest}), so a database that answers differently fails here and the
 * failure names the engine.
 *
 * <p><b>Where engines may differ</b> the scenario says what is asserted instead of copying H2: the <em>order of text</em> follows
 * the database's collation (see {@link #textOrderIsJavaOrder()}), so a text column is checked for what any collation must keep (no
 * row twice, none missing, descending is ascending reversed, equal values in identifier order) and, for the plain upper-case
 * references, for the exact order.
 */
public abstract class EngineListScenarios extends EngineTestBase {

    /**
     * Whether this engine orders the mixed text of the {@code notes} column (punctuation, digits, both cases, non-Latin letters)
     * the way Java's {@code String.compareTo} does. H2 does; PostgreSQL and MySQL follow their collations (documented in
     * {@code docs/PROJECT_DOCS.md}).
     */
    protected abstract boolean textOrderIsJavaOrder();

    // ---- paging -------------------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {10, 25, 50, 100})
    void everyPageSizeReturnsThatManyRowsOrFewerAndTheRightTotal(int size) {
        ListPage page = list(null, null, null, null, String.valueOf(size));

        assertThat(page.rows()).hasSize(Math.min(size, PARCELS));
        assertThat(page.total()).isEqualTo(PARCELS);
    }

    @Test
    void thePagesFollowEachOtherWithoutGapOrOverlapAndTheLastOneIsPartial() {
        List<Long> all = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            all.addAll(ids(list(null, null, null, String.valueOf(page), "25")));
        }

        assertThat(list(null, null, null, "2", "25").rows()).as("the last page holds what is left").hasSize(5);
        assertThat(all).hasSize(PARCELS).doesNotHaveDuplicates().isSorted();
    }

    @ParameterizedTest
    @ValueSource(strings = {"99", "3", "1000000", "2147483647", "99999999999999999999"})
    void aPagePastTheEndIsTheLastPageAndNotAnError(String requested) {
        ListPage page = list(null, null, null, requested, "25");

        assertThat(page.query().page()).as("clamped to the last page").isEqualTo(2);
        assertThat(page.rows()).hasSize(5);
        assertThat(page.total()).isEqualTo(PARCELS);
    }

    @Test
    void aHostDefaultPageSizeOfFiftyIsUsedWhenTheAddressSaysNothing() {
        ListQueryService fifty = ListQueryService.over(entityManager, registry,
                io.github.mdmusahub.jpaadmin.service.UploadFiles.none(), 50);
        AdminEntity parcel = parcelEntity();

        ListPage first = fifty.list(parcel, fifty.parse(parcel, null, null, null, null, null));
        ListPage second = fifty.list(parcel, fifty.parse(parcel, null, null, null, "1", null));

        assertThat(first.rows()).hasSize(50);
        assertThat(second.rows()).hasSize(5);
        assertThat(first.query().size()).isEqualTo(50);
    }

    // ---- search -------------------------------------------------------------------------------------------------------------

    @Test
    void theSearchIgnoresCaseAndTheTextAroundIt() {
        for (String text : List.of("pkg-007", "PKG-007", "Pkg-007", "  pkg-007  ")) {
            ListPage page = list(text, null, null, null, null);

            assertThat(page.total()).as(text).isEqualTo(1);
            assertThat(display(page, 0, "reference")).isEqualTo("PKG-007");
        }
    }

    @Test
    void theSearchMatchesAnywhereInAnyTextAttribute() {
        assertThat(list("care #3", null, null, null, "100").total()).as("in the notes").isGreaterThan(5);
        assertThat(list("G-05", null, null, null, "100").total()).as("inside the reference: 050 to 055").isEqualTo(6);
    }

    @Test
    void percentUnderscoreAndTheEscapeCharacterAreTakenLiterally() {
        assertThat(ids(list("100%", null, null, null, null))).containsExactly(idOfParcel(8));
        assertThat(ids(list("%", null, null, null, null))).containsExactly(idOfParcel(8));
        assertThat(ids(list("a_b", null, null, null, null))).containsExactly(idOfParcel(9));
        assertThat(ids(list("_", null, null, null, null))).containsExactly(idOfParcel(9));
        assertThat(ids(list("wow!", null, null, null, null))).as("the escape character is literal too")
                .containsExactly(idOfParcel(12));
        assertThat(ids(list("!", null, null, null, null))).containsExactly(idOfParcel(12));
        assertThat(ids(list("it's", null, null, null, null))).containsExactly(idOfParcel(11));
        assertThat(ids(list("\"quoted\"", null, null, null, null))).containsExactly(idOfParcel(11));
        assertThat(ids(list("C:\\temp", null, null, null, null))).as("a backslash is literal").containsExactly(idOfParcel(13));
        assertThat(ids(list("\\", null, null, null, null))).containsExactly(idOfParcel(13));
    }

    @Test
    void aWildcardLookalikeMatchesOnlyWhatItLiterallySpells() {
        for (String text : List.of("%%", "__", "_%", "PKG-0_1", "PKG%007", "p_ain")) {
            assertThat(list(text, null, null, null, null).total()).as(text).isZero();
        }
    }

    @Test
    void unicodeTextIsFoundInAnyCase() {
        // lower() of an upper-case non-ASCII letter follows the database's character set. All three engines are expected to
        // find it (PostgreSQL does when the database's locale is UTF-8, as in the official image).
        assertThat(ids(list("ZÜRICH", null, null, null, null))).as("unicode, any case").containsExactly(idOfParcel(14));
        assertThat(ids(list("zürich", null, null, null, null))).containsExactly(idOfParcel(14));
        assertThat(ids(list("東京", null, null, null, null))).containsExactly(idOfParcel(14));
        assertThat(ids(list("CAFÉ", null, null, null, null))).as("an accent is kept: É finds é").containsExactly(idOfParcel(14));
    }

    @Test
    void aLongTextAndALongUnbrokenWordAreFound() {
        assertThat(ids(list("LONGTEXT", null, null, null, null))).containsExactly(idOfParcel(20));
        assertThat(ids(list("unbroken", null, null, null, null))).containsExactly(idOfParcel(21));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x' or '1'='1", "'; drop table parcel; --", "\" or 1=1 --", "1' union select * from depot --",
            "%' or '%'='", "\\' or 1=1"})
    void anInjectionAttemptFindsNothingAndTheTextIsNeverInTheSql(String attempt) {
        ListPage page = list(attempt, null, null, null, "100");

        assertThat(page.total()).isZero();
        assertThat(RecordingStatementInspector.statements()).as("the text is bound, so it is not in any statement")
                .allSatisfy(sql -> assertThat(sql).doesNotContain(attempt).doesNotContain("1'='1").doesNotContain("drop table"));
        assertThat(count(Parcel.class)).as("the table is intact").isEqualTo(PARCELS);
    }

    @Test
    void aSensitiveValueCanNeverBeFoundBySearch() {
        assertThat(list("hunter2", null, null, null, null).total()).isZero();
        assertThat(list("memo-secret", null, null, null, null).total()).isZero();
        assertThat(list("internal-", null, null, null, null).total()).as("a hidden field is not searched").isZero();
    }

    @Test
    void aSearchSurvivesPagingAndSorting() {
        ListPage second = list("PKG-00", "weight", "desc", "1", "10");

        assertThat(second.total()).isEqualTo(9);
        assertThat(second.query().page()).as("one page of 9, so page 1 is clamped to 0").isZero();
        List<BigDecimal> weights = new ArrayList<>();
        for (int row = 0; row < second.rows().size(); row++) {
            weights.add(new BigDecimal(display(second, row, "weight")));
        }
        assertThat(weights).isSortedAccordingTo(Comparator.reverseOrder());
    }

    // ---- sort ---------------------------------------------------------------------------------------------------------------

    @Test
    void everySortableColumnWhoseOrderIsTheSameOnEveryDatabaseSortsBothWaysWithTheIdentifierAsTieBreaker() {
        record Case(String key, Comparator<Parcel> ascending) {
        }
        List<Case> cases = List.of(
                new Case("reference", Comparator.comparing(Parcel::getReference)),
                new Case("weight", Comparator.comparing(Parcel::getWeight)),
                new Case("pieces", Comparator.comparingInt(Parcel::getPieces)),
                new Case("fragile", Comparator.comparing(Parcel::isFragile)),
                new Case("status", Comparator.comparing(parcel -> parcel.getStatus().name())),
                new Case("shippedOn", Comparator.comparing(Parcel::getShippedOn)),
                new Case("createdAt", Comparator.comparing(Parcel::getCreatedAt)));

        for (Case sort : cases) {
            assertThat(sortedIds(sort.key(), "asc")).as(sort.key() + " asc").isEqualTo(expected(byIdThen(sort.ascending())));
            // Descending reverses the column, but equal values still come in identifier order.
            assertThat(sortedIds(sort.key(), "desc")).as(sort.key() + " desc")
                    .isEqualTo(expected(sort.ascending().reversed().thenComparing(Parcel::getId)));
        }
    }

    @Test
    void theNotesColumnSortsAsTheDatabaseCollatesAndKeepsWhatEveryCollationKeeps() {
        List<Long> ascending = sortedIds("notes", "asc");
        List<Long> descending = sortedIds("notes", "desc");

        assertThat(ascending).as("no row twice, none missing").hasSize(PARCELS).doesNotHaveDuplicates();
        assertThat(descending).hasSize(PARCELS).doesNotHaveDuplicates();
        assertThat(ascending).containsExactlyInAnyOrderElementsOf(expected(Comparator.comparing(Parcel::getId)));
        // Equal values come together and in identifier order; descending is the groups of ascending the other way round.
        List<List<Long>> ascendingGroups = groupsOfEqualNotes(ascending);
        List<List<Long>> descendingGroups = groupsOfEqualNotes(descending);
        assertThat(descendingGroups).as("descending is ascending reversed, group by group")
                .isEqualTo(reversed(ascendingGroups));
        for (List<Long> group : ascendingGroups) {
            assertThat(group).as("equal values are in identifier order").isSorted();
        }
        if (textOrderIsJavaOrder()) {
            assertThat(ascending).as("this engine collates like Java").isEqualTo(expected(byIdThen(Comparator.comparing(Parcel::getNotes))));
        }
    }

    private List<List<Long>> groupsOfEqualNotes(List<Long> ordered) {
        Map<Long, String> notes = new LinkedHashMap<>();
        seeded.forEach(parcel -> notes.put(parcel.getId(), parcel.getNotes()));
        List<List<Long>> groups = new ArrayList<>();
        String previous = null;
        for (Long id : ordered) {
            String current = notes.get(id);
            if (groups.isEmpty() || !java.util.Objects.equals(previous, current)) {
                groups.add(new ArrayList<>());
            }
            groups.get(groups.size() - 1).add(id);
            previous = current;
        }
        return groups;
    }

    private static <T> List<T> reversed(List<T> list) {
        List<T> copy = new ArrayList<>(list);
        java.util.Collections.reverse(copy);
        return copy;
    }

    @Test
    void theIdentifierColumnSortsBothWays() {
        assertThat(sortedIds("id", "asc")).isEqualTo(expected(Comparator.comparing(Parcel::getId)));
        assertThat(sortedIds("id", "desc")).isEqualTo(expected(Comparator.comparing(Parcel::getId).reversed()));
    }

    @Test
    void sortingByAColumnWithManyEqualValuesPagesWithoutRepeatsOrGaps() {
        // 55 parcels share only seven values of "pieces": without the tie-breaker the database may order equal rows differently on
        // each page, which repeats some rows and drops others.
        List<Long> pages = new ArrayList<>();
        for (int page = 0; page < 6; page++) {
            pages.addAll(ids(list(null, "pieces", "asc", String.valueOf(page), "10")));
        }

        assertThat(pages).hasSize(PARCELS).doesNotHaveDuplicates();
        assertThat(pages).isEqualTo(expected(byIdThen(Comparator.comparingInt(Parcel::getPieces))));
    }

    @Test
    void aRelationIsSortedByItsTargetsLabelBothWays() {
        for (boolean descending : new boolean[] {false, true}) {
            assertThat(sortedIds("depot", descending ? "desc" : "asc")).as("depot " + (descending ? "desc" : "asc"))
                    .isEqualTo(expected(nullsLast(EngineTestBase::depotName, descending)));
        }
        assertThat(sortedIds("depot", "asc").subList(0, 3)).as("North hub (even parcels) before South hub")
                .containsExactly(idOfParcel(2), idOfParcel(4), idOfParcel(6));
    }

    @Test
    void missingValuesAreLastInBothDirectionsForEveryNullableColumn() {
        addParcelsWithMissingValues();
        record Case(String key, Comparator<Parcel> ascending, Comparator<Parcel> descending) {
        }
        List<Case> cases = List.of(
                new Case("reference", nullsLast(Parcel::getReference, false), nullsLast(Parcel::getReference, true)),
                new Case("weight", nullsLast(Parcel::getWeight, false), nullsLast(Parcel::getWeight, true)),
                new Case("status", nullsLast(parcel -> parcel.getStatus() == null ? null : parcel.getStatus().name(), false),
                        nullsLast(parcel -> parcel.getStatus() == null ? null : parcel.getStatus().name(), true)),
                new Case("shippedOn", nullsLast(Parcel::getShippedOn, false), nullsLast(Parcel::getShippedOn, true)),
                new Case("createdAt", nullsLast(Parcel::getCreatedAt, false), nullsLast(Parcel::getCreatedAt, true)),
                new Case("depot", nullsLast(EngineTestBase::depotName, false), nullsLast(EngineTestBase::depotName, true)),
                new Case("courier", nullsLast(EngineTestBase::courierLabel, false),
                        nullsLast(EngineTestBase::courierLabel, true)));

        List<Long> bare = seeded.subList(PARCELS, PARCELS + 3).stream().map(Parcel::getId).toList();
        for (Case sort : cases) {
            for (String dir : List.of("asc", "desc")) {
                List<Long> actual = sortedIds(sort.key(), dir);
                assertThat(actual).as(sort.key() + " " + dir).hasSize(PARCELS + 3)
                        .isEqualTo(expected(dir.equals("asc") ? sort.ascending() : sort.descending()));
                assertThat(actual.subList(actual.size() - 3, actual.size()))
                        .as(sort.key() + " " + dir + ": the three records with no value are last, in identifier order")
                        .isEqualTo(bare);
            }
        }
    }

    @Test
    void theNullRuleReachesTheSqlOnlyWhereTheDatabaseNeedsIt() {
        // Hibernate writes "nulls last" only where the database's own default differs, so the SQL differs by engine; the result
        // does not (the test above). Here: nothing ever asks for nulls first.
        for (String key : List.of("reference", "weight", "status", "shippedOn", "depot", "courier")) {
            for (String dir : List.of("asc", "desc")) {
                RecordingStatementInspector.clear();
                list(null, key, dir, null, null);
                assertThat(RecordingStatementInspector.statements()).as(key + " " + dir)
                        .noneMatch(sql -> sql.toLowerCase(Locale.ROOT).contains("nulls first"));
            }
        }
    }

    // ---- filters ------------------------------------------------------------------------------------------------------------

    private ListPage run(String... pairs) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            params.computeIfAbsent(pairs[i], key -> new ArrayList<>()).add(pairs[i + 1]);
        }
        AdminEntity parcel = parcelEntity();
        FilterParse parsed = lists.parseFilters(parcel, params);
        return lists.list(parcel, lists.parse(parcel, null, null, null, null, "100").withParsedFilters(parsed.filters()));
    }

    private List<Long> expected(Predicate<Parcel> test) {
        return seeded.stream().filter(test).map(Parcel::getId).sorted().toList();
    }

    private static int number(Parcel parcel) {
        return Integer.parseInt(parcel.getReference().substring(4));
    }

    @Test
    void aTextFilterIsACaseInsensitiveContainsAndTakesWildcardsLiterally() {
        assertThat(ids(run("filter.reference", "pkg-01"))).isEqualTo(expected(p -> p.getReference().contains("PKG-01")));
        assertThat(ids(run("filter.notes", "100%"))).isEqualTo(expected(p -> p.getNotes().equals("100% sure")));
        assertThat(ids(run("filter.notes", "_"))).isEqualTo(expected(p -> p.getNotes().equals("a_b")));
        assertThat(run("filter.notes", "p_ain").total()).as("an underscore is not 'any one character'").isZero();
        assertThat(run("filter.reference", "zzz").total()).isZero();
    }

    @Test
    void anEnumAndABooleanFilterMatchExactly() {
        assertThat(ids(run("filter.status", "DELIVERED"))).isEqualTo(expected(p -> p.getStatus().name().equals("DELIVERED")));
        assertThat(ids(run("filter.fragile", "true"))).isEqualTo(expected(Parcel::isFragile));
        assertThat(ids(run("filter.fragile", "false"))).isEqualTo(expected(p -> !p.isFragile()));
    }

    @Test
    void aRelationFilterMatchesTheChosenRecord() {
        assertThat(ids(run("filter.depot", String.valueOf(north.getId())))).isEqualTo(expected(p -> number(p) % 2 == 0));
        assertThat(ids(run("filter.depot", String.valueOf(south.getId())))).isEqualTo(expected(p -> number(p) % 2 == 1));
    }

    @Test
    void aNumberRangeIncludesBothEndsAndAnExactDecimalMatches() {
        assertThat(ids(run("filter.weight.min", "30", "filter.weight.max", "45")))
                .isEqualTo(expected(p -> p.getWeight().compareTo(new BigDecimal("30")) >= 0
                        && p.getWeight().compareTo(new BigDecimal("45")) <= 0));
        assertThat(ids(run("filter.weight.min", "4.5", "filter.weight.max", "4.5"))).as("equal bounds: an exact match")
                .isEqualTo(expected(p -> number(p) == 3));
        assertThat(ids(run("filter.pieces.min", "2", "filter.pieces.max", "5")))
                .isEqualTo(expected(p -> p.getPieces() >= 2 && p.getPieces() <= 5));
    }

    @Test
    void aDateRangeAndADateTimeRangeIncludeTheirLastDay() {
        assertThat(ids(run("filter.shippedOn.from", "2026-01-10", "filter.shippedOn.to", "2026-01-20")))
                .isEqualTo(expected(p -> !p.getShippedOn().isBefore(LocalDate.of(2026, 1, 10))
                        && !p.getShippedOn().isAfter(LocalDate.of(2026, 1, 20))));
        assertThat(ids(run("filter.createdAt.from", "2026-03-02", "filter.createdAt.to", "2026-03-03")))
                .isEqualTo(expected(p -> !p.getCreatedAt().isBefore(LocalDateTime.of(2026, 3, 2, 0, 0))
                        && p.getCreatedAt().isBefore(LocalDateTime.of(2026, 3, 4, 0, 0))));
    }

    @Test
    void twoFiltersAndASearchNarrowTheListTogether() {
        ListPage page = run("filter.status", "DELIVERED", "filter.depot", String.valueOf(north.getId()));

        assertThat(ids(page)).isEqualTo(expected(p -> p.getStatus().name().equals("DELIVERED") && number(p) % 2 == 0));
        assertThat(page.total()).isGreaterThan(0);
    }

    // ---- hidden columns -----------------------------------------------------------------------------------------------------

    @Test
    void aHiddenRelationColumnIsNotJoinedOnAnyDatabase() {
        AdminEntity parcel = parcelEntity();
        RecordingStatementInspector.clear();
        lists.list(parcel, ListQuery.defaults());
        int all = joinsOfThePage();
        assertThat(all).as("depot, courier, crate and vault").isEqualTo(4);

        RecordingStatementInspector.clear();
        lists.list(parcel, lists.parse(parcel, null, null, null, null, null).withParsedHidden(List.of("depot", "courier")));

        assertThat(joinsOfThePage()).as("two relation columns hidden, two joins fewer").isEqualTo(2);
    }

    /** The number of left joins in the page query (the second select on the parcel table; the first is the count). */
    private static int joinsOfThePage() {
        List<String> selects = RecordingStatementInspector.statements().stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT))
                .filter(sql -> sql.stripLeading().startsWith("select") && sql.contains("parcel")).toList();
        String page = selects.get(1);
        int joins = 0;
        for (int at = page.indexOf(" left join "); at >= 0; at = page.indexOf(" left join ", at + 1)) {
            joins++;
        }
        return joins;
    }
}
