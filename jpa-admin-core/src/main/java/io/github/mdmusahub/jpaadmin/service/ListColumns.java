package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides, from the metadata alone, which columns an entity's list has. It never sees a row.
 *
 * <p>Only visible fields become columns (a hidden field is not in {@link AdminEntity#visibleFields()}, SEC-2).
 * To-many relations, embedded values and binary data never do (ARCH-4: collections are not rendered; the others are
 * not shown in v0.1). A sensitive field becomes a masked column that is not sortable (SEC-1, SEC-2). A relation
 * column is not sortable either: what it stores is an identifier and what it shows is a label, so sorting by it
 * would look wrong.
 *
 * <p>Package-private: it is a rule, not API.
 */
final class ListColumns {

    private ListColumns() {
    }

    /** The columns of {@code entity}, in display order: the identifier first, then the fields by name. */
    static List<ListColumn> of(AdminEntity entity) {
        List<ListColumn> columns = new ArrayList<>();
        for (AdminField field : entity.visibleFields()) {
            ListColumn column = column(field);
            if (column != null) {
                columns.add(column);
            }
        }
        return List.copyOf(columns);
    }

    private static ListColumn column(AdminField field) {
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
            case TO_ONE -> new ListColumn(field.name(), label, Cell.Type.RELATION, false, ListView.Column.Align.START);
            case OTHER -> new ListColumn(field.name(), label, Cell.Type.TEXT, false, ListView.Column.Align.START);
            case BINARY, TO_MANY, EMBEDDED -> null;
        };
    }

}
