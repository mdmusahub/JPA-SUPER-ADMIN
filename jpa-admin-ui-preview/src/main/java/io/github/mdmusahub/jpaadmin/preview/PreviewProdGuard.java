package io.github.mdmusahub.jpaadmin.preview;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Stops the preview app from starting under the {@code prod} profile (ARCH-11). The preview has no login, so it
 * must never run in production.
 */
@Component
class PreviewProdGuard {

    PreviewProdGuard(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "jpa-admin-ui-preview must not run with the 'prod' profile. It has no login and uses fake data.");
        }
    }
}
