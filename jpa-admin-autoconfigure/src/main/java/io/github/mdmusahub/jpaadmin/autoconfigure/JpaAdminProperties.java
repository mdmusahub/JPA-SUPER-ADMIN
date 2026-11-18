package io.github.mdmusahub.jpaadmin.autoconfigure;

import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.storage.StoragePaths;
import io.github.mdmusahub.jpaadmin.storage.StorageSettings;
import io.github.mdmusahub.jpaadmin.storage.UploadLimits;
import io.github.mdmusahub.jpaadmin.web.BasePath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

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

    /**
     * File storages, by name: {@code jpa.admin.storage.<name>.type}, {@code .directory}, {@code .max-size} and
     * {@code .public-path}. The storage named {@code default} always exists, with the defaults of {@link Storage}, so an
     * {@code @AdminUpload} field works without any property. Other names exist only when configured.
     */
    private Map<String, Storage> storage = new LinkedHashMap<>();

    /** Settings of the upload tickets. */
    private final Upload upload = new Upload();

    /** One file storage. */
    public static class Storage {

        /** Kind of storage. Only {@code local} (a folder on this server's disk) exists in v0.1; {@code s3} is planned for v0.2. */
        private String type = StorageSettings.LOCAL;

        /**
         * Folder of a local storage. Relative paths are relative to the working directory. Must be outside the web root
         * (not under the static resources and not the servlet context's folder) and writable.
         */
        private String directory = UploadLimits.DEFAULT_DIRECTORY;

        /** Largest file this storage accepts. An {@code @AdminUpload} field may lower it, never raise it. At most 100MB. */
        private DataSize maxSize = DataSize.ofBytes(UploadLimits.DEFAULT_MAX_BYTES);

        /**
         * A path (like {@code /files}) at which this storage's files are also served to everyone, without a session.
         * Off by default: files are then served only to a signed-in admin, under the console path.
         */
        private String publicPath;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public DataSize getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(DataSize maxSize) {
            this.maxSize = maxSize;
        }

        public String getPublicPath() {
            return publicPath;
        }

        public void setPublicPath(String publicPath) {
            this.publicPath = publicPath;
        }
    }

    /** Settings of the upload tickets. */
    public static class Upload {

        /**
         * Secret that signs the upload tickets, at least 32 characters. Unset: a random key is made at every start, so a
         * restart (or another instance) invalidates the tickets of forms that are open. Set it when you run several
         * instances. Never logged.
         */
        private String signingKey;

        public String getSigningKey() {
            return signingKey;
        }

        public void setSigningKey(String signingKey) {
            this.signingKey = signingKey;
        }
    }

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

    public Map<String, Storage> getStorage() {
        return storage;
    }

    public void setStorage(Map<String, Storage> storage) {
        this.storage = storage;
    }

    public Upload getUpload() {
        return upload;
    }

    /**
     * Every storage as the upload code sees it, the {@code default} one included (with its defaults when no property
     * names it). Called after {@link #validate()}, so every value is usable.
     */
    Map<String, StorageSettings> storageSettings() {
        Map<String, StorageSettings> settings = new LinkedHashMap<>();
        settings.put(UploadLimits.DEFAULT_STORAGE, StorageSettings.defaults());
        for (Map.Entry<String, Storage> entry : (storage == null ? Map.<String, Storage>of() : storage).entrySet()) {
            Storage configured = entry.getValue();
            if (configured == null) {
                continue;
            }
            settings.put(entry.getKey(), new StorageSettings(entry.getKey(), configured.getType().strip().toLowerCase(Locale.ROOT),
                    configured.getDirectory(), configured.getMaxSize().toBytes(),
                    configured.getPublicPath() == null || configured.getPublicPath().isBlank() ? null
                            : configured.getPublicPath().strip()));
        }
        return java.util.Collections.unmodifiableMap(settings);
    }

    /** The configured signing key, or {@code null} when none is set (the auto-configuration then makes a random one). */
    String signingKey() {
        String key = upload.getSigningKey();
        return key == null || key.isBlank() ? null : key;
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
        validateUploads(problems);
        if (!problems.isEmpty()) {
            throw new JpaAdminConfigurationException(problems);
        }
    }

    /** The shortest signing key accepted: 32 characters, so a guessable word cannot sign tickets. */
    static final int MIN_SIGNING_KEY_LENGTH = 32;

    private static final Pattern TYPE_WORD = Pattern.compile("[a-z][a-z0-9-]{0,15}");

    /**
     * Checks the storage and ticket properties. Every message names the property and says what to do; none repeats a value
     * (SEC-6). Whether a storage's {@code type} is one this version supports is decided by the startup check, which can name the
     * fields that use it (UPL-8); here it only has to be a plain word.
     */
    private void validateUploads(List<String> problems) {
        List<String> publicPaths = new ArrayList<>();
        if (storage != null) {
            for (Map.Entry<String, Storage> entry : storage.entrySet()) {
                validateStorage(entry.getKey(), entry.getValue(), publicPaths, problems);
            }
        }
        String key = upload.getSigningKey();
        if (key != null && !key.isBlank()) {
            if (UNRESOLVED_PLACEHOLDER.matcher(key.strip()).matches()) {
                problems.add(PREFIX + ".upload.signing-key refers to a variable that has no value (it is still a ${...} "
                        + "placeholder). Set that environment variable or property, or leave the property out to use a "
                        + "random key at every start.");
            } else if (key.length() < MIN_SIGNING_KEY_LENGTH) {
                problems.add(PREFIX + ".upload.signing-key is shorter than " + MIN_SIGNING_KEY_LENGTH + " characters. Use "
                        + "a long random value, or leave the property out to use a random key at every start.");
            }
        }
    }

    private void validateStorage(String name, Storage settings, List<String> publicPaths, List<String> problems) {
        if (!StoragePaths.validStorageName(name)) {
            problems.add(PREFIX + ".storage has a storage name that is not valid. A name is a lower-case word of letters, "
                    + "digits and '-' (starting with a letter, at most 32 characters), for example "
                    + "jpa.admin.storage.default.directory=./uploads.");
            return;
        }
        String base = PREFIX + ".storage." + name;
        if (settings == null) {
            problems.add(base + " has no settings. Give it at least a type, for example " + base + ".type=local.");
            return;
        }
        if (settings.getType() == null || !TYPE_WORD.matcher(settings.getType().strip().toLowerCase(Locale.ROOT)).matches()) {
            problems.add(base + ".type must be a plain word. The only type in this version is 'local'.");
        }
        String directory = settings.getDirectory();
        if (directory == null || directory.isBlank()) {
            problems.add(base + ".directory is blank. Give the folder for the files, for example " + base
                    + ".directory=./uploads, or leave the property out for that default.");
        } else if (directory.indexOf('\0') >= 0 || directory.length() > 1000 || !isPath(directory)) {
            problems.add(base + ".directory is not a usable folder path. Use a plain path such as ./uploads or "
                    + "/var/lib/app/uploads.");
        }
        DataSize size = settings.getMaxSize();
        if (size == null || size.toBytes() <= 0 || size.toBytes() > UploadLimits.CEILING_BYTES) {
            problems.add(base + ".max-size must be more than 0 and at most " + UploadLimits.describe(UploadLimits.CEILING_BYTES)
                    + " (the ceiling of this version), for example " + base + ".max-size=5MB.");
        }
        String publicPath = settings.getPublicPath();
        if (publicPath != null && !publicPath.isBlank()) {
            String path = publicPath.strip();
            if (!BasePath.isValid(path)) {
                problems.add(base + ".public-path must start with '/', must not be just '/' or end with '/', and may hold "
                        + "only letters, digits, '-' and '_' between slashes, for example " + base + ".public-path=/files. "
                        + "Leave it out to keep the files private.");
            } else if (overlaps(path, path(this.path))) {
                problems.add(base + ".public-path must not be the console path or inside it, and the console path must not "
                        + "be inside it: files served there would not be served the way the console serves them.");
            } else if (publicPaths.stream().anyMatch(other -> overlaps(path, other))) {
                problems.add(base + ".public-path overlaps the public path of another storage. Give each storage its own "
                        + "path.");
            } else {
                publicPaths.add(path);
            }
        }
    }

    private static String path(String configured) {
        return configured == null ? BasePath.DEFAULT : configured;
    }

    private static boolean isPath(String directory) {
        try {
            java.nio.file.Path.of(directory.strip());
            return true;
        } catch (java.nio.file.InvalidPathException invalid) {
            return false;
        }
    }

    /** Whether two paths are the same or one is inside the other ({@code /a} and {@code /a/b} overlap, {@code /a} and {@code /ab} do not). */
    static boolean overlaps(String first, String second) {
        return first.equals(second) || first.startsWith(second + "/") || second.startsWith(first + "/");
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
