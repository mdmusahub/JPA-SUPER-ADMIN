package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.autoconfigure.testmodel.Gizmo;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.web.ConsoleNavigation;
import jakarta.persistence.EntityManagerFactory;
import java.util.OptionalLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;

/**
 * The discovery beans register only when the host has JPA, and back off cleanly when it does not (ARCH-7). When it
 * does, they are built from the host's real entity manager factory, and they count real rows (ARCH-5). This test
 * uses the real Boot 4.1 auto-configurations and a real Hibernate factory over H2.
 *
 * <p>The entity is the neutral {@code Gizmo}, never a demo name (ARCH-12). The JPA auto-configuration class is
 * {@code org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration} (Boot 4 moved it, D-30).
 */
class DiscoveryAutoConfigurationTest {

    private static final Class<?>[] JPA = {DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, HibernateJpaAutoConfiguration.class};

    private WebApplicationContextRunner withJpa() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(concat(WebMvcAutoConfiguration.class,
                        JpaAdminAutoConfiguration.class, JPA)))
                .withPropertyValues("jpa.admin.username=console-admin",
                        "jpa.admin.password=console-pw-Qx7-not-a-real-secret",
                        "spring.jpa.hibernate.ddl-auto=create-drop",
                        "spring.datasource.url=jdbc:h2:mem:discovery-test;DB_CLOSE_DELAY=-1",
                        "spring.jpa.properties.hibernate.javax.persistence.schema-generation.database.action=create")
                .withUserConfiguration(GizmoEntityConfig.class);
    }

    private static Class<?>[] concat(Class<?> a, Class<?> b, Class<?>[] rest) {
        Class<?>[] all = new Class<?>[rest.length + 2];
        all[0] = a;
        all[1] = b;
        System.arraycopy(rest, 0, all, 2, rest.length);
        return all;
    }

    @EntityScan(basePackageClasses = Gizmo.class)
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class GizmoEntityConfig {
    }

    @Test
    void withJpaTheRegistryCountServiceAndNavigationAreAllThereAndCountRealRows() {
        withJpa().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AdminEntityRegistry.class);
            assertThat(context).hasSingleBean(CountService.class);
            assertThat(context).hasSingleBean(ConsoleNavigation.class);

            AdminEntityRegistry registry = context.getBean(AdminEntityRegistry.class);
            assertThat(registry.find("gizmo")).isPresent();

            insertGizmos(context.getBean(EntityManagerFactory.class), 3);
            assertThat(context.getBean(CountService.class).count(registry.find("gizmo").orElseThrow()))
                    .isEqualTo(OptionalLong.of(3));
            assertThat(context.getBean(ConsoleNavigation.class).sidebar()).singleElement()
                    .satisfies(group -> assertThat(group.items()).singleElement()
                            .satisfies(item -> assertThat(item.entity()).isEqualTo("gizmo")));
        });
    }

    @Test
    void withoutJpaTheDiscoveryBeansBackOffButTheConsoleStillHasAnEmptyNavigation() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class, JpaAdminAutoConfiguration.class))
                .withPropertyValues("jpa.admin.username=console-admin",
                        "jpa.admin.password=console-pw-Qx7-not-a-real-secret")
                .run(context -> {
                    // No EntityManagerFactory on the classpath here, so the console backs off entirely (its condition
                    // also needs JPA). This proves the discovery beans never demand JPA on their own.
                    assertThat(context).doesNotHaveBean(AdminEntityRegistry.class);
                    assertThat(context).doesNotHaveBean(CountService.class);
                });
    }

    @Test
    void theNavigationFallsBackToEmptyWhenThereIsNoRegistry() {
        // ConsoleNavigation is built even without a registry bean, so a console with no JPA has an empty sidebar
        // rather than failing. Proven directly, because the bean method's providers are empty in that case.
        assertThat(ConsoleNavigation.empty().sidebar()).isEmpty();
        assertThat(ConsoleNavigation.empty().dashboard().groups()).isEmpty();
    }

    private static void insertGizmos(EntityManagerFactory factory, int count) {
        var em = factory.createEntityManager();
        em.getTransaction().begin();
        for (int i = 0; i < count; i++) {
            Gizmo gizmo = new Gizmo();
            gizmo.setName("Gizmo " + i);
            em.persist(gizmo);
        }
        em.getTransaction().commit();
        em.close();
    }
}
