package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import java.util.Objects;

/**
 * One column of an entity's list, before any link is built: the key (an attribute name from the metadata), the header
 * text, what kind of cell it holds, whether the header can sort the list, and the text alignment.
 *
 * <p>A column with the type {@link Cell.Type#MASKED} belongs to a sensitive field: it is shown, but its value is
 * never read from the database (SEC-1) and it can be neither sorted nor searched.
 *
 * <p>Internal, not stable API. Public because the controller turns it into a {@link ListView.Column}.
 *
 * @param key      attribute name, exactly as the metadata has it
 * @param label    header text
 * @param type     kind of cell the column holds
 * @param sortable whether the column can be sorted
 * @param align    text alignment
 */
public record ListColumn(String key, String label, Cell.Type type, boolean sortable, ListView.Column.Align align) {

    public ListColumn {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(align, "align");
        if (sortable && (type == Cell.Type.MASKED || type == Cell.Type.RELATION)) {
            throw new IllegalArgumentException("a masked or relation column cannot be sorted");
        }
    }
}
