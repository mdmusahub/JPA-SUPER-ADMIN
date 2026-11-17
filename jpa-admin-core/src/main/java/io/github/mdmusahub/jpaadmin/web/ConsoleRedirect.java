package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import java.util.Objects;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Answers a successful POST with a redirect (303 See Other), so a reload of the next page never repeats the POST.
 *
 * <p>The target is never taken from a request. It is a link the console built itself ({@link ListLinks}), and it passes the
 * one link rule of the contract before it is used (the same rule as every link in a view-model record: a relative path, no
 * scheme, no {@code //}, no backslash, no space or control character, D-28) and must lie under the console's base path. A
 * target that fails either is a bug, so it is an exception and never a redirect to somewhere else (no open redirect).
 *
 * <p>Package-private: it is a rule, not API.
 */
final class ConsoleRedirect {

    private ConsoleRedirect() {
    }

    /**
     * @param basePath the console path
     * @param href     a link built by the console
     * @throws IllegalArgumentException if {@code href} breaks the link rule or does not lie under the base path
     */
    static ModelAndView to(BasePath basePath, String href) {
        Objects.requireNonNull(basePath, "basePath");
        // A Breadcrumb checks its link with the contract's rule (Hrefs), which is package-private: the record is the one
        // place the rule is written, and a redirect must pass it exactly like a printed link.
        new PageChrome.Breadcrumb("redirect", Objects.requireNonNull(href, "href"));
        String base = basePath.value();
        if (!(href.equals(base) || href.startsWith(base + "/") || href.startsWith(base + "?"))) {
            throw new IllegalArgumentException("a redirect must lie under the base path");
        }
        // Context-relative (the servlet context path is added), 303 See Other, no model attributes in the address, and
        // no {placeholder} expansion: the link was already encoded.
        RedirectView view = new RedirectView(href, true, false, false);
        view.setExpandUriTemplateVariables(false);
        return new ModelAndView(view);
    }
}
