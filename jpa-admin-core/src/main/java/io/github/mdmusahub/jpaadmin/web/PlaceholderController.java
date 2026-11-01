package io.github.mdmusahub.jpaadmin.web;

import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Phase 1 placeholder for the console's home page: a plain-text sign of life, with no data and no template engine.
 *
 * <p><strong>Replaced in Phase 2</strong> by the real controller (login, dashboard and the Thymeleaf pages). It
 * declares a mapping relative to the console: the auto-configuration puts the configured base path in front of every
 * controller in this package, so the path is decided in one place and never written here.
 *
 * <p>Internal, not stable API. Public only so the auto-configuration module can create it.
 */
@Controller
public class PlaceholderController {

    /** The whole response body. */
    public static final String MESSAGE = "JPA Admin console is running";

    private static final MediaType PLAIN_TEXT = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    /** Answers {@code GET {base path}} with {@value #MESSAGE}. */
    @GetMapping
    public ResponseEntity<String> home() {
        return ResponseEntity.ok().contentType(PLAIN_TEXT).body(MESSAGE);
    }
}
