package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The whole demo application on a real database, over real HTTP (Phase 14a, D-128): the packaged console, the real Hibernate, the
 * real session and CSRF protection, on H2 (the demo's own database), PostgreSQL and MySQL. One started application per test
 * class, one signed-in browser, and the pages a person uses: the dashboard with its chart, the list of every entity (searched, and
 * sorted by every column both ways), the CSV export of every entity, a detail page, and a record created, edited and deleted
 * through the real forms. A database that makes the console answer with an error page, or put a database word into any page,
 * fails here and the failure names the engine.
 *
 * <p>The demo entity names appear here because this is the sample application, not a library module (ARCH-12). The credentials of
 * the console and of the container are made up for each run and are not written anywhere.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.extension.ExtendWith(SampleEngineSkipLedger.class)
abstract class AbstractSampleEngineTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    /** What no page of the console may ever show: a trace of the database or of Hibernate (SEC-12). */
    private static final List<String> NEVER_IN_A_PAGE = List.of("org.hibernate", "jdbc:", "SQLException", "PSQLException",
            "Caused by", "Exception", "at java.", "could not execute statement");

    /**
     * A fixed, modern, non-UTC zone: the same on a laptop and on CI. The PostgreSQL driver sends the JVM's zone name as the
     * session's time zone, and the Debian 13 based official image refuses the legacy name some JVMs report for the same zone
     * ({@code Asia/Calcutta}), so without this the application cannot even connect (found by the first run, Phase 14a).
     */
    private static final String ZONE = "Asia/Kolkata";

    private java.util.TimeZone previousZone;
    private SampleEngineGate.Decision gate = new SampleEngineGate.Decision(SampleEngineGate.Verdict.RUN, "");
    private SampleEngineDatabase database;
    private ConfigurableApplicationContext context;
    private AdminEntityRegistry registry;
    private Http http;
    private final List<String> bodies = new ArrayList<>();
    private final List<String> aborted = new java.util.concurrent.CopyOnWriteArrayList<>();

    abstract SampleEngineDatabase newDatabase();

    // ---- one application per class ---------------------------------------------------------------------------------------

    @BeforeAll
    void startTheApplication() throws Exception {
        previousZone = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONE));
        database = newDatabase();
        gate = database.needsDocker()
                ? SampleEngineGate.decide(database.name(), database.testClass(), SampleEngineGate.dockerAvailable(),
                        SampleEngineGate.required())
                : new SampleEngineGate.Decision(SampleEngineGate.Verdict.RUN, "");
        if (gate.verdict() == SampleEngineGate.Verdict.FAIL) {
            SampleEngineGate.apply(gate);
        }
        if (gate.verdict() == SampleEngineGate.Verdict.SKIP) {
            System.err.println(gate.reason()); // and again as the reason of every skipped test, see signIn()
            return;
        }
        database.start();
        String password = "pw-" + HexFormat.of().formatHex(randomBytes(12));
        List<String> arguments = new ArrayList<>(List.of("--server.port=0", "--spring.main.banner-mode=off",
                "--spring.config.name=jpa-admin-no-such-config", "--jpa.admin.username=admin",
                "--jpa.admin.password=" + password, "--jpa.admin.storage.default.directory=target/engine-uploads",
                "--spring.jpa.open-in-view=false", "--spring.jpa.hibernate.ddl-auto=create-drop"));
        arguments.addAll(database.arguments());
        context = SpringApplication.run(SampleApplication.class, arguments.toArray(String[]::new));
        registry = context.getBean(AdminEntityRegistry.class);
        http = new Http("http://localhost:" + context.getEnvironment().getProperty("local.server.port"));
        HttpResponse<String> signedIn = http.post("/admin/login", "username", "admin", "password", password, "_csrf",
                token(http.get("/admin/login").body()));
        assertThat(signedIn.statusCode()).as("signing in to the console").isEqualTo(302);
    }

    /** Called by {@link SampleEngineSkipLedger} for every test that was aborted. */
    void recordAbort(String reason) {
        aborted.add(reason);
    }

    @AfterAll
    void stopTheApplication() {
        String silentSkip = SampleEngineSkipLedger.problem(gate, database == null ? "unknown engine" : database.name(), aborted);
        try {
            closeTheApplication();
        } finally {
            if (silentSkip != null) {
                throw new AssertionError(silentSkip);
            }
        }
    }

    private void closeTheApplication() {
        try {
            if (context != null) {
                context.close();
            }
        } finally {
            try {
                if (database != null) {
                    database.close();
                }
            } finally {
                if (previousZone != null) {
                    java.util.TimeZone.setDefault(previousZone);
                }
            }
        }
    }

    @BeforeEach
    void skipWhenThereIsNoDocker() {
        SampleEngineGate.apply(gate); // each skipped test carries the reason; a class aborted in @BeforeAll would show "0 tests run"
        bodies.clear();
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private String label() {
        return database.name();
    }

    // ---- what the tests say ------------------------------------------------------------------------------------------------

    @Test
    void theDashboardAndItsChartAnswerAndCountEveryEntity() throws Exception {
        String page = get("/admin", 200);

        assertThat(page).as(label() + ": the chart card is there").contains("Records created");
        assertThat(page).as(label() + ": no count and no chart is unavailable").doesNotContain("Count unavailable")
                .doesNotContainIgnoringCase("chart is unavailable");
        for (AdminEntity entity : registry.all()) {
            assertThat(page).as(label() + ": the dashboard names " + entity.urlName()).contains("/admin/" + entity.urlName());
        }
        assertNoDatabaseWordsAnywhere();
    }

    @Test
    void everyEntityHasAListThatSearchesAndSortsByEveryColumnBothWays() throws Exception {
        for (AdminEntity entity : registry.all()) {
            String list = "/admin/" + entity.urlName();
            get(list, 200);
            for (String text : List.of("a", "e", "%", "_", "o'brien", "ZÜRICH", "x\" or 1=1 --")) {
                get(list + "?q=" + encode(text), 200);
            }
            for (AdminField field : entity.fields()) {
                if (field.hidden() || field.sensitive()) {
                    continue;
                }
                for (String dir : List.of("asc", "desc")) {
                    get(list + "?sort=" + encode(field.name()) + "&dir=" + dir, 200);
                    get(list + "?sort=" + encode(field.name()) + "&dir=" + dir + "&size=10&page=1", 200);
                }
            }
        }
        assertNoDatabaseWordsAnywhere();
    }

    @Test
    void everyEntityExportsACsvFileWithAHeaderAndItsRows() throws Exception {
        for (AdminEntity entity : registry.all()) {
            if (!entity.canExport()) {
                continue;
            }
            HttpResponse<String> response = http.get("/admin/" + entity.urlName() + "/export.csv");

            assertThat(response.statusCode()).as(label() + ": export of " + entity.urlName()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
            String[] lines = response.body().split("\r\n");
            assertThat(lines.length).as(label() + ": a header and at least one row of " + entity.urlName())
                    .isGreaterThanOrEqualTo(2);
            assertThat(response.body()).as("a secret is in no file").doesNotContainIgnoringCase("password");
        }
    }

    @Test
    void aRecordCanBeOpenedCreatedEditedAndDeletedThroughTheRealForms() throws Exception {
        // The detail page of the first product.
        String products = get("/admin/product", 200);
        Matcher first = Pattern.compile("href=\"(/admin/product/\\d+)[?\"]").matcher(products);
        assertThat(first.find()).as(label() + ": a row of the product list links to its detail page").isTrue();
        String detail = get(first.group(1), 200);
        assertThat(detail).contains("Category");

        // Create a tag.
        String name = "engine-" + HexFormat.of().formatHex(randomBytes(4));
        String form = get("/admin/tag/new", 200);
        HttpResponse<String> created = http.post("/admin/tag", "name", name, "_csrf", token(form));
        assertThat(created.statusCode()).as(label() + ": a created record goes back to the list").isEqualTo(303);
        String found = get("/admin/tag?q=" + encode(name), 200);
        Matcher link = Pattern.compile("href=\"/admin/tag/(\\d+)[?\"]").matcher(found);
        assertThat(link.find()).as(label() + ": the new tag is in the list").isTrue();
        String id = link.group(1);

        // The same name again: refused in the console's words, nothing saved twice.
        HttpResponse<String> twice = http.post("/admin/tag", "name", name, "_csrf", token(get("/admin/tag/new", 200)));
        bodies.add(twice.body());
        assertThat(twice.statusCode()).as(label() + ": a clash is shown again, not an error").isIn(200, 422);
        assertThat(twice.body().toLowerCase(Locale.ROOT)).doesNotContain("unique", "duplicate", "constraint", "violat");
        Matcher again = Pattern.compile("href=\"/admin/tag/(\\d+)[?\"]").matcher(get("/admin/tag?q=" + encode(name), 200));
        java.util.Set<String> ids = new java.util.HashSet<>();
        while (again.find()) {
            ids.add(again.group(1));
        }
        assertThat(ids).as(label() + ": the clash was not saved, so one tag has that name").containsExactly(id);

        // Edit it.
        String renamed = name + "-b";
        HttpResponse<String> edited = http.post("/admin/tag/" + id, "name", renamed, "_csrf",
                token(get("/admin/tag/" + id + "/edit", 200)));
        assertThat(edited.statusCode()).as(label() + ": an edit goes back to the list").isEqualTo(303);
        assertThat(get("/admin/tag?q=" + encode(renamed), 200)).contains("/admin/tag/" + id);

        // Delete it.
        HttpResponse<String> deleted = http.post("/admin/tag/" + id + "/delete", "_csrf", token(get("/admin/tag/" + id, 200)));
        assertThat(deleted.statusCode()).as(label() + ": a delete goes back to the list").isEqualTo(303);
        assertThat(get("/admin/tag?q=" + encode(renamed), 200)).doesNotContain("/admin/tag/" + id);
        assertNoDatabaseWordsAnywhere();
    }

    @Test
    void aCategoryThatProductsPointAtIsKeptAndTheAnswerIsPlain() throws Exception {
        String categories = get("/admin/category?q=Electronics", 200);
        Matcher link = Pattern.compile("href=\"/admin/category/(\\d+)[?\"]").matcher(categories);
        assertThat(link.find()).as(label() + ": the Electronics category is in the demo data").isTrue();
        String id = link.group(1);

        HttpResponse<String> refused = http.post("/admin/category/" + id + "/delete", "_csrf", token(get("/admin/category/" + id, 200)));
        String after = http.get(refused.headers().firstValue("Location").orElse("/admin/category")).body();
        bodies.add(after);

        assertThat(refused.statusCode()).isIn(302, 303);
        assertThat(get("/admin/category/" + id, 200)).as(label() + ": the category is still there").contains("Electronics");
        assertThat(after).as(label() + ": the visitor is told, in plain words").contains("was not deleted. Other records still depend on it");
        assertNoDatabaseWordsAnywhere();
    }

    @Test
    void theNextRequestWorksAfterARefusedDeleteOnADatabaseThatAbortsTheTransaction() throws Exception {
        String categories = get("/admin/category?q=Electronics", 200);
        Matcher link = Pattern.compile("href=\"/admin/category/(\\d+)[?\"]").matcher(categories);
        assertThat(link.find()).isTrue();
        String id = link.group(1);
        http.post("/admin/category/" + id + "/delete", "_csrf", token(get("/admin/category/" + id, 200)));

        assertThat(get("/admin/product", 200)).as(label() + ": a list answers right after the refusal").contains("Products");
        assertThat(get("/admin", 200)).contains("Records created");
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------------

    private String get(String path, int expectedStatus) throws Exception {
        HttpResponse<String> response = http.get(path);
        bodies.add(response.body());
        assertThat(response.statusCode()).as(label() + ": GET " + path).isEqualTo(expectedStatus);
        return response.body();
    }

    private void assertNoDatabaseWordsAnywhere() {
        for (String body : bodies) {
            for (String word : NEVER_IN_A_PAGE) {
                assertThat(body).as(label() + ": a page must not show \"" + word + "\"").doesNotContain(word);
            }
        }
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static String token(String html) {
        Matcher matcher = TOKEN.matcher(html);
        assertThat(matcher.find()).as("a CSRF field in the page").isTrue();
        return matcher.group(1);
    }

    /** A browser: keeps cookies, follows nothing. */
    private static final class Http {
        private final CookieManager cookies = new CookieManager();
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        private final String root;

        Http(String root) {
            this.root = root;
        }

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(path.startsWith("http") ? path : root + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String... fields) throws Exception {
            StringBuilder body = new StringBuilder();
            for (int i = 0; i + 1 < fields.length; i += 2) {
                body.append(i > 0 ? "&" : "").append(encode(fields[i])).append('=').append(encode(fields[i + 1]));
            }
            return client.send(HttpRequest.newBuilder(URI.create(root + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
