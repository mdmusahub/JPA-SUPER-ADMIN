package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Puts the configured base path in front of every console controller, and in front of nothing else (SEC-4).
 *
 * <p>Console controllers (the classes in {@code io.github.mdmusahub.jpaadmin.web}) declare mappings relative to the
 * console, for example an empty {@code @GetMapping} for the home page. This configurer adds the prefix, so the path
 * comes from one validated place ({@link BasePath}) and a host application's own controllers are never moved.
 *
 * <p>Because Spring Framework asks this configurer for its prefix only when it builds the MVC mappings, and the
 * prefix comes from the {@link BasePath} bean, the properties are always validated before any mapping exists.
 */
final class JpaAdminPathPrefixConfigurer implements WebMvcConfigurer {

    static final String CONSOLE_PACKAGE = "io.github.mdmusahub.jpaadmin.web";

    private final BasePath basePath;

    JpaAdminPathPrefixConfigurer(BasePath basePath) {
        this.basePath = basePath;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(basePath.value(), JpaAdminPathPrefixConfigurer::isConsoleController);
    }

    /** Whether {@code type} is one of the console's own controllers: the package and its sub-packages, nothing else. */
    static boolean isConsoleController(Class<?> type) {
        return isConsolePackage(type.getPackageName());
    }

    /** {@code ...jpaadmin.web} and {@code ...jpaadmin.web.x} count; {@code ...jpaadmin.webhook} does not. */
    static boolean isConsolePackage(String packageName) {
        return packageName.equals(CONSOLE_PACKAGE) || packageName.startsWith(CONSOLE_PACKAGE + ".");
    }
}
