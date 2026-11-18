package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Courier;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Folio;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * The data and helpers the form tests share: two depots, a courier and one {@link Folio} (version 0) with a secret, in a real
 * database, and the registry the console would build from it. Each test starts from the same rows.
 */
abstract class AbstractFormServiceTest {

    /** What the seeded folio's secret is stored as (the host's setter adds the prefix). */
    protected static final String STORED_SECRET = "stored:old-secret-value";

    protected AdminEntityRegistry registry;
    protected EntityManager entityManager;
    protected Long depotOne;
    protected Long depotTwo;
    protected Long courier;
    protected Long folio;

    @BeforeEach
    void seedTheForms() {
        ListTestPersistence.empty();
        Long[] ids = new Long[4];
        ListTestPersistence.inTransaction(em -> {
            Depot one = new Depot();
            one.setName("Alpha depot");
            em.persist(one);
            Depot two = new Depot();
            two.setName("Beta depot");
            em.persist(two);
            Courier runner = new Courier();
            runner.setCallSign("Runner");
            em.persist(runner);
            Folio existing = new Folio();
            existing.setTitle("Existing folio");
            existing.setCode("E-1");
            existing.setQuantity(3);
            existing.setStatus(ParcelStatus.PENDING);
            existing.setDue(LocalDate.of(2026, 3, 4));
            existing.setOpenedAt(LocalDateTime.of(2026, 3, 4, 5, 6, 7, 123_456_789));
            existing.setHome(one);
            existing.setSecretNote("old-secret-value");
            existing.setInternalNote("internal");
            em.persist(existing);
            em.flush();
            ids[0] = one.getId();
            ids[1] = two.getId();
            ids[2] = runner.getId();
            ids[3] = existing.getId();
        });
        depotOne = ids[0];
        depotTwo = ids[1];
        courier = ids[2];
        folio = ids[3];
        registry = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(), DiscoverySettings.defaults());
        entityManager = ListTestPersistence.entityManager();
        RecordingStatementInspector.clear();
    }

    @AfterEach
    void closeTheForms() {
        Folio.failure = null;
        entityManager.close();
    }

    protected AdminEntity entity(String urlName) {
        return registry.find(urlName).orElseThrow();
    }

    /** The posted values as a function, the way the controller hands them over. */
    protected static Function<String, String> posted(Map<String, String> values) {
        return values::get;
    }

    /** A full, valid set of values for a new folio. */
    protected Map<String, String> validFolio() {
        Map<String, String> values = new HashMap<>();
        values.put("title", "A new folio");
        values.put("code", "N-1");
        values.put("quantity", "7");
        values.put("urgent", "true");
        values.put("status", "DELIVERED");
        values.put("due", "2026-11-17");
        values.put("openedAt", "2026-11-17T10:30");
        values.put("home", String.valueOf(depotOne));
        values.put("courier", String.valueOf(courier));
        values.put("secretNote", "typed-secret");
        return values;
    }

    /** Reads with an entity manager of its own, so the test sees what is committed. */
    protected <T> T read(Function<EntityManager, T> work) {
        try (EntityManager reader = ListTestPersistence.entityManager()) {
            return work.apply(reader);
        }
    }

    protected long count(Class<?> type) {
        return read(em -> em.createQuery("select count(e) from " + type.getSimpleName() + " e", Long.class).getSingleResult());
    }

    protected <E> E find(Class<E> type, Object id) {
        return read(em -> em.find(type, id));
    }

    protected long folioCount() {
        return read(em -> em.createQuery("select count(f) from Folio f", Long.class).getSingleResult());
    }

    protected Folio stored(Long id) {
        return read(em -> {
            Folio found = em.find(Folio.class, id);
            // Touch what the tests compare, inside the session.
            if (found != null) {
                org.hibernate.Hibernate.initialize(found.getHome());
                org.hibernate.Hibernate.initialize(found.getCourier());
            }
            return found;
        });
    }
}
