package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

class PreviewProdGuardTest {

    @Test
    void allowsStartupWithoutProdProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertThatCode(() -> new PreviewProdGuard(environment)).doesNotThrowAnyException();
    }

    @Test
    void rejectsProdProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new PreviewProdGuard(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prod");
    }

    @Test
    void applicationFailsToStartUnderProdProfile() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(JpaAdminUiPreviewApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("prod");

        assertThatThrownBy(application::run).hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
