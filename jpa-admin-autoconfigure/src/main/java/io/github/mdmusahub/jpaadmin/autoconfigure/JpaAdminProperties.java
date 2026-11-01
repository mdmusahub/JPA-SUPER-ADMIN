package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.web.BasePath;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the console, bound from {@code jpa.admin.*}.
 *
 * <p>Only {@code username} and {@code password} are required. After binding, {@link #afterPropertiesSet()} checks
 * every value and stops startup with one message that names each wrong property and says how to fix it. The checks
 * are plain code, not Bean Validation, so they cannot be skipped when no validation provider is on the classpath
 * (SEC-7, SEC-14, SEC-15), and no message ever repeats the password (SEC-6).
 *
 * <p>A username or password that is still a {@code ${...}} placeholder after binding (for example an environment
 * variable that is not set) counts as missing. Spring Boot leaves such text in place instead of failing.
 */
@ConfigurationProperties(prefix = JpaAdminProperties.PREFIX)
public class JpaAdminProperties implements InitializingBean {

    static final String PREFIX = "jpa.admin";

    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\$\\{[^{}]*}");

    /** Master switch. When false, the console registers no beans and no routes. */
    private boolean enabled = true;

    /**
     * URL path of the console. Starts with '/', is not just '/', has no trailing slash, and holds letters, digits,
     * '-' and '_' between slashes.
     */
    // A literal, not BasePath.DEFAULT: the configuration processor can only read literal defaults for the IDE
    // metadata (DOC-2). A test checks that it equals BasePath.DEFAULT.
    private String path = "/admin";

    /** Admin user name. Required. */
    private String username;

    /** Admin password. Required. Never logged, never printed. */
    private String password;

    /** Title shown in the header and on the login page. */
    private String title = "JPA Admin";

    /** Accent colour of the console, written as #rrggbb. */
    private String primaryColor = "#0d9f6e";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    /**
     * Checks every value. Collects all problems and throws once, so the operator can fix them in one pass.
     *
     * @throws JpaAdminConfigurationException if any value is missing or malformed
     */
    void validate() {
        List<String> problems = new ArrayList<>();
        checkCredential("username", username, "Set it in application.yml, for example jpa.admin.username=admin.",
                problems);
        checkCredential("password", password, "Set it in application.yml from a secret or an environment variable, "
                + "for example jpa.admin.password=${ADMIN_PASSWORD}.", problems);
        if (!BasePath.isValid(path)) {
            problems.add(PREFIX + ".path must start with '/', must not be just '/' or end with '/', and may hold only "
                    + "letters, digits, '-' and '_' between slashes (at most " + BasePath.MAX_LENGTH
                    + " characters), for example jpa.admin.path=" + BasePath.DEFAULT + ".");
        }
        if (primaryColor == null || !COLOR.matcher(primaryColor).matches()) {
            problems.add(PREFIX + ".primary-color must be a colour written as #rrggbb (six hex digits), "
                    + "for example jpa.admin.primary-color=#0d9f6e.");
        }
        if (!problems.isEmpty()) {
            throw new JpaAdminConfigurationException(problems);
        }
    }

    private static void checkCredential(String name, String value, String hint, List<String> problems) {
        String property = PREFIX + "." + name;
        if (value == null || value.isBlank()) {
            problems.add(property + " is required. " + hint);
        } else if (UNRESOLVED_PLACEHOLDER.matcher(value.strip()).matches()) {
            problems.add(property + " refers to a variable that has no value (it is still a ${...} placeholder). "
                    + "Set that environment variable or property, or write the value directly.");
        }
    }

    /** Never includes the password (SEC-6). */
    @Override
    public String toString() {
        return "JpaAdminProperties[enabled=" + enabled + ", path=" + path + ", username=" + username
                + ", password=" + (password == null ? "<not set>" : "<hidden>") + ", title=" + title
                + ", primaryColor=" + primaryColor + "]";
    }
}
