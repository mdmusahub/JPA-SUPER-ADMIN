package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.web.view.DashboardView;
import io.github.mdmusahub.jpaadmin.web.view.DetailView;
import io.github.mdmusahub.jpaadmin.web.view.ErrorView;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.LoginView;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.View;
import org.thymeleaf.context.Context;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafView;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * The console's private Thymeleaf (D-40) on its own, without a host around it: it accepts the console's template names
 * and nothing else, its settings come from constants and not from the environment, and it cannot render a template of
 * the host.
 */
class ConsoleThymeleafViewsTest {

    private static GenericWebApplicationContext context(Map<String, Object> environment) {
        GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", environment));
        return context;
    }

    private static ConsoleThymeleafViews views(GenericWebApplicationContext context) {
        context.refresh();
        return new ConsoleThymeleafViews(context);
    }

    private static ConsoleThymeleafViews views() {
        return views(context(Map.of()));
    }

    // ---- which names are accepted ----------------------------------------------------------------------------------

    @Test
    void everyTemplateNameOfTheViewContractIsAcceptedAndBecomesAThymeleafViewWithFixedSettings() {
        ConsoleThymeleafViews views = views();

        for (String name : List.of(DashboardView.TEMPLATE, DetailView.TEMPLATE, ErrorView.TEMPLATE, FormView.TEMPLATE,
                ListView.TEMPLATE, LoginView.TEMPLATE)) {
            View view = views.create(name, Locale.ENGLISH);

            assertThat(view).as(name).isInstanceOf(ThymeleafView.class);
            ThymeleafView thymeleaf = (ThymeleafView) view;
            assertThat(thymeleaf.getTemplateName()).isEqualTo(name);
            assertThat(thymeleaf.getContentType()).isEqualTo("text/html;charset=UTF-8");
            assertThat(thymeleaf.getCharacterEncoding()).isEqualTo("UTF-8");
            assertThat(thymeleaf.getProducePartialOutputWhileProcessing())
                    .as("a failing template must not leave half a page behind a 200").isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"home", "login", "jpa-admin", "jpa-admin/", "/jpa-admin/login", "jpa-admin//login",
            "jpa-admin/../login", "jpa-admin/login/..", "jpa-admin/./login", "jpa-admin/login.html", "jpa-admin\\login",
            "JPA-ADMIN/login", "jpa-admin/Login", "jpa-admin/login_page", "redirect:/jpa-admin/login", "forward:/jpa-admin/login",
            "classpath:/templates/jpa-admin/login", "file:/etc/passwd", "jpa-admin/login ", " jpa-admin/login", "jpa-admin/login\n",
            "jpa-admin/${x}", "jpa-admin/__${x}__", "jpa-admin/login :: fragment", "jpa-admin/login%2e%2e", "jpa-admin/ä", ""})
    void anythingThatIsNotAConsoleTemplateNameIsRefused(String name) {
        ConsoleThymeleafViews views = views();

        assertThatThrownBy(() -> views.create(name, Locale.ENGLISH)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a console template");
    }

    @ParameterizedTest
    @NullSource
    void aMissingNameOrLocaleIsRefused(String name) {
        ConsoleThymeleafViews views = views();

        assertThatThrownBy(() -> views.create(name, Locale.ENGLISH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> views.create(LoginView.TEMPLATE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void theViewResolverAnswersForConsoleNamesOnlyAndNeverForRedirectsOrForwards() throws Exception {
        ConsoleThymeleafViews views = views();

        assertThat(views.viewResolver().resolveViewName(LoginView.TEMPLATE, Locale.ENGLISH)).isNotNull();
        for (String name : List.of("home", "redirect:/admin", "forward:/admin", "redirect:/jpa-admin/login", "jpa-admin")) {
            assertThat(views.viewResolver().resolveViewName(name, Locale.ENGLISH)).as(name).isNull();
        }
        assertThat(views.viewResolver().getViewNames()).containsExactly("jpa-admin/*");
        assertThat(views.viewResolver().getAlwaysProcessRedirectAndForward()).isFalse();
    }

    // ---- the engine ----------------------------------------------------------------------------------------------------

    @Test
    void theEngineHasOneResolverForConsoleTemplatesWithSettingsFromConstantsAndOnlyTheStandardDialect() {
        ConsoleThymeleafViews views = views();

        assertThat(views.engine().getTemplateResolvers()).hasSize(1).first().isInstanceOfSatisfying(
                SpringResourceTemplateResolver.class, resolver -> {
                    assertThat(resolver.getPrefix()).isEqualTo("classpath:/templates/");
                    assertThat(resolver.getSuffix()).isEqualTo(".html");
                    assertThat(resolver.getTemplateMode()).isEqualTo(TemplateMode.HTML);
                    assertThat(resolver.getCharacterEncoding()).isEqualTo("UTF-8");
                    assertThat(resolver.isCacheable()).isTrue();
                    assertThat(resolver.getResolvablePatterns()).containsExactly("jpa-admin/*");
                });
        assertThat(views.engine().getDialects()).hasSize(1).first().isInstanceOf(SpringStandardDialect.class);
    }

    @Test
    void theEngineCannotRenderATemplateOfTheHostEvenIfAskedAndEvenIfTheFileIsOnTheClasspath() {
        Context model = new Context(Locale.ENGLISH, Map.of("message", "found"));
        // The control: a resolver without the name limit finds the file, so the fixture really is reachable.
        SpringResourceTemplateResolver unlimited = new SpringResourceTemplateResolver();
        GenericWebApplicationContext context = context(Map.of());
        context.refresh();
        unlimited.setApplicationContext(context);
        unlimited.setPrefix("classpath:/templates/");
        unlimited.setSuffix(".html");
        SpringTemplateEngine control = new SpringTemplateEngine();
        control.setTemplateResolver(unlimited);
        assertThat(control.process("host-only", model)).contains("<p>found</p>");

        ConsoleThymeleafViews views = new ConsoleThymeleafViews(context);

        assertThatThrownBy(() -> views.engine().process("host-only", model)).isInstanceOf(TemplateInputException.class);
    }

    // ---- nothing comes from the host ---------------------------------------------------------------------------------

    @Test
    void thePropertiesOfTheHostsThymeleafAreNotRead() {
        ConsoleThymeleafViews views = views(context(Map.of(
                "spring.thymeleaf.prefix", "classpath:/host-views/",
                "spring.thymeleaf.suffix", ".page",
                "spring.thymeleaf.mode", "TEXT",
                "spring.thymeleaf.encoding", "ISO-8859-1",
                "spring.thymeleaf.cache", "false",
                "spring.thymeleaf.servlet.content-type", "text/plain",
                "spring.thymeleaf.servlet.produce-partial-output-while-processing", "true",
                "spring.thymeleaf.view-names", "home",
                "spring.thymeleaf.excluded-view-names", "jpa-admin/*",
                "spring.thymeleaf.enabled", "false")));

        assertThat(views.engine().getTemplateResolvers()).hasSize(1).first().isInstanceOfSatisfying(
                SpringResourceTemplateResolver.class, resolver -> {
                    assertThat(resolver.getPrefix()).isEqualTo("classpath:/templates/");
                    assertThat(resolver.getSuffix()).isEqualTo(".html");
                    assertThat(resolver.getTemplateMode()).isEqualTo(TemplateMode.HTML);
                    assertThat(resolver.getCharacterEncoding()).isEqualTo("UTF-8");
                    assertThat(resolver.isCacheable()).isTrue();
                });
        ThymeleafView view = (ThymeleafView) views.create(LoginView.TEMPLATE, Locale.ENGLISH);
        assertThat(view.getContentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(view.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(view.getProducePartialOutputWhileProcessing()).isFalse();
        assertThat(views.viewResolver().getViewNames()).containsExactly("jpa-admin/*");
        assertThat(views.viewResolver().getExcludedViewNames()).isNull();
    }

    @Test
    void theHostsTemplateResolversAndDialectsInTheContextAreNotUsed() {
        GenericWebApplicationContext context = context(Map.of());
        SpringResourceTemplateResolver hostResolver = new SpringResourceTemplateResolver();
        hostResolver.setPrefix("classpath:/host-views/");
        hostResolver.setSuffix(".page");
        IDialect hostDialect = () -> "Host dialect";
        context.registerBean("hostResolver", ITemplateResolver.class, () -> hostResolver);
        context.registerBean("hostDialect", IDialect.class, () -> hostDialect);

        ConsoleThymeleafViews views = views(context);

        assertThat(views.engine().getTemplateResolvers()).hasSize(1).doesNotContain(hostResolver);
        assertThat(views.engine().getDialects()).hasSize(1).doesNotContain(hostDialect);
        assertThat(Set.copyOf(views.engine().getTemplateResolvers())).doesNotContain(hostResolver);
    }
}
