package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.TransactionalRecordWriteService;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Archive;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Counter;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Folio;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Frozen;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Sealed;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Create and update against a real database: what is stored, what is refused, and that a refused save leaves nothing behind.
 * The service runs inside the transaction handling a real application gives it ({@link TransactionalRecordWriteService}).
 */
class RecordWriteServiceTest extends AbstractFormServiceTest {

    private RecordWriteService writes() {
        return new TransactionalRecordWriteService(entityManager, registry);
    }

    private InvalidRecordException refused(Map<String, String> values) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> writes().create(entity("folio"), posted(values)));
        assertThat(thrown).isInstanceOf(InvalidRecordException.class);
        return (InvalidRecordException) thrown;
    }

    // ---- create ------------------------------------------------------------------------------------------------------

    @Test
    void createStoresEveryKindOfFieldAndTheHostsOwnSetterRuns() {
        long before = folioCount();

        RecordWriteService.Saved saved = writes().create(entity("folio"), posted(validFolio()));

        assertThat(folioCount()).isEqualTo(before + 1);
        Folio created = stored(Long.valueOf(saved.id()));
        assertThat(created.getTitle()).isEqualTo("A new folio");
        assertThat(created.getCode()).isEqualTo("N-1");
        assertThat(created.getQuantity()).isEqualTo(7);
        assertThat(created.isUrgent()).isTrue();
        assertThat(created.getStatus()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(created.getDue()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(created.getOpenedAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 10, 30));
        assertThat(created.getHome().getId()).isEqualTo(depotOne);
        assertThat(created.getCourier().getId()).isEqualTo(courier);
        assertThat(created.getSecretNote()).isEqualTo("stored:typed-secret"); // the console stores what it is given
        assertThat(created.getRevision()).isZero();
        assertThat(saved.label()).isEqualTo("A new folio");
    }

    @Test
    void anOptionalFieldLeftEmptyIsNull() {
        Map<String, String> values = validFolio();
        values.put("code", "");
        values.put("status", "");
        values.put("due", "  ");
        values.put("courier", "");
        values.put("secretNote", "");

        Folio created = stored(Long.valueOf(writes().create(entity("folio"), posted(values)).id()));

        assertThat(created.getCode()).isNull();
        assertThat(created.getStatus()).isNull();
        assertThat(created.getDue()).isNull();
        assertThat(created.getCourier()).isNull();
        assertThat(created.getSecretNote()).isNull();
    }

    @Test
    void aRequiredFieldLeftEmptyNeverSaves() {
        long before = folioCount();
        Map<String, String> values = validFolio();
        values.put("title", "   ");
        values.put("home", "");

        InvalidRecordException refusal = refused(values);

        assertThat(refusal.fieldErrors()).containsEntry("title", FieldMessages.required())
                .containsEntry("home", FieldMessages.required()).hasSize(2);
        assertThat(folioCount()).isEqualTo(before);
    }

    @Test
    void aNameThatWasNotSentIsEmptyOnCreate() {
        InvalidRecordException refusal = refused(new HashMap<>());

        assertThat(refusal.fieldErrors()).containsKeys("title", "quantity", "home");
        assertThat(refusal.fieldErrors()).doesNotContainKeys("code", "status", "courier", "secretNote");
    }

    @Test
    void textInANumberFieldAnImpossibleDateAnUnknownEnumAndAnUnknownRelationNeverSaveAndNeverThrow() {
        long before = folioCount();
        Map<String, String> values = validFolio();
        values.put("quantity", "twelve");
        values.put("due", "2026-02-30");
        values.put("openedAt", "tomorrow");
        values.put("status", "SHIPPED");
        values.put("home", "999999");
        values.put("courier", "abc");

        InvalidRecordException refusal = refused(values);

        assertThat(refusal.fieldErrors()).containsEntry("quantity", FieldMessages.wholeNumber())
                .containsEntry("due", FieldMessages.date()).containsEntry("openedAt", FieldMessages.dateTime())
                .containsEntry("status", FieldMessages.chooseListed())
                .containsEntry("home", FieldMessages.chooseExisting())
                .containsEntry("courier", FieldMessages.chooseExisting()).hasSize(6);
        assertThat(refusal.formErrors()).isEmpty();
        assertThat(folioCount()).isEqualTo(before);
        for (String message : refusal.fieldErrors().values()) {
            assertThat(message).doesNotContain("twelve", "SHIPPED", "999999", "abc", "tomorrow");
        }
    }

    @Test
    void aTextLongerThanItsColumnIsRefusedBeforeTheDatabaseIsAsked() {
        Map<String, String> values = validFolio();
        values.put("title", "x".repeat(31));
        RecordingStatementInspector.clear();

        InvalidRecordException refusal = refused(values);

        assertThat(refusal.fieldErrors()).containsEntry("title", FieldMessages.tooLong(30));
        assertThat(RecordingStatementInspector.statements()).noneMatch(sql -> sql.toLowerCase().startsWith("insert"));
    }

    @Test
    void aDatabaseConstraintBecomesOneSentenceForTheFormWithNoSqlInIt() {
        Map<String, String> values = validFolio();
        values.put("code", "E-1"); // the seeded folio has it, and the column is unique

        InvalidRecordException refusal = refused(values);

        assertThat(refusal.fieldErrors()).isEmpty();
        assertThat(refusal.formErrors()).containsExactly(FieldMessages.refused());
        assertThat(refusal.getMessage()).doesNotContain("E-1").doesNotContain("unique").doesNotContainIgnoringCase("folio");
        assertThat(refusal.getCause()).isNull();
        assertThat(folioCount()).isEqualTo(1);
    }

    @Test
    void aBeanValidationFailureIsMappedToItsFieldAndAHiddenPropertyToTheForm() throws Exception {
        java.lang.annotation.Annotation blank = ConstraintMessagesTest.annotation("blank");
        Folio.failure = folio -> new ConstraintViolationException("raw message needle-raw", violations(
                ConstraintMessagesTest.violation("title", blank, Map.of()),
                ConstraintMessagesTest.violation("internalNote", blank, Map.of())));

        InvalidRecordException refusal = refused(validFolio());

        assertThat(refusal.fieldErrors()).containsExactly(Map.entry("title", FieldMessages.required()));
        assertThat(refusal.formErrors()).containsExactly(FieldMessages.refused());
        assertThat(refusal.getMessage() + refusal.fieldErrors() + refusal.formErrors()).doesNotContain("needle-raw")
                .doesNotContain("internalNote");
        assertThat(folioCount()).isEqualTo(1);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Set violations(jakarta.validation.ConstraintViolation<?>... found) {
        return new java.util.LinkedHashSet<>(List.of(found));
    }

    // ---- SEC-11 ------------------------------------------------------------------------------------------------------

    @Test
    void aCreateOnAnEntityThatIsNotCreatableIsRefusedAndTheDatabaseIsNeverAsked() {
        RecordingStatementInspector.clear();

        assertThatThrownBy(() -> writes().create(entity("sealed"), posted(Map.of("title", "x"))))
                .isInstanceOf(ActionRefusedException.class);

        assertThat(RecordingStatementInspector.statements()).isEmpty();
        assertThat(count(Sealed.class)).isZero();
    }

    @Test
    void aCreateOnAReadOnlyEntityIsRefusedAndTheDatabaseIsNeverAsked() {
        RecordingStatementInspector.clear();

        assertThatThrownBy(() -> writes().create(entity("archive"), posted(Map.of("title", "x"))))
                .isInstanceOf(ActionRefusedException.class);

        assertThat(RecordingStatementInspector.statements()).isEmpty();
        assertThat(count(Archive.class)).isZero();
    }

    @Test
    void anUpdateOnAnEntityThatIsNotUpdatableIsRefusedAndTheDatabaseIsNeverAsked() {
        Long[] id = new Long[1];
        ListTestPersistence.inTransaction(em -> {
            Frozen frozen = new Frozen();
            frozen.setTitle("kept");
            em.persist(frozen);
            em.flush();
            id[0] = frozen.getId();
        });
        RecordingStatementInspector.clear();

        assertThatThrownBy(() -> writes().update(entity("frozen"), String.valueOf(id[0]), null, posted(Map.of("title", "changed"))))
                .isInstanceOf(ActionRefusedException.class);

        assertThat(RecordingStatementInspector.statements()).isEmpty();
        assertThat(find(Frozen.class, id[0]).getTitle()).isEqualTo("kept");
    }

    @Test
    void anUpdateOnAReadOnlyEntityIsRefused() {
        Long[] id = new Long[1];
        ListTestPersistence.inTransaction(em -> {
            Archive archive = new Archive();
            archive.setTitle("kept");
            em.persist(archive);
            em.flush();
            id[0] = archive.getId();
        });

        assertThatThrownBy(() -> writes().update(entity("archive"), String.valueOf(id[0]), null, posted(Map.of("title", "x"))))
                .isInstanceOf(ActionRefusedException.class);
        assertThat(find(Archive.class, id[0]).getTitle()).isEqualTo("kept");
    }

    @Test
    void anEntityTheConsoleDoesNotOfferIsUnknownToBothWrites() {
        RecordWriteService unknown = new TransactionalRecordWriteService(entityManager, AdminEntityRegistry.empty());

        assertThatThrownBy(() -> unknown.create(entity("folio"), posted(validFolio())))
                .isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> unknown.update(entity("folio"), String.valueOf(folio), "0", posted(validFolio())))
                .isInstanceOf(RecordNotFoundException.class);
        assertThat(folioCount()).isEqualTo(1);
    }

    @Test
    void anUnavailableServiceKnowsNoEntity() {
        assertThatThrownBy(() -> RecordWriteService.unavailable().create(entity("folio"), posted(validFolio())))
                .isInstanceOf(RecordNotFoundException.class);
    }

    // ---- mass assignment ---------------------------------------------------------------------------------------------

    @Test
    void theServiceAsksTheRequestOnlyForTheNamesOfEditableFields() {
        List<String> asked = new ArrayList<>();
        Map<String, String> values = validFolio();
        values.put("id", "999");
        values.put("revision", "77");
        values.put("internalNote", "hacked");
        values.put("class", "x");
        values.put("unknownField", "x");

        RecordWriteService.Saved saved = writes().create(entity("folio"), name -> {
            asked.add(name);
            return values.get(name);
        });

        assertThat(asked).containsExactlyInAnyOrder("title", "code", "quantity", "urgent", "status", "due", "openedAt", "home",
                "courier", "secretNote");
        Folio created = stored(Long.valueOf(saved.id()));
        assertThat(created.getId()).isNotEqualTo(999L);
        assertThat(created.getRevision()).isZero();
        assertThat(created.getInternalNote()).isNull();
    }

    @Test
    void anUpdateNeverChangesTheIdentifierTheVersionOrAHiddenFieldWhateverIsPosted() {
        Map<String, String> values = new HashMap<>();
        values.put("id", "999");
        values.put("revision", "77");
        values.put("internalNote", "hacked");
        values.put("title", "Retitled");

        writes().update(entity("folio"), String.valueOf(folio), "0", posted(values));

        Folio updated = stored(folio);
        assertThat(updated.getId()).isEqualTo(folio);
        assertThat(updated.getRevision()).isEqualTo(1); // one real change, counted by the persistence layer only
        assertThat(updated.getInternalNote()).isEqualTo("internal");
        assertThat(updated.getTitle()).isEqualTo("Retitled");
        assertThat(find(Folio.class, 999L)).isNull();
    }

    // ---- update ------------------------------------------------------------------------------------------------------

    @Test
    void anUpdateWritesTheChangedFieldsAndKeepsTheRest() {
        Map<String, String> values = new HashMap<>();
        values.put("title", "Changed title");
        values.put("quantity", "10");
        values.put("urgent", "true");
        values.put("home", String.valueOf(depotTwo));
        values.put("courier", String.valueOf(courier));
        values.put("code", "E-1");
        values.put("status", "PENDING");

        RecordWriteService.Saved saved = writes().update(entity("folio"), String.valueOf(folio), "0", posted(values));

        Folio updated = stored(folio);
        assertThat(saved.id()).isEqualTo(String.valueOf(folio));
        assertThat(updated.getTitle()).isEqualTo("Changed title");
        assertThat(updated.getQuantity()).isEqualTo(10);
        assertThat(updated.isUrgent()).isTrue();
        assertThat(updated.getHome().getId()).isEqualTo(depotTwo);
        assertThat(updated.getCourier().getId()).isEqualTo(courier);
        assertThat(updated.getCode()).isEqualTo("E-1");
        // Not posted, so unchanged:
        assertThat(updated.getDue()).isEqualTo(LocalDate.of(2026, 3, 4));
        assertThat(updated.getSecretNote()).isEqualTo(STORED_SECRET);
    }

    @Test
    void aFormSavedWithNoChangeDoesNotTouchTheRecordNotEvenItsVersionOrItsPreciseTime() {
        Map<String, String> unchanged = new HashMap<>();
        unchanged.put("title", "Existing folio");
        unchanged.put("code", "E-1");
        unchanged.put("quantity", "3");
        unchanged.put("urgent", "false");
        unchanged.put("status", "PENDING");
        unchanged.put("due", "2026-03-04");
        unchanged.put("openedAt", "2026-03-04T05:06:07.123"); // what the form shows: to the millisecond, not the nanosecond
        unchanged.put("home", String.valueOf(depotOne));
        unchanged.put("courier", "");
        unchanged.put("secretNote", "");

        LocalDateTime preciseBefore = stored(folio).getOpenedAt(); // the database keeps microseconds; the form shows milliseconds
        assertThat(preciseBefore.getNano()).isGreaterThan(123_000_000);

        writes().update(entity("folio"), String.valueOf(folio), "0", posted(unchanged));

        Folio updated = stored(folio);
        assertThat(updated.getRevision()).isZero();
        assertThat(updated.getOpenedAt()).isEqualTo(preciseBefore);
    }

    @Test
    void aBlankSecretKeepsTheStoredOneAndATypedSecretReplacesIt() {
        writes().update(entity("folio"), String.valueOf(folio), "0", posted(Map.of("secretNote", "")));
        assertThat(stored(folio).getSecretNote()).isEqualTo(STORED_SECRET);
        assertThat(stored(folio).getRevision()).isZero();

        writes().update(entity("folio"), String.valueOf(folio), "0", posted(Map.of("secretNote", "   ")));
        assertThat(stored(folio).getSecretNote()).isEqualTo(STORED_SECRET);

        writes().update(entity("folio"), String.valueOf(folio), "0", posted(Map.of("secretNote", "brand-new")));
        assertThat(stored(folio).getSecretNote()).isEqualTo("stored:brand-new");
    }

    @Test
    void aSecretTypedAgainWithTheSameTextIsStillStoredAsGiven() {
        writes().update(entity("folio"), String.valueOf(folio), "0", posted(Map.of("secretNote", "old-secret-value")));

        assertThat(stored(folio).getSecretNote()).isEqualTo(STORED_SECRET);
    }

    @Test
    void anUpdateWithABadValueSavesNothingAtAll() {
        Map<String, String> values = new HashMap<>();
        values.put("title", "Would be saved");
        values.put("quantity", "many");
        values.put("home", "999999");

        assertThatThrownBy(() -> writes().update(entity("folio"), String.valueOf(folio), "0", posted(values)))
                .isInstanceOfSatisfying(InvalidRecordException.class, refusal -> assertThat(refusal.fieldErrors())
                        .containsOnlyKeys("quantity", "home"));

        assertThat(stored(folio).getTitle()).isEqualTo("Existing folio");
    }

    @Test
    void aRequiredFieldEmptiedOnUpdateIsRefused() {
        assertThatThrownBy(() -> writes().update(entity("folio"), String.valueOf(folio), "0", posted(Map.of("title", ""))))
                .isInstanceOfSatisfying(InvalidRecordException.class, refusal -> assertThat(refusal.fieldErrors())
                        .containsEntry("title", FieldMessages.required()));
        assertThat(stored(folio).getTitle()).isEqualTo("Existing folio");
    }

    @Test
    void anOptionalRelationCanBeCleared() {
        writes().update(entity("folio"), String.valueOf(folio), "0", posted(Map.of("courier", String.valueOf(courier))));
        assertThat(stored(folio).getCourier()).isNotNull();

        writes().update(entity("folio"), String.valueOf(folio), "1", posted(Map.of("courier", "")));

        assertThat(stored(folio).getCourier()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "99999", "-1", "1.5", "007", "", " "})
    void anIdThatIsNotARecordIsTheSameRefusal(String id) {
        assertThatThrownBy(() -> writes().update(entity("folio"), id, "0", posted(Map.of("title", "x"))))
                .isInstanceOf(RecordNotFoundException.class);
        assertThat(stored(folio).getTitle()).isEqualTo("Existing folio");
    }

    // ---- stale version -----------------------------------------------------------------------------------------------

    @Test
    void aStaleFormIsRefusedWithAPlainMessageAndNothingIsSaved() {
        writes().update(entity("folio"), String.valueOf(folio), "0", posted(Map.of("title", "First change")));
        assertThat(stored(folio).getRevision()).isEqualTo(1);

        assertThatThrownBy(() -> writes().update(entity("folio"), String.valueOf(folio), "0", posted(Map.of("title", "Second"))))
                .isInstanceOfSatisfying(InvalidRecordException.class, refusal -> {
                    assertThat(refusal.formErrors()).containsExactly(FieldMessages.stale());
                    assertThat(refusal.fieldErrors()).isEmpty();
                    assertThat(refusal.getCause()).isNull();
                    assertThat(refusal.getStackTrace()).isEmpty(); // no stack trace to print
                });

        assertThat(stored(folio).getTitle()).isEqualTo("First change");
    }

    @Test
    void aFormWithNoVersionForAVersionedEntityIsStaleToo() {
        assertThatThrownBy(() -> writes().update(entity("folio"), String.valueOf(folio), null, posted(Map.of("title", "x"))))
                .isInstanceOfSatisfying(InvalidRecordException.class,
                        refusal -> assertThat(refusal.formErrors()).containsExactly(FieldMessages.stale()));
        assertThat(stored(folio).getTitle()).isEqualTo("Existing folio");
    }

    @Test
    void aChangeThatRacedThisOneIsTheSameStaleAnswer() {
        // The form is current when the service loads the record; another user commits a change while the values are read.
        java.util.concurrent.atomic.AtomicBoolean raced = new java.util.concurrent.atomic.AtomicBoolean();
        Map<String, String> values = Map.of("title", "My change");

        assertThatThrownBy(() -> writes().update(entity("folio"), String.valueOf(folio), "0", name -> {
            if (raced.compareAndSet(false, true)) {
                ListTestPersistence.inTransaction(other -> other.find(Folio.class, folio).setTitle("Their change"));
            }
            return values.get(name);
        })).isInstanceOfSatisfying(InvalidRecordException.class,
                refusal -> assertThat(refusal.formErrors()).containsExactly(FieldMessages.stale()));

        assertThat(stored(folio).getTitle()).isEqualTo("Their change");
    }

    // ---- an identifier the application assigns ---------------------------------------------------------------------

    @Test
    void anIdentifierThatIsNotGeneratedIsAnInputOfTheCreateFormOnly() {
        Map<String, String> values = new HashMap<>();
        values.put("id", "42");
        values.put("name", "forty-two");

        RecordWriteService.Saved saved = writes().create(entity("counter"), posted(values));

        assertThat(saved.id()).isEqualTo("42");
        assertThat(find(Counter.class, 42).getName()).isEqualTo("forty-two");

        // It never changes afterwards, whatever is posted:
        writes().update(entity("counter"), "42", null, posted(Map.of("id", "43", "name", "renamed")));
        assertThat(find(Counter.class, 42).getName()).isEqualTo("renamed");
        assertThat(find(Counter.class, 43)).isNull();
    }

    @Test
    void anAssignedIdentifierThatIsNotANumberOrIsTakenIsRefused() {
        assertThatThrownBy(() -> writes().create(entity("counter"), posted(Map.of("id", "abc", "name", "x"))))
                .isInstanceOfSatisfying(InvalidRecordException.class,
                        refusal -> assertThat(refusal.fieldErrors()).containsKey("id"));
        writes().create(entity("counter"), posted(Map.of("id", "7", "name", "x")));
        assertThatThrownBy(() -> writes().create(entity("counter"), posted(Map.of("id", "7", "name", "again"))))
                .isInstanceOf(InvalidRecordException.class);
        assertThat(count(Counter.class)).isEqualTo(1L);
    }

    // ---- a relation with too many choices ---------------------------------------------------------------------------

    @Test
    void aRelationWithMoreThan200ChoicesRefusesAChangeForThatFieldOnlyAndKeepsTheStoredValueOtherwise() {
        ListTestPersistence.inTransaction(em -> {
            for (int i = 0; i < 200; i++) {
                Depot depot = new Depot();
                depot.setName("Bulk depot " + i);
                em.persist(depot);
            }
        });
        // 202 depots now: the relation has no choices.
        assertThatThrownBy(() -> writes().update(entity("folio"), String.valueOf(folio), "0",
                posted(Map.of("title", "Retitled", "home", String.valueOf(depotTwo)))))
                .isInstanceOfSatisfying(InvalidRecordException.class, refusal -> assertThat(refusal.fieldErrors())
                        .containsOnlyKeys("home").containsEntry("home", FieldMessages.tooManyChoices("Home", 200)));
        assertThat(stored(folio).getTitle()).isEqualTo("Existing folio");

        // The unchanged value, or no value at all, keeps it and the rest saves:
        writes().update(entity("folio"), String.valueOf(folio), "0",
                posted(Map.of("title", "Retitled", "home", String.valueOf(depotOne))));
        writes().update(entity("folio"), String.valueOf(folio), "1", posted(Map.of("title", "Retitled again")));
        Folio updated = stored(folio);
        assertThat(updated.getTitle()).isEqualTo("Retitled again");
        assertThat(updated.getHome().getId()).isEqualTo(depotOne);

        // A create cannot choose it either:
        Map<String, String> values = validFolio();
        values.put("code", "N-9");
        assertThatThrownBy(() -> writes().create(entity("folio"), posted(values)))
                .isInstanceOfSatisfying(InvalidRecordException.class, refusal -> assertThat(refusal.fieldErrors())
                        .containsOnlyKeys("home"));
    }

    // ---- nothing leaks into the log ---------------------------------------------------------------------------------

    @Test
    void aRefusedSaveLogsNoValue() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(RecordWriteService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Map<String, String> values = validFolio();
            values.put("code", "E-1");
            values.put("secretNote", "needle-typed-secret");
            refused(values);
        } finally {
            logger.detachAppender(appender);
        }
        assertThat(appender.list).isNotEmpty();
        for (ch.qos.logback.classic.spi.ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain("needle").doesNotContain("E-1").doesNotContain("insert");
        }
    }
}
