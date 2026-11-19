package io.github.mdmusahub.jpaadmin.preview;

import static io.github.mdmusahub.jpaadmin.preview.ChromeFixtures.ALTERNATE_BASE;
import static io.github.mdmusahub.jpaadmin.preview.ChromeFixtures.BASE;

import io.github.mdmusahub.jpaadmin.web.view.DashboardView;
import io.github.mdmusahub.jpaadmin.web.view.DetailView;
import io.github.mdmusahub.jpaadmin.web.view.ErrorView;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.LoginView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Renders the real console templates with fixtures, with no database and no login: {@code /admin-preview/{page}?state=}
 * and {@code &chrome=} ({@code docs/FIXTURES.md}). The pages are {@code login}, {@code dashboard}, {@code list}, {@code detail},
 * {@code form} and {@code error} (tasks U0 to U5); each later UI task adds its own. Without {@code &chrome=} a state uses the chrome
 * variant that fits it ({@code default} for all but a few list states).
 *
 * <p>It fills the same model attributes as the real controllers ({@code chrome} and {@code view}) and returns the same
 * template names, so what you see here is what the console renders. An unknown page, state or chrome variant answers 404
 * with a plain-text list of the valid names, which is only possible because this app is never published (ARCH-11).
 *
 * <p>The preview refuses to start under the {@code prod} profile ({@link PreviewProdGuard}).
 */
@Controller
@RequestMapping(BASE)
class PreviewController {

    private static final MediaType CSS = new MediaType("text", "css", StandardCharsets.UTF_8);
    private static final MediaType TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);

    /** The accent colour of the preview: the console's default. */
    static final String THEME_CSS = ":root { --ac: #0d9f6e; }\n";

    /** Thrown for an unknown page, state or chrome variant. Answered with 404 and the list of valid names. */
    static final class UnknownFixture extends RuntimeException {
        UnknownFixture(String message) {
            super(message);
        }
    }

    @GetMapping
    String index(Model model) {
        model.addAttribute("catalog", PageFixtures.catalog());
        model.addAttribute("chromes", ChromeFixtures.names());
        model.addAttribute("base", BASE);
        return "preview/index";
    }

    @GetMapping("/{page}")
    String page(@PathVariable String page, @RequestParam(defaultValue = "normal") String state,
                @RequestParam(required = false) String chrome, Model model) {
        String variant = chrome != null ? chrome : "list".equals(page) ? preferredListChrome(state) : "default";
        PageChrome pageChrome = (chrome == null && "detail".equals(page) ? RecordFixtures.detailChrome(state, BASE)
                : chrome == null && "form".equals(page) ? FormFixtures.chrome(state, BASE)
                : ChromeFixtures.find(variant)).orElseThrow(() -> new UnknownFixture(
                "No chrome variant called '" + variant + "'. Valid chrome variants: " + ChromeFixtures.names()));
        model.addAttribute(PageChrome.ATTRIBUTE, pageChrome);
        return switch (page) {
            case "login" -> {
                model.addAttribute(PageChrome.VIEW_ATTRIBUTE, PageFixtures.login(state).orElseThrow(() -> unknown(page, state)));
                yield LoginView.TEMPLATE;
            }
            case "dashboard" -> {
                model.addAttribute(PageChrome.VIEW_ATTRIBUTE, PageFixtures.dashboard(state).orElseThrow(() -> unknown(page, state)));
                yield DashboardView.TEMPLATE;
            }
            case "list" -> {
                model.addAttribute(PageChrome.VIEW_ATTRIBUTE,
                        PageFixtures.list(state, pageChrome.basePath()).orElseThrow(() -> unknown(page, state)));
                yield ListView.TEMPLATE;
            }
            case "detail" -> {
                model.addAttribute(PageChrome.VIEW_ATTRIBUTE,
                        PageFixtures.detail(state, pageChrome.basePath()).orElseThrow(() -> unknown(page, state)));
                yield DetailView.TEMPLATE;
            }
            case "form" -> {
                model.addAttribute(PageChrome.VIEW_ATTRIBUTE,
                        PageFixtures.form(state, pageChrome.basePath()).orElseThrow(() -> unknown(page, state)));
                yield FormView.TEMPLATE;
            }
            case "error" -> {
                model.addAttribute(PageChrome.VIEW_ATTRIBUTE, PageFixtures.error(state).orElseThrow(() -> unknown(page, state)));
                yield ErrorView.TEMPLATE;
            }
            default -> throw new UnknownFixture("No page called '" + page + "'. Valid pages: " + PageFixtures.catalog().keySet());
        };
    }

    /** The accent stylesheet, as the console serves it at {@code {path}/theme.css}. Also answered under the second base path. */
    @GetMapping("/theme.css")
    @ResponseBody
    ResponseEntity<String> themeCss() {
        return ResponseEntity.ok().contentType(CSS).body(THEME_CSS);
    }

    /**
     * The text a browser would hold in localStorage for the saved views of the article list, one state at a time (task U8, Phase 12).
     * The preview never writes browser storage itself; a person or a script puts this text under the key named in the
     * X-Storage-Key header and opens {@code list?state=saved-views}. An unknown state answers 404 with the valid names.
     */
    @GetMapping("/stored-views")
    @ResponseBody
    ResponseEntity<String> storedViews(@RequestParam(defaultValue = "several") String state) {
        String body = ViewsFixtures.stored(state).orElseThrow(() -> new UnknownFixture("No stored views called '" + state
                + "'. Valid states: " + ViewsFixtures.storedStates()));
        return ResponseEntity.ok().contentType("invalid-json".equals(state) ? TEXT : MediaType.APPLICATION_JSON)
                .header("X-Storage-Key", ViewsFixtures.STORAGE_KEY).body(body);
    }

    /** The second console path of the {@code custom-base-path} variant needs a working stylesheet too. */
    @Controller
    @RequestMapping(ALTERNATE_BASE)
    static class AlternateBase {

        @GetMapping("/theme.css")
        @ResponseBody
        ResponseEntity<String> themeCss() {
            return ResponseEntity.ok().contentType(CSS).body(THEME_CSS);
        }
    }

    @ExceptionHandler(UnknownFixture.class)
    ResponseEntity<String> notFound(UnknownFixture exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(TEXT).body(exception.getMessage() + "\n");
    }

    private static String preferredListChrome(String state) {
        String record = RecordFixtures.preferredListChrome(state);
        return record != null ? record : ListFixtures.preferredChrome(state);
    }

    private static UnknownFixture unknown(String page, String state) {
        Map<String, Set<String>> catalog = PageFixtures.catalog();
        return new UnknownFixture("No state called '" + state + "' for page '" + page + "'. Valid states: "
                + catalog.getOrDefault(page, Set.of()));
    }
}
