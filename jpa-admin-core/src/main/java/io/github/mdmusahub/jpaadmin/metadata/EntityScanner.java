package io.github.mdmusahub.jpaadmin.metadata;

import io.github.mdmusahub.jpaadmin.annotation.AdminHidden;
import io.github.mdmusahub.jpaadmin.annotation.AdminSensitive;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the JPA metamodel once at startup and builds the console's metadata (ARCH-1). It never looks at a database
 * row and never keeps an {@code EntityManager}; it asks the metamodel what exists and reads the console's own
 * annotations from the entity classes.
 *
 * <p>No entity name is hard-coded anywhere (ARCH-12): everything comes from the metamodel and the host's own
 * configuration.
 *
 * <p>What it refuses to manage is written down, not thrown: an entity with a composite identifier, or one whose
 * class name holds nothing that can be an address, becomes an <em>unsupported</em> entity with a reason. The reason
 * is logged once, at WARN, and the entity is unreachable. The host application always starts.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module calls it.
 */
public final class EntityScanner {

    private static final Logger log = LoggerFactory.getLogger(EntityScanner.class);

    private static final Set<Class<?>> DATE_TYPES = Set.of(LocalDate.class, java.sql.Date.class);
    private static final Set<Class<?>> DATETIME_TYPES = Set.of(LocalDateTime.class, Instant.class,
            OffsetDateTime.class, ZonedDateTime.class, java.util.Date.class, java.sql.Timestamp.class,
            java.util.Calendar.class);

    private EntityScanner() {
    }

    /**
     * Builds the registry.
     *
     * @param metamodel the metamodel of the host's one entity manager factory
     * @param settings  which entities are excluded and which field names are sensitive
     */
    public static AdminEntityRegistry scan(Metamodel metamodel, DiscoverySettings settings) {
        Objects.requireNonNull(metamodel, "metamodel");
        Objects.requireNonNull(settings, "settings");

        List<Candidate> candidates = new ArrayList<>();
        for (EntityType<?> entityType : metamodel.getEntities()) {
            Class<?> type = javaTypeOf(entityType);
            if (type == null || isHidden(type, settings)) {
                continue;
            }
            candidates.add(new Candidate(entityType, type));
        }
        resolveAddresses(candidates);

        Map<Class<?>, String> addressesByType = new HashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate.unsupportedReason == null) {
                addressesByType.put(candidate.type, candidate.urlName);
            }
        }

        List<AdminEntity> supported = new ArrayList<>();
        List<AdminEntity> unsupported = new ArrayList<>();
        for (Candidate candidate : candidates) {
            AdminEntity entity = candidate.build(settings, addressesByType);
            (entity.supported() ? supported : unsupported).add(entity);
        }
        supported.sort(Comparator.comparing(AdminEntity::group).thenComparing(AdminEntity::label)
                .thenComparing(AdminEntity::className));
        unsupported.sort(Comparator.comparing(AdminEntity::className));

        report(supported, unsupported);
        return new AdminEntityRegistry(supported, unsupported);
    }

    private static Class<?> javaTypeOf(EntityType<?> entityType) {
        try {
            return entityType.getJavaType();
        } catch (RuntimeException ignored) {
            // A dynamic (map-based) model has no class. There is nothing the console can do with it.
            log.warn("The JPA Admin console skipped the entity '{}' because it has no Java class.",
                    entityType.getName());
            return null;
        }
    }

    private static boolean isHidden(Class<?> type, DiscoverySettings settings) {
        if (type.isAnnotationPresent(AdminHidden.class)) {
            return true;
        }
        for (String excluded : settings.excludedEntities()) {
            String entry = excluded.strip();
            if (entry.equalsIgnoreCase(type.getSimpleName()) || entry.equalsIgnoreCase(type.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives every entity its address. Two classes with the same simple name in different packages both move to
     * their fully qualified form, so neither keeps the short address and neither overwrites the other. Anything
     * that still collides, or that has no usable address at all, becomes unsupported.
     */
    private static void resolveAddresses(List<Candidate> candidates) {
        Map<String, List<Candidate>> bySimpleName = candidates.stream()
                .collect(Collectors.groupingBy(candidate -> Names.urlName(candidate.type.getSimpleName()),
                        LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<Candidate>> group : bySimpleName.entrySet()) {
            boolean collides = group.getValue().size() > 1;
            for (Candidate candidate : group.getValue()) {
                candidate.urlName = collides ? Names.qualifiedUrlName(candidate.type.getName()) : group.getKey();
            }
        }
        Map<String, List<Candidate>> byAddress = candidates.stream()
                .collect(Collectors.groupingBy(candidate -> candidate.urlName, LinkedHashMap::new,
                        Collectors.toList()));
        for (Map.Entry<String, List<Candidate>> group : byAddress.entrySet()) {
            if (group.getKey().isEmpty()) {
                group.getValue().forEach(candidate -> candidate.unsupportedReason =
                        "its class name holds no letter or digit, so the console has no address for it");
            } else if (group.getValue().size() > 1) {
                group.getValue().forEach(candidate -> candidate.unsupportedReason =
                        "another entity class maps to the same console address '" + group.getKey() + "'");
            }
        }
    }

    private static void report(List<AdminEntity> supported, List<AdminEntity> unsupported) {
        if (log.isInfoEnabled()) {
            log.info("The JPA Admin console found {} entities in {} groups.", supported.size(),
                    supported.stream().map(AdminEntity::group).distinct().count());
        }
        if (!unsupported.isEmpty()) {
            // Once, at startup, on the server only. A visitor is never told that these entities exist.
            log.warn("The JPA Admin console cannot manage {} of the entities it found, so they are not in the "
                            + "console: {}. See the Limitations page of the documentation.", unsupported.size(),
                    unsupported.stream().map(entity -> entity.className() + " (" + entity.unsupportedReason() + ")")
                            .collect(Collectors.joining(", ")));
        }
    }

    /** One entity while it is being built: the address is decided before the fields can name their targets. */
    private static final class Candidate {

        private final EntityType<?> entityType;
        private final Class<?> type;
        private String urlName = "";
        private String unsupportedReason;

        private Candidate(EntityType<?> entityType, Class<?> type) {
            this.entityType = entityType;
            this.type = type;
        }

        private AdminEntity build(DiscoverySettings settings, Map<Class<?>, String> addressesByType) {
            List<Attribute<?, ?>> attributes = new ArrayList<>(entityType.getAttributes());
            // getAttributes() is a Set, so the console sorts: the identifier first, then by name. The order is the
            // same on every start and on every machine (TEST-6).
            attributes.sort(Comparator.comparing((Attribute<?, ?> attribute) -> isId(attribute) ? 0 : 1)
                    .thenComparing(Attribute::getName));

            String reason = unsupportedReason != null ? unsupportedReason : identifierProblem(attributes);
            String idAttribute = reason != null ? null : attributes.stream().filter(Candidate::isId)
                    .map(Attribute::getName).findFirst().orElse(null);
            if (reason == null && idAttribute == null) {
                reason = "the console found no identifier attribute for it";
            }

            List<AdminField> fields = new ArrayList<>();
            for (Attribute<?, ?> attribute : attributes) {
                fields.add(field(attribute, settings, addressesByType));
            }
            String address = urlName.isEmpty() ? Names.qualifiedUrlName(type.getName()) : urlName;
            if (reason != null && address.isEmpty()) {
                // Only for the unsupported list and the log; it is never an address, because it is never registered.
                address = "entity";
            }
            return new AdminEntity(type, entityType.getName(), address, Names.label(type.getSimpleName()),
                    AdminEntity.DEFAULT_GROUP, AdminEntity.DEFAULT_ICON, idAttribute, reason, fields);
        }

        /** Why the identifier stops the console from managing this entity, or {@code null} when it does not. */
        private String identifierProblem(List<Attribute<?, ?>> attributes) {
            if (!entityType.hasSingleIdAttribute()) {
                return "it has a composite primary key (@IdClass), which v0.1 does not support";
            }
            for (Attribute<?, ?> attribute : attributes) {
                if (isId(attribute) && attribute instanceof SingularAttribute<?, ?> singular
                        && singular.getType().getPersistenceType() == Type.PersistenceType.EMBEDDABLE) {
                    return "it has a composite primary key (@EmbeddedId), which v0.1 does not support";
                }
            }
            return null;
        }

        private AdminField field(Attribute<?, ?> attribute, DiscoverySettings settings,
                                 Map<Class<?>, String> addressesByType) {
            String name = attribute.getName();
            boolean id = isId(attribute);
            boolean version = attribute instanceof SingularAttribute<?, ?> singular && singular.isVersion();
            AdminField.Kind kind = kindOf(attribute);
            List<String> enumValues = kind == AdminField.Kind.ENUM ? constantsOf(attribute.getJavaType()) : List.of();
            String target = switch (kind) {
                case TO_ONE -> addressesByType.get(attribute.getJavaType());
                case TO_MANY -> addressesByType.get(elementTypeOf(attribute));
                default -> null;
            };
            boolean nullable = !id && (!(attribute instanceof SingularAttribute<?, ?> singular) || singular.isOptional());
            boolean hidden = !id && hasAnnotation(attribute, AdminHidden.class);
            if (id && hasAnnotation(attribute, AdminHidden.class)) {
                log.warn("The JPA Admin console ignored @AdminHidden on the identifier '{}' of {}: records are "
                        + "addressed by their identifier, so it cannot be hidden.", name, type.getName());
            }
            boolean sensitive = hasAnnotation(attribute, AdminSensitive.class)
                    || Names.looksSensitive(name, settings.sensitiveFields());
            boolean required = !nullable && !id && !version && kind != AdminField.Kind.TO_MANY;
            return new AdminField(name, Names.label(name), attribute.getJavaType(), kind, enumValues, target, id,
                    version, nullable, required, sensitive, hidden);
        }

        private static boolean isId(Attribute<?, ?> attribute) {
            return attribute instanceof SingularAttribute<?, ?> singular && singular.isId();
        }

        private static Class<?> elementTypeOf(Attribute<?, ?> attribute) {
            return attribute instanceof PluralAttribute<?, ?, ?> plural ? plural.getElementType().getJavaType() : null;
        }

        private static AdminField.Kind kindOf(Attribute<?, ?> attribute) {
            return switch (attribute.getPersistentAttributeType()) {
                case MANY_TO_ONE, ONE_TO_ONE -> AdminField.Kind.TO_ONE;
                case ONE_TO_MANY, MANY_TO_MANY, ELEMENT_COLLECTION -> AdminField.Kind.TO_MANY;
                case EMBEDDED -> AdminField.Kind.EMBEDDED;
                case BASIC -> basicKind(attribute.getJavaType());
            };
        }

        private static AdminField.Kind basicKind(Class<?> javaType) {
            if (javaType == boolean.class || javaType == Boolean.class) {
                return AdminField.Kind.BOOLEAN;
            }
            if (javaType.isEnum()) {
                return AdminField.Kind.ENUM;
            }
            if (Number.class.isAssignableFrom(javaType) || javaType == int.class || javaType == long.class
                    || javaType == short.class || javaType == byte.class || javaType == double.class
                    || javaType == float.class || javaType == BigDecimal.class || javaType == BigInteger.class) {
                return AdminField.Kind.NUMBER;
            }
            if (DATE_TYPES.contains(javaType)) {
                return AdminField.Kind.DATE;
            }
            if (DATETIME_TYPES.contains(javaType)) {
                return AdminField.Kind.DATETIME;
            }
            if (javaType == byte[].class || javaType == Byte[].class || javaType == java.sql.Blob.class) {
                return AdminField.Kind.BINARY;
            }
            if (CharSequence.class.isAssignableFrom(javaType) || javaType == char.class || javaType == Character.class
                    || javaType == java.util.UUID.class) {
                return AdminField.Kind.TEXT;
            }
            return AdminField.Kind.OTHER;
        }

        private static List<String> constantsOf(Class<?> javaType) {
            Object[] constants = javaType.getEnumConstants();
            if (constants == null) {
                return List.of();
            }
            List<String> values = new ArrayList<>(constants.length);
            for (Object constant : constants) {
                values.add(((Enum<?>) constant).name());
            }
            return values;
        }

        /**
         * Whether the console's annotation is on the attribute: on the member JPA uses (field or getter), and also
         * on the other one, so it works with field access and with property access.
         */
        private boolean hasAnnotation(Attribute<?, ?> attribute, Class<? extends Annotation> annotation) {
            Member member = attribute.getJavaMember();
            if (member instanceof AnnotatedElement element && element.isAnnotationPresent(annotation)) {
                return true;
            }
            return isAnnotated(declaredField(attribute.getName()), annotation)
                    || isAnnotated(getter(attribute.getName()), annotation);
        }

        private static boolean isAnnotated(AnnotatedElement element, Class<? extends Annotation> annotation) {
            return element != null && element.isAnnotationPresent(annotation);
        }

        private Field declaredField(String name) {
            for (Class<?> current = type; current != null && current != Object.class;
                    current = current.getSuperclass()) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    // Try the superclass: a mapped superclass holds some of the attributes.
                }
            }
            return null;
        }

        private Method getter(String name) {
            String capitalized = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
            for (Class<?> current = type; current != null && current != Object.class;
                    current = current.getSuperclass()) {
                for (String prefix : new String[] {"get", "is"}) {
                    try {
                        return current.getDeclaredMethod(prefix + capitalized);
                    } catch (NoSuchMethodException ignored) {
                        // Not this one.
                    }
                }
            }
            return null;
        }
    }
}
