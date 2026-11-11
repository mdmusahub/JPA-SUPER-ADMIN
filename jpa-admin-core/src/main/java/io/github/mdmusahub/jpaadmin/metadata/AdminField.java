package io.github.mdmusahub.jpaadmin.metadata;

import java.util.List;
import java.util.Objects;

/**
 * One persistent attribute of an entity, as the console sees it. Built once at startup from the JPA metamodel and
 * never changed afterwards (ARCH-1).
 *
 * <p>Internal, not stable API. Public because the auto-configuration module and the console's pages read it.
 *
 * @param name          attribute name, exactly as JPA knows it (this is the name used in queries)
 * @param label         humanized name for the screen, for example {@code "Created at"} for {@code createdAt}
 * @param javaType      Java type of the attribute
 * @param kind          what the console has to render and offer for this field
 * @param enumValues    the constants of an {@link Kind#ENUM} field, in declaration order; empty for every other kind
 * @param targetEntity  URL name of the entity a {@link Kind#TO_ONE} or {@link Kind#TO_MANY} field points at, or
 *                      {@code null} when there is none or the target is not in the console (hidden or unsupported)
 * @param id            whether this is the identifier
 * @param version       whether this is the {@code @Version} attribute
 * @param nullable      whether the database allows no value
 * @param required      whether a value must be given when a record is created or changed
 * @param sensitive     whether the value is masked everywhere (SEC-1)
 * @param hidden        whether the field is removed from the console completely (SEC-2)
 */
public record AdminField(String name, String label, Class<?> javaType, Kind kind, List<String> enumValues,
                         String targetEntity, boolean id, boolean version, boolean nullable, boolean required,
                         boolean sensitive, boolean hidden) {

    public AdminField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(javaType, "javaType");
        Objects.requireNonNull(kind, "kind");
        enumValues = List.copyOf(Objects.requireNonNull(enumValues, "enumValues"));
        if (kind != Kind.ENUM && !enumValues.isEmpty()) {
            throw new IllegalArgumentException("only an ENUM field has enum values");
        }
        if (kind != Kind.TO_ONE && kind != Kind.TO_MANY && targetEntity != null) {
            throw new IllegalArgumentException("only a relation field has a target entity");
        }
        if (id && hidden) {
            // The identifier addresses the record; hiding it would make every link meaningless.
            throw new IllegalArgumentException("the identifier cannot be hidden");
        }
    }

    /**
     * What the console has to render and offer for a field. It is deliberately coarser than the Java type: the
     * console asks "what does this look like on screen and in a form", not "which class is it".
     *
     * <p>Internal, not stable API.
     */
    public enum Kind {
        /** Text of any length. */
        TEXT,
        /** Any number, whole or decimal. */
        NUMBER,
        /** A yes or no value. */
        BOOLEAN,
        /** A fixed set of constants; {@link AdminField#enumValues()} holds them. */
        ENUM,
        /** A date without a time. */
        DATE,
        /** A date with a time, or an instant. */
        DATETIME,
        /** A to-one relation ({@code @ManyToOne}, {@code @OneToOne}). */
        TO_ONE,
        /** A to-many relation or an element collection. Never rendered as data (ARCH-4). */
        TO_MANY,
        /** An {@code @Embedded} value. Not editable in v0.1. */
        EMBEDDED,
        /** Bytes, for example {@code byte[]} or a large object. Never printed. */
        BINARY,
        /** Anything the console has no better name for. Shown as text, never edited. */
        OTHER
    }
}
