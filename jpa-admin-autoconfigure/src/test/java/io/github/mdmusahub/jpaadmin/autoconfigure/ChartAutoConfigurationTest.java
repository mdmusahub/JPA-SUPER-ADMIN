package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.autoconfigure.testmodel.Gizmo;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.service.CreationDates;
import io.github.mdmusahub.jpaadmin.service.StatsService;
import io.github.mdmusahub.jpaadmin.web.DashboardCharts;
import io.github.mdmusahub.jpaadmin.web.StatsController;
import io.github.mdmusahub.jpaadmin.web.view.ChartSeries;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * The chart's beans (Phase 10, ARCH-7, ARCH-8): they are registered with the console, prefixed, replaceable, built over the host's
 * entity manager factory when there is one, and they use the host's {@code Clock} when it has one and the server's clock when
 * it has not. The reader's method is a real read-only transaction of its own (ARCH-5).
 */
class ChartAutoConfigurationTest {

    private WebApplicationContextRunner withJpa() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class, JpaAdminAutoConfiguration.class,
                        DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
                        TransactionAutoConfiguration.class, HibernateJpaAutoConfiguration.class))
                .withPropertyValues("jpa.admin.username=console-admin", "jpa.admin.password=console-pw-Qx7-not-a-real-secret",
                        "spring.jpa.hibernate.ddl-auto=create-drop",
                        "spring.datasource.url=jdbc:h2:mem:chart-autoconfig-test;DB_CLOSE_DELAY=-1")
                .withUserConfiguration(GizmoEntityConfig.class);
    }

    @EntityScan(basePackageClasses = Gizmo.class)
    @Configuration(proxyBeanMethods = false)
    static class GizmoEntityConfig {
    }

    @Configuration(proxyBeanMethods = false)
    static class HostClock {
        @Bean
        Clock hostClock() {
            return Clock.fixed(Instant.parse("2031-03-04T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HostStats {
        @Bean
        StatsService hostStats() {
            return StatsService.unavailable(AdminEntityRegistry.empty(), Clock.systemUTC());
        }
    }

    @Test
    void withJpaTheChartBeansAreThereWithTheConsolesPrefix() {
        withJpa().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CreationDates.class).hasSingleBean(StatsService.class)
                    .hasSingleBean(DashboardCharts.class).hasSingleBean(StatsController.class);
            assertThat(context).hasBean("jpaAdminCreationDates").hasBean("jpaAdminStatsService")
                    .hasBean("jpaAdminDashboardCharts").hasBean("jpaAdminStatsController");
        });
    }

    @Test
    void theReaderIsATransactionalProxyWhoseMethodIsReadOnly() {
        withJpa().run(context -> {
            CreationDates reader = context.getBean(CreationDates.class);

            assertThat(AopUtils.isAopProxy(reader)).as("the transactional proxy exists").isTrue();
            var read = AopUtils.getTargetClass(reader).getMethods();
            boolean readOnly = java.util.Arrays.stream(read).filter(method -> method.getName().equals("read"))
                    .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class))
                    .allMatch(annotation -> annotation != null && annotation.readOnly());
            assertThat(readOnly).isTrue();
        });
    }

    @Test
    void aHostWithoutAnyCreationFieldGetsNoChart() {
        withJpa().run(context -> {
            StatsService stats = context.getBean(StatsService.class);

            assertThat(stats.hasChart()).isFalse();
            assertThat(stats.series(null).entities()).isEmpty();
            assertThat(context.getBean(DashboardCharts.class).chart("7")).isEmpty();
        });
    }

    @Test
    void theHostsClockDecidesWhatTodayIs() {
        withJpa().withUserConfiguration(HostClock.class).run(context -> {
            ChartSeries series = context.getBean(StatsService.class).series("7");

            assertThat(series.days().get(6).date()).isEqualTo("2031-03-04");
        });
    }

    @Test
    void withoutAHostClockTheServersClockIsUsed() {
        withJpa().run(context -> {
            LocalDate before = LocalDate.now();
            ChartSeries series = context.getBean(StatsService.class).series("7");
            LocalDate after = LocalDate.now();

            assertThat(LocalDate.parse(series.days().get(6).date())).isBetween(before, after);
        });
    }

    @Test
    void aHostCanReplaceTheStatistics() {
        withJpa().withUserConfiguration(HostStats.class).run(context -> {
            assertThat(context).hasSingleBean(StatsService.class);
            assertThat(context).hasBean("hostStats").doesNotHaveBean("jpaAdminStatsService");
        });
    }

    @Test
    void withoutJpaTheStatisticsStillExistAndHaveNoChart() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class, JpaAdminAutoConfiguration.class))
                .withPropertyValues("jpa.admin.username=console-admin", "jpa.admin.password=console-pw-Qx7-not-a-real-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StatsService.class).doesNotHaveBean(CreationDates.class);
                    assertThat(context.getBean(StatsService.class).hasChart()).isFalse();
                    assertThat(context.getBean(DashboardCharts.class).chart("30")).isEmpty();
                });
    }
}
