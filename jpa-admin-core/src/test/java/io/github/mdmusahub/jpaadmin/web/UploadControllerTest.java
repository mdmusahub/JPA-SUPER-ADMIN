package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.storage.LocalStorageProvider;
import io.github.mdmusahub.jpaadmin.storage.StorageSettings;
import io.github.mdmusahub.jpaadmin.storage.UploadService;
import io.github.mdmusahub.jpaadmin.storage.UploadTargets;
import io.github.mdmusahub.jpaadmin.storage.UploadTestFiles;
import io.github.mdmusahub.jpaadmin.storage.UploadTicketService;
import io.github.mdmusahub.jpaadmin.testsupport.UploadTestPersistence;
import io.github.mdmusahub.jpaadmin.web.view.ErrorView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import io.github.mdmusahub.jpaadmin.web.view.UploadResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

/**
 * The upload endpoint through the real controller, service, tickets and local provider: the JSON answer of the uploader script,
 * the redirect or error page of a plain form (option A), the multipart errors, and a redirect that is built by the console and
 * never taken from the request. The security chain (session, CSRF) is tested over real HTTP in the sample app.
 */
class UploadControllerTest {

    private record Page(String template, Locale locale) implements View {
        @Override
        public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
            throw new UnsupportedOperationException("the test double renders nothing");
        }
    }

    private static final BasePath BASE = BasePath.of("/admin");
    private static final CsrfTokens TOKENS = request -> new PageChrome.Csrf("_csrf", "X-CSRF-TOKEN", "token-123");

    @TempDir
    Path temp;

    private UploadTicketService tickets;
    private UploadController controller;
    private ConsoleExceptionResolver resolver;
    private Path root;

    @BeforeEach
    void wire() {
        root = temp.resolve("root");
        Map<String, StorageSettings> storages = Map.of("default",
                new StorageSettings("default", "local", root.toString(), 5L * 1024 * 1024, null), "vault",
                new StorageSettings("vault", "local", temp.resolve("vault").toString(), 5L * 1024 * 1024, null));
        tickets = UploadTicketService.withRandomKey(Clock.systemUTC());
        AdminEntityRegistry registry = UploadTestPersistence.registry();
        UploadService service = new UploadService(UploadTargets.of(registry, storages), tickets,
                new LocalStorageProvider(storages, "/admin/files", List.of(), new SecureRandom(), Clock.systemUTC()));
        ConsoleNavigation navigation = new ConsoleNavigation(registry,
                CountService.over(UploadTestPersistence.entityManager(), Clock.systemUTC()));
        ConsoleChromeFactory chromes = new ConsoleChromeFactory(BASE, new ConsoleSettings("Back office", "#0d9f6e"), TOKENS,
                navigation);
        ConsoleViews views = new ConsoleViews(Page::new);
        controller = new UploadController(service, chromes, views, BASE);
        resolver = new ConsoleExceptionResolver(BASE, chromes, views);
    }

    private String ticket(String field) {
        return tickets.issue("dossier", field, 5L * 1024 * 1024);
    }

    private static MockHttpServletRequest request(boolean json) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/uploads");
        request.setSession(new MockHttpSession());
        if (json) {
            request.addHeader("Accept", "application/json");
        } else {
            request.addHeader("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8");
        }
        return request;
    }

    private static MockMultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    private ModelAndView post(MockHttpServletRequest request, MockHttpServletResponse response, String ticket, String field,
                              String record, MockMultipartFile file) throws IOException {
        return controller.upload("dossier", field, ticket, record, file, request, response);
    }

    private long storedFiles() throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    private static String redirectOf(ModelAndView result) {
        assertThat(result).isNotNull();
        assertThat(result.getView()).isInstanceOf(RedirectView.class);
        return ((RedirectView) result.getView()).getUrl();
    }

    // ---- the script: JSON ----------------------------------------------------------------------------------------------------------------

    @Test
    void theUploaderScriptGetsTheStoredAddressAsJson() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = post(request(true), response, ticket("cover"), "cover", null,
                file("my \"photo\".png", "image/png", UploadTestFiles.png()));

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("application/json");
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).matches("\\{\"url\":\"/admin/files/default/\\d{4}/\\d{2}/[0-9a-f]{32}\\.png\","
                + "\"name\":\"my \\\\\"photo\\\\\"\\.png\",\"size\":\\d+,\"error\":null}");
        assertThat(storedFiles()).isEqualTo(1);
    }

    @Test
    void aRefusalIsJsonWithTheStatusTheMessageAndNoAddress() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = post(request(true), response, ticket("cover"), "cover", null,
                file("photo.png", "image/png", UploadTestFiles.executable()));

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(415);
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).startsWith("{\"url\":null,\"name\":null,\"size\":0,\"error\":\"").contains("not one of the types")
                .doesNotContain("photo.png");
        assertThat(storedFiles()).isZero();
    }

    @Test
    void noTicketIsForbiddenAsJsonAndNothingIsStored() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        post(request(true), response, null, "cover", null, file("photo.png", "image/png", UploadTestFiles.png()));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("no longer valid");
        assertThat(storedFiles()).isZero();
    }

    @Test
    void noFileIsABadRequestAsJson() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        post(request(true), response, ticket("cover"), "cover", null, null);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("No file");
    }

    @Test
    void aBrowserThatChoseNothingSendsAnEmptyNamelessPartAndGetsNoFile() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        post(request(true), response, ticket("cover"), "cover", null, file("", "application/octet-stream", new byte[0]));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("No file");
    }

    @Test
    void anEmptyFileWithANameIsAnEmptyFile() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        post(request(true), response, ticket("cover"), "cover", null, file("a.png", "image/png", new byte[0]));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("The file is empty");
    }

    @Test
    void theJsonEscapesEverythingThatCouldMatterInAPage() {
        UploadResult result = UploadResult.success("/admin/files/x", "a\"b\\c</script>&'\n\u2028\u0001\u007f", 5);

        String json = UploadAnswers.json(result);

        assertThat(json).doesNotContain("<").doesNotContain(">").doesNotContain("&").doesNotContain("'").doesNotContain("\n")
                .doesNotContain("\u2028").doesNotContain("\u0001").doesNotContain("\u007f");
        assertThat(json).contains("\\\"", "\\\\", "\\u003c", "\\u003e", "\\u0026", "\\u0027", "\\n", "\\u2028", "\\u0001",
                "\\u007f");
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "APPLICATION/JSON", "text/html, application/json;q=0.9", "application/json, */*"})
    void anyAcceptHeaderThatNamesJsonGetsJson(String accept) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", accept);

        assertThat(UploadAnswers.wantsJson(request)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "*/*", "text/plain", ""})
    void everyOtherAcceptHeaderGetsThePlainFormAnswer(String accept) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", accept);

        assertThat(UploadAnswers.wantsJson(request)).isFalse();
        assertThat(UploadAnswers.wantsJson(new MockHttpServletRequest())).isFalse();
    }

    // ---- the plain form: option A -------------------------------------------------------------------------------------------------------

    @Test
    void aPlainFormGoesBackToTheCreateFormWithAMessage() throws IOException {
        MockHttpServletRequest request = request(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = post(request, response, ticket("cover"), "cover", null,
                file("holiday.png", "image/png", UploadTestFiles.png()));

        assertThat(redirectOf(result)).isEqualTo("/admin/dossier/new");
        assertThat(ConsoleFlash.take(request)).hasSize(1).first().satisfies(message -> {
            assertThat(message.level()).isEqualTo(PageChrome.FlashMessage.Level.SUCCESS);
            assertThat(message.text()).contains("holiday.png", "was uploaded");
        });
        assertThat(storedFiles()).isEqualTo(1);
    }

    @Test
    void aPlainFormForARecordGoesBackToItsEditForm() throws IOException {
        ModelAndView result = post(request(false), new MockHttpServletResponse(), ticket("cover"), "cover", "17",
                file("a.png", "image/png", UploadTestFiles.png()));

        assertThat(redirectOf(result)).isEqualTo("/admin/dossier/17/edit");
    }

    @Test
    void theRecordIdInTheRedirectIsEncodedAndAnIdWithoutAnAddressFallsBackToTheCreateForm() throws IOException {
        assertThat(redirectOf(post(request(false), new MockHttpServletResponse(), ticket("cover"), "cover", "a b?c",
                file("a.png", "image/png", UploadTestFiles.png())))).isEqualTo("/admin/dossier/a%20b%3Fc/edit");
        for (String bad : new String[] {"7/../x", "..", ".", "a\\b", "a\u0000b", "", "x".repeat(300)}) {
            assertThat(redirectOf(post(request(false), new MockHttpServletResponse(), ticket("cover"), "cover", bad,
                    file("a.png", "image/png", UploadTestFiles.png())))).as("record " + bad.length()).isEqualTo("/admin/dossier/new");
        }
    }

    @Test
    void nothingTheRequestCarriesIsEverTheAddressToGoTo() throws IOException {
        MockHttpServletRequest request = request(false);
        for (String name : new String[] {"next", "redirect", "returnTo", "url", "target", "successUrl", "_redirect", "continue"}) {
            request.addParameter(name, "https://evil.example/phish");
            request.addParameter(name, "//evil.example");
            request.addParameter(name, "/admin/dossier");
        }
        request.addHeader("Referer", "https://evil.example/");

        ModelAndView result = post(request, new MockHttpServletResponse(), ticket("cover"), "cover", null,
                file("a.png", "image/png", UploadTestFiles.png()));

        assertThat(redirectOf(result)).isEqualTo("/admin/dossier/new");
    }

    @Test
    void aRefusedPlainFormShowsThePlainErrorPageWithTheMessageAndTheStatus() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = post(request(false), response, ticket("cover"), "cover", null,
                file("photo.png", "image/png", UploadTestFiles.html()));

        assertThat(response.getStatus()).isEqualTo(415);
        assertThat(result).isNotNull();
        assertThat(result.getView()).isInstanceOf(Page.class);
        assertThat(((Page) result.getView()).template()).isEqualTo(ErrorView.TEMPLATE);
        ErrorView view = (ErrorView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE);
        assertThat(view.status()).isEqualTo(415);
        assertThat(view.heading()).isEqualTo("The file was not uploaded");
        assertThat(view.hint()).contains("not one of the types").doesNotContain("photo.png");
        assertThat(storedFiles()).isZero();
    }

    @Test
    void aPlainFormWithoutATicketIsForbiddenWithThePlainErrorPage() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = post(request(false), response, null, "cover", null, file("a.png", "image/png", UploadTestFiles.png()));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(((ErrorView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE)).hint()).contains("no longer valid");
        assertThat(storedFiles()).isZero();
    }

    // ---- over the host's multipart limit ------------------------------------------------------------------------------------------------

    @Test
    void aFileOverTheHostsLimitIsAJsonAnswerThatNamesTheHostsProperties() {
        MockHttpServletRequest request = request(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = resolver.resolveException(request, response, null, new MaxUploadSizeExceededException(1024 * 1024));

        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isTrue();
        assertThat(response.getStatus()).isEqualTo(413);
        try {
            assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("1 MB", "never reached the console",
                    "spring.servlet.multipart.max-file-size");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Test
    void aFileOverTheHostsLimitWithNoKnownSizeStillSaysWhatToDo() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        resolver.resolveException(request(true), response, null, new MaxUploadSizeExceededException(-1));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("larger than this server accepts")
                .doesNotContain("-1");
    }

    @Test
    void aPlainFormOverTheHostsLimitGetsTheErrorPage() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = resolver.resolveException(request(false), response, null,
                new MaxUploadSizeExceededException(10 * 1024 * 1024));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(((ErrorView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE)).hint()).contains("10 MB");
    }

    @Test
    void aRequestThatIsNotMultipartIsABadRequestThatSaysSo() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        resolver.resolveException(request(true), response, null, new MultipartException("Current request is not a multipart request"));

        assertThat(response.getStatus()).isEqualTo(400);
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("spring.servlet.multipart.enabled").doesNotContain("Current request");
    }

    @Test
    void theControllersOwnHandlerAnswersTheSameWhenTheHostResolvesMultipartLazily() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = controller.multipartRefused(new MaxUploadSizeExceededException(2 * 1024 * 1024), request(true),
                response);

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("2 MB");
    }

    @Test
    void aMultipartErrorOnAnotherRouteIsNotTheUploadEndpointsBusiness() {
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/admin/dossier");
        other.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = resolver.resolveException(other, response, null, new MaxUploadSizeExceededException(1));

        // The console's ordinary error page for the status the exception carries, not the upload answer.
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(((ErrorView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE)).heading()).isNotEqualTo("The file was not uploaded");
    }

    @Test
    void aHostRouteIsNeverAnsweredByTheConsole() {
        MockHttpServletRequest host = new MockHttpServletRequest("POST", "/host/uploads");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(resolver.resolveException(host, response, null, new MaxUploadSizeExceededException(1))).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void theUploadRouteOnlyCountsForPost() {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/admin/uploads");
        get.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();

        ModelAndView result = resolver.resolveException(get, response, null, new MultipartException("x"));

        assertThat(((ErrorView) result.getModel().get(PageChrome.VIEW_ATTRIBUTE)).heading()).isNotEqualTo("The file was not uploaded");
    }
}
