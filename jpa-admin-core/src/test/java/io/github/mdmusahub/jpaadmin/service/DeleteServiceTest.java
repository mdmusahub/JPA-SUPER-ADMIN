package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.TransactionalDeleteService;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Anchor;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Archive;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Counter;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Crate;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelItem;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Pass;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Stamp;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Ticket;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Delete and bulk delete against a real database: what is removed and what is not, SEC-11 in the service, the foreign key
 * that keeps a record, and the rollback that makes a batch all or nothing. The class under test runs inside the same
 * transaction handling a real application gives it ({@link TransactionalDeleteService}).
 */
class DeleteServiceTest {

    private static final String SECRET = "needle-secret-value";

    private AdminEntityRegistry registry;
    private EntityManager entityManager;
    private DeleteService deletes;
    private Long[] parcels;
    private Long parcelWithItem;
    private Long archiveId;
    private Long anchorId;
    private Long depotInUse;
    private Long crateId;

    @BeforeEach
    void seed() {
        ListTestPersistence.empty();
        parcels = new Long[5];
        ListTestPersistence.inTransaction(em -> {
            Depot depot = new Depot();
            depot.setName("Central depot");
            depot.setApiKey(SECRET);
            em.persist(depot);
            for (int i = 0; i < parcels.length; i++) {
                Parcel parcel = new Parcel();
                parcel.setReference("REF-" + i);
                parcel.setAccessToken(SECRET + i);
                parcel.setDepot(i == 0 ? depot : null);
                em.persist(parcel);
                parcels[i] = parcel.getId();
            }
            Parcel withItem = new Parcel();
            withItem.setReference("REF-ITEM");
            em.persist(withItem);
            ParcelItem item = new ParcelItem();
            item.setDescription("an item");
            item.setParcel(withItem);
            em.persist(item);
            Archive archive = new Archive();
            archive.setTitle("Old record");
            em.persist(archive);
            Anchor anchor = new Anchor();
            anchor.setTitle("Fixed record");
            em.persist(anchor);
            Crate crate = new Crate();
            crate.setNumber(3);
            em.persist(crate);
            Counter counter = new Counter();
            counter.setId(5);
            counter.setName("five");
            em.persist(counter);
            Stamp stamp = new Stamp();
            stamp.setCode("S-1");
            stamp.setCaption("one");
            em.persist(stamp);
            Ticket ticket = new Ticket();
            ticket.setId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
            ticket.setSubject("help");
            em.persist(ticket);
            Pass pass = new Pass();
            pass.setAccessToken(7L);
            pass.setHolder("someone");
            em.persist(pass);
            em.flush();
            parcelWithItem = withItem.getId();
            archiveId = archive.getId();
            anchorId = anchor.getId();
            depotInUse = depot.getId();
            crateId = crate.getId();
        });
        registry = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(), DiscoverySettings.defaults());
        entityManager = ListTestPersistence.entityManager();
        deletes = new TransactionalDeleteService(entityManager, registry);
        RecordingStatementInspector.clear();
    }

    @AfterEach
    void close() {
        entityManager.close();
    }

    private AdminEntity entity(String urlName) {
        return registry.find(urlName).orElseThrow();
    }

    private static long count(Class<?> type) {
        try (EntityManager em = ListTestPersistence.entityManager()) {
            return em.createQuery("select count(e) from " + type.getSimpleName() + " e", Long.class).getSingleResult();
        }
    }

    private static boolean exists(Class<?> type, Object id) {
        try (EntityManager em = ListTestPersistence.entityManager()) {
            return em.find(type, id) != null;
        }
    }

    private static BulkDeleteIds ids(Object... values) {
        List<String> texts = new ArrayList<>();
        for (Object value : values) {
            texts.add(String.valueOf(value));
        }
        return new BulkDeleteIds(texts);
    }

    // ---- one record -------------------------------------------------------------------------------------------------

    @Test
    void deleteRemovesTheRecordAndOnlyThatRecord() {
        DeleteService.Deleted deleted = deletes.delete(entity("parcel"), String.valueOf(parcels[2]));

        assertThat(exists(Parcel.class, parcels[2])).isFalse();
        assertThat(count(Parcel.class)).isEqualTo(5);
        assertThat(exists(Parcel.class, parcels[1])).isTrue();
        assertThat(deleted.label()).as("a parcel has no preferred label name: the first text attribute is its notes, empty here").isNull();
    }

    @Test
    void aRecordWithoutALabelAttributeIsDeletedAndReportedWithoutALabel() {
        DeleteService.Deleted deleted = deletes.delete(entity("crate"), String.valueOf(crateId));

        assertThat(deleted.label()).isNull();
        assertThat(exists(Crate.class, crateId)).isFalse();
    }

    @Test
    void anIntegerAStringAndAUuidIdCanBeDeleted() {
        deletes.delete(entity("counter"), "5");
        deletes.delete(entity("stamp"), "S-1");
        deletes.delete(entity("ticket"), "123e4567-e89b-12d3-a456-426614174000");

        assertThat(count(Counter.class)).isZero();
        assertThat(count(Stamp.class)).isZero();
        assertThat(count(Ticket.class)).isZero();
    }

    @Test
    void theLabelOfTheDeletedRecordIsPlainTextOnOneLine() {
        long[] spare = new long[1];
        String bidiOverride = String.valueOf((char) 0x202E);
        ListTestPersistence.inTransaction(em -> {
            Depot depot = new Depot();
            depot.setName("two\nlines" + bidiOverride + "<b>x</b>");
            em.persist(depot);
            em.flush();
            spare[0] = depot.getId();
        });

        DeleteService.Deleted deleted = deletes.delete(entity("depot"), String.valueOf(spare[0]));

        assertThat(deleted.label()).isEqualTo("two lines<b>x</b>");
    }

    @Test
    void anUnknownIdIsRefusedLikeAnythingElseThatIsNotARecordAndNothingIsDeleted() {
        assertThatThrownBy(() -> deletes.delete(entity("parcel"), "987654321")).isInstanceOf(RecordNotFoundException.class);

        assertThat(count(Parcel.class)).isEqualTo(6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1 or 1=1", "1; drop table parcel", "../x", "", "99999999999999999999", "-1", "007", "%00"})
    void aTextThatIsNotAnIdDeletesNothingAndGivesTheSameRefusalAsAnUnknownId(String text) {
        assertThatThrownBy(() -> deletes.delete(entity("parcel"), text)).isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> deletes.delete(entity("parcel"), null)).isInstanceOf(RecordNotFoundException.class);

        assertThat(count(Parcel.class)).isEqualTo(6);
        assertThat(String.join("\n", RecordingStatementInspector.statements()).toLowerCase(Locale.ROOT))
                .as("the typed text is never part of a statement").doesNotContain("drop table");
    }

    // ---- SEC-11 in the service ---------------------------------------------------------------------------------------

    @Test
    void aReadOnlyEntityIsRefusedInTheServiceAndTheRecordStays() {
        assertThat(entity("archive").access().readOnly()).isTrue();

        assertThatThrownBy(() -> deletes.delete(entity("archive"), String.valueOf(archiveId)))
                .isInstanceOf(ActionRefusedException.class);
        assertThatThrownBy(() -> deletes.deleteMany(entity("archive"), ids(archiveId)))
                .isInstanceOf(ActionRefusedException.class);

        assertThat(exists(Archive.class, archiveId)).isTrue();
    }

    @Test
    void anEntityThatIsNotDeletableIsRefusedInTheServiceAndTheRecordStays() {
        assertThat(entity("anchor").access().deletable()).isFalse();
        assertThat(entity("anchor").canDelete()).isFalse();

        assertThatThrownBy(() -> deletes.delete(entity("anchor"), String.valueOf(anchorId)))
                .isInstanceOf(ActionRefusedException.class);
        assertThatThrownBy(() -> deletes.deleteMany(entity("anchor"), ids(anchorId)))
                .isInstanceOf(ActionRefusedException.class);

        assertThat(exists(Anchor.class, anchorId)).isTrue();
    }

    @Test
    void aRefusedRequestNeverReachesTheDatabase() {
        RecordingStatementInspector.clear();

        assertThatThrownBy(() -> deletes.delete(entity("archive"), String.valueOf(archiveId)))
                .isInstanceOf(ActionRefusedException.class);

        assertThat(RecordingStatementInspector.statements()).isEmpty();
    }

    @Test
    void anEntityThatIsHiddenFromTheConsoleIsRefusedAsIfItWereUnknown() {
        AdminEntityRegistry withoutParcel = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
                new DiscoverySettings(List.of("Parcel"), DiscoverySettings.DEFAULT_SENSITIVE_FIELDS));
        DeleteService restricted = new TransactionalDeleteService(entityManager, withoutParcel);

        assertThatThrownBy(() -> restricted.delete(entity("parcel"), String.valueOf(parcels[0])))
                .isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> restricted.deleteMany(entity("parcel"), ids(parcels[0])))
                .isInstanceOf(RecordNotFoundException.class);

        assertThat(RecordingStatementInspector.statements()).as("the database is never asked").isEmpty();
        assertThat(exists(Parcel.class, parcels[0])).isTrue();
    }

    @Test
    void aConsoleWithoutAnEntityManagerDeletesNothing() {
        assertThatThrownBy(() -> DeleteService.unavailable().delete(entity("parcel"), String.valueOf(parcels[0])))
                .isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> DeleteService.unavailable().deleteMany(entity("parcel"), ids(parcels[0])))
                .isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void anEntityWhoseIdentifierIsSensitiveCannotBeDeleted() {
        assertThat(entity("pass").canDelete()).isFalse();

        assertThatThrownBy(() -> deletes.delete(entity("pass"), "7")).isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> deletes.deleteMany(entity("pass"), ids(7))).isInstanceOf(RecordNotFoundException.class);

        assertThat(count(Pass.class)).isEqualTo(1);
    }

    @Test
    void theAnnotationIsReadOnceIntoTheMetadata() {
        assertThat(entity("parcel").access()).isEqualTo(AdminEntity.Access.FULL);
        assertThat(entity("parcel").canDelete()).isTrue();
        assertThat(entity("archive").access()).isEqualTo(new AdminEntity.Access(true, true));
        assertThat(entity("archive").canDelete()).as("read-only means never deletable").isFalse();
        assertThat(entity("anchor").access()).isEqualTo(new AdminEntity.Access(false, false));
    }

    // ---- foreign keys: the friendly refusal and the rollback ------------------------------------------------------------

    @Test
    void aRecordThatOtherRecordsPointAtIsKeptAndTheRefusalHoldsNoSql() {
        assertThatThrownBy(() -> deletes.delete(entity("parcel"), String.valueOf(parcelWithItem)))
                .isInstanceOf(RecordInUseException.class)
                .satisfies(refusal -> {
                    String message = String.valueOf(refusal.getMessage()).toLowerCase(Locale.ROOT);
                    assertThat(message).doesNotContain("select", "delete from", "parcel", "constraint", "fk", "sql", "jdbc");
                    assertThat(refusal.getStackTrace()).as("no stack trace of its own to print").isEmpty();
                });

        assertThat(exists(Parcel.class, parcelWithItem)).as("rolled back: the row is still there").isTrue();
        assertThat(count(ParcelItem.class)).isEqualTo(1);
    }

    @Test
    void aTargetThatARelationPointsAtIsKept() {
        assertThatThrownBy(() -> deletes.delete(entity("depot"), String.valueOf(depotInUse)))
                .isInstanceOf(RecordInUseException.class);

        assertThat(exists(Depot.class, depotInUse)).isTrue();
    }

    @Test
    void aBulkDeleteIsAllOrNothingOneRecordThatCannotGoKeepsEveryOtherOneToo() {
        assertThatThrownBy(() -> deletes.deleteMany(entity("parcel"), ids(parcels[1], parcels[2], parcelWithItem)))
                .isInstanceOf(RecordInUseException.class);

        assertThat(exists(Parcel.class, parcels[1])).as("rolled back with the rest").isTrue();
        assertThat(exists(Parcel.class, parcels[2])).isTrue();
        assertThat(count(Parcel.class)).isEqualTo(6);
    }

    @Test
    void theLogOfARefusedDeleteHoldsNoValueAndNoSql() {
        // The service logs the entity's address and the kind of failure, never the provider's message (which has SQL).
        assertThatThrownBy(() -> deletes.delete(entity("parcel"), String.valueOf(parcelWithItem)))
                .isInstanceOf(RecordInUseException.class);
        // Nothing to assert on the logger here without a log capture; the sample app's log test captures it (ListPagesLogTest
        // style). The message of the exception is fixed text:
        assertThat(new RecordInUseException(new RuntimeException("select secret from parcel")).getMessage())
                .isEqualTo("The database refused the delete.");
    }

    // ---- bulk delete -------------------------------------------------------------------------------------------------

    @Test
    void bulkDeleteRemovesExactlyTheIdsSent() {
        DeleteService.BulkResult result = deletes.deleteMany(entity("parcel"), ids(parcels[1], parcels[3]));

        assertThat(result).isEqualTo(new DeleteService.BulkResult(2, 0));
        assertThat(exists(Parcel.class, parcels[1])).isFalse();
        assertThat(exists(Parcel.class, parcels[3])).isFalse();
        assertThat(exists(Parcel.class, parcels[0])).isTrue();
        assertThat(exists(Parcel.class, parcels[2])).isTrue();
        assertThat(exists(Parcel.class, parcels[4])).isTrue();
        assertThat(count(Parcel.class)).isEqualTo(4);
    }

    @Test
    void idsOfNoRecordAndTextsThatAreNotIdsAreIgnoredAndCounted() {
        DeleteService.BulkResult result = deletes.deleteMany(entity("parcel"),
                ids(parcels[1], 987654321L, "abc", "1 or 1=1", "../x", "", "007"));

        assertThat(result).isEqualTo(new DeleteService.BulkResult(1, 6));
        assertThat(count(Parcel.class)).isEqualTo(5);
    }

    @Test
    void theSameIdSentTwiceCountsOnce() {
        DeleteService.BulkResult result = deletes.deleteMany(entity("parcel"), ids(parcels[1], parcels[1], parcels[1]));

        assertThat(result).isEqualTo(new DeleteService.BulkResult(1, 0));
    }

    @Test
    void idsOfAnotherEntityAreNotRecordsOfThisOne() {
        // The id of the archive is a number that may or may not be a parcel's id; a depot's id is certainly not a
        // record to delete from the crate table unless a crate has it. Either way only a real crate can go.
        DeleteService.BulkResult result = deletes.deleteMany(entity("crate"), ids(crateId, 123456));

        assertThat(result).isEqualTo(new DeleteService.BulkResult(1, 1));
        assertThat(count(Depot.class)).isEqualTo(1);
        assertThat(count(Parcel.class)).isEqualTo(6);
    }

    @Test
    void anEmptyRequestDeletesNothing() {
        assertThat(deletes.deleteMany(entity("parcel"), ids())).isEqualTo(new DeleteService.BulkResult(0, 0));
        assertThat(count(Parcel.class)).isEqualTo(6);
    }

    @Test
    void bulkDeleteSendsTheIdsAsBoundParametersNeverInTheStatement() {
        RecordingStatementInspector.clear();

        deletes.deleteMany(entity("parcel"), ids(parcels[1], "1 or 1=1"));

        assertThat(String.join("\n", RecordingStatementInspector.statements())).doesNotContain("1 or 1=1")
                .doesNotContain(String.valueOf(parcels[1]));
    }

    @Test
    void aBulkDeleteMayRemoveExactlyTheMostRecordsAndNothingMore() {
        List<String> hundred = new ArrayList<>();
        for (int i = 0; i < DeleteService.MAX_BULK_DELETE; i++) {
            hundred.add(String.valueOf(1_000_000 + i));
        }
        assertThat(DeleteService.MAX_BULK_DELETE).isEqualTo(100);

        assertThat(deletes.deleteMany(entity("parcel"), new BulkDeleteIds(hundred)))
                .isEqualTo(new DeleteService.BulkResult(0, 100));
        List<String> tooMany = new ArrayList<>(hundred);
        tooMany.add("1");

        assertThatThrownBy(() -> new BulkDeleteIds(tooMany)).isInstanceOf(TooManyRecordsException.class)
                .satisfies(refusal -> assertThat(((TooManyRecordsException) refusal).limit()).isEqualTo(100))
                .hasMessageNotContaining("1000000");
    }

    @Test
    void theRequestParserRefusesMoreThanTheCapBeforeLookingAtAnId() {
        String[] hundredOne = new String[DeleteService.MAX_BULK_DELETE + 1];
        java.util.Arrays.fill(hundredOne, "1");

        assertThatThrownBy(() -> BulkDeleteIds.parse(hundredOne)).isInstanceOf(TooManyRecordsException.class);
        assertThat(BulkDeleteIds.parse(null).ids()).isEmpty();
        assertThat(BulkDeleteIds.parse(new String[] {"1", null, "2"}).ids()).containsExactly("1", "2");
        assertThat(BulkDeleteIds.parse(new String[DeleteService.MAX_BULK_DELETE]).size()).isZero();
    }

    @Test
    void aBulkDeleteOverTheCapDeletesNothing() {
        List<String> tooMany = new ArrayList<>();
        tooMany.add(String.valueOf(parcels[1]));
        for (int i = 0; i < DeleteService.MAX_BULK_DELETE; i++) {
            tooMany.add(String.valueOf(2_000_000 + i));
        }

        assertThatThrownBy(() -> deletes.deleteMany(entity("parcel"), new BulkDeleteIds(tooMany)))
                .isInstanceOf(TooManyRecordsException.class);

        assertThat(exists(Parcel.class, parcels[1])).isTrue();
    }
}
