package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.web.ConsoleViewFactory;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.View;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * The console's own Thymeleaf: one template engine, one template resolver and one view resolver, all created here and
 * held in private fields. None of them is a bean, so nothing in the host application can find, replace, reorder or
 * extend them, and they find nothing of the host's (D-40).
 *
 * <p>What that rules out, each point with a test:
 * <ul>
 *   <li>The host's {@code spring.thymeleaf.*} settings are not read. Prefix, suffix, mode, encoding, cache and content
 *       type are set below, from constants.</li>
 *   <li>The host's own {@code ITemplateResolver}, dialect, {@code ISpringTemplateEngine} or {@code ViewResolver} beans
 *       are not used. Boot's Thymeleaf auto-configuration collects every {@code ITemplateResolver} bean into its engine
 *       and backs off for an {@code ISpringTemplateEngine} bean, so a bean of ours would change the host's Thymeleaf.</li>
 *   <li>The host's view resolvers (another template technology, a resolver that claims every name, one with a higher
 *       priority) are never asked about a console page: {@link io.github.mdmusahub.jpaadmin.web.ConsoleViews} hands
 *       Spring MVC the {@code View} itself, not its name.</li>
 *   <li>The engine can resolve templates under {@code jpa-admin/} only, and the view resolver answers only for those
 *       names, so it cannot render a template of the host even if it were asked to.</li>
 * </ul>
 *
 * <p>Settings chosen on purpose: the output of a page is buffered and sent only when the whole page rendered, so a
 * failing template yields an error and never half a page behind a 200 status; templates are cached (they are inside
 * the jar and cannot change); {@code redirect:} and {@code forward:} names are not honoured.
 *
 * <p>Thymeleaf finds the templates through the application context's resource loader, from {@code classpath:/templates/}
 * (the folder {@code jpa-admin-ui} ships them in).
 */
final class ConsoleThymeleafViews implements ConsoleViewFactory {

    /** Every console template lives under this name prefix. */
    static final String NAME_PATTERN = "jpa-admin/*";

    static final String TEMPLATE_PREFIX = "classpath:/templates/";
    static final String TEMPLATE_SUFFIX = ".html";
    static final String CHARACTER_ENCODING = "UTF-8";
    static final String CONTENT_TYPE = "text/html;charset=" + CHARACTER_ENCODING;

    /**
     * The shape of a console template name: {@code jpa-admin/} and then lower-case words joined by hyphens or slashes.
     * No dot, no backslash, no colon, no {@code ..}: nothing that could leave the folder or name a protocol.
     */
    private static final Pattern TEMPLATE_NAME = Pattern.compile("jpa-admin/[a-z0-9-]+(/[a-z0-9-]+)*");

    private final SpringTemplateEngine engine;
    private final ThymeleafViewResolver viewResolver;

    ConsoleThymeleafViews(ApplicationContext context) {
        Objects.requireNonNull(context, "context");

        SpringResourceTemplateResolver templates = new SpringResourceTemplateResolver();
        templates.setApplicationContext(context);
        templates.setName("JPA Super Admin console templates");
        templates.setPrefix(TEMPLATE_PREFIX);
        templates.setSuffix(TEMPLATE_SUFFIX);
        templates.setTemplateMode(TemplateMode.HTML);
        templates.setCharacterEncoding(CHARACTER_ENCODING);
        templates.setCacheable(true);
        templates.setResolvablePatterns(Set.of(NAME_PATTERN));

        this.engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);

        this.viewResolver = new ThymeleafViewResolver();
        viewResolver.setApplicationContext(context);
        viewResolver.setTemplateEngine(engine);
        viewResolver.setViewNames(new String[] {NAME_PATTERN});
        viewResolver.setCharacterEncoding(CHARACTER_ENCODING);
        viewResolver.setContentType(CONTENT_TYPE);
        viewResolver.setAlwaysProcessRedirectAndForward(false);
        viewResolver.setProducePartialOutputWhileProcessing(false);
    }

    @Override
    public View create(String templateName, Locale locale) {
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(locale, "locale");
        if (!TEMPLATE_NAME.matcher(templateName).matches()) {
            throw new IllegalArgumentException("Not a console template: " + templateName);
        }
        View view;
        try {
            view = viewResolver.resolveViewName(templateName, locale);
        } catch (Exception e) {
            throw new IllegalStateException("The console view " + templateName + " could not be created", e);
        }
        if (view == null) {
            throw new IllegalArgumentException("Not a console template: " + templateName);
        }
        return view;
    }

    /** The private engine, for the tests that prove what it can and cannot resolve. */
    SpringTemplateEngine engine() {
        return engine;
    }

    /** The private view resolver, for the tests that prove which names it answers for. */
    ThymeleafViewResolver viewResolver() {
        return viewResolver;
    }
}
