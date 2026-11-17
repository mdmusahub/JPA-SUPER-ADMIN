package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides, from the metadata alone, which columns an entity's list has. It never sees a row.
 *
 * <p>Only visible fields become columns (a hidden field is not in {@link AdminEntity#visibleFields()}, SEC-2).
 * To-many relations, embedded values and binary data never do (ARCH-4: collections are not rendered; the others are
 * not shown in v0.1). A sensitive field becomes a masked column that is not sortable (SEC-1, SEC-2).
 *
 * <p>A to-one relation column is sortable (D-60) exactly when its target is in the console and has a label (see
 * {@link #labelAttributeOf}): the list is then ordered by that label. What the column stores is an identifier and what
 * it shows is a label, so ordering by the identifier would look wrong. A relation shown as {@code #id} (the target has
 * no usable text attribute, or is hidden from the console) is not sortable. The label is never a sensitive attribute,
 * so a sensitive attribute of a target can never become a sort key.
 *
 * <p>Package-private: it is a rule, not API.
 */
final class ListColumns {

    /** Attribute names that name a record on screen, in order of preference (D-54), compared without case. */
    private static final List<String> PREFERRED_LABELS = List.of("name", "title", "label", "fullname", "displayname",
            "username", "email", "code");

    private ListColumns() {
    }

    /**
     * The columns of {@code entity}, in display order: the identifier first, then the fields by name.
     *
     * @param registry the entities the console manages; a relation column is sortable only if its target is in it and
     *                 has a label
     */
    static List<ListColumn> of(AdminEntity entity, AdminEntityRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        List<ListColumn> columns = new ArrayList<>();
        for (AdminField field : entity.visibleFields()) {
            ListColumn column = column(field, registry);
            if (column != null) {
                columns.add(column);
            }
        }
        return List.copyOf(columns);
    }

    /** The attribute that names a record of {@code target} on screen, or empty when it has no usable text attribute. */
    static Optional<String> labelAttributeOf(AdminEntity target) {
        List<AdminField> texts = target.visibleFields().stream()
                .filter(field -> field.kind() == AdminField.Kind.TEXT && field.javaType() == String.class
                        && !field.sensitive() && !field.id())
                .toList();
        for (String preferred : PREFERRED_LABELS) {
            for (AdminField field : texts) {
                if (field.name().equalsIgnoreCase(preferred)) {
                    return Optional.of(field.name());
                }
            }
        }
        return texts.stream().map(AdminField::name).findFirst();
    }

    static boolean targetHasLabel(AdminField field, AdminEntityRegistry registry) {
        return field.targetEntity() != null
                && registry.find(field.targetEntity()).flatMap(ListColumns::labelAttributeOf).isPresent();
    }

    private static ListColumn column(AdminField field, AdminEntityRegistry registry) {
        String label = field.id() ? "ID" : field.label();
        if (field.kind() == AdminField.Kind.TO_MANY || field.kind() == AdminField.Kind.EMBEDDED) {
            return null;
        }
        if (field.sensitive()) {
            return new ListColumn(field.name(), label, Cell.Type.MASKED, false, ListView.Column.Align.START);
        }
        return switch (field.kind()) {
            case TEXT -> new ListColumn(field.name(), label, Cell.Type.TEXT, true, ListView.Column.Align.START);
            case NUMBER -> new ListColumn(field.name(), label, Cell.Type.NUMBER, true, ListView.Column.Align.END);
            case BOOLEAN -> new ListColumn(field.name(), label, Cell.Type.BOOLEAN, true, ListView.Column.Align.START);
            case ENUM -> new ListColumn(field.name(), label, Cell.Type.ENUM, true, ListView.Column.Align.START);
            case DATE, DATETIME -> new ListColumn(field.name(), label, Cell.Type.DATE, true,
                    ListView.Column.Align.START);
            case TO_ONE -> new ListColumn(field.name(), label, Cell.Type.RELATION, targetHasLabel(field, registry),
                    ListView.Column.Align.START);
            case OTHER -> new ListColumn(field.name(), label, Cell.Type.TEXT, false, ListView.Column.Align.START);
            case BINARY, TO_MANY, EMBEDDED -> null;
        };
    }

}
