package io.github.mdmusahub.jpaadmin.preview;

import static io.github.mdmusahub.jpaadmin.preview.ChromeFixtures.ALTERNATE_BASE;
import static io.github.mdmusahub.jpaadmin.preview.ChromeFixtures.BASE;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the console's stylesheets and scripts for the preview, the way the real console does: from
 * {@code classpath:/jpa-admin/assets/} under {@code {base path}/assets/**}, and nowhere else. The preview does not
 * depend on the auto-configuration module, so it has its own two-line version of that handler: one for the preview's
 * base path and one for the second base path of the {@code custom-base-path} chrome variant.
 */
@Configuration(proxyBeanMethods = false)
class PreviewWebConfig implements WebMvcConfigurer {

    private static final String ASSETS = "classpath:/jpa-admin/assets/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(BASE + "/assets/**").addResourceLocations(ASSETS);
        registry.addResourceHandler(ALTERNATE_BASE + "/assets/**").addResourceLocations(ASSETS);
    }
}
