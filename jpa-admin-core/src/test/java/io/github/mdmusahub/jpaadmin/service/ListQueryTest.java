package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The rules of {@link ListQuery#parse}: every part of a list request is untrusted (SEC-2), none of them can make the
 * parse fail, and each falls back to its documented default. Also which columns an entity has ({@link ListColumns}).
 */
class ListQueryTest {

    private static final AdminEntityRegistry REGISTRY = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
            DiscoverySettings.defaults());
    private static final AdminEntity PARCEL = REGISTRY.find("parcel").orElseThrow();

    private static ListQuery parse(String q, String sort, String dir, String page, String size) {
        return ListQuery.parse(PARCEL, q, sort, dir, page, size);
    }

    // ---- page -------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {"NULL|0", "''|0", "'   '|0", "abc|0", "-1|0", "-0|0",
            "1.5|0", "1e3|0", "0x10|0", "+3|0", "' 4 '|4", "0|0", "3|3", "007|7", "2147483647|2147483647",
            "99999999999999999999|2147483647", "1234567890123|2147483647", "٣|0", "１２|0", "1,2|0", "1;2|0"})
    void aPageThatIsNotAWholeNumberIsTheFirstPageAndAHugeOneIsFarPastTheEnd(String raw, int expected) {
        assertThat(parse(null, null, null, raw, null).page()).isEqualTo(expected);
    }

    // ---- size -------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {10, 25, 50, 100})
    void everyOfferedPageSizeIsAccepted(int size) {
        assertThat(parse(null, null, null, null, String.valueOf(size)).size()).isEqualTo(size);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {"NULL|25", "''|25", "0|25", "-10|25", "11|25", "9|25",
            "101|25", "1000|25", "abc|25", "25.0|25", "99999999999999999999|25", "010|10", "' 50 '|50", "1e1|25"})
    void aPageSizeThatIsNotOnOfferIsTheDefault(String raw, int expected) {
        assertThat(parse(null, null, null, null, raw).size()).isEqualTo(expected);
    }

    @Test
    void thePageSizesAreTheFourOfFr10AndTheDefaultIsOneOfThem() {
        assertThat(ListQuery.PAGE_SIZES).containsExactly(10, 25, 50, 100);
        assertThat(ListQuery.DEFAULT_SIZE).isEqualTo(25).isIn(ListQuery.PAGE_SIZES);
    }

    // ---- search -----------------------------------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void noSearchTextMeansNoSearch(String raw) {
        assertThat(parse(raw, null, null, null, null).search()).isEmpty();
    }

    @Test
    void searchTextIsTrimmedAndControlCharactersBecomeSpaces() {
        assertThat(parse("  iphone  ", null, null, null, null).search()).isEqualTo("iphone");
        assertThat(parse("a\nb\tc\u0000d\u001b[31m", null, null, null, null).search()).isEqualTo("a b c d [31m");
    }

    @Test
    void searchTextIsCutAtTheLimitAndNotRefused() {
        String longText = "a".repeat(5000);

        assertThat(parse(longText, null, null, null, null).search()).hasSize(ListQuery.MAX_SEARCH_LENGTH)
                .isEqualTo("a".repeat(100));
        assertThat(parse("b".repeat(100), null, null, null, null).search()).hasSize(100);
        assertThat(parse("b".repeat(101), null, null, null, null).search()).hasSize(100);
    }

    @Test
    void searchTextIsNeverCutInTheMiddleOfACharacter() {
        String emoji = "😀"; // one character, two chars
        String cut = parse(emoji.repeat(300), null, null, null, null).search();

        assertThat(cut.codePointCount(0, cut.length())).isEqualTo(ListQuery.MAX_SEARCH_LENGTH);
        assertThat(cut).isEqualTo(emoji.repeat(100));
    }

    @Test
    void searchTextThatLooksLikeSyntaxIsKeptAsItIs() {
        for (String text : List.of("http://x", "100%", "a_b", "it's", "say \"hi\"", "a\\b", "x' or '1'='1", "<b>", "%00")) {
            assertThat(parse(text, null, null, null, null).search()).isEqualTo(text);
        }
    }

    // ---- sort and direction -----------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"id", "reference", "notes", "weight", "pieces", "fragile", "status", "shippedOn",
            "createdAt"})
    void aSortableColumnIsAccepted(String key) {
        ListQuery query = parse(null, key, "desc", null, null);

        assertThat(query.sort()).isEqualTo(key);
        assertThat(query.dir()).isEqualTo("desc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"nosuch", "", " ", "ID", "Reference", "accessToken", "memo", "internalCode", "depot",
            "courier", "items", "picture", "dimensions", "reference desc", "reference;drop table parcel",
            "' or 1=1 --", "reference,pieces", "p.reference", "../id", "1"})
    void anyOtherSortKeyMeansNoSort(String key) {
        ListQuery query = parse(null, key, "desc", null, null);

        assertThat(query.sort()).isNull();
        assertThat(query.dir()).as("a direction without a sort is dropped too").isNull();
    }

    @Test
    void aMissingSortKeyMeansNoSort() {
        assertThat(parse(null, null, "asc", null, null).sort()).isNull();
        assertThat(parse(null, null, "desc", null, null).dir()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {"NULL|asc", "''|asc", "asc|asc", "desc|desc", "DESC|desc",
            "Desc|desc", "' desc '|desc", "ASC|asc", "up|asc", "down|asc", "sideways|asc", "descending|asc",
            "1|asc", "desc;drop|asc"})
    void aDirectionThatIsNotAscOrDescIsAscendingWhenTheSortKeyIsValid(String raw, String expected) {
        assertThat(parse(null, "pieces", raw, null, null).dir()).isEqualTo(expected);
    }

    // ---- the record itself ------------------------------------------------------------------------------------

    @Test
    void theRecordRefusesAnInconsistentQuery() {
        assertThatThrownBy(() -> new ListQuery("", "id", null, 0, 25)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListQuery("", null, "asc", 0, 25)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListQuery("", "id", "sideways", 0, 25)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListQuery("", null, null, -1, 25)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListQuery("", null, null, 0, 26)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ListQuery(null, null, null, 0, 25)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void changingSortOrSizeGoesBackToTheFirstPageBecauseTheOldPageWouldMeanOtherRows() {
        ListQuery third = new ListQuery("x", "pieces", "asc", 2, 10);

        assertThat(third.withSize(50).page()).isZero();
        assertThat(third.withSort("weight", "desc").page()).isZero();
        assertThat(third.withPage(1).page()).isEqualTo(1);
        assertThat(third.withPage(1).search()).isEqualTo("x");
    }

    // ---- which columns an entity has --------------------------------------------------------------------------

    @Test
    void theColumnsAreTheVisibleFieldsAndNothingElse() {
        List<String> keys = ListColumns.of(PARCEL).stream().map(ListColumn::key).toList();

        assertThat(keys).containsExactlyInAnyOrder("id", "accessToken", "courier", "crate", "createdAt", "depot",
                "fragile", "memo", "notes", "pieces", "reference", "shippedOn", "status", "vault", "weight");
        assertThat(keys).as("a hidden field").doesNotContain("internalCode")
                .as("a collection (ARCH-4)").doesNotContain("items")
                .as("binary data").doesNotContain("picture")
                .as("an embedded value").doesNotContain("dimensions");
        assertThat(keys.get(0)).as("the identifier comes first").isEqualTo("id");
    }

    @Test
    void aSensitiveFieldIsAMaskedColumnThatCannotBeSorted() {
        ListColumn token = column("accessToken");
        ListColumn memo = column("memo");

        for (ListColumn masked : List.of(token, memo)) {
            assertThat(masked.type()).isEqualTo(Cell.Type.MASKED);
            assertThat(masked.sortable()).isFalse();
        }
    }

    @Test
    void aRelationColumnIsNotSortableAndTheOthersAre() {
        for (String relation : List.of("depot", "courier", "crate", "vault")) {
            assertThat(column(relation).type()).isEqualTo(Cell.Type.RELATION);
            assertThat(column(relation).sortable()).as(relation).isFalse();
        }
        for (String key : List.of("id", "reference", "notes", "weight", "pieces", "fragile", "status", "shippedOn",
                "createdAt")) {
            assertThat(column(key).sortable()).as(key).isTrue();
        }
    }

    @Test
    void columnTypesAndAlignmentFollowTheKindOfField() {
        assertThat(column("reference").type()).isEqualTo(Cell.Type.TEXT);
        assertThat(column("weight").type()).isEqualTo(Cell.Type.NUMBER);
        assertThat(column("weight").align().name()).isEqualTo("END");
        assertThat(column("fragile").type()).isEqualTo(Cell.Type.BOOLEAN);
        assertThat(column("status").type()).isEqualTo(Cell.Type.ENUM);
        assertThat(column("shippedOn").type()).isEqualTo(Cell.Type.DATE);
        assertThat(column("createdAt").type()).isEqualTo(Cell.Type.DATE);
        assertThat(column("id").label()).isEqualTo("ID");
        assertThat(column("shippedOn").label()).isEqualTo("Shipped on");
    }

    private static ListColumn column(String key) {
        return ListColumns.of(PARCEL).stream().filter(column -> column.key().equals(key)).findFirst().orElseThrow();
    }
}
