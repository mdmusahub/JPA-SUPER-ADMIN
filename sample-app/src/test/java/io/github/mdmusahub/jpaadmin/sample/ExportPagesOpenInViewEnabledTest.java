package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

/**
 * Every export test with {@code spring.jpa.open-in-view=true} (D-29): a session is kept open for the whole web request, which
 * must change nothing about what the export reads, selects or costs.
 */
@SpringBootTest(properties = {"spring.jpa.open-in-view=true", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
class ExportPagesOpenInViewEnabledTest extends AbstractExportPagesTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSwitchIsRealASessionIsKeptOpenForTheRequest() {
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).hasSize(1);
    }
}
