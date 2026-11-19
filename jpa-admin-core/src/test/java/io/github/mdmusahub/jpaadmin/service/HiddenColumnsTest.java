package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The rules for the repeated {@code hide} parameter (Phase 12, D-112), against the neutral list model: a parcel with
 * thirteen columns that can be hidden, two sensitive fields (by name and by annotation, both masked columns), one hidden
 * ({@code @AdminHidden}) attribute that is not a column at all, a collection, binary data and an embedded value.
 */
class HiddenColumnsTest {

    private static final AdminEntityRegistry REGISTRY = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
            DiscoverySettings.defaults());
    private static final AdminEntity PARCEL = REGISTRY.find("parcel").orElseThrow();
    private static final List<ListColumn> COLUMNS = ListColumns.of(PARCEL, REGISTRY);

    /** Every column of the parcel list that can be hidden, in column order. */
    private static final List<String> HIDEABLE = List.of("id", "courier", "crate", "createdAt", "depot", "fragile", "notes", "pieces",
            "reference", "shippedOn", "status", "vault", "weight");

    private static HiddenParse parse(String... values) {
        return HiddenColumns.parse(COLUMNS, Arrays.asList(values));
    }

    @Test
    void theColumnsThatCanBeHiddenAreTheListsColumnsWithoutTheMaskedOnes() {
        assertThat(HiddenColumns.hideable(COLUMNS)).containsExactlyElementsOf(HIDEABLE);
        assertThat(COLUMNS).as("the masked columns are on the list, so they are real columns that cannot be hidden")
                .anyMatch(column -> column.type() == Cell.Type.MASKED && column.key().equals("accessToken"))
                .anyMatch(column -> column.type() == Cell.Type.MASKED && column.key().equals("memo"));
    }

    @Test
    void nothingAskedMeansNothingHidden() {
        assertThat(HiddenColumns.parse(COLUMNS, null)).isEqualTo(new HiddenParse(List.of(), false));
        assertThat(HiddenColumns.parse(COLUMNS, List.of())).isEqualTo(new HiddenParse(List.of(), false));
    }

    @Test
    void aVisibleColumnIsAccepted() {
        assertThat(parse("weight")).isEqualTo(new HiddenParse(List.of("weight"), false));
    }

    @Test
    void theResultIsInColumnOrderNotRequestOrderAndHasNoRepeat() {
        assertThat(parse("createdAt", "weight", "createdAt", "reference", "weight").hidden())
                .containsExactly("createdAt", "reference", "weight");
    }

    @Test
    void theIdentifierCanBeHidden() {
        assertThat(parse("id").hidden()).containsExactly("id");
    }

    @Test
    void aSensitiveColumnByNameIsNeverAccepted() {
        assertThat(parse("accessToken")).as("by name: the field name contains 'token'").isEqualTo(HiddenParse.NONE);
    }

    @Test
    void aSensitiveColumnByAnnotationIsNeverAccepted() {
        assertThat(parse("memo")).as("by @AdminSensitive").isEqualTo(HiddenParse.NONE);
    }

    @Test
    void aSensitiveNameBesideAValidOneIsIgnoredAndTheValidOneStays() {
        assertThat(parse("accessToken", "weight", "memo").hidden()).containsExactly("weight");
    }

    @Test
    void aHiddenAttributeIsNotAColumnSoItsNameIsIgnored() {
        assertThat(parse("internalCode")).isEqualTo(HiddenParse.NONE);
    }

    @Test
    void aSensitiveNameGivesTheSameAnswerAsAnUnknownOneSoHidingCannotTellThemApart() {
        assertThat(parse("accessToken")).isEqualTo(parse("nothingLikeThis"));
        assertThat(parse("memo", "internalCode")).isEqualTo(parse("x", "y"));
        assertThat(parse("accessToken", "memo", "internalCode", "weight")).isEqualTo(parse("weight"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "nothing", "Reference", "REFERENCE", " reference", "reference ", "weight,pieces",
            "weight;pieces", "weight\u0000", "../etc", "a b", "'; drop table parcel; --", "<script>", "filter.status", "dimensions",
            "items", "picture", "dimensions.width"})
    void anythingThatIsNotExactlyAColumnKeyIsIgnored(String value) {
        assertThat(parse(value)).as("[%s]", value).isEqualTo(HiddenParse.NONE);
        assertThat(parse(value, "pieces").hidden()).as("the valid name beside it stays").containsExactly("pieces");
    }

    @Test
    void aCollectionAnEmbeddedValueAndBinaryDataAreNotColumnsSoTheyCannotBeHidden() {
        assertThat(parse("items", "dimensions", "picture")).isEqualTo(HiddenParse.NONE);
    }

    @Test
    void anOverlongValueIsIgnoredWithoutBeingLookedAt() {
        assertThat(parse("w".repeat(100_000))).isEqualTo(HiddenParse.NONE);
        assertThat(parse("x".repeat(HiddenColumns.MAX_NAME_LENGTH + 1), "weight").hidden()).containsExactly("weight");
    }

    @Test
    void aNullValueInTheListIsIgnored() {
        assertThat(parse(null, "weight", null).hidden()).containsExactly("weight");
    }

    @Test
    void onlyTheFirstTwoHundredValuesAreRead() {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < HiddenColumns.MAX_VALUES_READ; i++) {
            values.add("junk" + i);
        }
        values.add("weight");

        assertThat(HiddenColumns.parse(COLUMNS, values)).as("the 201st value is not looked at").isEqualTo(HiddenParse.NONE);

        values.set(HiddenColumns.MAX_VALUES_READ - 1, "weight");

        assertThat(HiddenColumns.parse(COLUMNS, values).hidden()).as("the 200th is").containsExactly("weight");
    }

    @Test
    void hidingEveryColumnThatCanBeHiddenIsIgnoredAsAWholeAndSaysSo() {
        HiddenParse every = HiddenColumns.parse(COLUMNS, HIDEABLE);

        assertThat(every.hidden()).as("the last column that can stay is never hidden").isEmpty();
        assertThat(every.everyColumnHidden()).isTrue();
    }

    @Test
    void hidingAllButOneIsAllowed() {
        for (String stays : HIDEABLE) {
            List<String> asked = new ArrayList<>(HIDEABLE);
            asked.remove(stays);

            HiddenParse parsed = HiddenColumns.parse(COLUMNS, asked);

            assertThat(parsed.hidden()).as("all but %s", stays).hasSize(HIDEABLE.size() - 1).doesNotContain(stays);
            assertThat(parsed.everyColumnHidden()).isFalse();
        }
    }

    @Test
    void theMaskedColumnsDoNotCountAsTheColumnThatStays() {
        // The parcel has two masked columns on the list. Hiding everything else still counts as hiding every column.
        List<String> everythingButTheMasked = COLUMNS.stream().filter(column -> column.type() != Cell.Type.MASKED)
                .map(ListColumn::key).toList();

        assertThat(HiddenColumns.parse(COLUMNS, everythingButTheMasked).everyColumnHidden()).isTrue();
        assertThat(HiddenColumns.parse(COLUMNS, everythingButTheMasked).hidden()).isEmpty();
    }

    @Test
    void everyColumnPlusJunkIsStillEveryColumn() {
        List<String> asked = new ArrayList<>(HIDEABLE);
        asked.addAll(List.of("accessToken", "memo", "internalCode", "nothing"));

        assertThat(HiddenColumns.parse(COLUMNS, asked).everyColumnHidden()).isTrue();
    }

    @Test
    void anEntityWhoseOnlyColumnsAreMaskedHasNothingToHide() {
        List<ListColumn> masked = List.of(new ListColumn("secret", "Secret", Cell.Type.MASKED, false,
                io.github.mdmusahub.jpaadmin.web.view.ListView.Column.Align.START));

        assertThat(HiddenColumns.parse(masked, List.of("secret"))).isEqualTo(HiddenParse.NONE);
    }

    @Test
    void anEntityWithOneColumnThatCanBeHiddenCannotHideIt() {
        List<ListColumn> one = List.of(new ListColumn("name", "Name", Cell.Type.TEXT, true,
                io.github.mdmusahub.jpaadmin.web.view.ListView.Column.Align.START));

        HiddenParse parsed = HiddenColumns.parse(one, List.of("name"));

        assertThat(parsed.hidden()).isEmpty();
        assertThat(parsed.everyColumnHidden()).isTrue();
    }

    // ---- the second check, for a query that was not built by the parser ---------------------------------------------

    @Test
    void theSecondCheckDropsASensitiveColumnAnUnknownNameAndTheLastColumn() {
        assertThat(HiddenColumns.checked(COLUMNS, List.of("accessToken", "memo", "internalCode", "nothing", "weight")))
                .containsExactly("weight");
        assertThat(HiddenColumns.checked(COLUMNS, HIDEABLE)).as("never the last column").isEmpty();
        assertThat(HiddenColumns.checked(COLUMNS, List.of())).isEmpty();
    }

    @Test
    void theServiceParsesWithTheConsolesOwnColumns() {
        ListQueryService service = ListQueryService.over(ListTestPersistence.entityManager(), REGISTRY);

        assertThat(service.parseHidden(PARCEL, List.of("weight", "accessToken")).hidden()).containsExactly("weight");
        assertThat(service.parseHidden(PARCEL, null)).isEqualTo(HiddenParse.NONE);
    }

    @Test
    void theResultRecordRefusesToHideNothingAndEveryColumnAtOnce() {
        assertThatThrownBy(() -> new HiddenParse(List.of("a"), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HiddenParse(null, false)).isInstanceOf(NullPointerException.class);
    }
}
