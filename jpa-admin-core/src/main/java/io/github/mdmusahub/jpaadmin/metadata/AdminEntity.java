package io.github.mdmusahub.jpaadmin.metadata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One entity, as the console sees it. Built once at startup from the JPA metamodel and never changed afterwards
 * (ARCH-1), so it can be shared between requests without copying.
 *
 * <p>An entity the console cannot manage (a composite identifier, for example) is <em>unsupported</em>: it carries
 * the reason, it is never in {@link AdminEntityRegistry#all()} and it cannot be reached by URL. The reason is
 * logged once at startup and is never shown to a visitor.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module and the console's pages read it.
 *
 * @param type              the entity class
 * @param entityName        the name JPA knows the entity by (used in queries)
 * @param urlName           the name in the console's addresses: the simple class name in lower case, for example
 *                          {@code widgetpart} for {@code WidgetPart}. Lower-case letters, digits and hyphens only,
 *                          so it is always safe in a path
 * @param label             humanized name for the screen
 * @param group             sidebar group, {@value #DEFAULT_GROUP} unless something says otherwise
 * @param icon              icon name, {@value #DEFAULT_ICON} unless something says otherwise
 * @param idAttribute       name of the identifier attribute, or {@code null} for an unsupported entity that has none
 * @param unsupportedReason why the console cannot manage this entity, or {@code null} when it can
 * @param fields            every attribute the scanner found, hidden ones included; {@link #visibleFields()} is what
 *                          the console may show
 */
public record AdminEntity(Class<?> type, String entityName, String urlName, String label, String group, String icon,
                          String idAttribute, String unsupportedReason, List<AdminField> fields) {

    /** The group an entity belongs to while nothing says otherwise. */
    public static final String DEFAULT_GROUP = "Entities";

    /** The icon an entity uses while nothing says otherwise: a generic table. */
    public static final String DEFAULT_ICON = "table";

    private static final Pattern URL_NAME = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public AdminEntity {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(entityName, "entityName");
        Objects.requireNonNull(urlName, "urlName");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(icon, "icon");
        if (!URL_NAME.matcher(urlName).matches()) {
            throw new IllegalArgumentException("urlName must hold only a-z, 0-9 and single hyphens between them");
        }
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        if (unsupportedReason == null && idAttribute == null) {
            throw new IllegalArgumentException("a supported entity must have an identifier attribute");
        }
    }

    /** Whether the console can manage this entity. */
    public boolean supported() {
        return unsupportedReason == null;
    }

    /** The entity class name, for logs and for the {@code excluded-entities} property. */
    public String className() {
        return type.getName();
    }

    /** The fields the console may show: everything that is not hidden (SEC-2). */
    public List<AdminField> visibleFields() {
        return fields.stream().filter(field -> !field.hidden()).toList();
    }

    /**
     * The visible field with this name. A hidden field is never found, so a request that names one is refused in
     * exactly the same way as a request that names a field that does not exist (SEC-2).
     *
     * @param name attribute name from a request; untrusted
     */
    public Optional<AdminField> visibleField(String name) {
        return visibleFields().stream().filter(field -> field.name().equals(name)).findFirst();
    }

    /** The identifier field, or empty for an unsupported entity. */
    public Optional<AdminField> idField() {
        return fields.stream().filter(AdminField::id).findFirst();
    }
}
