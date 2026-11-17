package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

/**
 * Every filter test with {@code spring.jpa.open-in-view=true}, Spring Boot's default and what many hosts run with (D-29).
 * The console must give the same pages, with the same statements, whether or not the host keeps a session open.
 */
@SpringBootTest(properties = {"spring.jpa.open-in-view=true", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
class FilterPagesOpenInViewEnabledTest extends AbstractFilterPagesTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSwitchIsRealTheInterceptorExists() {
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).hasSize(1);
    }
}
