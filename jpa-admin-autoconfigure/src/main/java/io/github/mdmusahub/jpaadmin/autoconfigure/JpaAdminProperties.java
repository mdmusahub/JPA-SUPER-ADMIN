package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.web.BasePath;
import java.nio.charset.StandardCharsets;
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

    /** BCrypt hashes at most this many bytes of a password (counted in UTF-8). */
    static final int BCRYPT_MAX_BYTES = 72;

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

    /**
     * Entities the console never shows. An entry matches an entity by simple class name or by fully qualified
     * class name, ignoring case. A hidden entity is not in the sidebar, not on the dashboard and not reachable by
     * URL. Empty by default.
     */
    private List<String> excludedEntities = new ArrayList<>();

    /**
     * Field names whose values are always masked. The match ignores case, '_' and '-', and is a substring match,
     * so "password" also covers "userPassword" and "api_key" matches "apikey". @AdminSensitive adds to this list.
     */
    private List<String> sensitiveFields = new ArrayList<>(DiscoverySettings.DEFAULT_SENSITIVE_FIELDS);

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

    public List<String> getExcludedEntities() {
        return excludedEntities;
    }

    public void setExcludedEntities(List<String> excludedEntities) {
        this.excludedEntities = excludedEntities;
    }

    public List<String> getSensitiveFields() {
        return sensitiveFields;
    }

    public void setSensitiveFields(List<String> sensitiveFields) {
        this.sensitiveFields = sensitiveFields;
    }

    /**
     * What the scanner needs, without the property names. Called after {@link #validate()}, so both lists hold
     * usable values.
     */
    DiscoverySettings discoverySettings() {
        return new DiscoverySettings(excludedEntities == null ? List.of() : excludedEntities,
                sensitiveFields == null ? List.of() : sensitiveFields);
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
        checkNames("excluded-entities", excludedEntities, "Each entry is an entity's simple or fully qualified class "
                + "name, for example jpa.admin.excluded-entities[0]=AuditEntry.", problems);
        checkNames("sensitive-fields", sensitiveFields, "Each entry is a field name or part of one, for example "
                + "jpa.admin.sensitive-fields[0]=password. Leave the property out to keep the defaults ("
                + String.join(", ", DiscoverySettings.DEFAULT_SENSITIVE_FIELDS) + ").", problems);
        if (!problems.isEmpty()) {
            throw new JpaAdminConfigurationException(problems);
        }
    }

    /**
     * A list property holds no blank entry. The message names the property and the position, and never repeats the
     * entry itself, so this report stays as quiet about values as the rest of it (SEC-6).
     */
    private static void checkNames(String name, List<String> values, String hint, List<String> problems) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value == null || value.isBlank()) {
                problems.add(PREFIX + "." + name + " has a blank entry at position " + (i + 1)
                        + ". Remove it or give it a value. " + hint);
                return;
            }
        }
    }

    private static void checkCredential(String name, String value, String hint, List<String> problems) {
        String property = PREFIX + "." + name;
        if (value == null || value.isBlank()) {
            problems.add(property + " is required. " + hint);
        } else if (UNRESOLVED_PLACEHOLDER.matcher(value.strip()).matches()) {
            problems.add(property + " refers to a variable that has no value (it is still a ${...} placeholder). "
                    + "Set that environment variable or property, or write the value directly.");
        } else if (name.equals("password") && value.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            // BCrypt cannot hash more than 72 bytes and would fail later with a stack trace. Say it here, by name.
            problems.add(property + " is longer than " + BCRYPT_MAX_BYTES + " bytes, the most that the console's "
                    + "password hashing (BCrypt) can use. Use a shorter password, for example 24 to 64 random "
                    + "characters.");
        }
    }

    /** Never includes the password (SEC-6). */
    @Override
    public String toString() {
        return "JpaAdminProperties[enabled=" + enabled + ", path=" + path + ", username=" + username
                + ", password=" + (password == null ? "<not set>" : "<hidden>") + ", title=" + title
                + ", primaryColor=" + primaryColor + ", excludedEntities=" + excludedEntities
                + ", sensitiveFields=" + sensitiveFields + "]";
    }
}
