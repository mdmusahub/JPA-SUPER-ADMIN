package io.github.mdmusahub.jpaadmin.autoconfigure;

import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.CONSOLE_PASSWORD;
import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.CONSOLE_USER;
import static io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.mvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.mdmusahub.jpaadmin.autoconfigure.ConsoleTestHosts.Observed;
import io.github.mdmusahub.jpaadmin.web.ConsoleViewFactory;
import io.github.mdmusahub.jpaadmin.web.ErrorViews;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.support.RequestDataValueProcessor;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * The console renders its pages with its own Thymeleaf, whatever view technology the host uses, and the host's own views
 * render exactly as before (SEC-4, D-40). Each test builds the same family of hosts with the real auto-configurations:
 *
 * <ul>
 *   <li>(a) no template engine at all, with and without Spring Boot's security;</li>
 *   <li>(b) the host's own Thymeleaf: Boot's auto-configuration with another prefix, suffix, encoding, content type and
 *       view-name filter that even excludes the console's names; switched off; and the host's own resolver, dialect,
 *       engine and view resolver beans;</li>
 *   <li>(c) another view resolver with a higher priority that claims every view name;</li>
 *   <li>(d) other technologies, as test doubles: a JSP-style resolver that claims every name nobody else took, and a
 *       custom resolver standing in for FreeMarker or Mustache.</li>
 * </ul>
 */
class ConsoleViewIsolationTest {

    // ---- the hosts' pages -------------------------------------------------------------------------------------------

    /** The host's own routes, some answered with a view name, as a host with server-side pages does. */
    @Controller
    static class HostPages {

        @GetMapping("/start")
        String start(Model model) {
            model.addAttribute("greeting", "Hello from the host");
            return "home";
        }

        @GetMapping("/legacy")
        String legacy() {
            return "legacy";
        }

        @GetMapping("/hello")
        @ResponseBody
        String hello() {
            return "hello from the host";
        }
    }

    /** What a host view prints; the content type is plain text so nothing can mistake it for a console page. */
    record HostView(String text) implements View {

        @Override
        public String getContentType() {
            return "text/plain;charset=UTF-8";
        }

        @Override
        public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
            response.setContentType(getContentType());
            response.getWriter().write(text);
        }
    }

    /** Claims every view name before anyone else and prints the name: the worst case of "another view resolver". */
    static class GreedyViewResolver implements ViewResolver, Ordered {

        final List<String> asked = new CopyOnWriteArrayList<>();

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public View resolveViewName(String viewName, Locale locale) {
            asked.add(viewName);
            return new HostView("greedy:" + viewName);
        }
    }

    /** Stands in for FreeMarker, Mustache and the like: it renders the names it has a "template" for, and no others. */
    static class OtherTechnologyViewResolver implements ViewResolver, Ordered {

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE - 20;
        }

        @Override
        public View resolveViewName(String viewName, Locale locale) {
            return viewName.equals("home") ? new HostView("other technology: home") : null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class GreedyHost {

        @Bean
        GreedyViewResolver hostGreedyResolver() {
            return new GreedyViewResolver();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OtherTechnologyHost {

        @Bean
        OtherTechnologyViewResolver hostOtherTechnologyResolver() {
            return new OtherTechnologyViewResolver();
        }

        /** JSP style: forwards every name nobody else took to a file below {@code /WEB-INF/jsp/}. */
        @Bean
        InternalResourceViewResolver hostJspResolver() {
            InternalResourceViewResolver jsp = new InternalResourceViewResolver("/WEB-INF/jsp/", ".jsp");
            jsp.setOrder(Ordered.LOWEST_PRECEDENCE);
            return jsp;
        }
    }

    /** A host that set up Thymeleaf itself: its own template resolver, dialect, engine and view resolver. */
    @Configuration(proxyBeanMethods = false)
    static class HostWithItsOwnThymeleaf {

        @Bean
        ITemplateResolver hostTemplateResolver() {
            ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
            resolver.setPrefix("host-views/");
            resolver.setSuffix(".page");
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCharacterEncoding("UTF-8");
            resolver.setCacheable(false);
            return resolver;
        }

        @Bean
        IDialect hostDialect() {
            return () -> "Host dialect";
        }

        @Bean
        SpringTemplateEngine hostTemplateEngine(ITemplateResolver hostTemplateResolver, IDialect hostDialect) {
            SpringTemplateEngine engine = new SpringTemplateEngine();
            engine.setTemplateResolver(hostTemplateResolver);
            engine.addDialect(hostDialect);
            return engine;
        }

        /** Named like Boot's, so it takes Boot's place. Takes only the host's own view name. */
        @Bean
        ThymeleafViewResolver thymeleafViewResolver(SpringTemplateEngine hostTemplateEngine) {
            ThymeleafViewResolver resolver = new ThymeleafViewResolver();
            resolver.setTemplateEngine(hostTemplateEngine);
            resolver.setViewNames(new String[] {"home"});
            resolver.setCharacterEncoding("UTF-8");
            resolver.setContentType("text/html;charset=UTF-8");
            resolver.setOrder(Ordered.LOWEST_PRECEDENCE - 3);
            return resolver;
        }
    }

    /** Settings that would break any page that depended on them: another folder, suffix, encoding and content type. */
    private static final String[] HOSTILE_THYMELEAF_SETTINGS = {
        "spring.thymeleaf.prefix=classpath:/host-views/", "spring.thymeleaf.suffix=.page",
        "spring.thymeleaf.mode=HTML", "spring.thymeleaf.encoding=ISO-8859-1", "spring.thymeleaf.cache=false",
        "spring.thymeleaf.servlet.content-type=text/plain", "spring.thymeleaf.view-names=home",
        "spring.thymeleaf.excluded-view-names=jpa-admin/*", "spring.thymeleaf.check-template=false",
        "spring.thymeleaf.servlet.produce-partial-output-while-processing=true"};

    /** One kind of host. The name is the test's display name. */
    private record Host(String name, WebApplicationContextRunner runner, boolean springSecurity, boolean thymeleaf) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static Stream<Host> hosts() {
        return Stream.of(
                new Host("(a) no template engine",
                        ConsoleTestHosts.withoutSpringSecurity().withUserConfiguration(HostPages.class), false, false),
                new Host("(a) no template engine, Boot's security",
                        ConsoleTestHosts.withBootSecurity().withUserConfiguration(HostPages.class), true, false),
                new Host("(b) own Thymeleaf with another prefix, suffix, encoding, content type and view names",
                        ConsoleTestHosts.withHostThymeleaf().withUserConfiguration(HostPages.class)
                                .withPropertyValues(HOSTILE_THYMELEAF_SETTINGS), false, true),
                new Host("(b) own Thymeleaf switched off",
                        ConsoleTestHosts.withHostThymeleaf().withUserConfiguration(HostPages.class)
                                .withPropertyValues("spring.thymeleaf.enabled=false"), false, false),
                new Host("(b) own Thymeleaf beans: resolver, dialect, engine, view resolver",
                        ConsoleTestHosts.withHostThymeleaf().withUserConfiguration(HostPages.class, HostWithItsOwnThymeleaf.class),
                        false, true),
                new Host("(c) a view resolver with higher priority that claims every name",
                        ConsoleTestHosts.withoutSpringSecurity().withUserConfiguration(HostPages.class, GreedyHost.class),
                        false, false),
                new Host("(c) the same beside Boot's security",
                        ConsoleTestHosts.withBootSecurity().withUserConfiguration(HostPages.class, GreedyHost.class),
                        true, false),
                new Host("(c) the same beside the host's own Thymeleaf",
                        ConsoleTestHosts.withHostThymeleaf().withUserConfiguration(HostPages.class, GreedyHost.class)
                                .withPropertyValues(HOSTILE_THYMELEAF_SETTINGS), false, true),
                new Host("(d) a JSP-style resolver and a resolver of another technology",
                        ConsoleTestHosts.withoutSpringSecurity().withUserConfiguration(HostPages.class, OtherTechnologyHost.class),
                        false, false));
    }

    private static Stream<Host> hostsWithBootsThymeleafSwitchedOn() {
        return hosts().filter(Host::thymeleaf);
    }

    private static Stream<Host> hostsWithAGreedyViewResolver() {
        return hosts().filter(host -> host.name().startsWith("(c)"));
    }

    private static Host host(String namePrefix) {
        return hosts().filter(host -> host.name().startsWith(namePrefix)).findFirst().orElseThrow();
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    @FunctionalInterface
    private interface Probe<T> {
        T apply(AssertableWebApplicationContext context) throws Exception;
    }

    private static <T> T inContext(WebApplicationContextRunner runner, Probe<T> probe) {
        AtomicReference<T> result = new AtomicReference<>();
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            result.set(probe.apply(context));
        });
        return result.get();
    }

    private static WebApplicationContextRunner consoleOff(WebApplicationContextRunner runner) {
        return runner.withPropertyValues("jpa.admin.enabled=false");
    }

    /** Everything a client sees of an answer, including a forward (which MockMvc records and does not follow). */
    private static String describe(MockHttpServletResponse response) throws Exception {
        return Observed.of(response) + " forwardedUrl=" + response.getForwardedUrl();
    }

    /** The console's pages as a visitor meets them: signed out, a failed sign-in, signed in, a missing page, forbidden. */
    private static List<String> consoleTranscript(WebApplicationContextRunner runner) {
        return inContext(runner, context -> {
            MockBrowser browser = new MockBrowser(mvc(context));
            List<String> pages = new ArrayList<>();
            pages.add(describe(browser.get("/admin/login")));
            browser.signInToConsole("/admin", CONSOLE_USER, "not-the-password-0000");
            pages.add(describe(browser.get("/admin/login?error")));
            pages.add(describe(browser.get("/admin/login?logout")));
            browser.signInToConsole("/admin", CONSOLE_USER, CONSOLE_PASSWORD);
            pages.add(describe(browser.get("/admin")));
            pages.add(describe(browser.get("/admin/nope")));
            pages.add(describe(browser.get("/admin/error/forbidden")));
            return pages;
        });
    }

    /** The host's routes, answered. An exception is an answer too (it must be the same one). */
    private static Map<String, String> hostAnswers(WebApplicationContextRunner runner) {
        return inContext(runner, context -> {
            MockBrowser browser = new MockBrowser(mvc(context));
            Map<String, String> answers = new TreeMap<>();
            for (String path : List.of("/start", "/legacy", "/hello", "/nothing-here", "/administrator", "/jpa-admin/login")) {
                try {
                    answers.put("GET " + path, describe(browser.get(path)));
                } catch (Exception e) {
                    answers.put("GET " + path, "threw " + e.getClass().getName() + ": " + e.getMessage());
                }
            }
            return answers;
        });
    }

    private static final Object BASELINE_LOCK = new Object();
    private static List<String> baseline;

    /** The console's pages in the plainest host there is. Every other host must produce the same pages. */
    private static List<String> baseline() {
        synchronized (BASELINE_LOCK) {
            if (baseline == null) {
                baseline = consoleTranscript(ConsoleTestHosts.withoutSpringSecurity());
            }
            return baseline;
        }
    }

    // ---- the console's pages --------------------------------------------------------------------------------------------

    @Test
    void theBaselineIsRealPagesFromTheConsolesOwnTemplates() {
        List<String> pages = baseline();

        assertThat(pages).hasSize(6);
        assertThat(pages.get(0)).contains("<h1>Sign in</h1>").contains("name=\"username\"").contains("status=200");
        assertThat(pages.get(1)).contains(ErrorViews.SIGN_IN_FAILED);
        assertThat(pages.get(2)).contains("You have been signed out.");
        assertThat(pages.get(3)).contains("Welcome to <span>JPA Admin</span>");
        assertThat(pages.get(4)).contains("status=404").contains("Page not found");
        assertThat(pages.get(5)).contains("status=403").contains("Access denied");
        assertThat(String.join("", pages)).doesNotContain("greedy:", "other technology", "Hello from the host");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hosts")
    void theConsolesPagesAreIdenticalInEveryKindOfHost(Host host) {
        assertThat(consoleTranscript(host.runner())).isEqualTo(baseline());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hostsWithAGreedyViewResolver")
    void noViewResolverOfTheHostIsEverAskedAboutAConsolePage(Host host) {
        inContext(host.runner(), context -> {
            GreedyViewResolver greedy = context.getBean(GreedyViewResolver.class);
            MockBrowser browser = new MockBrowser(mvc(context));
            browser.get("/admin/login");
            browser.signInToConsole("/admin", CONSOLE_USER, CONSOLE_PASSWORD);
            browser.get("/admin");
            browser.get("/admin/nope");
            browser.get("/admin/error/forbidden");
            assertThat(greedy.asked).as("the host's resolver would have claimed them: it is not asked").isEmpty();

            browser.get("/start");
            assertThat(greedy.asked).as("control: it is asked about the host's own view").isEqualTo(
                    host.springSecurity() ? List.of() : List.of("home"));
            assertThat(greedy.resolveViewName("jpa-admin/login", Locale.ENGLISH)).as("control: it is greedy").isNotNull();
            return null;
        });
    }

    @Test
    void consolePagesAreUtf8HtmlInTheRequestsLocaleWhateverTheHostsThymeleafSays() {
        WebApplicationContextRunner runner = ConsoleTestHosts.withHostThymeleaf().withUserConfiguration(HostPages.class)
                .withPropertyValues(HOSTILE_THYMELEAF_SETTINGS);

        inContext(runner, context -> {
            MockBrowser browser = new MockBrowser(mvc(context));

            MockHttpServletResponse console = browser.perform(get("/admin/login").locale(Locale.GERMANY));
            MockHttpServletResponse host = browser.perform(get("/start"));

            assertThat(console.getContentType()).isEqualTo("text/html;charset=UTF-8");
            assertThat(console.getCharacterEncoding()).isEqualTo("UTF-8");
            assertThat(console.getHeader("Content-Language")).isEqualTo("de-DE");
            assertThat(console.getContentAsString()).contains("<h1>Sign in</h1>");
            // The host's own page still follows the host's settings.
            assertThat(host.getContentType()).startsWith("text/plain");
            assertThat(host.getContentAsString()).contains("<h1>Hello from the host</h1>");
            return null;
        });
    }

    // ---- the host's views ---------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("hosts")
    void theHostsOwnViewsRenderExactlyAsBeforeWithTheConsoleOffAndOn(Host host) {
        Map<String, String> off = hostAnswers(consoleOff(host.runner()));
        Map<String, String> on = hostAnswers(host.runner());

        assertThat(on).isEqualTo(off);
        assertThat(on).hasSize(6);
    }

    @Test
    void theHostsOwnViewsReallyRenderInTheirOwnTechnology() {
        assertThat(hostAnswers(host("(b) own Thymeleaf with another").runner()).get("GET /start"))
                .contains("<h1>Hello from the host</h1>").contains("text/plain").contains("action=\"/host/submit\"");
        assertThat(hostAnswers(host("(b) own Thymeleaf beans").runner()).get("GET /start"))
                .contains("<h1>Hello from the host</h1>");
        assertThat(hostAnswers(host("(c) a view resolver").runner()).get("GET /start")).contains("greedy:home");
        Map<String, String> other = hostAnswers(host("(d)").runner());
        assertThat(other.get("GET /start")).contains("other technology: home");
        assertThat(other.get("GET /legacy")).contains("forwardedUrl=/WEB-INF/jsp/legacy.jsp");
    }

    // ---- nothing is added for the host to find ------------------------------------------------------------------------

    private static final List<Class<?>> VIEW_TECHNOLOGY_TYPES = List.of(ViewResolver.class, View.class, ITemplateResolver.class,
            ITemplateEngine.class, IDialect.class, MessageSource.class, LocaleResolver.class);

    private static Map<String, Set<String>> beansOfViewTypes(WebApplicationContextRunner runner) {
        return inContext(runner, context -> {
            Map<String, Set<String>> beans = new TreeMap<>();
            for (Class<?> type : VIEW_TECHNOLOGY_TYPES) {
                beans.put(type.getSimpleName(), new TreeSet<>(List.of(context.getBeanNamesForType(type))));
            }
            return beans;
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hosts")
    void theConsoleAddsNoBeanOfAnyViewTechnologyType(Host host) {
        Map<String, Set<String>> off = beansOfViewTypes(consoleOff(host.runner()));
        Map<String, Set<String>> on = beansOfViewTypes(host.runner());

        assertThat(on).isEqualTo(off);
    }

    @Test
    void theConsolesViewFactoryIsOneBeanOfItsOwnTypeAndNotAViewResolverOrATemplateEngine() {
        inContext(ConsoleTestHosts.withHostThymeleaf(), context -> {
            assertThat(context.getBeansOfType(ConsoleViewFactory.class)).hasSize(1);
            Object factory = context.getBean(ConsoleViewFactory.class);
            assertThat(factory).isNotInstanceOfAny(ViewResolver.class, View.class, ITemplateResolver.class,
                    ITemplateEngine.class, IDialect.class);
            return null;
        });
    }

    @Test
    void aHostWithoutSpringSecurityGainsSpringSecuritysFormHookAndHostFormsStayTheSame() {
        // @EnableWebSecurity, which the console imports there (D-35), registers this bean. It only adds a hidden field when
        // the request passed Spring Security's CSRF filter, and a host request never does: the form in the host's page below is
        // the same with and without the console (theHostsOwnViewsRenderExactlyAsBeforeWithTheConsoleOffAndOn).
        WebApplicationContextRunner host = ConsoleTestHosts.withoutSpringSecurity().withUserConfiguration(HostPages.class);
        Set<String> off = inContext(consoleOff(host), context -> Set.of(context.getBeanNamesForType(
                RequestDataValueProcessor.class)));
        Set<String> on = inContext(host, context -> Set.of(context.getBeanNamesForType(
                RequestDataValueProcessor.class)));

        assertThat(off).isEmpty();
        assertThat(on).containsExactly("requestDataValueProcessor");
    }

    // ---- the host's own Thymeleaf is configured exactly as before ------------------------------------------------------------

    /** Every setting of the host's Thymeleaf view resolvers, template resolvers and dialects, as plain text. */
    private static List<String> hostThymeleaf(WebApplicationContextRunner runner) {
        return inContext(runner, context -> {
            List<String> lines = new ArrayList<>();
            new TreeMap<>(context.getBeansOfType(ThymeleafViewResolver.class)).forEach((name, resolver) -> lines.add(
                    "view resolver " + name + " order=" + resolver.getOrder() + " names="
                            + List.of(resolver.getViewNames() == null ? new String[0] : resolver.getViewNames()) + " excluded="
                            + List.of(resolver.getExcludedViewNames() == null ? new String[0] : resolver.getExcludedViewNames())
                            + " contentType=" + resolver.getContentType() + " encoding=" + resolver.getCharacterEncoding()
                            + " cache=" + resolver.isCache() + " partial=" + resolver.getProducePartialOutputWhileProcessing()
                            + " redirectAndForward=" + resolver.getAlwaysProcessRedirectAndForward()));
            new TreeMap<>(context.getBeansOfType(SpringTemplateEngine.class)).forEach((name, engine) -> {
                engine.getTemplateResolvers().forEach(resolver -> lines.add("engine " + name + " resolver "
                        + resolver.getClass().getSimpleName() + " " + resolver.getName() + " order=" + resolver.getOrder()
                        + (resolver instanceof AbstractConfigurableTemplateResolver configurable
                                ? " prefix=" + configurable.getPrefix() + " suffix=" + configurable.getSuffix() + " mode="
                                        + configurable.getTemplateMode() + " encoding=" + configurable.getCharacterEncoding()
                                        + " cacheable=" + configurable.isCacheable() + " patterns="
                                        + new TreeSet<>(configurable.getResolvablePatterns())
                                : "")));
                engine.getDialects().forEach(dialect -> lines.add("engine " + name + " dialect " + dialect.getName()));
            });
            new TreeMap<>(context.getBeansOfType(ITemplateResolver.class)).forEach((name, resolver) ->
                    lines.add("template resolver bean " + name + " " + resolver.getClass().getSimpleName()));
            return lines.stream().sorted().toList();
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hostsWithBootsThymeleafSwitchedOn")
    void theHostsThymeleafResolversEnginesAndDialectsAreConfiguredExactlyAsBeforeAndHoldNothingOfTheConsole(Host host) {
        List<String> off = hostThymeleaf(consoleOff(host.runner()));
        List<String> on = hostThymeleaf(host.runner());

        assertThat(on).isEqualTo(off).isNotEmpty();
        // The console's own resolver is named and limited to its names; neither may appear among the host's objects.
        assertThat(String.join("\n", on)).doesNotContain("JPA Super Admin").doesNotContain("patterns=[jpa-admin");
    }
}
