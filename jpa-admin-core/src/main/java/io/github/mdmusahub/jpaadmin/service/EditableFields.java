package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.web.view.FormView.Field.InputType;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Lob;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * <b>The one place that decides which fields a form can write</b> (SEC-1, SEC-2, SEC-11, mass assignment). The form shows
 * exactly these fields and the save reads exactly these names from the request; a posted name that is not here is never
 * looked at, so it cannot be stored.
 *
 * <p>A field is editable when it is visible (a hidden field does not exist for the console) and:
 * <ul>
 *   <li>it is not the version ({@code @Version} belongs to the persistence layer) and not a collection, an embedded
 *       value, binary data or a type the forms have no input for;</li>
 *   <li>it is not the identifier, with one exception: an identifier that the persistence layer does not generate (no
 *       {@code @GeneratedValue}) is an input of the <em>create</em> form only, because without it no record can be
 *       created. The identifier of an existing record never changes;</li>
 *   <li>a to-one relation needs a target that is in the console, has one identifier attribute, and a way to be written;</li>
 *   <li>it can be written: the entity has a setter for it or a non-final field.</li>
 * </ul>
 * A sensitive field is editable (it is masked, never shown back, SEC-1).
 *
 * <p>Package-private: it is a rule, not API.
 */
final class EditableFields {

    private static final Set<Class<?>> WHOLE = Set.of(int.class, Integer.class, long.class, Long.class, short.class,
            Short.class, byte.class, Byte.class, BigInteger.class);
    private static final Set<Class<?>> DECIMAL = Set.of(double.class, Double.class, float.class, Float.class,
            BigDecimal.class);
    private static final Set<Class<?>> DATES = Set.of(LocalDate.class, java.sql.Date.class);
    private static final Set<Class<?>> DATE_TIMES = Set.of(LocalDateTime.class, Instant.class, OffsetDateTime.class,
            ZonedDateTime.class, java.util.Date.class, java.sql.Timestamp.class);
    private static final Set<Class<?>> TEXTS = Set.of(String.class, UUID.class, char.class, Character.class);

    /** The longest text an input without a column limit is a single line for; longer ones are a text area. */
    private static final int SINGLE_LINE = 255;

    private EditableFields() {
    }

    /**
     * One editable field with what the form and the save need to know about it.
     *
     * @param field      the attribute
     * @param input      the input to show
     * @param maxLength  the longest text the column holds, or {@code 0} for no known limit
     * @param createOnly whether the field is an input of the create form only (an identifier that is not generated)
     * @param relation   the target of a to-one relation, else {@code null}
     */
    record Editable(AdminField field, InputType input, int maxLength, boolean createOnly, Relation relation) {

        String key() {
            return field.name();
        }
    }

    /** The editable fields of {@code entity}, in display order (the identifier of a create form first, then by name). */
    static List<Editable> of(AdminEntity entity, AdminEntityRegistry registry, EntityManager entityManager) {
        List<Editable> editable = new ArrayList<>();
        for (AdminField field : entity.visibleFields()) {
            Editable candidate = candidate(entity, registry, entityManager, field);
            if (candidate != null) {
                editable.add(candidate);
            }
        }
        return List.copyOf(editable);
    }

    private static Editable candidate(AdminEntity entity, AdminEntityRegistry registry, EntityManager entityManager,
                                      AdminField field) {
        // An upload field is written only through the upload endpoint and a form that carries a ticket (Phase 9b). Until
        // that exists the generic form must not offer a plain text input for it, or any text could be stored as a file
        // address.
        if (field.version() || field.isUpload() || !Members.writable(entity.type(), field.name())) {
            return null;
        }
        boolean createOnly = false;
        if (field.id()) {
            if (field.sensitive() || generated(entity, field) || !(field.kind() == AdminField.Kind.TEXT
                    || field.kind() == AdminField.Kind.NUMBER)) {
                return null;
            }
            createOnly = true;
        }
        Class<?> type = field.javaType();
        return switch (field.kind()) {
            case TEXT -> TEXTS.contains(type) ? text(entity, field, createOnly) : null;
            case NUMBER -> WHOLE.contains(type) || DECIMAL.contains(type)
                    ? new Editable(field, masked(field, InputType.NUMBER), 0, createOnly, null) : null;
            case BOOLEAN -> new Editable(field, masked(field, InputType.BOOLEAN), 0, createOnly, null);
            case DATE -> DATES.contains(type) ? new Editable(field, masked(field, InputType.DATE), 0, createOnly, null) : null;
            case DATETIME -> DATE_TIMES.contains(type)
                    ? new Editable(field, masked(field, InputType.DATETIME), 0, createOnly, null) : null;
            case ENUM -> new Editable(field, masked(field, InputType.SELECT), 0, createOnly, null);
            case TO_ONE -> relation(registry, entityManager, field);
            case TO_MANY, EMBEDDED, BINARY, OTHER -> null;
        };
    }

    private static Editable relation(AdminEntityRegistry registry, EntityManager entityManager, AdminField field) {
        if (field.sensitive() || field.targetEntity() == null) {
            return null;
        }
        Relation relation = Relation.of(entityManager, registry, field);
        return relation == null ? null : new Editable(field, InputType.SELECT, 0, false, relation);
    }

    private static Editable text(AdminEntity entity, AdminField field, boolean createOnly) {
        Class<?> type = field.javaType();
        if (type == UUID.class) {
            return new Editable(field, masked(field, InputType.TEXT), 36, createOnly, null);
        }
        if (type == char.class || type == Character.class) {
            return new Editable(field, masked(field, InputType.TEXT), 1, createOnly, null);
        }
        int length = columnLength(entity, field);
        boolean area = length == 0 || length > SINGLE_LINE;
        return new Editable(field, masked(field, area ? InputType.TEXTAREA : InputType.TEXT), length, createOnly, null);
    }

    /** A sensitive field is always a masked input, whatever its type (SEC-1). */
    private static InputType masked(AdminField field, InputType input) {
        return field.sensitive() ? InputType.MASKED : input;
    }

    /**
     * The length of a text column from the entity's mapping: {@code @Column(length)} (255 when the annotation does not
     * say), or {@code 0} for no known limit ({@code @Lob}, or a column the host defined with its own SQL).
     */
    private static int columnLength(AdminEntity entity, AdminField field) {
        if (Members.annotation(entity.type(), field.name(), Lob.class) != null) {
            return 0;
        }
        Column column = Members.annotation(entity.type(), field.name(), Column.class);
        if (column == null) {
            return SINGLE_LINE;
        }
        return column.columnDefinition().isEmpty() ? column.length() : 0;
    }

    private static boolean generated(AdminEntity entity, AdminField field) {
        return Members.annotation(entity.type(), field.name(), GeneratedValue.class) != null;
    }
}
