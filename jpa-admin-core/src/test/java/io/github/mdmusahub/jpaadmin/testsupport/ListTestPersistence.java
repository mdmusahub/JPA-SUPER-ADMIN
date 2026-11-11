package io.github.mdmusahub.jpaadmin.testsupport;

import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Courier;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Crate;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelItem;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Stamp;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Vault;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.function.Consumer;

/**
 * A second real JPA setup, for the list tests: its own persistence unit ({@code list-test}), its own in-memory database
 * and its own entities, so the data these tests write never changes a count another test class counts. Every statement
 * Hibernate prepares is recorded by {@link RecordingStatementInspector}.
 */
public final class ListTestPersistence {

    private static final EntityManagerFactory FACTORY = Persistence.createEntityManagerFactory("list-test");

    private ListTestPersistence() {
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
            for (Class<?> type : new Class<?>[] {ParcelItem.class, Parcel.class, Depot.class, Courier.class,
                    Crate.class, Vault.class, Stamp.class}) {
                em.createQuery("delete from " + type.getSimpleName()).executeUpdate();
            }
        });
    }
}
