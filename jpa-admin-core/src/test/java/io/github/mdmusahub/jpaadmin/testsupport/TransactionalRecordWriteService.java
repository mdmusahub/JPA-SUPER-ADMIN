package io.github.mdmusahub.jpaadmin.testsupport;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.service.RecordWriteService;
import jakarta.persistence.EntityManager;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The {@link RecordWriteService} with the transaction that Spring's {@code @Transactional} proxy gives it in a real
 * application: begin before the call, commit when it returns, roll back when it throws a runtime exception. The core tests
 * have no Spring context, so this test double does by hand what the proxy does (the sample app proves the real proxy). It
 * adds no behaviour of its own.
 */
public final class TransactionalRecordWriteService extends RecordWriteService {

    private final EntityManager entityManager;

    public TransactionalRecordWriteService(EntityManager entityManager, AdminEntityRegistry registry) {
        super(entityManager, registry);
        this.entityManager = entityManager;
    }

    @Override
    public Saved create(AdminEntity entity, Function<String, String> posted) {
        return inTransaction(() -> super.create(entity, posted));
    }

    @Override
    public Saved update(AdminEntity entity, String idText, String versionText, Function<String, String> posted) {
        return inTransaction(() -> super.update(entity, idText, versionText, posted));
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
