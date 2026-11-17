package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

/**
 * Every record-page test (detail, delete, bulk delete) with {@code spring.jpa.open-in-view=false} (D-29). Hibernate
 * statistics are on so the tests can count statements and loaded entities.
 */
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
class RecordPagesOpenInViewDisabledTest extends AbstractRecordPagesTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSwitchIsReal() {
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class).length > 0).isEqualTo(false);
    }
}
