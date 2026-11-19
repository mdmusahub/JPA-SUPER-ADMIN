package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

/**
 * Every hidden-column test with {@code spring.jpa.open-in-view=false} (D-29, and the setting the sample app runs with): no session is
 * kept open for the web request, so the list and the export must read everything inside its own read-only transactions. Hibernate statistics
 * are on so the tests can count statements and loaded entities.
 */
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
class HiddenColumnsPagesOpenInViewDisabledTest extends AbstractHiddenColumnsPagesTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSwitchIsRealNoSessionIsKeptOpenForTheRequest() {
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
    }
}
