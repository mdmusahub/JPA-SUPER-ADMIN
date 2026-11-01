package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

/** D-29: the sample app runs with open-in-view off, so the demo cannot hide a lazy-loading mistake. */
@SpringBootTest
class OpenInViewDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void noSessionIsKeptOpenForTheWholeWebRequest() {
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
    }
}
