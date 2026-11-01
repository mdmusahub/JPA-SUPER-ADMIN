package io.github.mdmusahub.jpaadmin.web;

import java.util.Locale;
import org.springframework.web.servlet.View;

/**
 * Turns the name of one of the console's templates (for example {@code jpa-admin/login}) into a {@link View} that
 * renders it.
 *
 * <p>The console renders its own pages with its own template engine, and it does not hand view names to the host's view
 * resolvers: a host may use another template technology, its own Thymeleaf setup, or a resolver that claims every name,
 * and none of that may change a console page, nor may the console change how the host's views render (SEC-4, D-40).
 * This interface is the seam: this module knows only the names and the model, and the auto-configuration module
 * provides the engine. This module has no dependency on Thymeleaf.
 *
 * <p>Internal, not stable API. Public only because the auto-configuration module implements it.
 */
public interface ConsoleViewFactory {

    /**
     * The view for a console template.
     *
     * @param templateName a console template name such as {@code jpa-admin/login}
     * @param locale       the locale of the request being answered, the one Spring MVC would have used
     * @throws IllegalArgumentException if the name is not one of the console's own templates
     */
    View create(String templateName, Locale locale);
}
