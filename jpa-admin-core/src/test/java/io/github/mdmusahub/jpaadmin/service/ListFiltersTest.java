package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.web.view.ListView.Filters.Definition.Kind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The filter rules from the metadata alone (SEC-2): which fields can be filtered, and how every kind of
 * {@code filter.*} parameter is read, accepted, ignored and reported. Nothing here touches the database; the effect of a
 * filter on rows is in {@link ListQueryServiceFilterTest}.
 */
class ListFiltersTest {

    private static final AdminEntityRegistry REGISTRY = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
            DiscoverySettings.defaults());
    private static final AdminEntity PARCEL = REGISTRY.find("parcel").orElseThrow();
    private static final AdminEntity LEDGER = REGISTRY.find("ledger").orElseThrow();
    private static final AdminEntity DEPOT = REGISTRY.find("depot").orElseThrow();

    /** Builds the parameters of a request: name, value, name, value ... */
    private static Map<String, List<String>> params(String... pairs) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            params.computeIfAbsent(pairs[i], key -> new java.util.ArrayList<>()).add(pairs[i + 1]);
        }
        return params;
    }

    private static FilterParse parse(String... pairs) {
        return ListFilters.parse(PARCEL, REGISTRY, params(pairs));
    }

    private static List<String> texts(FilterParse parse) {
        return parse.notes().stream().map(FilterNote::text).toList();
    }

    // ---- which fields -----------------------------------------------------------------------------------------

    @Test
    void parcelsFilterableFieldsAreExactlyTheVisibleNonSensitiveOnesOfASupportedKind() {
        Map<String, Kind> kinds = ListFilters.fields(PARCEL, REGISTRY).stream()
                .collect(Collectors.toMap(FilterField::key, FilterField::kind));

        assertThat(kinds).containsOnly(Map.entry("reference", Kind.TEXT), Map.entry("notes", Kind.TEXT),
                Map.entry("weight", Kind.NUMBER_RANGE), Map.entry("pieces", Kind.NUMBER_RANGE),
                Map.entry("fragile", Kind.BOOLEAN), Map.entry("status", Kind.ENUM),
                Map.entry("shippedOn", Kind.DATE_RANGE), Map.entry("createdAt", Kind.DATE_RANGE),
                Map.entry("depot", Kind.RELATION), Map.entry("courier", Kind.RELATION));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "accessToken", "memo", "internalCode", "crate", "vault", "items", "picture", "dimensions"})
    void noSensitiveHiddenIdentifierCollectionBinaryOrEmbeddedFieldIsEverFilterable(String key) {
        // accessToken: sensitive by name. memo: by annotation. internalCode: hidden. crate: a relation to a target with no
        // label. vault: a relation to a hidden entity. items: a collection. picture: bytes. dimensions: embedded.
        assertThat(ListFilters.fields(PARCEL, REGISTRY).stream().map(FilterField::key)).doesNotContain(key);
    }

    @Test
    void aSensitiveAttributeOfARelationTargetIsNotFilterableOnTheTargetEither() {
        assertThat(ListFilters.fields(DEPOT, REGISTRY).stream().map(FilterField::key)).contains("name", "city")
                .doesNotContain("apiKey", "id");
    }

    @Test
    void theFieldsComeInColumnOrder() {
        List<String> columns = ListColumns.of(PARCEL, REGISTRY).stream().map(ListColumn::key).toList();
        List<String> filters = ListFilters.fields(PARCEL, REGISTRY).stream().map(FilterField::key).toList();

        assertThat(columns).containsSubsequence(filters);
    }

    @Test
    void everyNumberAndDateTypeTheConsoleAcceptsIsFilterableAndTheCapNeedsTwelveOfThem() {
        Map<String, Kind> kinds = ListFilters.fields(LEDGER, REGISTRY).stream()
                .collect(Collectors.toMap(FilterField::key, FilterField::kind));

        assertThat(kinds).hasSize(12).containsEntry("label", Kind.TEXT);
        assertThat(kinds.entrySet().stream().filter(e -> e.getKey().equals("label") == false).map(Map.Entry::getValue))
                .containsOnly(Kind.NUMBER_RANGE, Kind.DATE_RANGE);
    }

    // ---- single values ----------------------------------------------------------------------------------------

    @Test
    void aTextFilterIsTrimmedAndKeptAsTyped() {
        FilterParse parse = parse("filter.reference", "  Pkg-00  ");

        assertThat(parse.filters()).containsExactly(ActiveFilter.of("reference", Kind.TEXT, "Pkg-00"));
        assertThat(parse.notes()).isEmpty();
    }

    @Test
    void aTextFilterTurnsControlCharactersIntoSpacesAndIsCutAtTheSearchLimitAndSaysSo() {
        FilterParse parse = parse("filter.notes", "a\tb\nc " + "x".repeat(200));

        assertThat(parse.filters()).singleElement().satisfies(filter -> {
            assertThat(filter.value()).startsWith("a b c ").hasSize(ListQuery.MAX_SEARCH_LENGTH);
        });
        assertThat(parse.notes()).containsExactly(new FilterNote("The Notes filter was cut to 100 characters.", true));
    }

    @Test
    void aTextFilterOfExactlyTheLimitIsNotReportedAsCut() {
        FilterParse parse = parse("filter.notes", "y".repeat(ListQuery.MAX_SEARCH_LENGTH));

        assertThat(parse.filters()).hasSize(1);
        assertThat(parse.notes()).isEmpty();
    }

    @Test
    void anEnumFilterNeedsOneConstantNameExactly() {
        assertThat(parse("filter.status", "IN_TRANSIT").filters())
                .containsExactly(ActiveFilter.of("status", Kind.ENUM, "IN_TRANSIT"));
        assertThat(parse("filter.status", " LOST ").filters()).containsExactly(ActiveFilter.of("status", Kind.ENUM, "LOST"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"in_transit", "Lost", "NOPE", "0", "LOST,PENDING", "LOST PENDING", "' or 1=1 --", "<script>", "ordinal"})
    void anUnknownEnumConstantIsIgnoredAndTheNoteNamesTheFilterButNotTheValue(String value) {
        FilterParse parse = parse("filter.status", value);

        assertThat(parse.filters()).isEmpty();
        assertThat(texts(parse)).containsExactly("The Status filter was ignored: that is not one of its values.");
    }

    @ParameterizedTest
    @CsvSource({"true,true", "TRUE,true", "False,false", "false,false", " true ,true"})
    void aBooleanFilterAcceptsTrueAndFalseInAnyLetterCase(String raw, String canonical) {
        assertThat(parse("filter.fragile", raw).filters()).containsExactly(ActiveFilter.of("fragile", Kind.BOOLEAN, canonical));
    }

    @ParameterizedTest
    @ValueSource(strings = {"any", "ANY", "", "   "})
    void aBooleanSetToAnyOrLeftBlankIsNoFilterAndNoMessage(String raw) {
        FilterParse parse = parse("filter.fragile", raw);

        assertThat(parse.filters()).isEmpty();
        assertThat(parse.notes()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "1", "maybe", "truee", "on"})
    void anyOtherBooleanValueIsIgnoredWithAnExplanation(String raw) {
        FilterParse parse = parse("filter.fragile", raw);

        assertThat(parse.filters()).isEmpty();
        assertThat(texts(parse)).containsExactly("The Fragile filter was ignored: use true, false or any.");
    }

    @Test
    void aRelationFilterKeepsTheChoiceAsTextAndIsCheckedAgainstTheTargetLaterWhenThePageIsRead() {
        assertThat(parse("filter.depot", "42").filters()).containsExactly(ActiveFilter.of("depot", Kind.RELATION, "42"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0000", "a\nb"})
    void aRelationChoiceWithControlCharactersIsIgnored(String raw) {
        FilterParse parse = parse("filter.depot", raw);

        assertThat(parse.filters()).isEmpty();
        assertThat(texts(parse)).containsExactly("The Depot filter was ignored: that is not one of the choices.");
    }

    @Test
    void aRelationChoiceLongerThanTheLimitIsIgnored() {
        assertThat(parse("filter.depot", "9".repeat(ListQuery.MAX_SEARCH_LENGTH + 1)).filters()).isEmpty();
    }

    // ---- number ranges ----------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"10,20,10,20", "10.50,20.5,10.50,20.5", "-5,5,-5,5", "007,0010,7,10", "-0,1,0,1"})
    void aNumberRangeIsWrittenBackInPlainDigits(String min, String max, String from, String to) {
        FilterParse parse = parse("filter.weight.min", min, "filter.weight.max", max);

        assertThat(parse.filters()).containsExactly(ActiveFilter.range("weight", Kind.NUMBER_RANGE, from, to));
        assertThat(parse.notes()).isEmpty();
    }

    @Test
    void aRangeWithOnlyOneBoundIsAFilterToo() {
        assertThat(parse("filter.weight.min", "3").filters()).containsExactly(ActiveFilter.range("weight", Kind.NUMBER_RANGE, "3", null));
        assertThat(parse("filter.weight.max", "3").filters()).containsExactly(ActiveFilter.range("weight", Kind.NUMBER_RANGE, null, "3"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1e5", "+5", "1,5", ".5", "5.", "0x10", "1 000", "NaN", "Infinity", "--1", "1.2.3", "٣", "5%", "' or 1=1",
            "99999999999999999999999999999999999"})
    void aBoundThatIsNotANumberIsIgnoredAloneAndTheOtherBoundIsKept(String bad) {
        FilterParse parse = parse("filter.weight.min", bad, "filter.weight.max", "50");

        assertThat(parse.filters()).containsExactly(ActiveFilter.range("weight", Kind.NUMBER_RANGE, null, "50"));
        assertThat(texts(parse)).containsExactly(
                "The Weight filter's minimum was ignored: it is not a number. Use digits, with a point for decimals.");
    }

    @Test
    void aMinimumGreaterThanTheMaximumIgnoresTheWholeFilter() {
        FilterParse parse = parse("filter.weight.min", "50", "filter.weight.max", "10");

        assertThat(parse.filters()).isEmpty();
        assertThat(texts(parse)).containsExactly("The Weight filter was ignored: the minimum is greater than the maximum.");
    }

    @Test
    void equalBoundsAreAnExactMatchAndAreAccepted() {
        assertThat(parse("filter.weight.min", "5", "filter.weight.max", "5").filters())
                .containsExactly(ActiveFilter.range("weight", Kind.NUMBER_RANGE, "5", "5"));
    }

    @Test
    void aWholeNumberFieldRoundsAFractionalBoundInwardsAMinimumUpAndAMaximumDown() {
        assertThat(parse("filter.pieces.min", "2.5", "filter.pieces.max", "5.9").filters())
                .containsExactly(ActiveFilter.range("pieces", Kind.NUMBER_RANGE, "3", "5"));
        assertThat(parse("filter.pieces.min", "-2.5").filters())
                .containsExactly(ActiveFilter.range("pieces", Kind.NUMBER_RANGE, "-2", null));
    }

    @Test
    void aBoundTheFieldTypeCannotHoldIsIgnoredNotGuessed() {
        FilterParse tooBigMinimum = parse("filter.pieces.min", "99999999999");
        FilterParse tooSmallMaximum = parse("filter.pieces.max", "-99999999999");

        assertThat(tooBigMinimum.filters()).isEmpty();
        assertThat(tooSmallMaximum.filters()).isEmpty();
        assertThat(texts(tooBigMinimum)).containsExactly("The Pieces filter's minimum was ignored: it is too large or too small for this field.");
        assertThat(texts(tooSmallMaximum)).containsExactly("The Pieces filter's maximum was ignored: it is too large or too small for this field.");
    }

    @Test
    void theLimitsOfEveryWholeNumberTypeAreRespected() {
        Map<String, Long> limits = Map.of("amount", Long.MAX_VALUE, "level", (long) Short.MAX_VALUE);
        limits.forEach((key, max) -> {
            FilterParse atLimit = ListFilters.parse(LEDGER, REGISTRY, params("filter." + key + ".max", String.valueOf(max)));
            assertThat(atLimit.filters()).as(key + " at its limit").hasSize(1);
            FilterParse past = ListFilters.parse(LEDGER, REGISTRY,
                    params("filter." + key + ".max", new java.math.BigDecimal(max).add(java.math.BigDecimal.ONE).toPlainString()));
            assertThat(past.filters()).as(key + " one past its limit").isEmpty();
        });
    }

    // ---- date ranges ------------------------------------------------------------------------------------------

    @Test
    void aDateRangeIsWrittenBackAsIsoDates() {
        FilterParse parse = parse("filter.shippedOn.from", " 2026-01-31 ", "filter.shippedOn.to", "2026-02-28");

        assertThat(parse.filters()).containsExactly(ActiveFilter.range("shippedOn", Kind.DATE_RANGE, "2026-01-31", "2026-02-28"));
        assertThat(parse.notes()).isEmpty();
    }

    @Test
    void aSingleDayIsFromAndToTheSameDay() {
        assertThat(parse("filter.createdAt.from", "2026-03-05", "filter.createdAt.to", "2026-03-05").filters())
                .containsExactly(ActiveFilter.range("createdAt", Kind.DATE_RANGE, "2026-03-05", "2026-03-05"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-13-01", "2026-00-10", "2026-02-29", "26-01-01", "2026/01/01", "01-02-2026",
            "2026-1-1", "2026-01-01T00:00", "yesterday", "0999-12-31", "9999-01-01", "+12026-01-01", "-0001-01-01",
            "2026-01-01; drop table parcel", "٢٠٢٦-٠١-٠١"})
    void aDateThatIsNotARealDayBetweenTheFirstAndLastYearIsIgnoredAloneAndTheOtherBoundIsKept(String bad) {
        FilterParse parse = parse("filter.shippedOn.from", bad, "filter.shippedOn.to", "2026-02-01");

        assertThat(parse.filters()).containsExactly(ActiveFilter.range("shippedOn", Kind.DATE_RANGE, null, "2026-02-01"));
        assertThat(texts(parse)).containsExactly(
                "The Shipped on filter's 'from' date was ignored: it is not a valid date. Use yyyy-MM-dd, for example 2026-11-30.");
    }

    @Test
    void theLastDayOfTheLeapYearIsAcceptedAndTheDayAfterItInAnOrdinaryYearIsNot() {
        assertThat(parse("filter.shippedOn.to", "2028-02-29").filters()).hasSize(1);
        assertThat(parse("filter.shippedOn.to", "2027-02-29").filters()).isEmpty();
    }

    @Test
    void aFromAfterTheToIgnoresTheWholeFilter() {
        FilterParse parse = parse("filter.createdAt.from", "2026-03-10", "filter.createdAt.to", "2026-03-01");

        assertThat(parse.filters()).isEmpty();
        assertThat(texts(parse)).containsExactly("The Created at filter was ignored: the 'from' date is after the 'to' date.");
    }

    // ---- silence, strangers and limits --------------------------------------------------------------------------

    @Test
    void blankValuesAreNotSetAndSayNothingBecauseAnHtmlFormSendsThem() {
        FilterParse parse = parse("filter.reference", "", "filter.status", "", "filter.weight.min", " ", "filter.weight.max", "",
                "filter.shippedOn.from", "", "filter.shippedOn.to", "", "filter.depot", "", "filter.fragile", "");

        assertThat(parse.filters()).isEmpty();
        assertThat(parse.notes()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"filter.accessToken", "filter.ACCESSTOKEN", "filter.access_token", "filter.memo", "filter.internalCode",
            "filter.vault", "filter.crate", "filter.items", "filter.picture", "filter.dimensions", "filter.id", "filter.nosuch",
            "filter.", "filter..min", "filter.weight.min.extra", "filter.weight", "filter.weight.from", "filter.status.min",
            "filter.shippedOn", "filter.shippedOn.min", "filter.reference.min", "filter.reference.", "filter.fragile.min"})
    void aNameThatIsNotAFilterIsIgnoredWithOneMessageThatNamesNothing(String name) {
        FilterParse parse = parse(name, "x");

        assertThat(parse.filters()).isEmpty();
        assertThat(texts(parse)).containsExactly("1 filter in the address does not match any filter of this table and was ignored.");
    }

    @Test
    void aSensitiveNameAndAnUnknownNameGiveIdenticalMessages() {
        assertThat(texts(parse("filter.accessToken", "hunter2"))).isEqualTo(texts(parse("filter.nothingLikeIt", "hunter2")));
        assertThat(texts(parse("filter.memo", "hunter2")).toString()).doesNotContain("hunter2", "memo", "accessToken");
    }

    @Test
    void severalStrangersAreCountedInOneMessage() {
        FilterParse parse = parse("filter.accessToken", "a", "filter.memo", "b", "filter.nosuch", "c", "other", "d");

        assertThat(texts(parse)).containsExactly("3 filters in the address do not match any filter of this table and were ignored.");
    }

    @Test
    void aBlankStrangerIsNotCounted() {
        assertThat(parse("filter.accessToken", "", "filter.nosuch", " ").notes()).isEmpty();
    }

    @Test
    void parametersThatAreNotFilterParametersAreNeverLookedAt() {
        FilterParse parse = parse("q", "x", "sort", "weight", "filterX", "1", "Filter.reference", "x");

        assertThat(parse.filters()).isEmpty();
        assertThat(parse.notes()).isEmpty();
    }

    @Test
    void aParameterGivenTwiceUsesTheFirstValueAndSaysSo() {
        FilterParse parse = parse("filter.status", "LOST", "filter.status", "PENDING");

        assertThat(parse.filters()).containsExactly(ActiveFilter.of("status", Kind.ENUM, "LOST"));
        assertThat(texts(parse)).containsExactly("The Status filter was given more than once, so only the first value was used.");
    }

    @Test
    void severalValidFiltersComeOutInColumnOrderWhateverTheOrderInTheAddress() {
        FilterParse parse = parse("filter.status", "LOST", "filter.depot", "1", "filter.reference", "PKG", "filter.weight.max", "9");

        assertThat(parse.filters().stream().map(ActiveFilter::key)).containsExactly("depot", "reference", "status", "weight");
    }

    @Test
    void theCapKeepsTheFirstTenInColumnOrderAndReportsHowManyWereIgnored() {
        Map<String, List<String>> all = params("filter.label", "x", "filter.amount.min", "1", "filter.level.min", "1",
                "filter.ratio.min", "1", "filter.tilt.min", "1", "filter.big.min", "1", "filter.seenAt.from", "2026-01-01",
                "filter.openedAt.from", "2026-01-01", "filter.closedAt.from", "2026-01-01", "filter.oldDate.from", "2026-01-01",
                "filter.stamped.from", "2026-01-01", "filter.plainDate.from", "2026-01-01");

        FilterParse parse = ListFilters.parse(LEDGER, REGISTRY, all);

        assertThat(ListQuery.MAX_FILTERS).isEqualTo(10);
        assertThat(parse.filters()).hasSize(10);
        List<String> columnOrder = ListFilters.fields(LEDGER, REGISTRY).stream().map(FilterField::key).toList();
        assertThat(parse.filters().stream().map(ActiveFilter::key).toList()).isEqualTo(columnOrder.subList(0, 10));
        assertThat(texts(parse)).containsExactly("Only 10 filters can be used at once, so 2 more were ignored.");
    }

    @Test
    void exactlyTenFiltersAreAllAppliedWithNoMessage() {
        Map<String, List<String>> ten = params("filter.label", "x", "filter.amount.min", "1", "filter.level.min", "1",
                "filter.ratio.min", "1", "filter.tilt.min", "1", "filter.big.min", "1", "filter.seenAt.from", "2026-01-01",
                "filter.openedAt.from", "2026-01-01", "filter.closedAt.from", "2026-01-01", "filter.oldDate.from", "2026-01-01");

        FilterParse parse = ListFilters.parse(LEDGER, REGISTRY, ten);

        assertThat(parse.filters()).hasSize(10);
        assertThat(parse.notes()).isEmpty();
    }

    @Test
    void aRangeWithBothBoundsCountsAsOneFilterForTheCap() {
        Map<String, List<String>> both = params("filter.amount.min", "1", "filter.amount.max", "9");

        assertThat(ListFilters.parse(LEDGER, REGISTRY, both).filters()).hasSize(1);
    }

    @Test
    void aFilterThatCannotBeReadDoesNotUseUpTheCap() {
        Map<String, List<String>> mixed = params("filter.label", "x", "filter.amount.min", "abc", "filter.level.min", "1",
                "filter.ratio.min", "1", "filter.tilt.min", "1", "filter.big.min", "1", "filter.seenAt.from", "2026-01-01",
                "filter.openedAt.from", "2026-01-01", "filter.closedAt.from", "2026-01-01", "filter.oldDate.from", "2026-01-01",
                "filter.stamped.from", "2026-01-01");

        FilterParse parse = ListFilters.parse(LEDGER, REGISTRY, mixed);

        assertThat(parse.filters()).hasSize(10);
        assertThat(texts(parse)).anyMatch(text -> text.contains("Amount filter's minimum was ignored"));
        assertThat(texts(parse)).noneMatch(text -> text.contains("Only 10"));
    }

    @Test
    void theParserNeverThrowsOnHostileInput() {
        Map<String, List<String>> hostile = new LinkedHashMap<>();
        hostile.put("filter.weight.min", List.of("\u0000", "x".repeat(100_000)));
        hostile.put("filter.reference", java.util.Arrays.asList((String) null));
        hostile.put("filter.notes", List.of());
        hostile.put("filter.status", null);
        hostile.put(null, List.of("x"));
        hostile.put("filter.depot", List.of("\uD800"));

        FilterParse parse = ListFilters.parse(PARCEL, REGISTRY, hostile);

        assertThat(parse.filters().size()).isLessThanOrEqualTo(ListQuery.MAX_FILTERS);
    }

    @Test
    void noNoteEverEchoesWhatWasTyped() {
        String needle = "NEEDLE-4711";
        FilterParse parse = parse("filter.weight.min", needle, "filter.status", needle, "filter.fragile", needle,
                "filter.shippedOn.from", needle, "filter.depot", needle + "\n", "filter." + needle, needle, "filter.accessToken", needle);

        assertThat(texts(parse)).isNotEmpty().noneMatch(text -> text.contains(needle));
    }
}
