package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code jpa.admin.enabled=false}: no console bean and no console route exists, and the credentials are not even
 * asked for (blank here on purpose, which would fail startup if the console were on).
 */
@SpringBootTest(properties = {"jpa.admin.enabled=false", "jpa.admin.username=", "jpa.admin.password="})
@AutoConfigureMockMvc
class ConsoleDisabledTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void theApplicationStartsAndHasNoConsoleBean() {
        assertThat(Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> name.startsWith("jpaAdmin") || name.startsWith("jpa.admin")
                        || name.contains("JpaAdmin")))
                .isEmpty();
    }

    @Test
    void noSpringSecurityMachineryIsLeftBehindEither() {
        assertThat(context.containsBean("springSecurityFilterChain")).isFalse();
        assertThat(context.getBeanNamesForType(org.springframework.security.web.SecurityFilterChain.class)).isEmpty();
        assertThat(context.getBeansOfType(jakarta.servlet.Filter.class).keySet())
                .noneMatch(name -> name.toLowerCase().contains("security"));
    }

    @Test
    void theConsolePathAndItsAssetsAreNotFound() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/assets/css/tokens.css")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/login")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/theme.css")).andExpect(status().isNotFound());
    }

    @Test
    void theHostsOwnStaticContentIsStillServed() throws Exception {
        mvc.perform(get("/host-marker.txt")).andExpect(status().isOk());
    }
}
