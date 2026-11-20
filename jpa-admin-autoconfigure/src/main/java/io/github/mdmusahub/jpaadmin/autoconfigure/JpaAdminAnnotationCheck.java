package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AnnotationProblems;
import java.util.List;

/**
 * Runs the check of the Phase 13 annotations once, when the application starts (D-120 to D-122): a wrong
 * {@code @AdminBadge}, {@code @AdminLookup} or {@code @AdminResource(plural)} stops startup with one report that names every
 * offending entity and field and says what to write instead. Nothing the host wrote in an annotation is repeated in it.
 *
 * <p>Created {@code @Lazy(false)}, like the upload check: nothing injects it, so a lazy bean would never run and the check
 * would be skipped silently.
 */
final class JpaAdminAnnotationCheck {

    JpaAdminAnnotationCheck(AdminEntityRegistry registry) {
        List<String> problems = AnnotationProblems.of(registry);
        if (!problems.isEmpty()) {
            throw new JpaAdminConfigurationException(problems);
        }
    }
}
