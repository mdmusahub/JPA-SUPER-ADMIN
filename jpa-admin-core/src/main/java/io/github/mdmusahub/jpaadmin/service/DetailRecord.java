package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.Cell;
import java.util.List;
import java.util.Objects;

/**
 * One record as the detail page shows it: display values only, never an entity, a proxy or a collection (ARCH-3, ARCH-4).
 * A sensitive field is a masked cell with no value (SEC-1); a hidden field is not here at all.
 *
 * <p>Internal, not stable API. Public because the controller turns it into a {@code DetailView}.
 *
 * @param id    the record's identifier as text
 * @param label the record's own label (the value of its label attribute, on one line), or {@code null} when the entity has
 *              no label attribute or the record has no value there
 * @param items one item per visible field, in display order (the identifier first)
 */
public record DetailRecord(String id, String label, List<Item> items) {

    public DetailRecord {
        Objects.requireNonNull(id, "id");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    /**
     * One labelled value.
     *
     * @param label        the field's label
     * @param cell         the value, already formatted
     * @param targetEntity for a relation to an entity that is in the console: that entity's URL name, else {@code null}
     * @param targetId     for a relation that is set: the related record's identifier as text, else {@code null}. Both are
     *                     used only to build the link to the related record, which the web layer does
     */
    public record Item(String label, Cell cell, String targetEntity, String targetId) {

        public Item {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(cell, "cell");
        }

        /** A value that is not a link. */
        public static Item of(String label, Cell cell) {
            return new Item(label, cell, null, null);
        }
    }
}
