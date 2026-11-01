package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

/**
 * D-29: the console must work in a host app that leaves open-in-view on (Spring Boot's default) as well as one that
 * turns it off. This test proves the switch is real: with the property on, the interceptor exists. Without it, the
 * "disabled" test above could pass for the wrong reason.
 */
@SpringBootTest(properties = "spring.jpa.open-in-view=true")
class OpenInViewEnabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void sessionIsKeptOpenForTheWebRequestWhenTheHostAppAsksForIt() {
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).hasSize(1);
    }
}
