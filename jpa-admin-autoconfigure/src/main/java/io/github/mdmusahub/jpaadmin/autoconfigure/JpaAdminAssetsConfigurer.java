package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the console's CSS, JavaScript and images: {@code {base path}/assets/**} from
 * {@code classpath:/jpa-admin/assets/}, and from nowhere else.
 *
 * <p>The location is deliberately not one of Spring Boot's default static locations ({@code /static},
 * {@code /public}, {@code /resources}, {@code /META-INF/resources}), and this handler maps only the one pattern. So the
 * files are never reachable at a second URL in the host application, outside the console path and its security
 * chain (D-24, ARCH-8, SEC-4), and the host's own static content is never reachable through this handler.
 *
 * <p>Hidden files and folders (any path segment that starts with a dot) are never served, even when one is present in
 * the folder: see {@link NoDotfilesResourceResolver} (D-32).
 *
 * <p>Internal, not stable API. Public and non-final so a host can replace it (the bean backs off).
 */
public class JpaAdminAssetsConfigurer implements WebMvcConfigurer {

    /** The only place assets are read from. Must stay out of Spring Boot's default static locations. */
    static final String LOCATION = "classpath:/jpa-admin/assets/";

    private final BasePath basePath;

    public JpaAdminAssetsConfigurer(BasePath basePath) {
        this.basePath = basePath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(basePath.assetsPattern())
                .addResourceLocations(LOCATION)
                .resourceChain(false)
                .addResolver(new NoDotfilesResourceResolver());
    }
}
