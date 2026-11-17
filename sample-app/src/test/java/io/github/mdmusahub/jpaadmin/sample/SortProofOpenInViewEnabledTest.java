package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

/**
 * The Phase 5 sort and search proof ({@link AbstractSortProofTest}) with {@code spring.jpa.open-in-view=true}, Spring
 * Boot's default and what many hosts run with (D-29): the console must sort the same rows in the same order whether or
 * not the host keeps a session open for the request.
 */
@SpringBootTest(properties = {"spring.jpa.open-in-view=true"})
@AutoConfigureMockMvc
class SortProofOpenInViewEnabledTest extends AbstractSortProofTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSwitchIsRealTheInterceptorExists() {
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).hasSize(1);
    }
}
