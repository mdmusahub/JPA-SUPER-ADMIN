package io.github.mdmusahub.jpaadmin.autoconfigure;

import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.CONSOLE_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

/**
 * SEC-8: under the {@code prod} profile the console warns, once, at startup, when nothing says that it is reached over
 * HTTPS; and it changes no cookie or session setting (those are global and would change the host, D-8).
 */
@ExtendWith(OutputCaptureExtension.class)
class JpaAdminTransportCheckTest {

    private static final String WARNING = "is served over plain HTTP while the 'prod' profile is active";

    private static JpaAdminTransportCheck check(MockEnvironment environment) {
        return new JpaAdminTransportCheck(BasePath.of("/admin"), environment);
    }

    private static MockEnvironment prod() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }

    // ---- the warning ----------------------------------------------------------------------------------------------

    @Test
    void underProdOverPlainHttpItWarnsAndSaysHowToFixItForBothTheDirectAndTheProxyCase(CapturedOutput output) {
        check(prod()).afterPropertiesSet();

        assertThat(output.getAll()).containsOnlyOnce(WARNING)
                .contains("The console at /admin")
                .contains("server.ssl.*")
                .contains("reverse proxy", "server.forward-headers-strategy=native or framework")
                .contains("server.servlet.session.cookie.secure=true", "server.servlet.session.cookie.same-site=lax")
                .contains("does not change cookie settings");
    }

    @Test
    void theWarningNamesTheConfiguredPath(CapturedOutput output) {
        new JpaAdminTransportCheck(BasePath.of("/tools/admin"), prod()).afterPropertiesSet();

        assertThat(output.getAll()).contains("The console at /tools/admin");
    }

    @Test
    void itAlsoWarnsWhenProdIsOneOfSeveralProfiles(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("cloud", "prod");

        check(environment).afterPropertiesSet();

        assertThat(output.getAll()).contains(WARNING);
    }

    // ---- no warning -----------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "local", "staging", "production", "Prod", "PROD", ""})
    void otherProfilesGetNoWarning(String profile, CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        if (!profile.isEmpty()) {
            environment.setActiveProfiles(profile);
        }

        check(environment).afterPropertiesSet();

        assertThat(output.getAll()).doesNotContain("plain HTTP");
    }

    @ParameterizedTest
    @ValueSource(strings = {"server.ssl.bundle=web", "server.ssl.key-store=classpath:keystore.p12",
            "server.ssl.certificate=classpath:cert.pem", "server.forward-headers-strategy=native",
            "server.forward-headers-strategy=framework", "server.forward-headers-strategy=NATIVE",
            "server.tomcat.remoteip.protocol-header=X-Forwarded-Proto"})
    void underProdItIsQuietWhenTheConfigurationSaysHttpsIsInUse(String property, CapturedOutput output) {
        MockEnvironment environment = prod();
        String[] parts = property.split("=", 2);
        environment.setProperty(parts[0], parts[1]);

        check(environment).afterPropertiesSet();

        assertThat(output.getAll()).doesNotContain("plain HTTP");
    }

    @ParameterizedTest
    @ValueSource(strings = {"server.ssl.enabled=false", "server.forward-headers-strategy=none",
            "server.forward-headers-strategy=", "server.ssl.bundle=", "server.ssl.key-store=  ",
            "server.tomcat.remoteip.protocol-header=", "server.port=8443", "server.address=127.0.0.1"})
    void underProdItStillWarnsWhenTheSettingIsOffBlankOrIrrelevant(String property, CapturedOutput output) {
        MockEnvironment environment = prod();
        String[] parts = property.split("=", 2);
        environment.setProperty(parts[0], parts.length > 1 ? parts[1] : "");
        if (property.equals("server.ssl.enabled=false")) {
            // TLS switched off explicitly beats a key store that is still configured.
            environment.setProperty("server.ssl.key-store", "classpath:keystore.p12");
        }

        check(environment).afterPropertiesSet();

        assertThat(output.getAll()).contains(WARNING);
    }

    // ---- inside the auto-configuration -----------------------------------------------------------------------------

    @Test
    void theAutoConfigurationRunsTheCheckAtStartupUnderProdAndNotOtherwise(CapturedOutput output) {
        ConsoleTestHosts.withoutSpringSecurity().withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasNotFailed());
        assertThat(output.getAll()).containsOnlyOnce(WARNING);
    }

    @Test
    void theAutoConfigurationStaysQuietUnderOtherProfiles(CapturedOutput output) {
        ConsoleTestHosts.withoutSpringSecurity().withInitializer(c -> c.getEnvironment().setActiveProfiles("dev"))
                .run(context -> assertThat(context).hasNotFailed());
        ConsoleTestHosts.withoutSpringSecurity().run(context -> assertThat(context).hasNotFailed());

        assertThat(output.getAll()).doesNotContain("plain HTTP");
    }

    @Test
    void theAutoConfigurationStaysQuietUnderProdWhenTlsIsConfigured(CapturedOutput output) {
        ConsoleTestHosts.withoutSpringSecurity().withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("server.forward-headers-strategy=framework")
                .run(context -> assertThat(context).hasNotFailed());

        assertThat(output.getAll()).doesNotContain("plain HTTP");
    }

    @Test
    void theWarningNeverContainsThePassword(CapturedOutput output) {
        ConsoleTestHosts.withoutSpringSecurity().withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasNotFailed());

        assertThat(output.getAll()).contains(WARNING).doesNotContain(CONSOLE_PASSWORD);
    }

    // ---- no cookie or session setting is touched ----------------------------------------------------------------------

    @Test
    void theConsoleChangesNoCookieOrSessionSettingOfTheHost() {
        ConsoleTestHosts.withoutSpringSecurity().withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    var cookie = context.getServletContext().getSessionCookieConfig();
                    assertThat(cookie.isSecure()).isFalse();
                    assertThat(cookie.isHttpOnly()).isFalse();
                    assertThat(cookie.getName()).isNull();
                    assertThat(cookie.getDomain()).isNull();
                    assertThat(cookie.getPath()).isNull();
                    assertThat(cookie.getMaxAge()).isEqualTo(-1);
                    assertThat(context.getServletContext().getEffectiveSessionTrackingModes())
                            .as("tracking modes are the container's own").isNotNull();
                    assertThat(context.getEnvironment().getProperty("server.servlet.session.cookie.secure")).isNull();
                    assertThat(context.getEnvironment().getProperty("server.servlet.session.cookie.same-site")).isNull();
                    assertThat(context.getEnvironment().getProperty("server.servlet.session.timeout")).isNull();
                    assertThat(context.getEnvironment().getProperty("server.servlet.session.tracking-modes")).isNull();
                });
    }
}
