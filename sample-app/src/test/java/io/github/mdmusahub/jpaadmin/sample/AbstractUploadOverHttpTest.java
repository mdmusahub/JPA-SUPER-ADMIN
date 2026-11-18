package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.storage.UploadTargets;
import io.github.mdmusahub.jpaadmin.storage.UploadTicketService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.MultipartConfigElement;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Uploads end to end over real HTTP against embedded Tomcat, the way a browser sees them (UPL-1 to UPL-7): sign in, a ticket,
 * a real PNG, the stored address, the file served back to the signed-in admin and refused to everyone else, and every refusal
 * leaving nothing on the disk. Real Tomcat matters here: only it parses a multipart body before Spring Security's CSRF filter
 * reads the hidden {@code _csrf} field, and only it enforces the host's multipart limits before any controller runs.
 *
 * <p>The tests run with {@code spring.jpa.open-in-view} both true and false (D-29): the concrete subclasses set it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractUploadOverHttpTest {

    private static final String PASSWORD = "Zx9-fake-strong-pw-QWERTY-7731";

    private Path temp;
    private ConfigurableApplicationContext context;
    private Path storage;
    private String base;

    abstract String openInView();

    private static ConfigurableApplicationContext start(Path directory, String openInView, String... extra) {
        java.util.List<String> args = new java.util.ArrayList<>(java.util.List.of("--server.port=0", "--spring.main.banner-mode=off",
                "--spring.config.name=jpa-admin-no-such-config", "--jpa.admin.username=admin", "--jpa.admin.password=" + PASSWORD,
                "--jpa.admin.storage.default.directory=" + directory.toString().replace('\\', '/'),
                "--spring.servlet.multipart.max-file-size=5MB", "--spring.servlet.multipart.max-request-size=6MB",
                "--spring.jpa.open-in-view=" + openInView, "--spring.jpa.hibernate.ddl-auto=create-drop"));
        // A later setting replaces an earlier one with the same name; two command-line values for one name would be joined.
        for (String setting : extra) {
            String name = setting.substring(0, setting.indexOf('='));
            args.removeIf(existing -> existing.startsWith(name + "="));
            args.add(setting);
        }
        return SpringApplication.run(SampleApplication.class, args.toArray(String[]::new));
    }

    @BeforeAll
    void startTheSampleApp() throws IOException {
        Path target = Files.createDirectories(Path.of("target"));
        temp = Files.createTempDirectory(target, "upload-http-");
        storage = Files.createDirectories(temp.resolve("shared-storage"));
        context = start(storage, openInView());
        base = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
    }

    @AfterAll
    void stopTheSampleApp() throws IOException {
        if (context != null) {
            context.close();
            context = null;
        }
        if (temp != null && Files.exists(temp)) {
            try (Stream<Path> walk = Files.walk(temp)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    // ---- a browser and a multipart body ------------------------------------------------------------------------------------------

    /** A browser: keeps cookies, follows nothing. */
    static final class Browser {
        final CookieManager cookies = new CookieManager();
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        final String root;

        Browser(String root) {
            this.root = root;
        }

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(root + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<byte[]> getBytes(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(root + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        }

        HttpResponse<String> send(String method, String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(root + path)).method(method,
                    HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> postForm(String path, String... fields) throws Exception {
            StringBuilder body = new StringBuilder();
            for (int i = 0; i + 1 < fields.length; i += 2) {
                body.append(i > 0 ? "&" : "").append(URLEncoder.encode(fields[i], StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(fields[i + 1], StandardCharsets.UTF_8));
            }
            return client.send(HttpRequest.newBuilder(URI.create(root + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> upload(Multipart body, String csrfHeader, String accept) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(root + "/admin/uploads"))
                    .header("Content-Type", body.contentType()).header("Accept", accept)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.build()));
            if (csrfHeader != null) {
                request.header("X-CSRF-TOKEN", csrfHeader);
            }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        void signIn() throws Exception {
            HttpResponse<String> signedIn = postForm("/admin/login", "username", "admin", "password", PASSWORD, "_csrf",
                    token(get("/admin/login").body()));
            assertThat(signedIn.statusCode()).isEqualTo(302);
        }

        /** The CSRF token of a page the signed-in admin can see. */
        String csrf() throws Exception {
            return token(get("/admin").body());
        }
    }

    static String token(String html) {
        Matcher matcher = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).as("a CSRF field in the page").isTrue();
        return matcher.group(1);
    }

    /** A multipart/form-data body, built by hand. */
    static final class Multipart {
        private final String boundary = "----jpaadmin" + UUID.randomUUID().toString().replace("-", "");
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Multipart text(String name, String value) {
            write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
            return this;
        }

        Multipart file(String field, String filename, String contentType, byte[] bytes) {
            write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename
                    + "\"\r\nContent-Type: " + contentType + "\r\n\r\n");
            out.writeBytes(bytes);
            write("\r\n");
            return this;
        }

        byte[] build() {
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            all.writeBytes(out.toByteArray());
            all.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.ISO_8859_1));
            return all.toByteArray();
        }

        String contentType() {
            return "multipart/form-data; boundary=" + boundary;
        }

        private void write(String text) {
            out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                image.setRGB(x, y, 0x225588 + x * 9 + y);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    static String ticket(ConfigurableApplicationContext running, String field, long max) {
        return running.getBean(UploadTicketService.class).issue("category", field, max);
    }

    static long files(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    private Multipart good(String ticket, String filename, String type, byte[] bytes) {
        return new Multipart().text("entity", "category").text("field", "image").text("ticket", ticket)
                .file("file", filename, type, bytes);
    }

    private static String urlOf(String json) {
        Matcher matcher = Pattern.compile("\"url\":\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).as("a url in " + json).isTrue();
        return matcher.group(1);
    }

    // ---- the sample app is set up for uploads -------------------------------------------------------------------------------------------

    @Test
    void theSampleHasOneUploadFieldAndItsTargetIsTheCategoryImage() {
        UploadTargets targets = context.getBean(UploadTargets.class);

        assertThat(targets.all()).hasSize(1);
        UploadTargets.Target target = targets.find("category", "image").orElseThrow();
        assertThat(target.storage()).isEqualTo("default");
        assertThat(target.folder()).isEqualTo("categories");
        assertThat(target.accept()).containsExactly("image/png", "image/jpeg", "image/gif", "image/webp");
        assertThat(target.maxBytes()).isEqualTo(2L * 1024 * 1024);
    }

    @Test
    void theSeedRowsHaveNoImage() {
        EntityManagerFactory factory = context.getBean(EntityManagerFactory.class);
        try (EntityManager em = factory.createEntityManager()) {
            Long withImage = em.createQuery("select count(c) from Category c where c.image is not null", Long.class)
                    .getSingleResult();
            Long all = em.createQuery("select count(c) from Category c", Long.class).getSingleResult();

            assertThat(withImage).isZero();
            assertThat(all).isPositive();
        }
    }

    // ---- sign in, upload, view and serve ---------------------------------------------------------------------------------------------------

    @Test
    void signInUploadViewAndServeTheFile() throws Exception {
        Browser admin = new Browser(base);
        admin.signIn();
        byte[] png = png();
        long before = files(storage);

        HttpResponse<String> uploaded = admin.upload(good(ticket(context, "image", 2L * 1024 * 1024), "my picture.png", "image/png",
                png), admin.csrf(), "application/json");

        assertThat(uploaded.statusCode()).isEqualTo(200);
        assertThat(uploaded.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        String url = urlOf(uploaded.body());
        assertThat(url).matches("/admin/files/default/categories/\\d{4}/\\d{2}/[0-9a-f]{32}\\.png");
        assertThat(uploaded.body()).contains("\"name\":\"my picture.png\"").contains("\"error\":null");
        assertThat(files(storage)).isEqualTo(before + 1);

        // Serve: the signed-in admin gets the bytes back, as an image, with the safe headers.
        HttpResponse<byte[]> served = admin.getBytes(url);
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.body()).isEqualTo(png);
        assertThat(served.headers().firstValue("Content-Type").orElseThrow()).isEqualTo("image/png");
        assertThat(served.headers().firstValue("Content-Disposition").orElseThrow()).isEqualTo("inline");
        assertThat(served.headers().firstValue("X-Content-Type-Options").orElseThrow()).isEqualTo("nosniff");
        assertThat(served.headers().firstValue("Content-Security-Policy").orElseThrow()).contains("default-src 'none'");

        // View: a record that holds the address shows it in the list and on its page, and the address leads to the file.
        Long id = setCategoryImage(url);
        try {
            HttpResponse<String> detail = admin.get("/admin/category/" + id);
            assertThat(detail.statusCode()).isEqualTo(200);
            assertThat(detail.body()).contains(url);
            assertThat(admin.get("/admin/category").body()).contains(url);
        } finally {
            setCategoryImage(null);
        }

        // Anyone without the session is sent to sign in and never gets the file.
        Browser stranger = new Browser(base);
        HttpResponse<byte[]> refused = stranger.getBytes(url);
        assertThat(refused.statusCode()).isEqualTo(302);
        assertThat(refused.headers().firstValue("Location").orElseThrow()).contains("/admin/login");
        assertThat(refused.body()).isNotEqualTo(png);
    }

    /** Sets (or with {@code null} clears) the image of the first category, as the host's own code would. */
    private Long setCategoryImage(String url) {
        EntityManagerFactory factory = context.getBean(EntityManagerFactory.class);
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            Long id = em.createQuery("select c.id from Category c order by c.id", Long.class).setMaxResults(1).getSingleResult();
            em.createQuery("update Category c set c.image = :url where c.id = :id").setParameter("url", url)
                    .setParameter("id", id).executeUpdate();
            em.getTransaction().commit();
            return id;
        }
    }

    @Test
    void aPlainFormUploadWithTheHiddenCsrfFieldRedirectsBackWithAMessage() throws Exception {
        Browser admin = new Browser(base);
        admin.signIn();
        long before = files(storage);
        Multipart form = new Multipart().text("_csrf", admin.csrf()).text("entity", "category").text("field", "image")
                .text("ticket", ticket(context, "image", 2L * 1024 * 1024)).text("record", "3")
                .file("file", "form upload.png", "image/png", png());

        HttpResponse<String> response = admin.upload(form, null, "text/html,application/xhtml+xml");

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/admin/category/3/edit");
        assertThat(files(storage)).isEqualTo(before + 1);
        // The message is shown once, on the page the visitor lands on.
        HttpResponse<String> edit = admin.get("/admin/category/3/edit");
        assertThat(edit.body()).contains("form upload.png").contains("was uploaded");
        assertThat(admin.get("/admin/category/3/edit").body()).doesNotContain("was uploaded");
    }

    @Test
    void aPlainFormUploadWithoutARecordGoesBackToTheCreateForm() throws Exception {
        Browser admin = new Browser(base);
        admin.signIn();
        Multipart form = new Multipart().text("_csrf", admin.csrf()).text("entity", "category").text("field", "image")
                .text("ticket", ticket(context, "image", 2L * 1024 * 1024)).file("file", "a.png", "image/png", png());

        HttpResponse<String> response = admin.upload(form, null, "text/html");

        assertThat(response.statusCode()).isEqualTo(303);
        assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/admin/category/new");
    }

    @Test
    void aRefusedPlainFormShowsAPlainPageWithTheMessageAndStoresNothing() throws Exception {
        Browser admin = new Browser(base);
        admin.signIn();
        long before = files(storage);
        Multipart form = new Multipart().text("_csrf", admin.csrf()).text("entity", "category").text("field", "image")
                .text("ticket", ticket(context, "image", 2L * 1024 * 1024))
                .file("file", "holiday.png", "image/png", "MZ not an image".getBytes(StandardCharsets.ISO_8859_1));

        HttpResponse<String> response = admin.upload(form, null, "text/html");

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/html");
        assertThat(response.body()).contains("The file was not uploaded").contains("not one of the types")
                .doesNotContain("holiday.png").doesNotContain("Exception");
        assertThat(files(storage)).isEqualTo(before);
    }

    // ---- session, CSRF, ticket ------------------------------------------------------------------------------------------------------------

    @Test
    void withoutASessionThePostIsSentToSignInAndNothingIsStored() throws Exception {
        long before = files(storage);
        Browser stranger = new Browser(base);

        HttpResponse<String> response = stranger.upload(good(ticket(context, "image", 1000), "a.png", "image/png", png()), "x",
                "application/json");

        assertThat(response.statusCode()).isIn(302, 401, 403);
        assertThat(response.headers().firstValue("Location").orElse("/admin/login")).contains("/admin/login");
        assertThat(files(storage)).isEqualTo(before);
    }

    @Test
    void aValidTicketAloneIsNotASession() throws Exception {
        Browser stranger = new Browser(base);
        stranger.get("/admin/login");

        HttpResponse<String> response = stranger.upload(good(ticket(context, "image", 1000), "a.png", "image/png", png()),
                token(stranger.get("/admin/login").body()), "application/json");

        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(response.body()).doesNotContain("/admin/files/");
    }

    @Test
    void aPostWithoutTheCsrfTokenIsForbiddenWhateverElseIsRight() throws Exception {
        Browser admin = new Browser(base);
        admin.signIn();
        long before = files(storage);

        HttpResponse<String> none = admin.upload(good(ticket(context, "image", 1000), "a.png", "image/png", png()), null,
                "application/json");
        HttpResponse<String> wrong = admin.upload(good(ticket(context, "image", 1000), "a.png", "image/png", png()), "wrong",
                "application/json");
        Multipart wrongField = new Multipart().text("_csrf", "wrong").text("entity", "category").text("field", "image")
                .text("ticket", ticket(context, "image", 1000)).file("file", "a.png", "image/png", png());
        HttpResponse<String> wrongHidden = admin.upload(wrongField, null, "text/html");
        Multipart noField = new Multipart().text("entity", "category").text("field", "image")
                .text("ticket", ticket(context, "image", 1000)).file("file", "a.png", "image/png", png());
        HttpResponse<String> noHidden = admin.upload(noField, null, "text/html");

        for (HttpResponse<String> response : java.util.List.of(none, wrong, wrongHidden, noHidden)) {
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.body()).doesNotContain("/admin/files/").doesNotContain("Exception");
        }
        assertThat(files(storage)).isEqualTo(before);
        assertThat(admin.get("/admin").statusCode()).as("still signed in").isEqualTo(200);
    }

    @Test
    void noTicketOrABadOneIsForbiddenAndNothingIsStored() throws Exception {
        Browser admin = new Browser(base);
        admin.signIn();
        String csrf = admin.csrf();
        long before = files(storage);

        HttpResponse<String> none = admin.upload(new Multipart().text("entity", "category").text("field", "image")
                .file("file", "a.png", "image/png", png()), csrf, "application/json");
        HttpResponse<String> garbage = admin.upload(good("v1.a.b", "a.png", "image/png", png()), csrf, "application/json");
        HttpResponse<String> otherField = admin.upload(good(context.getBean(UploadTicketService.class).issue("category", "name", 1000),
                "a.png", "image/png", png()), csrf, "application/json");
        HttpResponse<String> otherEntity = admin.upload(good(context.getBean(UploadTicketService.class).issue("product", "image",
                1000), "a.png", "image/png", png()), csrf, "application/json");

        for (HttpResponse<String> response : java.util.List.of(none, garbage, otherField, otherEntity)) {
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.body()).contains("\"url\":null").contains("no longer valid");
        }
        assertThat(files(storage)).isEqualTo(before);
    }

    // ---- dangerous files ---------------------------------------------------------------------------------------------------------------------

    @Test
    void disguisedHiddenAndEmptyFilesAreRefusedAndNothingIsStored() throws Exception {
        Browser admin = new Browser(base);
        admin.signIn();
        String csrf = admin.csrf();
        long before = files(storage);
        byte[] exe = new byte[300];
        exe[0] = 'M';
        exe[1] = 'Z';
        byte[] html = "<!DOCTYPE html><html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        byte[] polyglot = new ByteArrayOutputStream() {
            {
                writeBytes(png());
                writeBytes("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
            }
        }.toByteArray();
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8);

        record Case(String what, String filename, String type, byte[] bytes, int status, String message) {
        }
        for (Case c : java.util.List.of(
                new Case("executable named png", "photo.png", "image/png", exe, 415, "not one of the types"),
                new Case("html named jpg", "photo.jpg", "image/jpeg", html, 415, "not one of the types"),
                new Case("svg", "logo.svg", "image/svg+xml", svg, 415, "not one of the types"),
                new Case("polyglot", "photo.png", "image/png", polyglot, 415, "hidden inside"),
                new Case("empty", "empty.png", "image/png", new byte[0], 400, "empty"),
                new Case("wrong content type with a right body", "photo.png", "text/html", png(), 415, "does not match"),
                new Case("over the field limit", "big.png", "image/png", bigPng(2 * 1024 * 1024 + 1), 413, "2 MB"))) {
            HttpResponse<String> response = admin.upload(good(ticket(context, "image", 5L * 1024 * 1024), c.filename(), c.type(),
                    c.bytes()), csrf, "application/json");

            assertThat(response.statusCode()).as(c.what()).isEqualTo(c.status());
            assertThat(response.body()).as(c.what()).contains("\"url\":null").contains(c.message());
        }
        assertThat(files(storage)).as("nothing was stored").isEqualTo(before);
    }

    private static byte[] bigPng(int size) throws IOException {
        byte[] bytes = new byte[size];
        byte[] head = png();
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }

    // ---- serving ---------------------------------------------------------------------------------------------------------------------------------

    @Test
    void anHtmlFileThatReachedTheFolderAnotherWayIsServedAsAnAttachment() throws Exception {
        Browser admin = new Browser(base);
        admin.signIn();
        Path planted = storage.resolve("categories/2026/11/0123456789abcdef0123456789abcdef.html");
        Files.createDirectories(planted.getParent());
        Files.writeString(planted, "<html><script>alert(1)</script></html>");

        HttpResponse<byte[]> response = admin.getBytes("/admin/files/default/categories/2026/11/0123456789abcdef0123456789abcdef.html");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).isEqualTo("application/octet-stream");
        assertThat(response.headers().firstValue("Content-Disposition").orElseThrow()).startsWith("attachment");
        assertThat(response.headers().firstValue("X-Content-Type-Options").orElseThrow()).isEqualTo("nosniff");
    }

    @Test
    void everyAnswerOfTheFileRouteCarriesTheSafeHeaders() throws Exception {
        Browser admin = new Browser(base);
        admin.signIn();

        for (String path : new String[] {"/admin/files/default/categories/2026/11/0123456789abcdef0123456789abcdef.png",
                "/admin/files/nothing/2026/11/0123456789abcdef0123456789abcdef.png", "/admin/files/default/x"}) {
            HttpResponse<String> response = admin.get(path);

            assertThat(response.statusCode()).as(path).isEqualTo(404);
            assertThat(response.headers().firstValue("X-Content-Type-Options")).as(path).hasValue("nosniff");
            assertThat(response.headers().firstValue("Content-Security-Policy")).as(path).isPresent();
        }
    }

    @Test
    void traversalInAnyFormIsRefusedOverRealHttpAndNeverServesAFileFromOutsideTheStorage() throws Exception {
        Browser admin = new Browser(base);
        admin.signIn();
        Path secret = temp.resolve("secret.png");
        Files.write(secret, png());

        for (String path : new String[] {"/admin/files/default/../../secret.png", "/admin/files/default/%2e%2e/%2e%2e/secret.png",
                "/admin/files/default/..%2f..%2fsecret.png", "/admin/files/default/..;/secret.png",
                "/admin/files/default/categories/2026/11/..%2f..%2f..%2f..%2f..%2fsecret.png",
                "/admin/files/default/categories%2f2026/11/0123456789abcdef0123456789abcdef.png",
                "/admin/files/default/%2e%2e%5csecret.png", "/admin/files/default/categories/%00", "/admin/files/%252e%252e/secret.png",
                "/admin/files/..%252fsecret.png"}) {
            HttpResponse<byte[]> response = admin.getBytes(path);

            assertThat(response.statusCode()).as(path).isIn(400, 404);
            assertThat(response.body()).as(path).isNotEqualTo(png());
        }
    }

    @Test
    void aFileRouteThatIsNotAGetIsNotAllowedOrNotFoundAndNeverServesAFile() throws Exception {
        Browser admin = new Browser(base);
        admin.signIn();

        HttpResponse<String> response = admin.send("DELETE", "/admin/files/default/categories/2026/11/0123456789abcdef0123456789abcdef.png");

        assertThat(response.statusCode()).isIn(403, 404, 405);
    }

    // ---- the host's own limits and routes ---------------------------------------------------------------------------------------------------------

    @Test
    void theHostsMultipartSettingsAreTheHostsOwnAndTheConsoleDidNotChangeThem() {
        MultipartConfigElement host = context.getBean(MultipartConfigElement.class);

        assertThat(host.getMaxFileSize()).isEqualTo(5L * 1024 * 1024);
        assertThat(host.getMaxRequestSize()).isEqualTo(6L * 1024 * 1024);
        assertThat(context.getBeansOfType(MultipartConfigElement.class)).hasSize(1);
    }

    @Test
    void withNoPublicPathTheConsoleRegistersNoRouteOutsideItsOwnPath() throws Exception {
        assertThat(context.containsBean("jpaAdminPublicFilesMapping")).isFalse();
        Browser anyone = new Browser(base);

        assertThat(anyone.get("/files/categories/2026/11/0123456789abcdef0123456789abcdef.png").statusCode()).isEqualTo(404);
        assertThat(anyone.get("/session").statusCode()).as("the host's own route").isEqualTo(200);
    }

    // ---- the host's multipart limit, public path, and the key (own contexts) -------------------------------------------------------------------------------

    @Test
    void aFileOverTheHostsMultipartLimitIsReportedClearlyAndNothingIsStored() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("limit-storage"));
        try (ConfigurableApplicationContext small = start(folder, openInView(), "--spring.servlet.multipart.max-file-size=1MB",
                "--spring.servlet.multipart.max-request-size=2MB")) {
            String root = "http://localhost:" + small.getEnvironment().getProperty("local.server.port");
            Browser admin = new Browser(root);
            admin.signIn();
            String csrf = admin.csrf();

            HttpResponse<String> response = admin.upload(good(ticket(small, "image", 2L * 1024 * 1024), "big.png", "image/png",
                    bigPng(1536 * 1024)), csrf, "application/json");

            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
            assertThat(response.body()).contains("larger than this server accepts", "spring.servlet.multipart.max-file-size")
                    .contains("\"url\":null");
            assertThat(files(folder)).isZero();
            MultipartConfigElement host = small.getBean(MultipartConfigElement.class);
            assertThat(host.getMaxFileSize()).as("the console did not raise the host's limit").isEqualTo(1024 * 1024);
            assertThat(host.getMaxRequestSize()).isEqualTo(2L * 1024 * 1024);
            // A plain form (hidden _csrf field, no script) over the same limit. Tomcat stops reading the body at the limit while
            // Spring Security's CSRF filter is still looking for the token, so the answer is Tomcat's 413 and the HOST's error
            // page, not the console's. That is a limit of the plain-form flow (documented): the script flow above gets the message.
            Multipart form = new Multipart().text("_csrf", csrf).text("entity", "category").text("field", "image")
                    .text("ticket", ticket(small, "image", 2L * 1024 * 1024)).file("file", "big.png", "image/png", bigPng(1536 * 1024));
            HttpResponse<String> plain = admin.upload(form, null, "text/html");
            assertThat(plain.statusCode()).isEqualTo(413);
            assertThat(plain.body()).doesNotContain("/admin/files/");
            assertThat(files(folder)).isZero();
            // A file inside the host's limit still works in the same application.
            assertThat(admin.upload(good(ticket(small, "image", 2L * 1024 * 1024), "ok.png", "image/png", png()), csrf,
                    "application/json").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void thePublicPathServesTheFileToEveryoneWithTheSafeHeadersWhileTheConsoleRouteStillNeedsASession() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("public-storage"));
        try (ConfigurableApplicationContext open = start(folder, openInView(),
                "--jpa.admin.storage.default.public-path=/public-files")) {
            String root = "http://localhost:" + open.getEnvironment().getProperty("local.server.port");
            assertThat(open.containsBean("jpaAdminPublicFilesMapping")).isTrue();
            Browser admin = new Browser(root);
            admin.signIn();
            byte[] png = png();
            HttpResponse<String> uploaded = admin.upload(good(ticket(open, "image", 2L * 1024 * 1024), "a.png", "image/png", png),
                    admin.csrf(), "application/json");
            String url = urlOf(uploaded.body());

            assertThat(url).matches("/public-files/categories/\\d{4}/\\d{2}/[0-9a-f]{32}\\.png");
            Browser anyone = new Browser(root);
            HttpResponse<byte[]> publicAnswer = anyone.getBytes(url);
            assertThat(publicAnswer.statusCode()).isEqualTo(200);
            assertThat(publicAnswer.body()).isEqualTo(png);
            assertThat(publicAnswer.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
            assertThat(publicAnswer.headers().firstValue("Content-Disposition")).hasValue("inline");
            assertThat(publicAnswer.headers().firstValue("Cache-Control")).hasValue("public, max-age=3600");
            String key = url.substring("/public-files/".length());
            assertThat(anyone.getBytes("/admin/files/default/" + key).statusCode()).as("the console route still needs a session")
                    .isEqualTo(302);
            assertThat(admin.getBytes("/admin/files/default/" + key).statusCode()).isEqualTo(200);
            assertThat(anyone.getBytes("/public-files/../secret").statusCode()).isIn(400, 404);
            assertThat(anyone.getBytes("/public-files/%2e%2e/secret").statusCode()).isIn(400, 404);
            assertThat(anyone.send("POST", url).statusCode()).isIn(403, 405);
            assertThat(anyone.get("/session").statusCode()).as("the host's own route").isEqualTo(200);
        }
    }

    @Test
    void aConfiguredSigningKeyKeepsTicketsValidAcrossARestartAndARandomOneDoesNot() throws Exception {
        String key = "a-configured-signing-key-of-32-chars-or-more";
        Path folder = Files.createDirectories(temp.resolve("key-storage"));
        String ticket;
        try (ConfigurableApplicationContext first = start(folder, openInView(), "--jpa.admin.upload.signing-key=" + key)) {
            ticket = ticket(first, "image", 1000);
        }
        try (ConfigurableApplicationContext second = start(folder, openInView(), "--jpa.admin.upload.signing-key=" + key)) {
            assertThat(second.getBean(UploadTicketService.class).verify(ticket, "category", "image").valid()).isTrue();
        }
        String random;
        try (ConfigurableApplicationContext third = start(folder, openInView())) {
            random = ticket(third, "image", 1000);
        }
        try (ConfigurableApplicationContext fourth = start(folder, openInView())) {
            assertThat(fourth.getBean(UploadTicketService.class).verify(random, "category", "image").valid()).isFalse();
        }
    }
}
