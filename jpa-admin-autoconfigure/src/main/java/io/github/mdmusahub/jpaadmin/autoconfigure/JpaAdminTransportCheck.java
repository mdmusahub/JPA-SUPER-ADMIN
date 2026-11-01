package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * The plain-HTTP warning (SEC-8). Under the {@code prod} profile, when nothing says that the console is reached over
 * HTTPS, one WARN line says so and what to do. It only logs: the console changes no cookie, session or server
 * setting, because those are global and would also change the host application (D-8).
 *
 * <p>"Reached over HTTPS" means one of: TLS is configured in the application ({@code server.ssl.*}), or the application
 * trusts the forwarded headers of a reverse proxy that terminates TLS ({@code server.forward-headers-strategy} is
 * {@code native} or {@code framework}, or a Tomcat remote-IP protocol header is set). Only configuration is read; no
 * request is inspected, so the message is the same for every start. It never contains a credential.
 */
final class JpaAdminTransportCheck implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(JpaAdminTransportCheck.class);

    private final BasePath basePath;
    private final Environment environment;

    JpaAdminTransportCheck(BasePath basePath, Environment environment) {
        this.basePath = basePath;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (environment.acceptsProfiles(Profiles.of(JpaAdminPasswordGuard.PROD_PROFILE)) && !isHttpsConfigured()) {
            log.warn("The console at {} is served over plain HTTP while the '{}' profile is active, so the sign-in "
                    + "form, the password and the session cookie travel unencrypted. Serve the application over HTTPS: "
                    + "either configure TLS in the application (server.ssl.*), or terminate TLS in a reverse proxy "
                    + "and make the application trust the proxy's forwarded headers "
                    + "(server.forward-headers-strategy=native or framework) so requests are seen as HTTPS. The "
                    + "console does not change cookie settings: set server.servlet.session.cookie.secure=true and "
                    + "server.servlet.session.cookie.same-site=lax in your application.", basePath.value(),
                    JpaAdminPasswordGuard.PROD_PROFILE);
        }
    }

    /** Whether the configuration says the console is reached over HTTPS: own TLS, or a trusted TLS-terminating proxy. */
    boolean isHttpsConfigured() {
        return hasOwnTls() || trustsForwardedHeaders();
    }

    private boolean hasOwnTls() {
        boolean enabled = environment.getProperty("server.ssl.enabled", Boolean.class, Boolean.TRUE);
        return enabled && (isSet("server.ssl.bundle") || isSet("server.ssl.key-store")
                || isSet("server.ssl.certificate"));
    }

    private boolean trustsForwardedHeaders() {
        String strategy = environment.getProperty("server.forward-headers-strategy", "").strip()
                .toLowerCase(Locale.ROOT);
        return strategy.equals("native") || strategy.equals("framework") || isSet("server.tomcat.remoteip.protocol-header");
    }

    private boolean isSet(String property) {
        String value = environment.getProperty(property);
        return value != null && !value.isBlank();
    }
}
