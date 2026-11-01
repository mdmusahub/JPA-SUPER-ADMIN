package io.github.mdmusahub.jpaadmin.autoconfigure;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * The production password check (SEC-7, D-7). Under the {@code prod} profile a weak password stops startup. Under any
 * other profile it logs a warning. Neither the exception nor the log line contains the password (SEC-6).
 *
 * <p>Runs after {@link JpaAdminProperties} has been validated, so the password is known to be present.
 */
final class JpaAdminPasswordGuard implements InitializingBean {

    static final String PROD_PROFILE = "prod";

    private static final Logger log = LoggerFactory.getLogger(JpaAdminPasswordGuard.class);

    private final JpaAdminProperties properties;
    private final Environment environment;

    JpaAdminPasswordGuard(JpaAdminProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        Optional<String> weakness = PasswordPolicy.weakness(properties.getPassword());
        if (weakness.isEmpty()) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of(PROD_PROFILE))) {
            throw new JpaAdminConfigurationException(List.of("jpa.admin.password is too weak for the '" + PROD_PROFILE
                    + "' profile: " + weakness.get() + ". Use at least " + PasswordPolicy.MIN_LENGTH
                    + " characters and avoid common passwords, ideally a random secret from your secret store."));
        }
        log.warn("jpa.admin.password is weak: {}. Use at least {} characters and avoid common passwords. "
                + "With the '{}' profile active the application refuses to start with a weak password.",
                weakness.get(), PasswordPolicy.MIN_LENGTH, PROD_PROFILE);
    }
}
