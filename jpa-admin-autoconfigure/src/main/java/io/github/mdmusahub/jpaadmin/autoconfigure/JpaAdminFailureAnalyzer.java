package io.github.mdmusahub.jpaadmin.autoconfigure;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns a {@link JpaAdminConfigurationException} into Spring Boot's "APPLICATION FAILED TO START" report, instead of
 * a stack trace. Registered in {@code META-INF/spring.factories}.
 */
final class JpaAdminFailureAnalyzer extends AbstractFailureAnalyzer<JpaAdminConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, JpaAdminConfigurationException cause) {
        StringBuilder description = new StringBuilder("The JPA Admin console is not configured correctly:");
        for (String problem : cause.problems()) {
            description.append(System.lineSeparator()).append("  - ").append(problem);
        }
        String action = "Fix the properties listed above and start the application again. "
                + "Set jpa.admin.enabled=false to turn the console off.";
        return new FailureAnalysis(description.toString(), action, cause);
    }
}
