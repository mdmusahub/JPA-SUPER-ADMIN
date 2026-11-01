package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.web.ConsoleViewFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * D-40 in a real Spring Boot application: the starter brings the Thymeleaf-Spring library that the console renders with,
 * and not Spring Boot's Thymeleaf auto-configuration. So a host that never asked for Thymeleaf gets no template engine,
 * no template resolver, no Thymeleaf view resolver and no {@code spring.thymeleaf.*} settings, and the console's pages
 * still render, with their own private engine.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsoleOwnThymeleafTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    @Test
    void theStarterBringsTheThymeleafLibraryButNotBootsThymeleafAutoConfiguration() {
        ClassLoader loader = getClass().getClassLoader();

        assertThat(ClassUtils.isPresent("org.thymeleaf.spring6.SpringTemplateEngine", loader)).isTrue();
        assertThat(ClassUtils.isPresent("org.springframework.boot.thymeleaf.autoconfigure.ThymeleafAutoConfiguration", loader))
                .as("spring-boot-starter-thymeleaf would add a view resolver and an engine to every host").isFalse();
    }

    @Test
    void theHostHasNoThymeleafBeanOfAnyKindAndTheConsoleStillRendersItsPages() throws Exception {
        for (Class<?> type : List.of(ITemplateEngine.class, ITemplateResolver.class, IDialect.class, ThymeleafViewResolver.class)) {
            assertThat(context.getBeanNamesForType(type)).as(type.getSimpleName()).isEmpty();
        }
        assertThat(context.getBeanNamesForType(ConsoleViewFactory.class)).hasSize(1);

        MockHttpServletResponse login = new MockBrowser(mvc).get("/admin/login");
        MockHttpServletResponse dashboard = MockBrowser.signedIn(mvc).get("/admin");

        assertThat(login.getStatus()).isEqualTo(200);
        assertThat(login.getContentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(login.getContentAsString()).contains("<h1>Sign in</h1>");
        assertThat(dashboard.getStatus()).isEqualTo(200);
        assertThat(dashboard.getContentAsString()).contains("Welcome to <span>JPA Admin</span>");
    }
}
