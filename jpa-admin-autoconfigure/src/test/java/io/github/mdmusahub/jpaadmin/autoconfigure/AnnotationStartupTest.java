package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.autoconfigure.testmodel.Gizmo;
import io.github.mdmusahub.jpaadmin.autoconfigure.tonemodel.bad.Faucet;
import io.github.mdmusahub.jpaadmin.autoconfigure.tonemodel.good.Valve;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * The startup check of the Phase 13 annotations on real Spring contexts with a real Hibernate factory (D-120 to D-122): good
 * annotations start, every wrong one stops startup in one report that names the entity and field and repeats nothing the host
 * wrote, a host without any of them is left alone, and the page size reaches the list service.
 */
class AnnotationStartupTest {

    private static final String PASSWORD = "console-pw-Qx7-not-a-real-secret";

    @EntityScan(basePackageClasses = Valve.class)
    @Configuration(proxyBeanMethods = false)
    static class GoodEntities {
    }

    @EntityScan(basePackageClasses = Faucet.class)
    @Configuration(proxyBeanMethods = false)
    static class BadEntities {
    }

    @EntityScan(basePackageClasses = Gizmo.class)
    @Configuration(proxyBeanMethods = false)
    static class PlainEntities {
    }

    private static int databases;

    private WebApplicationContextRunner host(Class<?> entities, String... properties) {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class, JpaAdminAutoConfiguration.class,
                        DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
                        HibernateJpaAutoConfiguration.class))
                .withPropertyValues("jpa.admin.username=console-admin", "jpa.admin.password=" + PASSWORD,
                        "spring.jpa.hibernate.ddl-auto=create-drop",
                        "spring.datasource.url=jdbc:h2:mem:annotation-startup-" + (databases++) + ";DB_CLOSE_DELAY=-1")
                .withPropertyValues(properties).withUserConfiguration(entities);
    }

    private static String rootMessages(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            text.append(t.getMessage()).append('\n');
        }
        return text.toString();
    }

    @Test
    void goodAnnotationsStartAndTheScannerKeptThem() {
        host(GoodEntities.class).run(context -> {
            assertThat(context).hasNotFailed();
            AdminEntityRegistry registry = context.getBean(AdminEntityRegistry.class);
            AdminEntity valve = registry.find("valve").orElseThrow();
            assertThat(valve.plural()).isEqualTo("Valves (all kinds)");
            assertThat(valve.visibleField("flow").orElseThrow().badge().toneOf("SHUT")).contains("grey");
            assertThat(valve.visibleField("pipe").orElseThrow().lookup()).isEqualTo("tag");
            assertThat(registry.find("pipe").orElseThrow().plural()).isEqualTo("Pipes");
        });
    }

    @Test
    void everyWrongAnnotationStopsStartupInOneReportThatNamesEachField() {
        host(BadEntities.class).run(context -> {
            assertThat(context).hasFailed();
            String text = rootMessages(context.getStartupFailure());

            assertThat(text).contains("The JPA Admin console is not configured correctly");
            String faucet = Faucet.class.getName();
            assertThat(text).contains("@AdminResource(plural) on " + faucet)
                    .contains("@AdminBadge on " + faucet + ".inlineColour")
                    .contains("@AdminBadge on " + faucet + ".mode")
                    .contains("@AdminBadge on " + faucet + ".pressure")
                    .contains("@AdminLookup on " + faucet + ".sensitiveLookup")
                    .contains("@AdminLookup on " + faucet + ".missingLookup")
                    .contains("@AdminLookup on " + faucet + ".lookupOnText");
        });
    }

    @Test
    void theReportRepeatsNothingTheHostWroteAndNeverPrintsTheSecret() {
        host(BadEntities.class).run(context -> {
            String text = rootMessages(context.getStartupFailure());

            assertThat(text).doesNotContain("#ff0000").doesNotContain("LUKEWARM").doesNotContain("secretNote")
                    .doesNotContain("nothing\"").doesNotContain("Fau\u0007cets").doesNotContain(PASSWORD);
        });
    }

    @Test
    void aHostWithoutTheAnnotationsIsLeftAlone() {
        host(PlainEntities.class).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void theConsoleBacksOffCleanlyWhenItIsSwitchedOffEvenWithWrongAnnotations() {
        host(BadEntities.class, "jpa.admin.enabled=false").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void thePageSizeOfTheConfigurationReachesTheListService() {
        host(GoodEntities.class, "jpa.admin.page-size=50").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ListQueryService.class).defaultPageSize()).isEqualTo(50);
        });
        host(GoodEntities.class).run(context -> assertThat(context.getBean(ListQueryService.class).defaultPageSize())
                .isEqualTo(ListQuery.DEFAULT_SIZE));
    }

    @Test
    void aBadPageSizeStopsTheRealHostAndNamesTheProperty() {
        host(GoodEntities.class, "jpa.admin.page-size=33").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessages(context.getStartupFailure())).contains("jpa.admin.page-size").doesNotContain(PASSWORD);
        });
    }
}
