package io.github.mdmusahub.jpaadmin.metadata;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The rule that says which attribute of an entity holds the moment a record was created, for the dashboard chart
 * (Phase 10, D-101). Package-private: a rule, not API; the pages read {@link AdminEntity#creationField()}.
 *
 * <p>A field qualifies when all of these hold: its name is one of {@link #NAMES} (compared case-insensitively, ignoring
 * {@code _} and {@code -}), its type is one of {@link #TYPES}, and it is visible and not sensitive (SEC-1, SEC-2). When
 * several fields qualify, the one whose name comes first in {@link #NAMES} wins, and for two fields that differ only in
 * spelling (<code>createdAt</code> and <code>created_at</code>) the first one declared. No entity name is known here
 * (ARCH-12): only attribute names that are common in every kind of application.
 */
final class CreationFields {

    /** The accepted names, in the order of preference. */
    static final List<String> NAMES = List.of("createdAt", "createdDate", "createdOn", "creationDate", "creationTime",
            "createTime", "createdDateTime");

    /** The accepted types: the date and time types of {@code java.time} that name a calendar day. */
    static final Set<Class<?>> TYPES = Set.of(LocalDate.class, LocalDateTime.class, Instant.class,
            OffsetDateTime.class, ZonedDateTime.class);

    private CreationFields() {
    }

    /** The field the chart counts by, or empty when the entity has none. */
    static Optional<AdminField> find(AdminEntity entity) {
        if (!entity.supported()) {
            return Optional.empty();
        }
        for (String name : NAMES) {
            String wanted = normalize(name);
            for (AdminField field : entity.fields()) {
                if (qualifies(field) && normalize(field.name()).equals(wanted)) {
                    return Optional.of(field);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean qualifies(AdminField field) {
        return !field.hidden() && !field.sensitive() && TYPES.contains(field.javaType());
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
