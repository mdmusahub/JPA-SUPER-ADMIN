package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class PlaceholderControllerTest {

    @Test
    void answersWithAPlainSignOfLifeAndNothingElse() {
        ResponseEntity<String> response = new PlaceholderController().home();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("JPA Admin console is running");
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
    }

    @Test
    void carriesNoLinkBecauseItBuildsNone() {
        // D-29: nothing to prefix or encode here. When this controller grows links, they must come from BasePath.
        assertThat(new PlaceholderController().home().getBody()).doesNotContain("/", "href", "<", "http");
    }

    /** The path is decided in one place (the auto-configuration's prefix), so the controller must not hard-code it. */
    @Test
    void declaresOnlyARelativeMapping() throws NoSuchMethodException {
        Method home = PlaceholderController.class.getMethod("home");
        GetMapping mapping = home.getAnnotation(GetMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).isEmpty();
        assertThat(mapping.path()).isEmpty();
        assertThat(AnnotatedElementUtils.findMergedAnnotation(PlaceholderController.class, RequestMapping.class))
                .as("no class-level path").isNull();
        assertThat(PlaceholderController.class.isAnnotationPresent(Controller.class)).isTrue();
    }

    @Test
    void livesInThePackageThatTheAutoConfigurationPrefixes() {
        assertThat(PlaceholderController.class.getPackageName()).isEqualTo("io.github.mdmusahub.jpaadmin.web");
        assertThat(Arrays.stream(PlaceholderController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class))).hasSize(1);
    }
}
