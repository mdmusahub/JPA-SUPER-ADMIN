package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

/**
 * The Phase 5 sort and search proof ({@link AbstractSortProofTest}) with {@code spring.jpa.open-in-view=false} (D-29,
 * and the setting the sample app runs with): no session is kept open for the request, so a sort by a relation must be
 * read entirely inside the console's own read-only transaction.
 */
@SpringBootTest(properties = {"spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
class SortProofOpenInViewDisabledTest extends AbstractSortProofTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSwitchIsRealNoSessionIsKeptOpenForTheRequest() {
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
    }
}
