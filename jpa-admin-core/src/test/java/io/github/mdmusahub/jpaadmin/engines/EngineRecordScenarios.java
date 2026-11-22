package io.github.mdmusahub.jpaadmin.engines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.service.BulkDeleteIds;
import io.github.mdmusahub.jpaadmin.service.DeleteService;
import io.github.mdmusahub.jpaadmin.service.DetailQueryService;
import io.github.mdmusahub.jpaadmin.service.DetailRecord;
import io.github.mdmusahub.jpaadmin.service.ExportService;
import io.github.mdmusahub.jpaadmin.service.ExportSettings;
import io.github.mdmusahub.jpaadmin.service.FieldMessages;
import io.github.mdmusahub.jpaadmin.service.InvalidRecordException;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.service.RecordInUseException;
import io.github.mdmusahub.jpaadmin.service.RecordNotFoundException;
import io.github.mdmusahub.jpaadmin.service.RecordWriteService;
import io.github.mdmusahub.jpaadmin.testsupport.Csv;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.TransactionalDeleteService;
import io.github.mdmusahub.jpaadmin.testsupport.TransactionalRecordWriteService;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Anchor;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Folio;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Detail, export, create, edit, the stale form and delete on a real database (Phase 14a, D-128). The same checks the H2 tests of
 * Phases 7, 8 and 11 make, run on every engine: what is stored comes back the same, a refusal is in the console's words and holds
 * no SQL, a refused write leaves no row behind, and a database that aborts a transaction after an error (PostgreSQL) leaves the
 * next request unharmed.
 */
public abstract class EngineRecordScenarios extends EngineListScenarios {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC);

    // ---- detail -------------------------------------------------------------------------------------------------------------

    private DetailRecord detail(String urlName, Object id) {
        return DetailQueryService.over(entityManager, registry).find(entity(urlName), String.valueOf(id)).orElseThrow();
    }

    private static DetailRecord.Item item(DetailRecord record, String label) {
        return record.items().stream().filter(item -> item.label().equalsIgnoreCase(label)).findFirst()
                .orElseThrow(() -> new AssertionError("no field labelled " + label + " in " + record.items().stream()
                        .map(DetailRecord.Item::label).toList()));
    }

    @Test
    void theDetailPageShowsTheStoredValuesTheRelationAsALinkAndTheSecretAsAMask() {
        Parcel stored = seeded.get(13);
        DetailRecord record = detail("parcel", stored.getId());

        assertThat(record.id()).isEqualTo(String.valueOf(stored.getId()));
        assertThat(item(record, "Reference").cell().display()).isEqualTo("PKG-014");
        assertThat(item(record, "Notes").cell().display()).isEqualTo("Zürich café 東京");
        assertThat(new java.math.BigDecimal(item(record, "Weight").cell().display())).isEqualByComparingTo("21");
        assertThat(item(record, "Depot").cell().display()).isEqualTo("North hub");
        assertThat(item(record, "Depot").targetEntity()).isEqualTo("depot");
        assertThat(item(record, "Depot").targetId()).isEqualTo(String.valueOf(north.getId()));
        assertThat(item(record, "Access token").cell().type()).isEqualTo(io.github.mdmusahub.jpaadmin.web.view.Cell.Type.MASKED);
        assertThat(record.items().stream().map(item -> item.cell().display()).toList())
                .as("a secret is on no detail page").noneMatch(text -> text != null && text.contains(SECRET_WORD));
    }

    @Test
    void anUnknownOrMalformedIdentifierHasNoDetailPage() {
        DetailQueryService details = DetailQueryService.over(entityManager, registry);

        assertThat(details.find(parcelEntity(), "999999")).isEmpty();
        assertThat(details.find(parcelEntity(), "abc")).isEmpty();
        assertThat(details.find(parcelEntity(), "1 or 1=1")).isEmpty();
    }

    // ---- export -------------------------------------------------------------------------------------------------------------

    private ExportService exports(long cap) {
        return ExportService.over(lists, registry, new ExportSettings(true, cap, false), CLOCK);
    }

    private static String file(ExportService.Export export) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Writer out = new OutputStreamWriter(bytes, StandardCharsets.UTF_8);
        export.writeTo(out);
        out.flush();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void theExportIsTheListsOwnRowsInTheListsOwnOrder() throws IOException {
        AdminEntity parcel = parcelEntity();
        Map<String, List<String>> filters = Map.of("filter.status", List.of("DELIVERED"));
        ListQuery query = lists.parse(parcel, null, "weight", "desc", null, "100")
                .withParsedFilters(lists.parseFilters(parcel, filters).filters());
        io.github.mdmusahub.jpaadmin.service.ListPage page = lists.list(parcel, query);

        List<List<String>> table = Csv.parse(file(exports(50_000).open(parcel, query)));

        int reference = table.get(0).indexOf("Reference");
        assertThat(reference).as("the header names the column: " + table.get(0)).isGreaterThanOrEqualTo(0);
        assertThat(table.subList(1, table.size()).stream().map(row -> row.get(reference)).toList())
                .isEqualTo(page.rows().stream().map(row -> cell(page, row, "reference").display()).toList());
        assertThat(table.get(0)).as("no secret and no hidden column in the header")
                .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("token") || name.contains("Internal"));
        assertThat(String.join("|", table.stream().flatMap(List::stream).toList())).doesNotContain(SECRET_WORD);
    }

    @Test
    void aFileOfMoreThanOnePageIsReadInPagesOf500AndCutAtTheCap() throws IOException {
        rowsWillChange();
        seedAnchors(1_203);
        AdminEntity anchor = entity("anchor");

        RecordingStatementInspector.clear();
        ExportService.Export whole = exports(50_000).open(anchor, ListQuery.defaults());
        List<List<String>> table = Csv.parse(file(whole));

        assertThat(table).as("header and every row").hasSize(1_204);
        assertThat(whole.truncated()).isFalse();
        assertThat(selectsFrom("anchor")).as("a count and three pages of 500, 500 and 203").isEqualTo(4);
        assertThat(table.get(1).get(table.get(0).indexOf("Title"))).as("a cell that starts like a formula is escaped")
                .isEqualTo("'=1+1");

        RecordingStatementInspector.clear();
        ExportService.Export cut = exports(1_000).open(anchor, ListQuery.defaults());
        List<List<String>> short_ = Csv.parse(file(cut));

        assertThat(short_).hasSize(1_001);
        assertThat(cut.truncated()).isTrue();
        assertThat(cut.matching()).isEqualTo(1_203);
        assertThat(selectsFrom("anchor")).as("a count and two pages").isEqualTo(3);
        List<String> firstTitles = new ArrayList<>(table.subList(1, 1_001).stream().map(row -> row.get(1)).toList());
        assertThat(short_.subList(1, 1_001).stream().map(row -> row.get(1)).toList())
                .as("the cut file is the first rows of the whole one").isEqualTo(firstTitles);
    }

    private void seedAnchors(int count) {
        EnginePersistence.inTransaction(factory, em -> {
            for (int i = 1; i <= count; i++) {
                Anchor anchor = new Anchor();
                anchor.setTitle(i == 1 ? "=1+1" : "Anchor " + i);
                em.persist(anchor);
                if (i % 200 == 0) {
                    em.flush();
                    em.clear();
                }
            }
        });
    }

    private static long selectsFrom(String table) {
        return RecordingStatementInspector.statements().stream().map(sql -> sql.toLowerCase(Locale.ROOT))
                .filter(sql -> sql.stripLeading().startsWith("select") && sql.contains("from " + table + " ")).count();
    }

    // ---- create, edit and the stale form --------------------------------------------------------------------------------------

    private RecordWriteService writes() {
        return new TransactionalRecordWriteService(entityManager, registry);
    }

    private static Function<String, String> posted(Map<String, String> values) {
        return values::get;
    }

    private Map<String, String> validFolio() {
        Map<String, String> values = new HashMap<>();
        values.put("title", "A new folio");
        values.put("code", "N-1");
        values.put("quantity", "7");
        values.put("urgent", "true");
        values.put("status", "DELIVERED");
        values.put("due", "2026-11-17");
        values.put("openedAt", "2026-11-17T10:30");
        values.put("home", String.valueOf(north.getId()));
        values.put("courier", String.valueOf(falcon.getId()));
        values.put("secretNote", "typed-secret");
        return values;
    }

    private Long seedFolio() {
        rowsWillChange();
        Long[] id = new Long[1];
        EnginePersistence.inTransaction(factory, em -> {
            Folio existing = new Folio();
            existing.setTitle("Existing folio");
            existing.setCode("E-1");
            existing.setQuantity(3);
            existing.setStatus(ParcelStatus.PENDING);
            existing.setDue(LocalDate.of(2026, 3, 4));
            existing.setOpenedAt(LocalDateTime.of(2026, 3, 4, 5, 6, 7, 123_456_789));
            existing.setHome(em.getReference(Depot.class, north.getId()));
            existing.setSecretNote("old-secret-value");
            existing.setInternalNote("internal");
            em.persist(existing);
            em.flush();
            id[0] = existing.getId();
        });
        return id[0];
    }

    /** What a folio holds, read in a session of its own and copied out of it. */
    private record Stored(String title, String code, int quantity, boolean urgent, ParcelStatus status, LocalDate due,
                          LocalDateTime openedAt, Long home, Long courier, String secretNote, long revision) {
    }

    private Stored stored(Long id) {
        return read(em -> {
            Folio folio = em.find(Folio.class, id);
            return folio == null ? null : new Stored(folio.getTitle(), folio.getCode(), folio.getQuantity(), folio.isUrgent(),
                    folio.getStatus(), folio.getDue(), folio.getOpenedAt(), folio.getHome().getId(),
                    folio.getCourier() == null ? null : folio.getCourier().getId(), folio.getSecretNote(), folio.getRevision());
        });
    }

    private InvalidRecordException refused(Map<String, String> values) {
        return (InvalidRecordException) catchThrowable(() -> writes().create(entity("folio"), posted(values)));
    }

    private static Throwable catchThrowable(Runnable work) {
        try {
            work.run();
        } catch (RuntimeException failure) {
            return failure;
        }
        throw new AssertionError("the call was expected to be refused");
    }

    @Test
    void aCreatedRecordComesBackAsItWasTypedWithTheHostsSetterApplied() {
        rowsWillChange();
        RecordWriteService.Saved saved = writes().create(entity("folio"), posted(validFolio()));

        Stored back = stored(Long.valueOf(saved.id()));
        assertThat(back.title()).isEqualTo("A new folio");
        assertThat(back.code()).isEqualTo("N-1");
        assertThat(back.quantity()).isEqualTo(7);
        assertThat(back.urgent()).isTrue();
        assertThat(back.status()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(back.due()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(back.openedAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 10, 30));
        assertThat(back.home()).isEqualTo(north.getId());
        assertThat(back.courier()).isEqualTo(falcon.getId());
        assertThat(back.secretNote()).as("the host's own setter ran").isEqualTo("stored:typed-secret");
        assertThat(back.revision()).isZero();
        assertThat(saved.label()).as("a message names the record's label").isEqualTo("A new folio");
    }

    @Test
    void anEditWritesOnlyWhatWasPostedAndRaisesTheVersion() {
        Long id = seedFolio();

        writes().update(entity("folio"), String.valueOf(id), "0", posted(Map.of("title", "Changed")));

        Stored back = stored(id);
        assertThat(back.title()).isEqualTo("Changed");
        assertThat(back.code()).as("a field that was not sent is unchanged").isEqualTo("E-1");
        assertThat(back.quantity()).isEqualTo(3);
        assertThat(back.secretNote()).as("a blank secret keeps the stored one").isEqualTo("stored:old-secret-value");
        assertThat(back.revision()).isEqualTo(1);
    }

    @Test
    void aStaleFormIsRefusedWithAPlainMessageAndNothingIsSaved() {
        Long id = seedFolio();
        writes().update(entity("folio"), String.valueOf(id), "0", posted(Map.of("title", "First change")));

        assertThatThrownBy(() -> writes().update(entity("folio"), String.valueOf(id), "0", posted(Map.of("title", "Second"))))
                .isInstanceOfSatisfying(InvalidRecordException.class, refusal -> {
                    assertThat(refusal.formErrors()).containsExactly(FieldMessages.stale());
                    assertThat(refusal.fieldErrors()).isEmpty();
                    assertThat(refusal.getCause()).isNull();
                    assertThat(refusal.getStackTrace()).as("no stack trace to print").isEmpty();
                });

        assertThat(stored(id).title()).isEqualTo("First change");
        assertThat(stored(id).revision()).isEqualTo(1);
    }

    @Test
    void aFormWithNoVersionForAVersionedEntityIsStaleToo() {
        Long id = seedFolio();

        assertThatThrownBy(() -> writes().update(entity("folio"), String.valueOf(id), null, posted(Map.of("title", "x"))))
                .isInstanceOfSatisfying(InvalidRecordException.class,
                        refusal -> assertThat(refusal.formErrors()).containsExactly(FieldMessages.stale()));
        assertThat(stored(id).title()).isEqualTo("Existing folio");
    }

    @Test
    void aChangeThatRacedThisOneIsTheSameStaleAnswerOnEveryDatabase() {
        // The form is current when the service loads the record; another user commits a change while the values are read, so the
        // database's own version check (UPDATE ... WHERE version = ?) is what refuses, and it must read as the same plain message.
        Long id = seedFolio();
        AtomicBoolean raced = new AtomicBoolean();
        Map<String, String> values = Map.of("title", "My change");

        assertThatThrownBy(() -> writes().update(entity("folio"), String.valueOf(id), "0", name -> {
            if (raced.compareAndSet(false, true)) {
                EnginePersistence.inTransaction(factory, other -> other.find(Folio.class, id).setTitle("Their change"));
            }
            return values.get(name);
        })).isInstanceOfSatisfying(InvalidRecordException.class,
                refusal -> assertThat(refusal.formErrors()).containsExactly(FieldMessages.stale()));

        assertThat(stored(id).title()).isEqualTo("Their change");
    }

    @Test
    void aDatabaseConstraintBecomesOneSentenceForTheFormWithNoSqlInIt() {
        seedFolio();
        Map<String, String> values = validFolio();
        values.put("code", "E-1"); // the seeded folio has it, and the column is unique

        InvalidRecordException refusal = refused(values);

        assertThat(refusal.fieldErrors()).isEmpty();
        assertThat(refusal.formErrors()).containsExactly(FieldMessages.refused());
        assertThat(refusal.getMessage().toLowerCase(Locale.ROOT)).doesNotContain("e-1", "unique", "folio", "duplicate", "constraint",
                "insert", "violat");
        assertThat(refusal.getCause()).isNull();
        assertThat(count(Folio.class)).as("nothing was added").isEqualTo(1);
    }

    @Test
    void aWriteThatFollowsARefusedOneWorksOnADatabaseThatAbortsTheTransaction() {
        seedFolio();
        Map<String, String> clash = validFolio();
        clash.put("code", "E-1");
        refused(clash);

        RecordWriteService.Saved saved = writes().create(entity("folio"), posted(validFolio()));

        assertThat(stored(Long.valueOf(saved.id()))).isNotNull();
        assertThat(count(Folio.class)).isEqualTo(2);
    }

    @Test
    void aTimeKeptWithMoreThanMicrosecondsIsStoredToTheMicrosecondOnEveryDatabase() {
        Long id = seedFolio(); // opened at 05:06:07.123456789

        LocalDateTime back = stored(id).openedAt();

        assertThat(back.withNano(0)).isEqualTo(LocalDateTime.of(2026, 3, 4, 5, 6, 7));
        assertThat(Math.abs(back.getNano() - 123_456_789)).as("stored to the microsecond: %s", back).isLessThan(1_000);
        assertThat(item(detail("folio", id), "Opened at").cell().display())
                .as("the page shows the minute, whatever the database kept").isEqualTo("2026-03-04 05:06");
    }

    // ---- delete -------------------------------------------------------------------------------------------------------------

    private DeleteService deletes() {
        return new TransactionalDeleteService(entityManager, registry);
    }

    private boolean exists(Class<?> type, Object id) {
        return read(em -> em.find(type, id) != null);
    }

    private Long newDepot(String name) {
        Long[] id = new Long[1];
        EnginePersistence.inTransaction(factory, em -> {
            Depot depot = new Depot();
            depot.setName(name);
            em.persist(depot);
            em.flush();
            id[0] = depot.getId();
        });
        return id[0];
    }

    @Test
    void aRecordNobodyPointsAtIsDeletedAndTheNextReadSeesIt() {
        rowsWillChange();
        Long free = newDepot("Free depot");

        DeleteService.Deleted deleted = deletes().delete(entity("depot"), String.valueOf(free));

        assertThat(deleted.label()).isEqualTo("Free depot");
        assertThat(exists(Depot.class, free)).isFalse();
    }

    @Test
    void aRecordThatOtherRecordsPointAtIsKeptAndTheRefusalHoldsNoSql() {
        rowsWillChange();

        assertThatThrownBy(() -> deletes().delete(entity("depot"), String.valueOf(north.getId())))
                .isInstanceOfSatisfying(RecordInUseException.class, refusal -> {
                    assertThat(String.valueOf(refusal.getMessage()).toLowerCase(Locale.ROOT)).doesNotContain("select",
                            "delete from", "depot", "constraint", "foreign", "sql", "jdbc", "violat");
                    assertThat(refusal.getStackTrace()).as("no stack trace of its own to print").isEmpty();
                });

        assertThat(exists(Depot.class, north.getId())).as("rolled back: the row is still there").isTrue();
        assertThat(count(Parcel.class)).isEqualTo(PARCELS);
    }

    @Test
    void aBulkDeleteIsAllOrNothingOneRecordThatCannotGoKeepsEveryOtherOneToo() {
        rowsWillChange();
        Long free = newDepot("Free depot");

        assertThatThrownBy(() -> deletes().deleteMany(entity("depot"),
                new BulkDeleteIds(List.of(String.valueOf(free), String.valueOf(north.getId())))))
                .isInstanceOf(RecordInUseException.class);

        assertThat(exists(Depot.class, free)).as("rolled back with the rest").isTrue();
        assertThat(exists(Depot.class, north.getId())).isTrue();
    }

    @Test
    void aBulkDeleteOfFreeRecordsRemovesThemAllAndAnUnknownOneIsCounted() {
        rowsWillChange();
        Long one = newDepot("Free one");
        Long two = newDepot("Free two");

        DeleteService.BulkResult result = deletes().deleteMany(entity("depot"),
                new BulkDeleteIds(List.of(String.valueOf(one), String.valueOf(two), "999999")));

        assertThat(result.deleted()).isEqualTo(2);
        assertThat(result.notDeleted()).isEqualTo(1);
        assertThat(exists(Depot.class, one)).isFalse();
        assertThat(exists(Depot.class, two)).isFalse();
    }

    @Test
    void aRefusedDeleteDoesNotHarmTheNextRequestOnADatabaseThatAbortsTheTransaction() {
        rowsWillChange();
        assertThatThrownBy(() -> deletes().delete(entity("depot"), String.valueOf(north.getId())))
                .isInstanceOf(RecordInUseException.class);
        Long free = newDepot("Free depot");

        deletes().delete(entity("depot"), String.valueOf(free));

        assertThat(exists(Depot.class, free)).isFalse();
        assertThat(lists.list(parcelEntity(), ListQuery.defaults()).total()).isEqualTo(PARCELS);
    }

    @Test
    void anUnknownRecordIsTheConsolesOwnNotFound() {
        assertThatThrownBy(() -> deletes().delete(entity("depot"), "999999")).isInstanceOf(RecordNotFoundException.class);
    }
}
