package io.github.mdmusahub.jpaadmin.sample;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The stale-form story with {@code spring.jpa.open-in-view=true} (D-29, Phase 14a). */
@SpringBootTest(properties = "spring.jpa.open-in-view=true")
@AutoConfigureMockMvc
@Import(AbstractStaleFormPagesTest.VersionedEntity.class)
class StaleFormPagesOpenInViewEnabledTest extends AbstractStaleFormPagesTest {
}
