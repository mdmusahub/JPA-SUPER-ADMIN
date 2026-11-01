package io.github.mdmusahub.jpaadmin.web;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Serves {@code {base path}/theme.css}: the configured accent colour as one CSS variable, and nothing else (SEC-15).
 *
 * <p>The colour reaches the browser only through this stylesheet, never through an inline {@code style} attribute, an
 * inline {@code <style>} block or a script. {@link ConsoleSettings} accepts only {@code #rrggbb}, so the response
 * cannot carry anything but a colour. The static stylesheets derive every other shade from {@code --ac}.
 *
 * <p>The page is public (the sign-in page needs it) and cached for a few minutes, so a changed colour shows up soon
 * but a page load does not fetch it again each time.
 *
 * <p>Internal, not stable API. Public only so the auto-configuration module can create it.
 */
@Controller
public class ThemeController {

    /** How long a browser may reuse the stylesheet. */
    static final Duration MAX_AGE = Duration.ofMinutes(5);

    private static final MediaType CSS = new MediaType("text", "css", StandardCharsets.UTF_8);

    private final String body;

    public ThemeController(ConsoleSettings settings) {
        this.body = ":root { --ac: " + Objects.requireNonNull(settings, "settings").primaryColor() + "; }\n";
    }

    /** {@code GET {base path}/theme.css}. */
    @GetMapping("/theme.css")
    @ResponseBody
    public ResponseEntity<String> themeCss() {
        return ResponseEntity.ok()
                .contentType(CSS)
                .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
                .body(body);
    }
}
