package io.github.mdmusahub.jpaadmin.metadata;

import java.util.List;
import java.util.Objects;

/**
 * The configuration the scanner needs, handed over by the auto-configuration so this module does not have to know
 * the property names.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module builds it.
 *
 * @param excludedEntities entity names that are hidden completely. An entry matches an entity by simple name or by
 *                         fully qualified name, ignoring case. Blank entries are refused at startup
 * @param sensitiveFields  name fragments that make a field sensitive. The match ignores case, {@code _} and
 *                         {@code -}, and is a substring match, so {@code password} also covers {@code userPassword}
 *                         and {@code api_key} matches {@code apikey} (D-11, SEC-2)
 */
public record DiscoverySettings(List<String> excludedEntities, List<String> sensitiveFields) {

    /** The field names masked while {@code jpa.admin.sensitive-fields} is not set. */
    public static final List<String> DEFAULT_SENSITIVE_FIELDS = List.of("password", "secret", "token", "apikey");

    public DiscoverySettings {
        excludedEntities = List.copyOf(Objects.requireNonNull(excludedEntities, "excludedEntities"));
        sensitiveFields = List.copyOf(Objects.requireNonNull(sensitiveFields, "sensitiveFields"));
        if (excludedEntities.stream().anyMatch(entry -> entry == null || entry.isBlank())) {
            throw new IllegalArgumentException("an excluded entity name must not be blank");
        }
        if (sensitiveFields.stream().anyMatch(entry -> entry == null || entry.isBlank())) {
            throw new IllegalArgumentException("a sensitive field name must not be blank");
        }
    }

    /** Nothing excluded, the default sensitive names. */
    public static DiscoverySettings defaults() {
        return new DiscoverySettings(List.of(), DEFAULT_SENSITIVE_FIELDS);
    }
}
