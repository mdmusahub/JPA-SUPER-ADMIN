package io.github.mdmusahub.jpaadmin.preview;

import static io.github.mdmusahub.jpaadmin.preview.ChromeFixtures.ALTERNATE_BASE;
import static io.github.mdmusahub.jpaadmin.preview.ChromeFixtures.BASE;

import io.github.mdmusahub.jpaadmin.web.EntityController;
import io.github.mdmusahub.jpaadmin.web.view.DashboardView;
import io.github.mdmusahub.jpaadmin.web.view.ErrorView;
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
 * and {@code &chrome=} ({@code docs/FIXTURES.md}). The pages are {@code login}, {@code dashboard}, {@code entity}
 * and {@code error} (tasks U0 and U1); each later UI task adds its own.
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
                @RequestParam(defaultValue = "default") String chrome, Model model) {
        PageChrome pageChrome = ChromeFixtures.find(chrome).orElseThrow(() -> new UnknownFixture(
                "No chrome variant called '" + chrome + "'. Valid chrome variants: " + ChromeFixtures.names()));
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
            case "entity" -> {
                // The one page with no record of its own: the shell plus the notice (Phase 3, EntityController).
                if (!PageFixtures.hasEntityState(state)) {
                    throw unknown(page, state);
                }
                yield EntityController.TEMPLATE;
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

    private static UnknownFixture unknown(String page, String state) {
        Map<String, Set<String>> catalog = PageFixtures.catalog();
        return new UnknownFixture("No state called '" + state + "' for page '" + page + "'. Valid states: "
                + catalog.getOrDefault(page, Set.of()));
    }
}
