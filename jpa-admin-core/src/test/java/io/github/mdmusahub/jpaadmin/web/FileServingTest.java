package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.storage.LocalStorageProvider;
import io.github.mdmusahub.jpaadmin.storage.StorageSettings;
import io.github.mdmusahub.jpaadmin.storage.StoredFile;
import io.github.mdmusahub.jpaadmin.storage.UploadTestFiles;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serving stored files (UPL-6, UPL-7): the detected type, {@code nosniff} and a sandboxing policy on every answer, an
 * attachment for everything that is not an image, a file that reached the folder some other way served as an attachment, and
 * every way of writing a path that climbs out of the storage refused as an ordinary 404. The security chain (the session) is
 * tested over real HTTP in the sample app; here the controller and the public handler are called directly, with no chain, so
 * the headers asserted are the ones these classes write themselves.
 */
class FileServingTest {

    private static final String NAME = "0123456789abcdef0123456789abcdef";
    private static final Clock NOVEMBER = Clock.fixed(Instant.parse("2026-11-18T10:15:30Z"), ZoneOffset.UTC);

    @TempDir
    Path temp;

    private Path root;
    private Path outside;
    private LocalStorageProvider provider;
    private FileController console;
    private PublicFileHandler publicFiles;

    @BeforeEach
    void wire() throws IOException {
        root = temp.resolve("root");
        outside = temp.resolve("outside");
        Files.createDirectories(outside);
        Map<String, StorageSettings> storages = Map.of("default",
                new StorageSettings("default", "local", root.toString(), 1024 * 1024, null), "pub",
                new StorageSettings("pub", "local", temp.resolve("pub").toString(), 1024 * 1024, "/files"));
        provider = new LocalStorageProvider(storages, "/admin/files", List.of(), new SecureRandom(), NOVEMBER);
        console = new FileController(provider, BasePath.of("/admin"));
        publicFiles = new PublicFileHandler("pub", "/files", provider);
    }

    private StoredFile store(String storage, String folder, String extension, byte[] bytes) throws IOException {
        return provider.store(storage, folder, extension, new ByteArrayInputStream(bytes), 1024 * 1024);
    }

    private Path plant(String relative, byte[] bytes) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        return file;
    }

    private MockHttpServletResponse get(String uri) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        console.serve(request, response);
        return response;
    }

    private MockHttpServletResponse getPublic(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        publicFiles.handleRequest(request, response);
        return response;
    }

    private static void assertSafeHeaders(MockHttpServletResponse response) {
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'; sandbox");
    }

    // ---- images are shown, everything else is downloaded -----------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpg", "gif", "webp"})
    void anImageIsServedInlineWithItsDetectedType(String extension) throws IOException {
        byte[] bytes = switch (extension) {
            case "png" -> UploadTestFiles.png();
            case "jpg" -> UploadTestFiles.jpeg();
            case "gif" -> UploadTestFiles.gif();
            default -> UploadTestFiles.webp();
        };
        String mime = switch (extension) {
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            case "gif" -> "image/gif";
            default -> "image/webp";
        };
        StoredFile stored = store("default", "", extension, bytes);

        MockHttpServletResponse response = get("/admin/files/default/" + stored.key());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo(mime);
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("inline");
        assertThat(response.getContentAsByteArray()).isEqualTo(bytes);
        assertThat(response.getHeader("Content-Length")).isEqualTo(String.valueOf(bytes.length));
        assertSafeHeaders(response);
    }

    @Test
    void aPdfIsAnAttachmentNamedByItsStoredNameNeverByAClientName() throws IOException {
        StoredFile stored = store("default", "docs", "pdf", UploadTestFiles.pdf());

        MockHttpServletResponse response = get("/admin/files/default/" + stored.key());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\""
                + stored.key().substring(stored.key().lastIndexOf('/') + 1) + "\"");
        assertSafeHeaders(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"html", "htm", "svg", "js", "exe", "txt", "png"})
    void aFileThatIsNotOneOfTheKnownTypesIsAnOctetStreamAttachmentWhateverItsNameSays(String extension) throws IOException {
        // Put there some other way (an old version, a copy by hand): the content decides, never the name.
        byte[] html = UploadTestFiles.html();
        plant("2026/11/" + NAME + "." + extension, html);

        MockHttpServletResponse response = get("/admin/files/default/2026/11/" + NAME + "." + extension);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/octet-stream").doesNotContain("html");
        assertThat(response.getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(response.getContentAsByteArray()).isEqualTo(html);
        assertSafeHeaders(response);
    }

    @Test
    void anImageIsInlineByItsContentNotByAnHtmlExtension() throws IOException {
        // The reverse: real PNG bytes under a name that says html are an image, served as one, and nosniff keeps it one.
        plant("2026/11/" + NAME + ".html", UploadTestFiles.png());

        MockHttpServletResponse response = get("/admin/files/default/2026/11/" + NAME + ".html");

        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void aHeadRequestGetsTheHeadersAndNoBody() throws IOException {
        StoredFile stored = store("default", "", "png", UploadTestFiles.png());
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/admin/files/default/" + stored.key());
        request.setRequestURI("/admin/files/default/" + stored.key());
        MockHttpServletResponse response = new MockHttpServletResponse();

        console.serve(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Length")).isEqualTo(String.valueOf(UploadTestFiles.png().length));
        assertSafeHeaders(response);
    }

    @Test
    void theContextPathIsLeftOut() throws IOException {
        StoredFile stored = store("default", "", "png", UploadTestFiles.png());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shop/admin/files/default/" + stored.key());
        request.setContextPath("/shop");
        request.setRequestURI("/shop/admin/files/default/" + stored.key());
        MockHttpServletResponse response = new MockHttpServletResponse();

        console.serve(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ---- refusals: one 404 for everything that is not a stored file -----------------------------------------------------------------

    @Test
    void aFileThatIsNotThereIsAnOrdinaryNotFoundWithTheSafeHeaders() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/files/default/2026/11/" + NAME + ".png");
        request.setRequestURI("/admin/files/default/2026/11/" + NAME + ".png");

        assertThatThrownBy(() -> console.serve(request, response)).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertSafeHeaders(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/files/default/../../outside/secret.png",
            "/admin/files/default/2026/11/../../../outside/secret.png",
            "/admin/files/default/%2e%2e/%2e%2e/outside/secret.png",
            "/admin/files/default/%2E%2E/secret.png",
            "/admin/files/default/2026/11/..%2f..%2fsecret.png",
            "/admin/files/default/2026/11/..%5c..%5csecret.png",
            "/admin/files/default/..;/secret.png",
            "/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png;jsessionid=1",
            "/admin/files/default/2026%2f11/0123456789abcdef0123456789abcdef.png",
            "/admin/files/default/2026\\11\\0123456789abcdef0123456789abcdef.png",
            "/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png/",
            "/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png/..",
            "/admin/files/default//2026/11/0123456789abcdef0123456789abcdef.png",
            "/admin/files//default/2026/11/0123456789abcdef0123456789abcdef.png",
            "/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png%00",
            "/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png\u0000",
            "/admin/files/default/2026/11/0123456789ABCDEF0123456789abcdef.png",
            "/admin/files/DEFAULT/2026/11/0123456789abcdef0123456789abcdef.png",
            "/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png.exe",
            "/admin/files/default/2026/11/aux.png",
            "/admin/files/default/2026/11/",
            "/admin/files/default/2026/11",
            "/admin/files/default",
            "/admin/files/default/",
            "/admin/files/",
            "/admin/files",
            "/admin/files/C:/secret.png",
            "/admin/files/default/c:/secret.png",
            "/admin/files/default/~/secret.png",
            "/admin/files/default/%252e%252e/secret.png",
            "/other/files/default/2026/11/0123456789abcdef0123456789abcdef.png"})
    void everyWayOfWritingATraversalOrAnythingElseThatIsNotAKeyIsAnOrdinaryNotFound(String uri) throws IOException {
        Files.writeString(outside.resolve("secret.png"), "TOP-SECRET");
        Files.createDirectories(root.resolve("2026/11"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> console.serve(request, response)).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        assertThat(response.getContentAsString()).doesNotContain("TOP-SECRET");
        assertSafeHeaders(response);
    }

    @Test
    void aFileOutsideTheRootIsNotServedEvenWithAKeyThatLooksRight() throws IOException {
        Files.createDirectories(outside.resolve("2026/11"));
        Files.write(outside.resolve("2026/11/" + NAME + ".png"), UploadTestFiles.png());

        assertThatThrownBy(() -> get("/admin/files/default/2026/11/" + NAME + ".png")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void anUnknownStorageIsNotFound() throws IOException {
        StoredFile stored = store("default", "", "png", UploadTestFiles.png());

        assertThatThrownBy(() -> get("/admin/files/nothing/" + stored.key())).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aFileInAnotherStorageIsNotServedUnderThisOne() throws IOException {
        StoredFile stored = store("pub", "", "png", UploadTestFiles.png());

        assertThatThrownBy(() -> get("/admin/files/default/" + stored.key())).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void theParserAcceptsOnlyAStorageAndAKey() {
        assertThat(FileResponder.parse("default/2026/11/" + NAME + ".png")).hasValue(List.of("default", "2026/11/" + NAME + ".png"));
        assertThat(FileResponder.parse("default/docs/2026/11/" + NAME + ".pdf")).isPresent();
        assertThat(FileResponder.parse(null)).isEmpty();
        assertThat(FileResponder.parse("")).isEmpty();
        assertThat(FileResponder.parse("/2026/11/" + NAME + ".png")).isEmpty();
        assertThat(FileResponder.parse("default")).isEmpty();
        assertThat(FileResponder.parse("default/")).isEmpty();
        assertThat(FileResponder.parse("../2026/11/" + NAME + ".png")).isEmpty();
    }

    // ---- the public path (UPL-7) --------------------------------------------------------------------------------------------------------

    @Test
    void thePublicPathServesTheFileWithTheSameSafeHeadersAndAPublicCacheLifetime() throws Exception {
        StoredFile stored = store("pub", "", "png", UploadTestFiles.png());

        MockHttpServletResponse response = getPublic("GET", "/files/" + stored.key());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("inline");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("public, max-age=3600");
        assertThat(response.getContentAsByteArray()).isEqualTo(UploadTestFiles.png());
        assertSafeHeaders(response);
    }

    @Test
    void thePublicPathStillDownloadsADocumentAndNeverRendersAPage() throws Exception {
        StoredFile stored = store("pub", "", "pdf", UploadTestFiles.pdf());
        Path page = temp.resolve("pub/2026/11/" + NAME + ".html");
        Files.createDirectories(page.getParent());
        Files.write(page, UploadTestFiles.html());

        MockHttpServletResponse pdf = getPublic("GET", "/files/" + stored.key());
        MockHttpServletResponse html = getPublic("GET", "/files/2026/11/" + NAME + ".html");

        assertThat(pdf.getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(html.getContentType()).isEqualTo("application/octet-stream");
        assertThat(html.getHeader("Content-Disposition")).startsWith("attachment");
        assertSafeHeaders(pdf);
        assertSafeHeaders(html);
    }

    @Test
    void thePublicHandlerServesOnlyItsOwnStorage() throws Exception {
        StoredFile mine = store("default", "", "png", UploadTestFiles.png());

        MockHttpServletResponse response = getPublic("GET", "/files/" + mine.key());

        assertThat(response.getStatus()).isEqualTo(404);
        assertSafeHeaders(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/files/../outside/secret.png", "/files/%2e%2e/secret.png", "/files/2026/11/..%2fsecret.png",
            "/files/..;/secret.png", "/files/2026\\11\\0123456789abcdef0123456789abcdef.png", "/files/", "/files",
            "/files/2026/11/0123456789abcdef0123456789abcdef.png/", "/files/default/2026/11/0123456789abcdef0123456789abcdef.png",
            "/elsewhere/2026/11/0123456789abcdef0123456789abcdef.png", "/filesx/2026/11/0123456789abcdef0123456789abcdef.png"})
    void thePublicPathRefusesTraversalAndEverythingThatIsNotAKeyWithTheSafeHeaders(String uri) throws Exception {
        Files.writeString(outside.resolve("secret.png"), "TOP-SECRET");

        MockHttpServletResponse response = getPublic("GET", uri);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).doesNotContain("TOP-SECRET");
        assertSafeHeaders(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH", "OPTIONS"})
    void thePublicPathAnswersOnlyGetAndHead(String method) throws Exception {
        StoredFile stored = store("pub", "", "png", UploadTestFiles.png());

        MockHttpServletResponse response = getPublic(method, "/files/" + stored.key());

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertSafeHeaders(response);
    }

    @Test
    void thePublicPathAnswersHeadWithoutABody() throws Exception {
        StoredFile stored = store("pub", "", "png", UploadTestFiles.png());

        MockHttpServletResponse response = getPublic("HEAD", "/files/" + stored.key());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Length")).isEqualTo(String.valueOf(UploadTestFiles.png().length));
    }

    @Test
    void thePublicHandlerRefusesABadStorageName() {
        assertThatThrownBy(() -> new PublicFileHandler("../x", "/files", provider)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theConsoleControllersAnswerCarriesNoClientName() throws IOException {
        StoredFile stored = provider.store("default", "", "pdf", new ByteArrayInputStream("%PDF-1.4 x".getBytes(StandardCharsets.UTF_8)),
                1000);

        MockHttpServletResponse response = get("/admin/files/default/" + stored.key());

        assertThat(response.getHeader("Content-Disposition")).contains(stored.key().substring(stored.key().lastIndexOf('/') + 1));
    }
}
