package io.github.mdmusahub.jpaadmin.testsupport;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The real JPA setup the metadata, count and page tests share: Hibernate over an in-memory H2 database, built from
 * {@code src/test/resources/META-INF/persistence.xml} with plain JPA, so no Spring application context is started
 * in {@code jpa-admin-core}.
 *
 * <p>The factory is built once and never closed: building it is the slow part, every test reads the same metamodel,
 * and the database lives as long as the test JVM.
 */
public final class TestPersistence {

    private static final EntityManagerFactory FACTORY = Persistence.createEntityManagerFactory("console-test");

    private TestPersistence() {
    }

    /** The one entity manager factory of the test model. */
    public static EntityManagerFactory factory() {
        return FACTORY;
    }

    /** A fresh entity manager of the test model. The caller closes it. */
    public static EntityManager entityManager() {
        return FACTORY.createEntityManager();
    }

    /**
     * An entity manager that counts every criteria query it is asked to build, and fails for one entity class, so a
     * test can prove how many queries a page really costs and what a broken table does.
     *
     * <p>A plain dynamic proxy: no mocking library, and it counts what the database would really be asked.
     *
     * @param delegate the real entity manager to pass everything on to
     * @param queries  counted up once per criteria query
     * @param failFor  the entity class whose count must fail, or a supplier of {@code null} for none
     */
    public static EntityManager countingEntityManager(EntityManager delegate, AtomicInteger queries,
                                                      Supplier<Class<?>> failFor) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("createQuery") && args != null && args.length == 1
                    && args[0] instanceof CriteriaQuery<?> query) {
                queries.incrementAndGet();
                Class<?> broken = failFor.get();
                for (Root<?> root : query.getRoots()) {
                    if (root.getJavaType().equals(broken)) {
                        throw new IllegalStateException("the table is not there");
                    }
                }
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        };
        return (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                new Class<?>[] {EntityManager.class}, handler);
    }
}
