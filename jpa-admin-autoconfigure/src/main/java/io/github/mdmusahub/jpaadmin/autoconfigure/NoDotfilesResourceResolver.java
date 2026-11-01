package io.github.mdmusahub.jpaadmin.autoconfigure;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.AbstractResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import org.springframework.web.util.UriUtils;

/**
 * Makes the console's asset handler refuse every hidden file and folder: any path segment that starts with a dot
 * ({@code .gitkeep}, {@code .DS_Store}, {@code .env}, {@code .git/config}, {@code css/.hidden}) is "not found", whether
 * or not such a file exists, so the answer does not reveal whether it does.
 *
 * <p>A dotfile is never a console asset. Build tools and editors leave them in resource folders (the Maven resources
 * plugin copies {@code .gitkeep} into the jar), and a handler that serves "whatever is in the folder" would publish
 * them. The check looks at the path as written and after percent-decoding, so {@code %2egitkeep} is refused too.
 * A path that cannot be decoded is refused as well.
 *
 * <p>Registered first in the handler's resolver chain; Spring Framework appends its default path resolver after it.
 */
final class NoDotfilesResourceResolver extends AbstractResourceResolver {

    @Override
    protected Resource resolveResourceInternal(HttpServletRequest request, String requestPath,
            List<? extends Resource> locations, ResourceResolverChain chain) {
        return isHidden(requestPath) ? null : chain.resolveResource(request, requestPath, locations);
    }

    @Override
    protected String resolveUrlPathInternal(String resourceUrlPath, List<? extends Resource> locations,
            ResourceResolverChain chain) {
        return isHidden(resourceUrlPath) ? null : chain.resolveUrlPath(resourceUrlPath, locations);
    }

    /** Whether any segment of {@code path}, as written or percent-decoded, starts with a dot. */
    static boolean isHidden(String path) {
        if (hasDotSegment(path)) {
            return true;
        }
        try {
            return hasDotSegment(UriUtils.decode(path, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException malformedEncoding) {
            return true;
        }
    }

    private static boolean hasDotSegment(String path) {
        for (String segment : path.split("[/\\\\]")) {
            if (segment.startsWith(".")) {
                return true;
            }
        }
        return false;
    }
}
