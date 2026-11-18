package io.github.mdmusahub.jpaadmin.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * True only when some storage has a {@code public-path} (UPL-7). Without one the console registers nothing outside its own path:
 * no handler mapping, no route, nothing for the host to notice (SEC-4).
 *
 * <p>The properties are bound with the same binder and the same class as the real configuration, so every spelling that works
 * for the property (kebab case, camel case, environment variable) also works here.
 */
final class PublicPathConfigured extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        JpaAdminProperties bound = Binder.get(context.getEnvironment()).bind(JpaAdminProperties.PREFIX, JpaAdminProperties.class)
                .orElse(null);
        boolean configured = bound != null && bound.getStorage() != null && bound.getStorage().values().stream()
                .anyMatch(storage -> storage != null && storage.getPublicPath() != null && !storage.getPublicPath().isBlank());
        return configured ? ConditionOutcome.match("a storage has a public-path")
                : ConditionOutcome.noMatch("no storage has a public-path");
    }
}
