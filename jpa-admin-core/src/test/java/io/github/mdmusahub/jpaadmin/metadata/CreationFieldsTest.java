package io.github.mdmusahub.jpaadmin.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminField.Kind;
import io.github.mdmusahub.jpaadmin.metadata.testmodel.Widget;
import io.github.mdmusahub.jpaadmin.testsupport.TestPersistence;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The rule that picks the creation field of an entity for the dashboard chart (Phase 10, D-101): the seven accepted names
 * in their order, case and separators ignored, the five accepted types, and the two things that always rule a field out
 * (sensitive and hidden, SEC-1, SEC-2). Built from plain metadata records, so no entity name of any real model is needed
 * (ARCH-12).
 */
class CreationFieldsTest {

    private static AdminField field(String name, Class<?> type) {
        return field(name, type, false, false);
    }

    private static AdminField field(String name, Class<?> type, boolean sensitive, boolean hidden) {
        return new AdminField(name, name, type, type == LocalDate.class ? Kind.DATE : Kind.DATETIME, List.of(), null,
                false, false, true, false, sensitive, hidden);
    }

    private static AdminEntity entity(AdminField... fields) {
        List<AdminField> all = new ArrayList<>();
        all.add(new AdminField("id", "Id", Long.class, Kind.NUMBER, List.of(), null, true, false, false, false, false,
                false));
        all.addAll(List.of(fields));
        return new AdminEntity(Object.class, "Thing", "thing", "Thing", "Entities", "table", "id", null, all);
    }

    private static String creationName(AdminEntity entity) {
        return entity.creationField().map(AdminField::name).orElse(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"createdAt", "createdDate", "createdOn", "creationDate", "creationTime", "createTime",
            "createdDateTime"})
    void eachAcceptedNameIsFound(String name) {
        assertThat(creationName(entity(field("title", String.class), field(name, LocalDateTime.class)))).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATEDAT", "createdat", "CreatedAt", "created_at", "CREATED_AT", "created-at", "Created_On",
            "creation__date", "CREATE-TIME", "created_date_time", "_created_at_"})
    void caseAndSeparatorsAreIgnored(String name) {
        assertThat(creationName(entity(field(name, LocalDateTime.class)))).isEqualTo(name);
    }

    @Test
    void allFiveTemporalTypesAreAccepted() {
        for (Class<?> type : List.of(LocalDate.class, LocalDateTime.class, Instant.class, OffsetDateTime.class,
                ZonedDateTime.class)) {
            assertThat(creationName(entity(field("createdAt", type)))).as(type.getSimpleName()).isEqualTo("createdAt");
        }
    }

    @Test
    void aFieldOfAnotherTypeIsIgnoredEvenWithTheRightName() {
        for (Class<?> type : List.of(String.class, Long.class, long.class, Date.class, java.sql.Timestamp.class,
                java.time.LocalTime.class, java.time.OffsetTime.class, Object.class)) {
            assertThat(entity(field("createdAt", type)).creationField()).as(type.getSimpleName()).isEmpty();
        }
    }

    @Test
    void aFieldWithAnotherNameIsIgnoredEvenWithTheRightType() {
        for (String name : List.of("updatedAt", "created", "createdBy", "createdAtUtc", "dateCreated", "modifiedOn",
                "timestamp", "happenedAt", "creat")) {
            assertThat(entity(field(name, LocalDateTime.class)).creationField()).as(name).isEmpty();
        }
    }

    @Test
    void aSensitiveFieldIsNeverTheCreationField() {
        assertThat(entity(field("createdAt", LocalDateTime.class, true, false)).creationField()).isEmpty();
    }

    @Test
    void aHiddenFieldIsNeverTheCreationField() {
        assertThat(entity(field("createdAt", LocalDateTime.class, false, true)).creationField()).isEmpty();
    }

    @Test
    void aSensitiveOrHiddenFieldIsSkippedAndTheNextAcceptableNameIsUsed() {
        AdminEntity entity = entity(field("createdAt", LocalDateTime.class, true, false),
                field("createdDate", LocalDate.class, false, true), field("createdOn", Instant.class));

        assertThat(creationName(entity)).isEqualTo("createdOn");
    }

    @Test
    void severalCandidatesUseTheOrderOfTheList_notTheOrderOfDeclaration() {
        AdminEntity entity = entity(field("createdDateTime", LocalDateTime.class), field("createTime", LocalDateTime.class),
                field("creationDate", LocalDate.class), field("createdOn", LocalDate.class),
                field("createdDate", LocalDate.class));

        assertThat(creationName(entity)).isEqualTo("createdDate");
    }

    @Test
    void theNameListIsInTheDocumentedOrder() {
        assertThat(CreationFields.NAMES).containsExactly("createdAt", "createdDate", "createdOn", "creationDate",
                "creationTime", "createTime", "createdDateTime");
    }

    @Test
    void twoSpellingsOfTheSameNameUseTheFirstDeclared() {
        assertThat(creationName(entity(field("created_at", LocalDateTime.class), field("createdAt", LocalDateTime.class))))
                .isEqualTo("created_at");
        assertThat(creationName(entity(field("createdAt", LocalDateTime.class), field("created_at", LocalDateTime.class))))
                .isEqualTo("createdAt");
    }

    @Test
    void anEntityWithoutAnyIsLeftOut() {
        assertThat(entity(field("title", String.class), field("price", Long.class)).creationField()).isEmpty();
        assertThat(entity().creationField()).isEmpty();
    }

    @Test
    void anUnsupportedEntityHasNoCreationField() {
        AdminEntity unsupported = new AdminEntity(Object.class, "Thing", "thing", "Thing", "Entities", "table", null,
                "a composite key", List.of(field("createdAt", LocalDateTime.class)));

        assertThat(unsupported.creationField()).isEmpty();
    }

    @Test
    void theScannerFindsTheFieldOfARealModel() {
        AdminEntity widget = EntityScanner.scan(TestPersistence.factory().getMetamodel(), DiscoverySettings.defaults())
                .all().stream().filter(entity -> entity.type() == Widget.class).findFirst().orElseThrow();

        assertThat(creationName(widget)).isEqualTo("createdAt");
    }
}
