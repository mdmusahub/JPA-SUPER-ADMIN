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
 * @param access            what the console may do with the records, from {@code @AdminResource} (SEC-11)
 * @param plural            the plural of {@code label} for headings and the dashboard: {@code @AdminResource(plural)} when it
 *                          is set and acceptable (at most {@value #MAX_PLURAL_LENGTH} characters, no control character), else
 *                          the plain English guess of {@link #guessPlural}. Never blank
 */
public record AdminEntity(Class<?> type, String entityName, String urlName, String label, String group, String icon,
                          String idAttribute, String unsupportedReason, List<AdminField> fields, Access access,
                          String plural) {

    /** The longest plural name {@code @AdminResource(plural)} may give, in characters. */
    public static final int MAX_PLURAL_LENGTH = 60;

    /** An entity whose plural is the guess of {@link #guessPlural}: the shape every entity had before Phase 13. */
    public AdminEntity(Class<?> type, String entityName, String urlName, String label, String group, String icon,
                       String idAttribute, String unsupportedReason, List<AdminField> fields, Access access) {
        this(type, entityName, urlName, label, group, icon, idAttribute, unsupportedReason, fields, access, null);
    }

    /**
     * The plural of a label by the plain English rules, until the host says it with {@code @AdminResource(plural)}.
     * {@code Invoice} becomes {@code Invoices}, {@code Box} becomes {@code Boxes}, a consonant followed by {@code y} becomes
     * {@code ies}. Irregular plurals ({@code Person}) come out wrong, which is why the host can override it.
     */
    public static String guessPlural(String label) {
        return Names.plural(label);
    }

    /** Whether {@code plural} is acceptable as a plural name: not blank, within the limit, no control character. */
    public static boolean acceptablePlural(String plural) {
        return plural != null && !plural.isBlank() && plural.strip().codePointCount(0, plural.strip().length()) <= MAX_PLURAL_LENGTH
                && plural.codePoints().noneMatch(Character::isISOControl);
    }

    /**
     * What the console may do with an entity's records. Read once from {@code @AdminResource} at startup and enforced
     * in the services, not only in the pages (SEC-11).
     *
     * <p>Internal, not stable API.
     *
     * @param readOnly  whether the console only shows the records (implies no create, no edit and no delete)
     * @param creatable whether records can be created; ignored when {@code readOnly}
     * @param updatable whether records can be changed; ignored when {@code readOnly}
     * @param deletable whether records can be deleted; ignored when {@code readOnly}
     * @param exportable whether the records can be exported as CSV (D-106). Not affected by {@code readOnly}: an export only
     *                  reads
     */
    public record Access(boolean readOnly, boolean creatable, boolean updatable, boolean deletable, boolean exportable) {

        /** No annotation: nothing is forbidden. */
        public static final Access FULL = new Access(false, true, true, true, true);

        /** Read-only and deletable only: the pair of options Phase 7 had. Creating and changing stay allowed. */
        public Access(boolean readOnly, boolean deletable) {
            this(readOnly, true, true, deletable, true);
        }

        /** The four options that existed before Phase 11; the entity can be exported. */
        public Access(boolean readOnly, boolean creatable, boolean updatable, boolean deletable) {
            this(readOnly, creatable, updatable, deletable, true);
        }

        /** Whether records can be created. */
        public boolean canCreate() {
            return !readOnly && creatable;
        }

        /** Whether records can be changed. */
        public boolean canUpdate() {
            return !readOnly && updatable;
        }

        /** Whether records can be deleted. */
        public boolean canDelete() {
            return !readOnly && deletable;
        }

        /** Whether the records can be exported: {@code exportable}, whatever {@code readOnly} says (an export only reads). */
        public boolean canExport() {
            return exportable;
        }
    }

    /** The group an entity belongs to while nothing says otherwise. */
    public static final String DEFAULT_GROUP = "Entities";

    /** The icon an entity uses while nothing says otherwise: a generic table. */
    public static final String DEFAULT_ICON = "table";

    private static final Pattern URL_NAME = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    /** An entity with full access: no {@code @AdminResource} restriction. */
    public AdminEntity(Class<?> type, String entityName, String urlName, String label, String group, String icon,
                       String idAttribute, String unsupportedReason, List<AdminField> fields) {
        this(type, entityName, urlName, label, group, icon, idAttribute, unsupportedReason, fields, Access.FULL);
    }

    public AdminEntity {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(entityName, "entityName");
        Objects.requireNonNull(urlName, "urlName");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(icon, "icon");
        // A plural the host gave that is not acceptable is replaced by the guess here too, so a wrong value can never reach a
        // page even when the startup check did not run; the check (AnnotationProblems) is what tells the host.
        plural = acceptablePlural(plural) ? plural.strip() : guessPlural(label);
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

    /**
     * Whether a record of this entity can have an address of its own: it has an identifier and the identifier is not a
     * sensitive field. A record is addressed by its identifier, so an address (and a checkbox value, and a delete form)
     * would otherwise print a sensitive value (SEC-1). Such an entity is listed, with the identifier masked, but its
     * records have no detail page, no link, no checkbox and no delete.
     */
    public boolean addressable() {
        return supported() && idField().map(field -> !field.sensitive()).orElse(false);
    }

    /** Whether the console may delete this entity's records: addressable, not read-only, deletable (SEC-11). */
    public boolean canDelete() {
        return addressable() && access.canDelete();
    }

    /** Whether the console may create records of this entity: supported, not read-only, creatable (SEC-11). */
    public boolean canCreate() {
        return supported() && access.canCreate();
    }

    /**
     * Whether the console may change this entity's records: addressable (a record is found by its identifier), not
     * read-only, updatable (SEC-11).
     */
    public boolean canUpdate() {
        return addressable() && access.canUpdate();
    }

    /**
     * Whether the console may export this entity's records as CSV: supported and not switched off with
     * {@code @AdminResource(exportable = false)} (SEC-11, D-106). It does not look at {@code jpa.admin.export-enabled}, which
     * is the service's business, and a read-only entity can be exported.
     */
    public boolean canExport() {
        return supported() && access.canExport();
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

    /**
     * The attribute that says when a record was created, for the dashboard chart: a visible, non-sensitive date or time
     * attribute named {@code createdAt}, {@code createdDate}, {@code createdOn}, {@code creationDate},
     * {@code creationTime}, {@code createTime} or {@code createdDateTime} (case, {@code _} and {@code -} ignored; the first
     * name in that order wins). Empty when there is none, and then the entity is not in the chart (D-101).
     */
    public Optional<AdminField> creationField() {
        return CreationFields.find(this);
    }

    /** The identifier field, or empty for an unsupported entity. */
    public Optional<AdminField> idField() {
        return fields.stream().filter(AdminField::id).findFirst();
    }
}
