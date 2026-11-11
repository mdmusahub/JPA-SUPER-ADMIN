package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Builds the answer of every console page: the {@code chrome} and {@code view} model attributes (the frozen contract of
 * {@code docs/UI_PLAN.md} section 3) together with a ready-made {@code View}.
 *
 * <p>Because the {@code ModelAndView} carries the view itself and not just its name, Spring MVC renders it directly and
 * never asks the host's view resolvers about it (D-40). The template names are still the contract's constants
 * ({@code LoginView.TEMPLATE} and so on). The locale is the one Spring MVC would have used for the request.
 *
 * <p>Internal, not stable API. Public only because the auto-configuration module creates it.
 */
public final class ConsoleViews {

    private final ConsoleViewFactory factory;

    public ConsoleViews(ConsoleViewFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    /**
     * A page: the template, the chrome shared by every page and the page's own record.
     *
     * @param request  the request being answered, for its locale
     * @param template one of the contract's template names
     * @param chrome   the shell data, model attribute {@value PageChrome#ATTRIBUTE}
     * @param view     the page's record, model attribute {@value PageChrome#VIEW_ATTRIBUTE}
     */
    public ModelAndView page(HttpServletRequest request, String template, PageChrome chrome, Object view) {
        ModelAndView page = page(request, template, chrome);
        page.addObject(PageChrome.VIEW_ATTRIBUTE, Objects.requireNonNull(view, "view"));
        return page;
    }

    /**
     * A page that has nothing but the shell, so it needs no record of its own and gets no {@code view} attribute.
     * Only the temporary entity page uses it, until Phase 4 fills a {@code ListView} there.
     *
     * @param request  the request being answered, for its locale
     * @param template one of the contract's template names
     * @param chrome   the shell data, model attribute {@value PageChrome#ATTRIBUTE}
     */
    public ModelAndView page(HttpServletRequest request, String template, PageChrome chrome) {
        ModelAndView page = new ModelAndView(factory.create(template, RequestContextUtils.getLocale(request)));
        page.addObject(PageChrome.ATTRIBUTE, Objects.requireNonNull(chrome, "chrome"));
        return page;
    }
}
