package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import io.github.mdmusahub.jpaadmin.web.PlaceholderController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the console. It is active only in a servlet web application with Spring MVC and JPA on the classpath, and
 * only while {@code jpa.admin.enabled} is {@code true} (the default). Otherwise it backs off completely: no
 * properties are bound (so no credentials are demanded), no beans and no routes exist (ARCH-7).
 *
 * <p>JPA is checked by class name, so this module has no JPA dependency of its own. Bean names start with
 * {@code jpaAdmin} (ARCH-8). The controller and the assets handler are replaceable ({@code @ConditionalOnMissingBean});
 * the base path, the path prefix and the password guard are not, because they enforce rules (SEC-4, SEC-7, SEC-14).
 *
 * <p>Phase 1 contents: properties, the placeholder home page and the asset handler. The security chain arrives in
 * Phase 2.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(value = WebMvcConfigurer.class, name = "jakarta.persistence.EntityManager")
@ConditionalOnProperty(prefix = JpaAdminProperties.PREFIX, name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(JpaAdminProperties.class)
public class JpaAdminAutoConfiguration {

    /** The validated console path. Created from the properties, so the properties are checked before it exists. */
    @Bean
    BasePath jpaAdminBasePath(JpaAdminProperties properties) {
        return BasePath.of(properties.getPath());
    }

    /**
     * The production password check. {@code @Lazy(false)} keeps it eager even when the host runs with
     * {@code spring.main.lazy-initialization=true}; nothing else injects it, so a lazy bean would never run and the
     * credential checks would be skipped silently.
     */
    @Bean
    @Lazy(false)
    JpaAdminPasswordGuard jpaAdminPasswordGuard(JpaAdminProperties properties, Environment environment) {
        return new JpaAdminPasswordGuard(properties, environment);
    }

    /** Adds the base path in front of console controllers only. Not replaceable (SEC-4). */
    @Bean
    JpaAdminPathPrefixConfigurer jpaAdminPathPrefixConfigurer(BasePath basePath) {
        return new JpaAdminPathPrefixConfigurer(basePath);
    }

    /** Serves {@code {path}/assets/**} from the console's own asset folder and from nowhere else. */
    @Bean
    @ConditionalOnMissingBean
    JpaAdminAssetsConfigurer jpaAdminAssetsConfigurer(BasePath basePath) {
        return new JpaAdminAssetsConfigurer(basePath);
    }

    /** Phase 1 home page. Replaced in Phase 2. */
    @Bean
    @ConditionalOnMissingBean
    PlaceholderController jpaAdminPlaceholderController() {
        return new PlaceholderController();
    }
}
