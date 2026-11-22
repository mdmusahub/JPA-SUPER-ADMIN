package io.github.mdmusahub.jpaadmin.sample;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The stale-form story with {@code spring.jpa.open-in-view=false} (D-29, Phase 14a). */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@Import(AbstractStaleFormPagesTest.VersionedEntity.class)
class StaleFormPagesOpenInViewDisabledTest extends AbstractStaleFormPagesTest {
}
