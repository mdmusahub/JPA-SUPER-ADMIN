package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import io.github.mdmusahub.jpaadmin.web.PlaceholderController;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;

/** SEC-4: the base path is put in front of the console's controllers and in front of nothing else. */
class JpaAdminPathPrefixConfigurerTest {

    @Test
    void theConsoleControllerIsPrefixed() {
        assertThat(JpaAdminPathPrefixConfigurer.isConsoleController(PlaceholderController.class)).isTrue();
    }

    @Test
    void aHostControllerIsNotPrefixed() {
        assertThat(JpaAdminPathPrefixConfigurer.isConsoleController(JpaAdminPathPrefixConfigurerTest.class)).isFalse();
        assertThat(JpaAdminPathPrefixConfigurer.isConsoleController(JpaAdminAutoConfiguration.class)).isFalse();
        assertThat(JpaAdminPathPrefixConfigurer.isConsoleController(String.class)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"io.github.mdmusahub.jpaadmin.web", "io.github.mdmusahub.jpaadmin.web.view",
            "io.github.mdmusahub.jpaadmin.web.upload"})
    void theConsolePackageAndItsSubPackagesCount(String packageName) {
        assertThat(JpaAdminPathPrefixConfigurer.isConsolePackage(packageName)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "io.github.mdmusahub.jpaadmin", "io.github.mdmusahub.jpaadmin.webhook",
            "io.github.mdmusahub.jpaadmin.webx.admin", "io.github.mdmusahub.jpaadmin.autoconfigure",
            "io.github.mdmusahub.jpaadmin.other", "com.example.io.github.mdmusahub.jpaadmin.web", "web"})
    void aLookalikePackageDoesNotCount(String packageName) {
        assertThat(JpaAdminPathPrefixConfigurer.isConsolePackage(packageName)).isFalse();
    }

    /** {@code getPathPrefixes()} is protected in Spring Framework; this exposes it to the test. */
    private static final class InspectableConfigurer extends PathMatchConfigurer {
        Map<String, Predicate<Class<?>>> prefixes() {
            return getPathPrefixes();
        }
    }

    @Test
    void registersTheBasePathWithThePredicateAndNoOtherPrefix() {
        InspectableConfigurer configurer = new InspectableConfigurer();

        new JpaAdminPathPrefixConfigurer(BasePath.of("/backoffice")).configurePathMatch(configurer);

        assertThat(configurer.prefixes()).hasSize(1).containsKey("/backoffice");
        Predicate<Class<?>> predicate = configurer.prefixes().get("/backoffice");
        assertThat(predicate.test(PlaceholderController.class)).isTrue();
        assertThat(predicate.test(JpaAdminPathPrefixConfigurerTest.class)).isFalse();
    }
}
