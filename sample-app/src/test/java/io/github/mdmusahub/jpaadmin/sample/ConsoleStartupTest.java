package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts the real sample application, the way a user runs it, and checks what is printed. Covers the Phase 1
 * acceptance rows that need a real startup: the starter plus two properties are enough, a missing username stops
 * startup with a message that says what to add (SEC-7), and the password never reaches the output (SEC-6).
 *
 * <p>Runs on a random port. Tests that need the properties to be truly absent use {@code spring.config.name} to skip
 * every application config file, including the test one that holds fake credentials.
 */
@ExtendWith(OutputCaptureExtension.class)
class ConsoleStartupTest {

    /** Distinctive, so a leak is easy to find in the output. Fake, protects nothing. */
    private static final String STRONG = "Zx9-fake-strong-pw-QWERTY-7731";
    private static final String WEAK = "weak-pw-1";
    private static final String NO_CONFIG_FILES = "--spring.config.name=jpa-admin-no-such-config";
    private static final String USERNAME_REQUIRED =
            "jpa.admin.username is required. Set it in application.yml, for example jpa.admin.username=admin.";

    private static ConfigurableApplicationContext start(String... arguments) {
        String[] all = Stream.concat(Stream.of("--server.port=0", "--spring.main.banner-mode=off"),
                Stream.of(arguments)).toArray(String[]::new);
        return SpringApplication.run(SampleApplication.class, all);
    }

    private static HttpResponse<String> get(ConfigurableApplicationContext context, String path)
            throws IOException, InterruptedException {
        String port = context.getEnvironment().getProperty("local.server.port");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ---- the promise: starter + two properties --------------------------------------------------------------

    @Test
    void withTheStarterAndTheTwoPropertiesTheConsoleRespondsOverRealHttp(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start(NO_CONFIG_FILES, "--jpa.admin.username=admin",
                "--jpa.admin.password=" + STRONG)) {
            HttpResponse<String> home = get(context, "/admin");
            assertThat(home.statusCode()).isEqualTo(302);
            assertThat(home.headers().firstValue("Location").orElseThrow()).endsWith("/admin/login");
            HttpResponse<String> login = get(context, "/admin/login");
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(login.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/html");
            assertThat(login.body()).contains("<h1>Sign in</h1>");
            assertThat(get(context, "/admin/assets/css/tokens.css").statusCode()).isEqualTo(200);
            assertThat(get(context, "/jpa-admin/assets/css/tokens.css").statusCode()).isEqualTo(404);
            assertThat(get(context, "/assets/css/tokens.css").statusCode()).isEqualTo(404);
            assertThat(get(context, "/css/tokens.css").statusCode()).isEqualTo(404);
        }
        assertThat(output.getAll()).doesNotContain(STRONG).doesNotContain("password=" + STRONG);
    }

    @Test
    void aCustomPathWorksOverRealHttpAndTheDefaultPathIsGone() throws Exception {
        try (ConfigurableApplicationContext context = start(NO_CONFIG_FILES, "--jpa.admin.username=admin",
                "--jpa.admin.password=" + STRONG, "--jpa.admin.path=/backoffice")) {
            assertThat(get(context, "/backoffice").statusCode()).isEqualTo(302);
            assertThat(get(context, "/backoffice/login").statusCode()).isEqualTo(200);
            assertThat(get(context, "/backoffice/assets/css/tokens.css").statusCode()).isEqualTo(200);
            assertThat(get(context, "/admin").statusCode()).isEqualTo(404);
            assertThat(get(context, "/admin/assets/css/tokens.css").statusCode()).isEqualTo(404);
        }
    }

    // ---- missing or wrong properties stop startup with a message that says what to do ---------------------------

    @Test
    void startingWithoutAUsernameStopsWithAClearMessage(CapturedOutput output) {
        assertThatThrownBy(() -> start(NO_CONFIG_FILES, "--jpa.admin.password=" + STRONG))
                .isInstanceOf(RuntimeException.class);

        assertThat(output.getAll())
                .contains("APPLICATION FAILED TO START")
                .contains(USERNAME_REQUIRED)
                .doesNotContain("jpa.admin.password is required")
                .doesNotContain(STRONG);
    }

    @Test
    void startingWithoutAnyCredentialNamesBoth(CapturedOutput output) {
        assertThatThrownBy(() -> start(NO_CONFIG_FILES)).isInstanceOf(RuntimeException.class);

        assertThat(output.getAll())
                .contains("APPLICATION FAILED TO START")
                .contains(USERNAME_REQUIRED)
                .contains("jpa.admin.password is required. Set it in application.yml");
    }

    @Test
    void startingWithABlankUsernameStopsToo(CapturedOutput output) {
        assertThatThrownBy(() -> start("--jpa.admin.username=", "--jpa.admin.password=" + STRONG))
                .isInstanceOf(RuntimeException.class);

        assertThat(output.getAll()).contains(USERNAME_REQUIRED).doesNotContain(STRONG);
    }

    @Test
    void anUnsetEnvironmentVariablePlaceholderStopsStartupInsteadOfBecomingThePassword(CapturedOutput output) {
        assertThatThrownBy(() -> start("--jpa.admin.username=admin",
                "--jpa.admin.password=${JPA_ADMIN_SAMPLE_TEST_VARIABLE_THAT_IS_NOT_SET}"))
                .isInstanceOf(RuntimeException.class);

        assertThat(output.getAll()).contains("jpa.admin.password refers to a variable that has no value");
    }

    @Test
    void aBadPathStopsStartup(CapturedOutput output) {
        assertThatThrownBy(() -> start("--jpa.admin.path=/admin/", "--jpa.admin.password=" + STRONG))
                .isInstanceOf(RuntimeException.class);

        assertThat(output.getAll()).contains("jpa.admin.path must start with '/'").doesNotContain(STRONG);
    }

    @Test
    void aBadColourStopsStartup(CapturedOutput output) {
        assertThatThrownBy(() -> start("--jpa.admin.primary-color=red", "--jpa.admin.password=" + STRONG))
                .isInstanceOf(RuntimeException.class);

        assertThat(output.getAll()).contains("jpa.admin.primary-color must be a colour written as #rrggbb")
                .doesNotContain(STRONG);
    }

    // ---- weak passwords (SEC-7) -----------------------------------------------------------------------------

    @Test
    void underTheProdProfileAWeakPasswordStopsStartupWithoutPrintingIt(CapturedOutput output) {
        assertThatThrownBy(() -> start("--spring.profiles.active=prod", "--jpa.admin.password=" + WEAK))
                .isInstanceOf(RuntimeException.class);

        assertThat(output.getAll())
                .contains("APPLICATION FAILED TO START")
                .contains("jpa.admin.password is too weak for the 'prod' profile")
                .contains("it is shorter than 12 characters")
                .doesNotContain(WEAK);
    }

    @Test
    void underTheProdProfileAStrongPasswordStarts(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start("--spring.profiles.active=prod",
                "--jpa.admin.password=" + STRONG)) {
            assertThat(get(context, "/admin/login").statusCode()).isEqualTo(200);
        }
        assertThat(output.getAll()).doesNotContain("is weak").doesNotContain(STRONG);
    }

    @Test
    void underAnyOtherProfileAWeakPasswordOnlyWarnsWithoutPrintingIt(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start("--jpa.admin.password=" + WEAK)) {
            assertThat(get(context, "/admin/login").statusCode()).isEqualTo(200);
        }

        assertThat(output.getAll())
                .contains("jpa.admin.password is weak: it is shorter than 12 characters")
                .doesNotContain(WEAK);
    }

    // ---- SEC-8: plain HTTP under prod ----------------------------------------------------------------------------------

    private static final String PLAIN_HTTP_WARNING = "is served over plain HTTP while the 'prod' profile is active";

    @Test
    void underProdOverPlainHttpTheStartupLogsTheWarningAndTheConsoleStillWorks(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start("--spring.profiles.active=prod",
                "--jpa.admin.password=" + STRONG)) {
            assertThat(get(context, "/admin/login").statusCode()).isEqualTo(200);
        }

        assertThat(output.getAll()).containsOnlyOnce(PLAIN_HTTP_WARNING).contains("reverse proxy").doesNotContain(STRONG);
    }

    @Test
    void underProdBehindATrustedProxyOrOutsideProdThereIsNoSuchWarning(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext context = start("--spring.profiles.active=prod",
                "--server.forward-headers-strategy=framework", "--jpa.admin.password=" + STRONG)) {
            assertThat(get(context, "/admin/login").statusCode()).isEqualTo(200);
        }
        try (ConfigurableApplicationContext context = start("--jpa.admin.password=" + STRONG)) {
            assertThat(get(context, "/admin/login").statusCode()).isEqualTo(200);
        }

        assertThat(output.getAll()).doesNotContain(PLAIN_HTTP_WARNING);
    }

    @Test
    void aPasswordOverTheBcryptLimitStopsStartupAndNamesTheProperty(CapturedOutput output) {
        String tooLong = "p".repeat(80);
        assertThatThrownBy(() -> start("--jpa.admin.password=" + tooLong)).isInstanceOf(RuntimeException.class);

        assertThat(output.getAll()).contains("jpa.admin.password is longer than 72 bytes").doesNotContain(tooLong);
    }
}
