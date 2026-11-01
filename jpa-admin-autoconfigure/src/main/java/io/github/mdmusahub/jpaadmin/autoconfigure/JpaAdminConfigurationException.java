package io.github.mdmusahub.jpaadmin.autoconfigure;

import java.util.List;

/**
 * Thrown at startup when the console is configured wrongly. Each problem is a full sentence that names the property
 * and says how to fix it, so the message alone is useful. A problem never contains the password (SEC-6).
 */
final class JpaAdminConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    JpaAdminConfigurationException(List<String> problems) {
        super("The JPA Admin console is not configured correctly. " + String.join(" ", problems));
        this.problems = List.copyOf(problems);
    }

    /** One sentence per problem, in the order found. */
    List<String> problems() {
        return problems;
    }
}
