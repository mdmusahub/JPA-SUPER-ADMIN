package io.github.mdmusahub.jpaadmin.testsupport;

import io.github.mdmusahub.jpaadmin.testsupport.tonemodel.Dial;
import io.github.mdmusahub.jpaadmin.testsupport.tonemodel.Gauge;
import io.github.mdmusahub.jpaadmin.testsupport.tonemodel.Shelf;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.function.Consumer;

/**
 * A real JPA setup for the Phase 13 annotation tests: its own persistence unit ({@code tone-test}), its own in-memory
 * database and its own neutral entities ({@link Gauge}, {@link Dial}, {@link Shelf}), so nothing another test class counts
 * changes.
 */
public final class ToneTestPersistence {

    private static final EntityManagerFactory FACTORY = Persistence.createEntityManagerFactory("tone-test");

    private ToneTestPersistence() {
    }

    public static EntityManagerFactory factory() {
        return FACTORY;
    }

    /** A fresh entity manager. The caller closes it. */
    public static EntityManager entityManager() {
        return FACTORY.createEntityManager();
    }

    /** Runs {@code work} in a transaction of its own and commits it. */
    public static void inTransaction(Consumer<EntityManager> work) {
        try (EntityManager em = FACTORY.createEntityManager()) {
            em.getTransaction().begin();
            try {
                work.accept(em);
                em.getTransaction().commit();
            } catch (RuntimeException failure) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                throw failure;
            }
        }
    }

    /** Empties every table, children first. */
    public static void empty() {
        inTransaction(em -> {
            for (Class<?> type : new Class<?>[] {Gauge.class, Dial.class, Shelf.class}) {
                em.createQuery("delete from " + type.getSimpleName()).executeUpdate();
            }
        });
    }
}
