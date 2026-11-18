package io.github.mdmusahub.jpaadmin.testsupport;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

/**
 * A third real JPA setup, for the upload tests: its own persistence unit ({@code upload-test}), its own in-memory database and
 * its own neutral entity, so nothing the upload tests add changes a count another test class counts.
 */
public final class UploadTestPersistence {

    private static final EntityManagerFactory FACTORY = Persistence.createEntityManagerFactory("upload-test");

    private UploadTestPersistence() {
    }

    public static EntityManagerFactory factory() {
        return FACTORY;
    }

    public static EntityManager entityManager() {
        return FACTORY.createEntityManager();
    }

    /** The registry the console would build from the upload model. */
    public static AdminEntityRegistry registry() {
        return EntityScanner.scan(FACTORY.getMetamodel(), DiscoverySettings.defaults());
    }
}
