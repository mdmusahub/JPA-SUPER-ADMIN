package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Counter;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Courier;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Crate;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Dimensions;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Pass;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelItem;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Stamp;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Ticket;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Vault;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The detail page's read, against a real database: every kind of field, the masking, what is absent, what costs a
 * statement, the ids that must all look alike, and the copy seed that never holds a sensitive value.
 */
class DetailQueryServiceTest {

    private static final String SECRET = "needle-secret-value";

    private static AdminEntityRegistry registry;
    private static EntityManager entityManager;
    private static DetailQueryService details;
    private static Long fullParcel;
    private static Long bareParcel;
    private static Long longParcel;
    private static Long depotId;
    private static Long crateId;
    private static Long vaultId;
    private static UUID ticketId;
    private static final UUID WORDY = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @BeforeAll
    static void seed() {
        ListTestPersistence.empty();
        long[] ids = new long[6];
        ListTestPersistence.inTransaction(em -> {
            Depot depot = new Depot();
            depot.setName("Central depot");
            depot.setCity("Oslo");
            depot.setApiKey(SECRET + "-depot");
            em.persist(depot);
            Courier courier = new Courier();
            courier.setCallSign("Falcon");
            courier.setBadgeNumber(SECRET + "-badge");
            em.persist(courier);
            Crate crate = new Crate();
            crate.setNumber(7);
            em.persist(crate);
            Vault vault = new Vault();
            vault.setName("Hidden vault");
            em.persist(vault);

            Parcel full = new Parcel();
            full.setReference("REF-FULL");
            full.setNotes("First line\r\nSecond line\n\n\n\nThird <b>line</b>\tafter a tab");
            full.setWeight(new BigDecimal("12.50"));
            full.setPieces(3);
            full.setFragile(true);
            full.setStatus(ParcelStatus.IN_TRANSIT);
            full.setShippedOn(LocalDate.of(2026, 3, 14));
            full.setCreatedAt(LocalDateTime.of(2026, 3, 14, 9, 30));
            full.setDepot(depot);
            full.setCourier(courier);
            full.setCrate(crate);
            full.setVault(vault);
            full.setAccessToken(SECRET + "-token");
            full.setMemo(SECRET + "-memo");
            full.setInternalCode("hidden-" + SECRET);
            full.setPicture(("picture-" + SECRET).getBytes());
            Dimensions dimensions = new Dimensions();
            dimensions.setWidth(4);
            dimensions.setHeight(5);
            full.setDimensions(dimensions);
            em.persist(full);
            for (int i = 0; i < 2; i++) {
                ParcelItem item = new ParcelItem();
                item.setDescription("item " + i + " " + SECRET);
                item.setParcel(full);
                em.persist(item);
            }

            Parcel bare = new Parcel();
            bare.setReference("REF-BARE");
            em.persist(bare);

            Parcel longer = new Parcel();
            longer.setReference("REF-LONG");
            longer.setNotes("word ".repeat(780));
            Ticket wordy = new Ticket();
            wordy.setId(WORDY);
            wordy.setSubject("w".repeat(6000));
            em.persist(wordy);
            em.persist(longer);

            Stamp stamp = new Stamp();
            stamp.setCode("STAMP-1");
            stamp.setCaption("A stamp");
            em.persist(stamp);
            Ticket ticket = new Ticket();
            ticket.setId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
            ticket.setSubject("A ticket");
            em.persist(ticket);
            Counter counter = new Counter();
            counter.setId(42);
            counter.setName("A counter");
            em.persist(counter);
            Pass pass = new Pass();
            pass.setAccessToken(99L);
            pass.setHolder("Someone");
            em.persist(pass);
            em.flush();
            ids[0] = full.getId();
            ids[1] = bare.getId();
            ids[2] = longer.getId();
            ids[3] = depot.getId();
            ids[4] = crate.getId();
            ids[5] = vault.getId();
        });
        fullParcel = ids[0];
        bareParcel = ids[1];
        longParcel = ids[2];
        depotId = ids[3];
        crateId = ids[4];
        vaultId = ids[5];
        ticketId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        registry = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(), DiscoverySettings.defaults());
        entityManager = ListTestPersistence.entityManager();
        details = DetailQueryService.over(entityManager, registry);
    }

    @BeforeEach
    void forgetStatements() {
        RecordingStatementInspector.clear();
        entityManager.clear();
    }

    private static AdminEntity entity(String urlName) {
        return registry.find(urlName).orElseThrow();
    }

    private static DetailRecord detail(String urlName, Object id) {
        return details.find(entity(urlName), String.valueOf(id)).orElseThrow();
    }

    private static DetailRecord.Item item(DetailRecord record, String label) {
        return record.items().stream().filter(item -> item.label().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("no item " + label + " in " + labels(record)));
    }

    private static List<String> labels(DetailRecord record) {
        return record.items().stream().map(DetailRecord.Item::label).toList();
    }

    // ---- what is shown ----------------------------------------------------------------------------------------------

    @Test
    void theRecordHasItsIdAndItsLabelAndOneItemForEveryVisibleField() {
        DetailRecord record = detail("parcel", fullParcel);

        assertThat(record.id()).isEqualTo(String.valueOf(fullParcel));
        assertThat(record.label()).as("the first label-like text attribute: the reference is the first text attribute")
                .isNotNull();
        assertThat(labels(record)).first().isEqualTo("ID");
        assertThat(labels(record)).contains("Reference", "Notes", "Weight", "Pieces", "Fragile", "Status", "Shipped on",
                "Created at", "Depot", "Courier", "Crate", "Vault", "Access token", "Memo", "Items", "Picture",
                "Dimensions");
    }

    @Test
    void everyKindOfValueIsFormattedLikeTheListAndTheIdentifierComesFirst() {
        DetailRecord record = detail("parcel", fullParcel);

        assertThat(item(record, "ID").cell()).isEqualTo(new Cell(Cell.Type.NUMBER, String.valueOf(fullParcel),
                Cell.Tone.NONE, null, null));
        assertThat(item(record, "Reference").cell().display()).isEqualTo("REF-FULL");
        assertThat(item(record, "Weight").cell().display()).isEqualTo("12.50");
        assertThat(item(record, "Pieces").cell().display()).isEqualTo("3");
        assertThat(item(record, "Fragile").cell()).isEqualTo(new Cell(Cell.Type.BOOLEAN, "Yes", Cell.Tone.GREEN, null, null));
        assertThat(item(record, "Status").cell().display()).isEqualTo("In transit");
        assertThat(item(record, "Shipped on").cell().display()).isEqualTo("2026-03-14");
        assertThat(item(record, "Created at").cell().display()).isEqualTo("2026-03-14 09:30");
    }

    @Test
    void aRecordWithNoValuesShowsEmptyCellsAndNoRelationTargets() {
        DetailRecord record = detail("parcel", bareParcel);

        assertThat(item(record, "Notes").cell().type()).isEqualTo(Cell.Type.EMPTY);
        assertThat(item(record, "Status").cell().type()).isEqualTo(Cell.Type.EMPTY);
        for (String relation : List.of("Depot", "Courier", "Crate", "Vault")) {
            DetailRecord.Item missing = item(record, relation);
            assertThat(missing.cell().type()).as(relation).isEqualTo(Cell.Type.EMPTY);
            assertThat(missing.targetEntity()).as(relation).isNull();
            assertThat(missing.targetId()).as(relation).isNull();
        }
    }

    // ---- relations ----------------------------------------------------------------------------------------------------

    @Test
    void aRelationShowsTheTargetsLabelAndNamesTheTargetForTheLink() {
        DetailRecord record = detail("parcel", fullParcel);

        DetailRecord.Item depot = item(record, "Depot");
        assertThat(depot.cell().type()).isEqualTo(Cell.Type.RELATION);
        assertThat(depot.cell().display()).isEqualTo("Central depot");
        assertThat(depot.targetEntity()).isEqualTo("depot");
        assertThat(depot.targetId()).isEqualTo(String.valueOf(depotId));
        assertThat(item(record, "Courier").cell().display()).as("the first text attribute that is not sensitive")
                .isEqualTo("Falcon");
    }

    @Test
    void aTargetWithNoLabelShowsHashIdAndStillNamesItsEntity() {
        DetailRecord.Item crate = item(detail("parcel", fullParcel), "Crate");

        assertThat(crate.cell().display()).isEqualTo("#" + crateId);
        assertThat(crate.targetEntity()).isEqualTo("crate");
        assertThat(crate.targetId()).isEqualTo(String.valueOf(crateId));
    }

    @Test
    void aHiddenTargetShowsHashIdOnlyAndNamesNoEntitySoThereIsNoLink() {
        DetailRecord.Item vault = item(detail("parcel", fullParcel), "Vault");

        assertThat(vault.cell().display()).isEqualTo("#" + vaultId).doesNotContain("Hidden vault");
        assertThat(vault.targetEntity()).as("D-54: a hidden target gets no link").isNull();
    }

    // ---- sensitive, hidden, collections --------------------------------------------------------------------------------

    @Test
    void aSensitiveFieldIsAMaskedCellAndItsValueIsInNoPartOfTheRecord() {
        DetailRecord record = detail("parcel", fullParcel);

        assertThat(item(record, "Access token").cell()).isEqualTo(Cell.masked());
        assertThat(item(record, "Memo").cell()).as("sensitive by annotation").isEqualTo(Cell.masked());
        assertThat(record.toString()).doesNotContain(SECRET);
    }

    @Test
    void aSensitiveColumnIsNeverInTheSql() {
        detail("parcel", fullParcel);

        String sql = String.join("\n", RecordingStatementInspector.statements()).toLowerCase(Locale.ROOT);
        assertThat(sql).doesNotContain("access_token", "memo", "api_key", "badge_number");
    }

    @Test
    void aHiddenFieldIsAbsentAndItsValueIsNowhere() {
        DetailRecord record = detail("parcel", fullParcel);

        assertThat(labels(record)).doesNotContain("Internal code");
        assertThat(record.toString()).doesNotContain("hidden-" + SECRET);
        assertThat(String.join("\n", RecordingStatementInspector.statements()).toLowerCase(Locale.ROOT))
                .doesNotContain("internal_code");
    }

    @Test
    void aCollectionAnEmbeddedValueAndBinaryDataShowOnlyTheirLabel() {
        DetailRecord record = detail("parcel", fullParcel);

        for (String label : List.of("Items", "Dimensions", "Picture")) {
            assertThat(item(record, label).cell().display()).as(label).isEqualTo("(not shown)");
        }
        assertThat(record.toString()).doesNotContain("item 0", "picture-");
        String sql = String.join("\n", RecordingStatementInspector.statements()).toLowerCase(Locale.ROOT);
        assertThat(sql).as("the collection, the picture and the embedded columns are not selected")
                .doesNotContain("parcelitem", "picture", "width", "height");
    }

    // ---- cost and what is loaded -------------------------------------------------------------------------------------

    @Test
    void theRecordCostsOneStatementWhateverItsRelations() {
        detail("parcel", fullParcel);

        assertThat(RecordingStatementInspector.statements()).hasSize(1);
        String sql = RecordingStatementInspector.statements().get(0).toLowerCase(Locale.ROOT);
        assertThat(sql.split("left join", -1).length - 1).as("one join per relation, no more").isEqualTo(4);
    }

    @Test
    void noEntityAndNoCollectionIsLoadedIntoThePersistenceContext() {
        detail("parcel", fullParcel);

        assertThat(entityManager.unwrap(Session.class).getStatistics().getEntityCount()).isZero();
        assertThat(entityManager.unwrap(Session.class).getStatistics().getCollectionCount()).isZero();
    }

    @Test
    void theFilterChoiceQueryIsNeverRun() {
        detail("parcel", fullParcel);

        // The list issues a small statement per filterable relation; the detail page is one statement on parcel only.
        assertThat(RecordingStatementInspector.statements()).allSatisfy(sql ->
                assertThat(sql.toLowerCase(Locale.ROOT)).contains("from parcel"));
    }

    // ---- text ------------------------------------------------------------------------------------------------------

    @Test
    void textKeepsItsLineBreaksButNotTheOnesBeyondTwoAndMarkupIsKeptAsPlainText() {
        String notes = item(detail("parcel", fullParcel), "Notes").cell().display();

        assertThat(notes).isEqualTo("First line\nSecond line\n\nThird <b>line</b> after a tab");
    }

    @Test
    void aLongTextBelowTheLimitIsShownInFullAndAVeryLongOneIsCutWithAnEllipsis() {
        String below = item(detail("parcel", longParcel), "Notes").cell().display();
        String subject = item(detail("ticket", WORDY), "Subject").cell().display();

        assertThat(below).doesNotEndWith("…").hasSize(779 * 5 + 4);
        assertThat(subject).endsWith("…");
        assertThat(subject.codePointCount(0, subject.length())).isEqualTo(CellFormatter.MAX_DETAIL_TEXT + 1);
    }

    // ---- ids that must look alike ------------------------------------------------------------------------------------

    @Test
    void anUnknownIdIsNothing() {
        assertThat(details.find(entity("parcel"), "987654321")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1 or 1=1", "../x", "", "99999999999999999999", "-1", "0", "007", "1.0", "%00", "<script>"})
    void aTextThatIsNotAnIdIsNothingJustLikeAnUnknownId(String text) {
        assertThat(details.find(entity("parcel"), text)).isEmpty();
        assertThat(details.find(entity("parcel"), null)).isEmpty();
    }

    @Test
    void anIntegerIdAStringIdAndAUuidIdAreFound() {
        assertThat(details.find(entity("counter"), "42")).isPresent();
        assertThat(details.find(entity("counter"), "43")).isEmpty();
        assertThat(details.find(entity("stamp"), "STAMP-1")).isPresent();
        assertThat(details.find(entity("stamp"), "stamp-1")).as("the id is case-sensitive text").isEmpty();
        assertThat(details.find(entity("ticket"), ticketId.toString())).isPresent();
        assertThat(details.find(entity("ticket"), ticketId.toString().toUpperCase(Locale.ROOT))).isPresent();
        assertThat(details.find(entity("ticket"), "not-a-uuid")).isEmpty();
        assertThat(details.find(entity("ticket"), "1-1-1-1-1")).isEmpty();
    }

    @Test
    void anEntityThatIsNotInTheConsoleGivesNothingExactlyLikeAnUnknownId() {
        AdminEntityRegistry withoutParcel = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
                new DiscoverySettings(List.of("Parcel"), DiscoverySettings.DEFAULT_SENSITIVE_FIELDS));
        DetailQueryService restricted = DetailQueryService.over(entityManager, withoutParcel);

        assertThat(withoutParcel.find("parcel")).isEmpty();
        assertThat(restricted.find(entity("parcel"), String.valueOf(fullParcel))).as("hidden: same as unknown").isEmpty();
        assertThat(restricted.copySeed(entity("parcel"), String.valueOf(fullParcel))).isEmpty();
        assertThat(RecordingStatementInspector.statements()).as("the database is never asked").isEmpty();
    }

    @Test
    void aConsoleWithoutAnEntityManagerFindsNothing() {
        assertThat(DetailQueryService.unavailable().find(entity("parcel"), String.valueOf(fullParcel))).isEmpty();
        assertThat(DetailQueryService.unavailable().copySeed(entity("parcel"), String.valueOf(fullParcel))).isEmpty();
    }

    @Test
    void anEntityWhoseIdentifierIsSensitiveHasNoDetailPage() {
        AdminEntity pass = entity("pass");

        assertThat(pass.addressable()).isFalse();
        assertThat(details.find(pass, "99")).isEmpty();
        assertThat(details.copySeed(pass, "99")).isEmpty();
    }

    // ---- the copy seed -----------------------------------------------------------------------------------------------

    @Test
    void theCopySeedHoldsTheCopyableFieldsAsPlainText() {
        CopySeed seed = details.copySeed(entity("parcel"), String.valueOf(fullParcel)).orElseThrow();

        assertThat(seed.sourceId()).isEqualTo(String.valueOf(fullParcel));
        assertThat(seed.values()).extracting(CopySeed.Value::key).containsExactlyInAnyOrder("reference", "notes", "weight",
                "pieces", "fragile", "status", "shippedOn", "createdAt", "depot", "courier", "crate");
        assertThat(value(seed, "weight")).isEqualTo("12.50");
        assertThat(value(seed, "status")).isEqualTo("IN_TRANSIT");
        assertThat(value(seed, "shippedOn")).isEqualTo("2026-03-14");
        assertThat(value(seed, "depot")).as("a relation is copied as the target's id").isEqualTo(String.valueOf(depotId));
    }

    @Test
    void theCopySeedNeverHoldsASensitiveValueTheIdentifierAHiddenFieldACollectionOrBinaryData() {
        CopySeed seed = details.copySeed(entity("parcel"), String.valueOf(fullParcel)).orElseThrow();

        assertThat(seed.values()).extracting(CopySeed.Value::key).doesNotContain("id", "accessToken", "memo",
                "internalCode", "items", "picture", "dimensions", "vault");
        assertThat(seed.toString()).doesNotContain(SECRET);
        String sql = String.join("\n", RecordingStatementInspector.statements()).toLowerCase(Locale.ROOT);
        assertThat(sql).doesNotContain("access_token", "memo", "internal_code", "picture", "api_key");
    }

    @Test
    void aRecordWithoutValuesGivesASeedWithoutThem() {
        CopySeed seed = details.copySeed(entity("parcel"), String.valueOf(bareParcel)).orElseThrow();

        assertThat(seed.values()).extracting(CopySeed.Value::key).containsExactlyInAnyOrder("reference", "pieces", "fragile");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1 or 1=1", "../x", "", "987654321"})
    void theCopySeedOfAnUnknownOrMalformedIdIsNothing(String text) {
        assertThat(details.copySeed(entity("parcel"), text)).isEmpty();
        assertThat(details.copySeed(entity("parcel"), null)).isEmpty();
    }

    private static String value(CopySeed seed, String key) {
        Optional<CopySeed.Value> found = seed.values().stream().filter(v -> v.key().equals(key)).findFirst();
        return found.orElseThrow(() -> new AssertionError("no value " + key)).text();
    }
}
