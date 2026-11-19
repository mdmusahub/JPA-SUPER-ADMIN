package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

/**
 * Every chart test (dashboard card and JSON endpoint) with {@code spring.jpa.open-in-view=true} (D-29). Hibernate statistics
 * are on so the tests can count the statements a request really runs.
 */
@SpringBootTest(properties = {"spring.jpa.open-in-view=true", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@Import(FixedClockConfiguration.class)
class ChartPagesOpenInViewEnabledTest extends AbstractChartPagesTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSwitchIsReal() {
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class).length > 0).isEqualTo(true);
    }
}
