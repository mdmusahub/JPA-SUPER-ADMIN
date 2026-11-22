package io.github.mdmusahub.jpaadmin.engines;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.testsupport.PinnedTestZone;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Courier;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Crate;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Dimensions;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelItem;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Vault;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The one shared base of the engine tests (Phase 14a, D-128). It starts the database <b>once per test class</b>, builds the
 * persistence unit on it, and gives every test the same 55 parcels, so the three engine classes (H2, PostgreSQL, MySQL) run the
 * very same scenarios and a difference between engines shows as a failure that names the engine.
 *
 * <p>What happens when there is no Docker is {@link EngineGate}'s decision: a skip with a reason on a laptop, a failure when
 * {@code JPA_ADMIN_REQUIRE_ENGINES} is true.
 *
 * <p>The seed is the Phase 4 list model ({@code Parcel} with its depots, couriers, crates and vault) so the expected values can be
 * computed in Java from the objects that were stored, as the H2 list tests do.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({EngineNamesFailures.class, EngineSkipLedger.class})
public abstract class EngineTestBase {

    /** 55 parcels: pages of 25 are 25, 25 and 5, and pages of 10 end with a partial page of 5. */
    protected static final int PARCELS = 55;

    protected static final String SECRET_WORD = "hunter2-searchword";

    /**
     * The zone every test of this module runs in, pinned once for the whole run by {@link PinnedTestZone} (which says why it is not
     * set here, class by class: H2 keeps the zone it first saw). A fixed, modern, non-UTC zone: the same on a laptop and on CI, and
     * one that exposes a zone mistake (a JVM in UTC hides it). It is also what the PostgreSQL driver sends as the session's time
     * zone, and the Debian 13 based official image no longer knows the legacy name some JVMs report for the same zone
     * ({@code Asia/Calcutta}), so a connection would be refused (found by the first run of this class, Phase 14a).
     */
    protected static final String ZONE = PinnedTestZone.ID;

    private EngineDatabase database;
    private EngineGate.Decision gate = new EngineGate.Decision(EngineGate.Verdict.RUN, "");
    private final java.util.List<String> aborted = new java.util.concurrent.CopyOnWriteArrayList<>();
    private String version;
    protected EntityManagerFactory factory;
    protected AdminEntityRegistry registry;

    protected final List<Parcel> seeded = new ArrayList<>();
    protected EntityManager entityManager;
    protected ListQueryService lists;
    protected Depot north;
    protected Depot south;
    protected Courier falcon;

    /** Set by a test that writes: the next test then starts from a fresh set of rows. A read-only test leaves the rows alone. */
    private boolean rowsChanged = true;

    /** The engine this class runs on. */
    protected abstract EngineDatabase newDatabase();

    /** The engine's name and version, in front of every failure. */
    public String engineLabel() {
        return version != null ? version : database != null ? database.name() : "unknown engine";
    }

    // ---- the engine, once per class ---------------------------------------------------------------------------------------

    @BeforeAll
    void startEngine() {
        PinnedTestZone.requirePinned(); // the zone is pinned for the whole run, never changed here (see PinnedTestZone)
        database = newDatabase();
        gate = database.needsDocker()
                ? EngineGate.decide(database.name(), database.testClass(), EngineGate.dockerAvailable(), EngineGate.required())
                : new EngineGate.Decision(EngineGate.Verdict.RUN, "");
        if (gate.verdict() == EngineGate.Verdict.FAIL) {
            EngineGate.apply(gate); // the class fails, and says which engine and why
        }
        if (gate.verdict() == EngineGate.Verdict.SKIP) {
            // Said once in the build log, and again as the reason of every skipped test (see seedTheParcels): a class that is
            // aborted in @BeforeAll shows up in the report as "0 tests run", with no skip and no reason.
            System.err.println(gate.reason());
            return;
        }
        database.start();
        version = database.version();
        factory = EnginePersistence.open(database);
        registry = EntityScanner.scan(factory.getMetamodel(), DiscoverySettings.defaults());
    }

    /** Called by {@link EngineSkipLedger} for every scenario that was aborted. */
    void recordAbort(String reason) {
        aborted.add(reason);
    }

    @AfterAll
    void stopEngine() {
        String silentSkip = EngineSkipLedger.problem(gate, engineLabel(), aborted);
        try {
            closeTheEngine();
        } finally {
            if (silentSkip != null) {
                throw new AssertionError(silentSkip);
            }
        }
    }

    private void closeTheEngine() {
        try {
            if (factory != null) {
                factory.close();
            }
        } finally {
            if (database != null) {
                database.close();
            }
        }
    }


    // ---- the data, before each test ---------------------------------------------------------------------------------------

    @BeforeEach
    void seedTheParcels() {
        EngineGate.apply(gate); // a skip is reported test by test, each with its reason (never "0 tests run")
        if (rowsChanged) {
            reseed();
            rowsChanged = false;
        }
        entityManager = factory.createEntityManager();
        lists = ListQueryService.over(entityManager, registry);
        RecordingStatementInspector.clear();
    }

    /** Call first in a test that adds, changes or deletes rows: the rows are made again before the next test. */
    protected void rowsWillChange() {
        rowsChanged = true;
    }

    private void reseed() {
        EnginePersistence.empty(factory);
        seeded.clear();
        EnginePersistence.inTransaction(factory, em -> {
            north = depot(em, "North hub", "Leeds");
            south = depot(em, "South hub", "Bath");
            Courier courier = new Courier();
            courier.setBadgeNumber("BADGE-SECRET-1");
            courier.setCallSign("Falcon");
            em.persist(courier);
            falcon = courier;
            Crate crate = new Crate();
            crate.setNumber(42);
            em.persist(crate);
            Vault vault = new Vault();
            vault.setName("Deep vault name");
            em.persist(vault);
            for (int i = 1; i <= PARCELS; i++) {
                Parcel parcel = new Parcel();
                parcel.setReference(String.format("PKG-%03d", i));
                parcel.setNotes(notes(i));
                parcel.setWeight(new BigDecimal("1.5").multiply(BigDecimal.valueOf(i)));
                parcel.setPieces(i % 7);
                parcel.setFragile(i % 3 == 0);
                parcel.setStatus(ParcelStatus.values()[i % 4]);
                parcel.setShippedOn(LocalDate.of(2026, 1, 1).plusDays((i * 5L) % 40));
                parcel.setCreatedAt(LocalDateTime.of(2026, 3, 1, 8, 0).plusMinutes(i * 37L % 500));
                parcel.setDepot(i % 2 == 0 ? north : south);
                parcel.setCourier(i % 2 == 0 ? courier : null);
                parcel.setCrate(i % 5 == 0 ? crate : null);
                parcel.setVault(i % 11 == 0 ? vault : null);
                parcel.setAccessToken(SECRET_WORD + "-" + i);
                parcel.setMemo("memo-secret-" + i);
                parcel.setInternalCode("internal-" + i);
                parcel.setPicture(new byte[] {1, 2, 3});
                Dimensions size = new Dimensions();
                size.setWidth(i);
                size.setHeight(2 * i);
                parcel.setDimensions(size);
                em.persist(parcel);
                ParcelItem item = new ParcelItem();
                item.setDescription("item of " + i);
                item.setParcel(parcel);
                em.persist(item);
                seeded.add(parcel);
            }
        });
    }

    @AfterEach
    void closeTheEntityManager() {
        if (entityManager != null && entityManager.isOpen()) {
            entityManager.close();
        }
    }

    private static Depot depot(EntityManager em, String name, String city) {
        Depot depot = new Depot();
        depot.setName(name);
        depot.setCity(city);
        depot.setApiKey("DEPOT-KEY-SECRET");
        em.persist(depot);
        return depot;
    }

    /** Special texts at fixed rows: syntax characters, unicode, a long text with line breaks. */
    protected static String notes(int i) {
        return switch (i) {
            case 7 -> "see http://x for details";
            case 8 -> "100% sure";
            case 9 -> "a_b";
            case 11 -> "it's \"quoted\"";
            case 12 -> "wow! really";
            case 13 -> "C:\\temp\\file";
            case 14 -> "Zürich café 東京";
            case 15 -> "line one\nline two\tTabbed";
            case 20 -> "LongText " + "word ".repeat(80) + "end";
            case 21 -> "x".repeat(200) + "UNBROKEN";
            default -> "Handle with care #" + i;
        };
    }

    /** Three more parcels with nothing filled in, so every nullable column has missing values. */
    protected void addParcelsWithMissingValues() {
        rowsWillChange();
        EnginePersistence.inTransaction(factory, em -> {
            for (int i = 0; i < 3; i++) {
                Parcel bare = new Parcel();
                bare.setAccessToken(SECRET_WORD + "-bare-" + i);
                em.persist(bare);
                seeded.add(bare);
            }
        });
        // A fresh entity manager: the first one may have cached the table.
        entityManager.close();
        entityManager = factory.createEntityManager();
        lists = ListQueryService.over(entityManager, registry);
        RecordingStatementInspector.clear();
    }

    // ---- helpers the scenarios share -----------------------------------------------------------------------------------------

    protected AdminEntity entity(String urlName) {
        return registry.find(urlName).orElseThrow();
    }

    protected AdminEntity parcelEntity() {
        return entity("parcel");
    }

    protected ListPage list(String q, String sort, String dir, String page, String size) {
        return lists.list(parcelEntity(), ListQuery.parse(parcelEntity(), registry, q, sort, dir, page, size));
    }

    protected static List<Long> ids(ListPage page) {
        return page.rows().stream().map(row -> Long.valueOf(row.id())).toList();
    }

    protected static Cell cell(ListPage page, ListView.Row row, String key) {
        for (int i = 0; i < page.columns().size(); i++) {
            if (page.columns().get(i).key().equals(key)) {
                return row.cells().get(i);
            }
        }
        throw new AssertionError("no column " + key);
    }

    protected static String display(ListPage page, int row, String key) {
        return cell(page, page.rows().get(row), key).display();
    }

    protected Long idOfParcel(int number) {
        return seeded.get(number - 1).getId();
    }

    protected List<Long> expected(Comparator<Parcel> comparator) {
        return seeded.stream().sorted(comparator).map(Parcel::getId).toList();
    }

    protected static Comparator<Parcel> byIdThen(Comparator<Parcel> primary) {
        return primary.thenComparing(Parcel::getId);
    }

    /** Every page of a sorted list, in order, 10 rows at a time. */
    protected List<Long> sortedIds(String key, String dir) {
        List<Long> all = new ArrayList<>();
        int pages = (seeded.size() + 9) / 10;
        for (int page = 0; page < pages; page++) {
            all.addAll(ids(list(null, key, dir, String.valueOf(page), "10")));
        }
        return all;
    }

    /** Ascending or descending by {@code key}, missing values last either way, then the identifier ascending. */
    protected static <T extends Comparable<T>> Comparator<Parcel> nullsLast(java.util.function.Function<Parcel, T> key,
                                                                           boolean descending) {
        Comparator<T> order = descending ? Comparator.<T>naturalOrder().reversed() : Comparator.<T>naturalOrder();
        return Comparator.comparing(key, Comparator.nullsLast(order)).thenComparing(Parcel::getId);
    }

    protected static String depotName(Parcel parcel) {
        return parcel.getDepot() == null ? null : parcel.getDepot().getName();
    }

    protected static String courierLabel(Parcel parcel) {
        return parcel.getCourier() == null ? null : parcel.getCourier().getCallSign();
    }

    /** Reads with an entity manager of its own, so a test sees what is committed. */
    protected <T> T read(java.util.function.Function<EntityManager, T> work) {
        try (EntityManager reader = factory.createEntityManager()) {
            return work.apply(reader);
        }
    }

    protected long count(Class<?> type) {
        return read(em -> em.createQuery("select count(e) from " + type.getSimpleName() + " e", Long.class).getSingleResult());
    }
}
