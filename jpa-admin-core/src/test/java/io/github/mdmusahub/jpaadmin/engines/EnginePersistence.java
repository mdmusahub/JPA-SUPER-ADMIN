package io.github.mdmusahub.jpaadmin.engines;

import io.github.mdmusahub.jpaadmin.testsupport.enginemodel.Essay;
import io.github.mdmusahub.jpaadmin.testsupport.enginemodel.Tome;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Anchor;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Courier;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Crate;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Folio;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Ledger;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelItem;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Ticket;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Vault;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.function.Consumer;

/**
 * The persistence unit {@code engine-test} (see {@code persistence.xml}) opened on one {@link EngineDatabase}: the same Hibernate,
 * the same entities and the same console code, whatever the database. Only the connection properties change; Hibernate finds
 * the dialect from the connection, as it does in a host application.
 */
public final class EnginePersistence {

    private EnginePersistence() {
    }

    /** The factory for {@code database}, with the schema created from the entities. The caller closes it. */
    public static EntityManagerFactory open(EngineDatabase database) {
        return Persistence.createEntityManagerFactory("engine-test", database.properties());
    }

    /** Runs {@code work} in a transaction of its own and commits it. */
    public static void inTransaction(EntityManagerFactory factory, Consumer<EntityManager> work) {
        try (EntityManager em = factory.createEntityManager()) {
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

    /** Empties every table, the ones that point at others first. */
    public static void empty(EntityManagerFactory factory) {
        inTransaction(factory, em -> {
            for (Class<?> type : new Class<?>[] {Folio.class, ParcelItem.class, Parcel.class, Depot.class, Courier.class,
                    Crate.class, Vault.class, Anchor.class, Ticket.class, Ledger.class, Essay.class, Tome.class}) {
                em.createQuery("delete from " + type.getSimpleName()).executeUpdate();
            }
        });
    }
}
