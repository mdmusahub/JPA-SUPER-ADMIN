package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.autoconfigure.exportmodel.Receipt;
import io.github.mdmusahub.jpaadmin.autoconfigure.exportmodel.Withheld;
import io.github.mdmusahub.jpaadmin.service.ExportService;
import io.github.mdmusahub.jpaadmin.service.ExportSettings;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.web.ConsoleNavigation;
import io.github.mdmusahub.jpaadmin.web.ExportController;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The export in a real application context with the real security chain (Phase 11): the beans exist with the console's prefix
 * and are replaceable, the three properties reach the service, the service reads through the transactional proxy, and the
 * route obeys the session, the method, CSRF, {@code exportable} and {@code export-enabled} exactly as the other console routes
 * do. The entities are the neutral {@code Receipt} and {@code Withheld} (ARCH-12).
 */
class ExportAutoConfigurationTest {

    private static final String BASE = "/admin";

    @EntityScan(basePackageClasses = Receipt.class)
    @Configuration(proxyBeanMethods = false)
    static class ExportEntities {
    }

    @Configuration(proxyBeanMethods = false)
    static class HostClock {
        @Bean
        Clock hostClock() {
            return Clock.fixed(Instant.parse("2031-03-04T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HostExportService {
        @Bean
        ExportService hostExport() {
            return ExportService.none();
        }
    }

    private WebApplicationContextRunner host(String... properties) {
        return ConsoleTestHosts.withoutSpringSecurity()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class,
                        HibernateJpaAutoConfiguration.class))
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=create-drop",
                        "spring.datasource.url=jdbc:h2:mem:export-autoconfig-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")
                .withPropertyValues(properties)
                .withUserConfiguration(ExportEntities.class);
    }

    private static void seed(EntityManagerFactory factory, int receipts) {
        EntityManager em = factory.createEntityManager();
        try {
            em.getTransaction().begin();
            for (int i = 1; i <= receipts; i++) {
                Receipt receipt = new Receipt();
                receipt.setTitle("receipt " + i);
                receipt.setAmount(new BigDecimal("12.50"));
                receipt.setAccessToken("TOKEN-SECRET-" + i);
                em.persist(receipt);
            }
            Withheld withheld = new Withheld();
            withheld.setTitle("withheld record");
            em.persist(withheld);
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    private static MockBrowser signedIn(MockMvc mvc) throws Exception {
        MockBrowser browser = new MockBrowser(mvc);
        MockHttpServletResponse response = browser.signInToConsole(BASE, ConsoleTestHosts.CONSOLE_USER,
                ConsoleTestHosts.CONSOLE_PASSWORD);
        assertThat(response.getStatus()).isEqualTo(302);
        return browser;
    }

    // ---- the beans ---------------------------------------------------------------------------------------------------

    @Test
    void theExportBeansAreThereWithTheConsolesPrefix() {
        host().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ExportService.class).hasSingleBean(ExportSettings.class)
                    .hasSingleBean(ExportController.class);
            assertThat(context).hasBean("jpaAdminExportService").hasBean("jpaAdminExportSettings")
                    .hasBean("jpaAdminExportController");
        });
    }

    @Test
    void theServiceReadsThroughTheTransactionalListProxySoEveryPageIsItsOwnReadOnlyTransaction() {
        host().run(context -> assertThat(AopUtils.isAopProxy(context.getBean(ListQueryService.class))).isTrue());
    }

    @Test
    void theExportServiceIsReplaceable() {
        host().withUserConfiguration(HostExportService.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ExportService.class).hasBean("hostExport").doesNotHaveBean("jpaAdminExportService");
        });
    }

    @Test
    void theThreePropertiesReachTheSettingsAndTheService() {
        host("jpa.admin.export-enabled=false", "jpa.admin.export-max-rows=77", "jpa.admin.export-bom=true").run(context -> {
            ExportSettings settings = context.getBean(ExportSettings.class);

            assertThat(settings.enabled()).isFalse();
            assertThat(settings.cap()).isEqualTo(77);
            assertThat(settings.bom()).isTrue();
            assertThat(context.getBean(ExportService.class).cap()).isEqualTo(77);
        });
    }

    @Test
    void withNoPropertyTheDefaultsApply() {
        host().run(context -> {
            assertThat(context.getBean(ExportSettings.class)).isEqualTo(ExportSettings.defaults());
            assertThat(context.getBean(ExportService.class).cap()).isEqualTo(50_000);
        });
    }

    @Test
    void aCapAboveTheCeilingStopsTheApplicationWithAMessageThatNamesTheProperty() {
        host("jpa.admin.export-max-rows=50001").run(context -> {
            assertThat(context).hasFailed();
            StringBuilder messages = new StringBuilder();
            for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
                messages.append(t.getMessage()).append('\n');
            }
            assertThat(messages.toString()).contains("jpa.admin.export-max-rows").contains("never raise");
        });
    }

    @Test
    void theConsoleBacksOffCompletelyWhenDisabledSoThereIsNoExportBeanEither() {
        host("jpa.admin.enabled=false").run(context -> assertThat(context).doesNotHaveBean(ExportService.class)
                .doesNotHaveBean(ExportController.class).doesNotHaveBean(ConsoleNavigation.class));
    }

    // ---- the route over the real chain ---------------------------------------------------------------------------

    @Test
    void withoutASessionTheExportIsRefusedAndShowsNoData() {
        host().run(context -> {
            seed(context.getBean(EntityManagerFactory.class), 3);
            MockHttpServletResponse response = ConsoleTestHosts.mvc(context)
                    .perform(MockMvcRequestBuilders.get(BASE + "/receipt/export.csv")).andReturn().getResponse();

            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getHeader("Location")).endsWith(BASE + "/login");
            assertThat(response.getContentAsString()).doesNotContain("receipt 1", "Title");
        });
    }

    @Test
    void aSignedInAdminGetsTheFileWithTheDownloadHeaders() {
        host().withUserConfiguration(HostClock.class).run(context -> {
            seed(context.getBean(EntityManagerFactory.class), 3);
            MockBrowser browser = signedIn(ConsoleTestHosts.mvc(context));

            MockHttpServletResponse response = browser.get(BASE + "/receipt/export.csv");

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).startsWith("text/csv").containsIgnoringCase("charset=UTF-8");
            assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"receipt-2031-03-04.csv\"");
            assertThat(response.getHeaders("X-Content-Type-Options")).containsExactly("nosniff");
            assertThat(response.getHeader("Cache-Control")).contains("no-store");
            String body = response.getContentAsString(StandardCharsets.UTF_8);
            assertThat(body).startsWith("ID,Amount,Title\r\n").contains("receipt 1", "12.50");
            assertThat(body).doesNotContain("TOKEN-SECRET", "Access token");
            assertThat(body.split("\r\n")).hasSize(1 + 3);
        });
    }

    @Test
    void aPostIsRefusedWithoutTheTokenAndDoesNotExportWithIt() {
        host().run(context -> {
            seed(context.getBean(EntityManagerFactory.class), 2);
            MockBrowser browser = signedIn(ConsoleTestHosts.mvc(context));
            String token = MockBrowser.csrf(browser.get(BASE + "/receipt/new").getContentAsString());

            MockHttpServletResponse withoutToken = browser.post(BASE + "/receipt/export.csv");
            MockHttpServletResponse withToken = browser.post(BASE + "/receipt/export.csv", "_csrf", token);

            assertThat(withoutToken.getStatus()).isEqualTo(403);
            assertThat(withToken.getStatus()).isBetween(400, 499);
            assertThat(withToken.getContentType()).doesNotContain("csv");
            assertThat(withToken.getContentAsString()).doesNotContain("receipt 1");
        });
    }

    @Test
    void otherMethodsAreNotExports() {
        host().run(context -> {
            seed(context.getBean(EntityManagerFactory.class), 2);
            MockBrowser browser = signedIn(ConsoleTestHosts.mvc(context));
            String token = MockBrowser.csrf(browser.get(BASE + "/receipt/new").getContentAsString());

            for (var method : new org.springframework.http.HttpMethod[] {org.springframework.http.HttpMethod.PUT,
                    org.springframework.http.HttpMethod.DELETE, org.springframework.http.HttpMethod.PATCH}) {
                MockHttpServletResponse response = browser.perform(MockMvcRequestBuilders.request(method, BASE + "/receipt/export.csv")
                        .param("_csrf", token));
                assertThat(response.getStatus()).as(method.name()).isBetween(400, 499);
                assertThat(response.getContentAsString()).as(method.name()).doesNotContain("receipt 1");
            }
        });
    }

    @Test
    void anEntityThatIsNotExportableIsForbiddenAndAnUnknownOneIsNotFound() {
        host().run(context -> {
            seed(context.getBean(EntityManagerFactory.class), 1);
            MockBrowser browser = signedIn(ConsoleTestHosts.mvc(context));

            assertThat(browser.get(BASE + "/withheld/export.csv").getStatus()).isEqualTo(403);
            assertThat(browser.get(BASE + "/withheld/export.csv").getContentAsString()).doesNotContain("withheld record");
            assertThat(browser.get(BASE + "/nothing-like-this/export.csv").getStatus()).isEqualTo(404);
            assertThat(browser.get(BASE + "/withheld").getContentAsString()).as("its list has no export link")
                    .doesNotContain("export.csv").contains("withheld record");
        });
    }

    @Test
    void theGlobalSwitchRemovesTheButtonAndForbidsTheRoute() {
        host("jpa.admin.export-enabled=false").run(context -> {
            seed(context.getBean(EntityManagerFactory.class), 2);
            MockBrowser browser = signedIn(ConsoleTestHosts.mvc(context));

            assertThat(browser.get(BASE + "/receipt").getContentAsString()).doesNotContain("export.csv", "Export CSV");
            assertThat(browser.get(BASE + "/receipt/export.csv").getStatus()).isEqualTo(403);
            assertThat(browser.get(BASE + "/nothing-like-this/export.csv").getStatus()).isEqualTo(404);
        });
    }

    @Test
    void theButtonIsOnTheListWhenTheExportIsOn() {
        host().run(context -> {
            seed(context.getBean(EntityManagerFactory.class), 2);
            MockBrowser browser = signedIn(ConsoleTestHosts.mvc(context));

            String page = browser.get(BASE + "/receipt?q=receipt&sort=title&dir=desc&page=0&size=10").getContentAsString();

            assertThat(page).contains("href=\"/admin/receipt/export.csv?q=receipt&amp;sort=title&amp;dir=desc\"", "Export CSV");
        });
    }

    @Test
    void aLoweredCapCutsTheFileSaysSoInTheHeadersAndTheListPage() {
        host("jpa.admin.export-max-rows=2").run(context -> {
            seed(context.getBean(EntityManagerFactory.class), 5);
            MockBrowser browser = signedIn(ConsoleTestHosts.mvc(context));

            MockHttpServletResponse file = browser.get(BASE + "/receipt/export.csv");
            String page = browser.get(BASE + "/receipt").getContentAsString();

            assertThat(file.getHeader("X-Export-Truncated")).isEqualTo("true");
            assertThat(file.getHeader("X-Export-Row-Limit")).isEqualTo("2");
            assertThat(file.getContentAsString().split("\r\n")).as("the header and two records").hasSize(3);
            assertThat(page).contains("Export is limited to the first 2 of 5 matching records.");
        });
    }

    @Test
    void theRouteAndTheLinkFollowACustomConsolePath() {
        host("jpa.admin.path=/back-office").run(context -> {
            seed(context.getBean(EntityManagerFactory.class), 2);
            MockBrowser browser = new MockBrowser(ConsoleTestHosts.mvc(context));
            browser.signInToConsole("/back-office", ConsoleTestHosts.CONSOLE_USER, ConsoleTestHosts.CONSOLE_PASSWORD);

            MockHttpServletResponse file = browser.get("/back-office/receipt/export.csv");
            String page = browser.get("/back-office/receipt").getContentAsString();

            assertThat(file.getStatus()).isEqualTo(200);
            assertThat(file.getHeader("Content-Disposition")).startsWith("attachment; filename=\"receipt-");
            assertThat(page).contains("href=\"/back-office/receipt/export.csv\"").doesNotContain("href=\"/admin/");
            assertThat(browser.get("/admin/receipt/export.csv").getStatus()).as("the old path is not the console").isEqualTo(404);
        });
    }

    @Test
    void theByteOrderMarkSettingReachesTheBytes() {
        host("jpa.admin.export-bom=true").run(context -> {
            seed(context.getBean(EntityManagerFactory.class), 1);
            MockBrowser browser = signedIn(ConsoleTestHosts.mvc(context));

            byte[] bytes = browser.get(BASE + "/receipt/export.csv").getContentAsByteArray();

            assertThat(bytes[0]).isEqualTo((byte) 0xEF);
            assertThat(bytes[1]).isEqualTo((byte) 0xBB);
            assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        });
    }
}
