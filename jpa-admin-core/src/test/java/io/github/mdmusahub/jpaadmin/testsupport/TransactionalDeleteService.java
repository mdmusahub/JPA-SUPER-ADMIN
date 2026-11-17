package io.github.mdmusahub.jpaadmin.testsupport;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.service.BulkDeleteIds;
import io.github.mdmusahub.jpaadmin.service.DeleteService;
import jakarta.persistence.EntityManager;
import java.util.function.Supplier;

/**
 * The {@link DeleteService} with the transaction that Spring's {@code @Transactional} proxy gives it in a real
 * application: begin before the call, commit when it returns, roll back when it throws a runtime exception. The core
 * tests have no Spring context, so this test double does by hand what the proxy does (the sample app proves the real
 * proxy). It adds no behaviour of its own.
 */
public final class TransactionalDeleteService extends DeleteService {

    private final EntityManager entityManager;

    public TransactionalDeleteService(EntityManager entityManager, AdminEntityRegistry registry) {
        super(entityManager, registry);
        this.entityManager = entityManager;
    }

    @Override
    public Deleted delete(AdminEntity entity, String idText) {
        return inTransaction(() -> super.delete(entity, idText));
    }

    @Override
    public BulkResult deleteMany(AdminEntity entity, BulkDeleteIds request) {
        return inTransaction(() -> super.deleteMany(entity, request));
    }

    private <T> T inTransaction(Supplier<T> work) {
        entityManager.getTransaction().begin();
        try {
            T result = work.get();
            entityManager.getTransaction().commit();
            return result;
        } catch (RuntimeException failure) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.clear();
            throw failure;
        }
    }
}
