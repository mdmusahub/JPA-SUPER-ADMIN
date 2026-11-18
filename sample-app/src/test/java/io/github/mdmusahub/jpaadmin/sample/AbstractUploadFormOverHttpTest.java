package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.sample.AbstractUploadOverHttpTest.Browser;
import io.github.mdmusahub.jpaadmin.sample.AbstractUploadOverHttpTest.Multipart;
import io.github.mdmusahub.jpaadmin.storage.UploadTicketService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Phase 9b over real HTTP (Tomcat, the real security chain, the real forms): the upload field of a category's form with a ticket
 * of its own, the no-script round trip (a plain multipart form with the hidden CSRF field and the ticket, the redirect back, the
 * address in the field, the save), replace, remove and blank, an address the storage did not make, the thumbnails, and the
 * copy. Run with {@code spring.jpa.open-in-view} on and off (D-29): the concrete subclasses set it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractUploadFormOverHttpTest {

    private static final String PASSWORD = "Zx9-fake-strong-pw-QWERTY-7731";
    private static final Pattern FIELD = Pattern.compile("<input type=\"hidden\" name=\"%s\" value=\"([^\"]*)\"");

    private Path temp;
    private ConfigurableApplicationContext context;
    private Path storage;
    private String base;

    abstract String openInView();

    @BeforeAll
    void start() throws IOException {
        temp = Files.createTempDirectory(Files.createDirectories(Path.of("target")), "upload-form-http-");
        storage = Files.createDirectories(temp.resolve("storage"));
        context = SpringApplication.run(SampleApplication.class, "--server.port=0", "--spring.main.banner-mode=off",
                "--spring.config.name=jpa-admin-no-such-config", "--jpa.admin.username=admin", "--jpa.admin.password=" + PASSWORD,
                "--jpa.admin.storage.default.directory=" + storage.toString().replace('\\', '/'),
                "--spring.servlet.multipart.max-file-size=5MB", "--spring.servlet.multipart.max-request-size=6MB",
                "--spring.jpa.open-in-view=" + openInView(), "--spring.jpa.hibernate.ddl-auto=create-drop");
        base = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
    }

    @AfterAll
    void stop() throws IOException {
        if (context != null) {
            context.close();
        }
        if (temp != null && Files.exists(temp)) {
            try (Stream<Path> walk = Files.walk(temp)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @AfterEach
    void putTheSampleBack() {
        setImage(null);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------------------

    private Browser signedIn() throws Exception {
        Browser browser = new Browser(base);
        browser.signIn();
        return browser;
    }

    /** The first category: its id and its name (a category needs a name to be saved). */
    private record Subject(Long id, String name) {
    }

    private Subject first() {
        try (EntityManager em = context.getBean(EntityManagerFactory.class).createEntityManager()) {
            Object[] row = em.createQuery("select c.id, c.name from Category c order by c.id", Object[].class).setMaxResults(1)
                    .getSingleResult();
            return new Subject((Long) row[0], (String) row[1]);
        }
    }

    private void setImage(String url) {
        if (context == null) {
            return;
        }
        try (EntityManager em = context.getBean(EntityManagerFactory.class).createEntityManager()) {
            em.getTransaction().begin();
            em.createQuery("update Category c set c.image = :url").setParameter("url", url).executeUpdate();
            em.getTransaction().commit();
        }
    }

    private String imageOf(Long id) {
        try (EntityManager em = context.getBean(EntityManagerFactory.class).createEntityManager()) {
            return em.createQuery("select c.image from Category c where c.id = :id", String.class).setParameter("id", id)
                    .getSingleResult();
        }
    }

    private static String hidden(String html, String name) {
        Matcher matcher = Pattern.compile(String.format(FIELD.pattern(), Pattern.quote(name))).matcher(html);
        assertThat(matcher.find()).as("a hidden input called " + name).isTrue();
        return matcher.group(1);
    }

    /** The hidden address of the upload field inside the record form. */
    private static String imageValue(String html) {
        Matcher matcher = Pattern.compile("<input type=\"hidden\" name=\"image\" value=\"([^\"]*)\" data-upload-value>").matcher(html);
        assertThat(matcher.find()).as("the hidden address of the image field").isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> plainUpload(Browser admin, String ticket, String csrf, String record, byte[] bytes) throws Exception {
        Multipart form = new Multipart();
        if (csrf != null) {
            form.text("_csrf", csrf);
        }
        form.text("entity", "category").text("field", "image");
        if (ticket != null) {
            form.text("ticket", ticket);
        }
        if (record != null) {
            form.text("record", record);
        }
        form.file("file", "plain form.png", "image/png", bytes);
        return admin.upload(form, null, "text/html,application/xhtml+xml");
    }

    private HttpResponse<String> saveCategory(Browser admin, Subject subject, String... extra) throws Exception {
        String csrf = hidden(admin.get("/admin/category/" + subject.id() + "/edit").body(), "_csrf");
        List<String> fields = new ArrayList<>(List.of("_csrf", csrf, "name", subject.name()));
        fields.addAll(List.of(extra));
        return admin.postForm("/admin/category/" + subject.id(), fields.toArray(String[]::new));
    }

    private static int formDepth(String html) {
        int depth = 0;
        int deepest = 0;
        Matcher tags = Pattern.compile("(?i)<(/?)form\\b").matcher(html);
        while (tags.find()) {
            depth += tags.group(1).isEmpty() ? 1 : -1;
            deepest = Math.max(deepest, depth);
        }
        return deepest;
    }

    // ---- the form renders the upload field with a ticket of its own ------------------------------------------------------------------------

    @Test
    void theEditFormHasTheUploadFieldAndATicketBoundToThatFieldAndNoOtherFieldHasOne() throws Exception {
        Browser admin = signedIn();
        Subject category = first();

        String html = admin.get("/admin/category/" + category.id() + "/edit").body();

        assertThat(html).contains("<form id=\"record-form\"", "enctype=\"multipart/form-data\"", "type=\"file\" name=\"file\" id=\"f-image\"");
        String ticket = hidden(html, "ticket");
        assertThat(context.getBean(UploadTicketService.class).verify(ticket, "category", "image").valid()).isTrue();
        assertThat(context.getBean(UploadTicketService.class).verify(ticket, "category", "name").valid()).isFalse();
        assertThat(Pattern.compile("name=\"ticket\"").matcher(html).results().count()).isEqualTo(1);
        assertThat(hidden(html, "entity")).isEqualTo("category");
        assertThat(hidden(html, "field")).isEqualTo("image");
        assertThat(hidden(html, "record")).isEqualTo(String.valueOf(category.id()));
        assertThat(html).contains("data-accept=\"image/png,image/jpeg,image/gif,image/webp\"", "data-max-bytes=\"2097152\"");
        assertThat(formDepth(html)).as("no form inside another form").isLessThanOrEqualTo(2);
    }

    @Test
    void noFormInsideAnotherFormOnTheCreateAndEditPagesAndTheTicketIsNeverInALogLine() throws Exception {
        Browser admin = signedIn();
        Subject category = first();

        for (String path : List.of("/admin/category/new", "/admin/category/" + category.id() + "/edit")) {
            String html = admin.get(path).body();
            // The sidebar's sign-out form closes before the main area; inside the main area the depth never reaches two.
            String main = html.substring(html.indexOf("<main"), html.indexOf("</main>"));
            assertThat(formDepth(main)).as(path).isEqualTo(1);
            assertThat(main).contains("<form id=\"record-form\"").doesNotContain("th:").doesNotContain("<script");
        }
    }

    @Test
    void otherEntitiesFormsHaveNoUploadSectionAndNoTicket() throws Exception {
        Browser admin = signedIn();

        for (String path : List.of("/admin/product/new", "/admin/customer/new", "/admin/tag/new", "/admin/order/new")) {
            String html = admin.get(path).body();
            assertThat(html).as(path).doesNotContain("name=\"ticket\"").doesNotContain("data-upload").doesNotContain("type=\"file\"");
        }
    }

    // ---- the no-script path, end to end ----------------------------------------------------------------------------------------------------------

    @Test
    void aPlainFormUploadIsRedirectedBackTheAddressIsInTheFieldOnceAndTheSaveStoresItAndTheThumbnailsShowIt() throws Exception {
        Browser admin = signedIn();
        Subject category = first();
        String edit = admin.get("/admin/category/" + category.id() + "/edit").body();
        byte[] png = AbstractUploadOverHttpTest.png();

        HttpResponse<String> sent = plainUpload(admin, hidden(edit, "ticket"), hidden(edit, "_csrf"), String.valueOf(category.id()), png);

        assertThat(sent.statusCode()).isEqualTo(303);
        assertThat(sent.headers().firstValue("Location").orElseThrow()).endsWith("/admin/category/" + category.id() + "/edit");
        String back = admin.get("/admin/category/" + category.id() + "/edit").body();
        String address = imageValue(back);
        assertThat(address).matches("/admin/files/default/categories/\\d{4}/\\d{2}/[0-9a-f]{32}\\.png");
        assertThat(back).contains("plain form.png").contains("was uploaded");
        assertThat(back).contains("<img class=\"upload-preview\" src=\"" + address + "\" alt=\"Image\" data-upload-preview>");
        assertThat(imageOf(category.id())).as("nothing is saved until the record is").isNull();
        assertThat(imageValue(admin.get("/admin/category/" + category.id() + "/edit").body())).as("shown once").isEmpty();

        HttpResponse<String> saved = saveCategory(admin, category, "image", address);

        assertThat(saved.statusCode()).isEqualTo(303);
        assertThat(imageOf(category.id())).isEqualTo(address);
        String list = admin.get("/admin/category").body();
        assertThat(list).contains("<img class=\"cell-thumb\" src=\"" + address + "\" alt=\"Image\">");
        String detail = admin.get("/admin/category/" + category.id()).body();
        assertThat(detail).contains("<img class=\"cell-thumb\" src=\"" + address + "\" alt=\"Image\">")
                .contains("<img class=\"detail-thumb\" src=\"" + address + "\" alt=\"Image\">");
        HttpResponse<byte[]> served = admin.getBytes(address);
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(served.headers().firstValue("Content-Type")).hasValue("image/png");
        assertThat(served.body()).isEqualTo(png);
    }

    @Test
    void aPlainFormOnTheCreatePageComesBackToTheCreatePage() throws Exception {
        Browser admin = signedIn();
        String create = admin.get("/admin/category/new").body();

        HttpResponse<String> sent = plainUpload(admin, hidden(create, "ticket"), hidden(create, "_csrf"), null,
                AbstractUploadOverHttpTest.png());

        assertThat(sent.statusCode()).isEqualTo(303);
        assertThat(sent.headers().firstValue("Location").orElseThrow()).endsWith("/admin/category/new");
        assertThat(imageValue(admin.get("/admin/category/new").body())).startsWith("/admin/files/default/categories/");
    }

    @Test
    void aPlainFormWithoutTheCsrfFieldOrWithoutTheTicketIsRefusedAndNothingIsStored() throws Exception {
        Browser admin = signedIn();
        Subject category = first();
        String edit = admin.get("/admin/category/" + category.id() + "/edit").body();
        long before = AbstractUploadOverHttpTest.files(storage);
        byte[] png = AbstractUploadOverHttpTest.png();

        HttpResponse<String> noCsrf = plainUpload(admin, hidden(edit, "ticket"), null, String.valueOf(category.id()), png);
        HttpResponse<String> wrongCsrf = plainUpload(admin, hidden(edit, "ticket"), "wrong", String.valueOf(category.id()), png);
        HttpResponse<String> noTicket = plainUpload(admin, null, hidden(edit, "_csrf"), String.valueOf(category.id()), png);
        HttpResponse<String> badTicket = plainUpload(admin, "v1.a.b", hidden(edit, "_csrf"), String.valueOf(category.id()), png);

        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(wrongCsrf.statusCode()).isEqualTo(403);
        assertThat(noTicket.statusCode()).isEqualTo(403);
        assertThat(badTicket.statusCode()).isEqualTo(403);
        assertThat(noTicket.body()).contains("The file was not uploaded").contains("no longer valid");
        assertThat(AbstractUploadOverHttpTest.files(storage)).isEqualTo(before);
        assertThat(imageValue(admin.get("/admin/category/" + category.id() + "/edit").body())).isEmpty();
    }

    // ---- saving: only an address the storage made --------------------------------------------------------------------------------------------

    private String uploaded(Browser admin) throws Exception {
        Subject category = first();
        String edit = admin.get("/admin/category/" + category.id() + "/edit").body();
        plainUpload(admin, hidden(edit, "ticket"), hidden(edit, "_csrf"), String.valueOf(category.id()), AbstractUploadOverHttpTest.png());
        return imageValue(admin.get("/admin/category/" + category.id() + "/edit").body());
    }

    @Test
    void anAddressTheStorageDidNotMakeIsRefusedForThatFieldAndNothingElseIsSaved() throws Exception {
        Browser admin = signedIn();
        Subject category = first();
        String real = uploaded(admin);
        assertThat(saveCategory(admin, category, "image", real).statusCode()).isEqualTo(303);

        for (String bad : List.of("https://evil.example/x.png", "javascript:alert(1)", "/admin/files/default/../../x.png",
                "/admin/files/default/categories/2026/11/0123456789abcdef0123456789abcdef.png", "<script>alert(1)</script>")) {
            HttpResponse<String> refused = saveCategory(admin, category, "image", bad, "description", "Changed by a refused save");

            assertThat(refused.statusCode()).as(bad).isEqualTo(422);
            assertThat(refused.body()).contains("The file is not one this console uploaded. Upload it again.").doesNotContain("<script>alert");
            assertThat(imageOf(category.id())).as(bad).isEqualTo(real);
        }
        try (EntityManager em = context.getBean(EntityManagerFactory.class).createEntityManager()) {
            assertThat(em.createQuery("select c.description from Category c where c.id = :id", String.class)
                    .setParameter("id", category.id()).getSingleResult()).doesNotContain("Changed by a refused save");
        }
    }

    @Test
    void blankKeepsTheStoredAddressAndTheRemoveMarkerClearsItButTheFileStaysOnDisk() throws Exception {
        Browser admin = signedIn();
        Subject category = first();
        String real = uploaded(admin);
        saveCategory(admin, category, "image", real);
        long files = AbstractUploadOverHttpTest.files(storage);

        assertThat(saveCategory(admin, category, "image", "").statusCode()).isEqualTo(303);
        assertThat(imageOf(category.id())).as("blank keeps").isEqualTo(real);

        assertThat(saveCategory(admin, category, "image", real, "image-remove", "true").statusCode()).isEqualTo(303);
        assertThat(imageOf(category.id())).as("remove clears").isNull();
        assertThat(AbstractUploadOverHttpTest.files(storage)).as("UPL-9: the old file stays").isEqualTo(files);
        assertThat(admin.getBytes(real).statusCode()).isEqualTo(200);
    }

    @Test
    void aSecondUploadReplacesTheFirstAndBothFilesStayOnDisk() throws Exception {
        Browser admin = signedIn();
        Subject category = first();
        String first = uploaded(admin);
        saveCategory(admin, category, "image", first);
        String second = uploaded(admin);
        assertThat(second).isNotEqualTo(first);

        saveCategory(admin, category, "image", second);

        assertThat(imageOf(category.id())).isEqualTo(second);
        assertThat(admin.getBytes(first).statusCode()).isEqualTo(200);
        assertThat(admin.getBytes(second).statusCode()).isEqualTo(200);
    }

    @Test
    void aDuplicateOfARecordWithAnImageCarriesNoFile() throws Exception {
        Browser admin = signedIn();
        Subject category = first();
        saveCategory(admin, category, "image", uploaded(admin));

        HttpResponse<String> copy = admin.get("/admin/category/new?copyFrom=" + category.id());

        assertThat(copy.statusCode()).isEqualTo(200);
        assertThat(imageValue(copy.body())).isEmpty();
        assertThat(copy.body()).contains("value=\"" + category.name() + "\"");
    }

    @Test
    void aPostedNameThatIsNotAnEditableFieldIsNeverStored() throws Exception {
        Browser admin = signedIn();
        Subject category = first();

        saveCategory(admin, category, "Image", "https://evil.example/x.png", "image.remove", "true", "image--remove", "true");

        assertThat(imageOf(category.id())).isNull();
    }

    // ---- cells: a missing file and a foreign value are text, never a broken picture or a link ----------------------------------------------------------------

    @Test
    void aStoredFileThatIsGoneShowsTheWordsNotABrokenPictureAndAForeignValueIsEscapedText() throws Exception {
        Browser admin = signedIn();
        Subject category = first();
        String real = uploaded(admin);
        saveCategory(admin, category, "image", real);
        Path file = storage.resolve(real.substring("/admin/files/default/".length()));
        Files.delete(file);

        String list = admin.get("/admin/category").body();
        String detail = admin.get("/admin/category/" + category.id()).body();

        assertThat(list).contains("Image: file not found").doesNotContain("src=\"" + real + "\"");
        assertThat(detail).contains("Image: file not found").doesNotContain("src=\"" + real + "\"").doesNotContain("detail-thumb");

        setImage("<img src=x onerror=alert(1)>");
        String foreign = admin.get("/admin/category").body();
        assertThat(foreign).contains("&lt;img src=x onerror=alert(1)&gt;").doesNotContain("<img src=x");
        setImage("https://evil.example/x.png");
        String external = admin.get("/admin/category").body();
        assertThat(external).contains("https://evil.example/x.png").doesNotContain("src=\"https://evil.example").doesNotContain(
                "href=\"https://evil.example");
        assertThat(admin.get("/admin/category/" + category.id() + "/edit").body()).doesNotContain("https://evil.example");
    }

    @Test
    void aStoredValueThatIsAnSvgAddressIsNeverAPictureOrALink() throws Exception {
        Browser admin = signedIn();
        Subject category = first();
        setImage("/admin/files/default/categories/2026/11/0123456789abcdef0123456789abcdef.svg");

        String list = admin.get("/admin/category").body();
        String edit = admin.get("/admin/category/" + category.id() + "/edit").body();

        assertThat(list).doesNotContain("src=\"/admin/files/default/categories/2026/11/0123456789abcdef0123456789abcdef.svg\"")
                .doesNotContain("href=\"/admin/files/default/categories/2026/11/0123456789abcdef0123456789abcdef.svg\"");
        assertThat(edit).doesNotContain(".svg");
    }

    @Test
    void theScriptIsLoadedFromTheAssetsAndNothingIsInline() throws Exception {
        Browser admin = signedIn();
        Subject category = first();

        String html = admin.get("/admin/category/" + category.id() + "/edit").body();
        HttpResponse<String> script = admin.get("/admin/assets/js/uploader.js");

        assertThat(html).contains("<script src=\"/admin/assets/js/uploader.js\" defer></script>");
        assertThat(Pattern.compile("(?i)<script(?![^>]*\\ssrc=)[^>]*>").matcher(html).find()).as("no inline script").isFalse();
        assertThat(html).doesNotContain(" style=").doesNotContain(" onclick=").doesNotContain(" onchange=");
        assertThat(script.statusCode()).isEqualTo(200);
        assertThat(script.body()).contains("XMLHttpRequest");
        assertThat(script.body().getBytes(StandardCharsets.UTF_8).length).isLessThan(16_000);
    }
}
