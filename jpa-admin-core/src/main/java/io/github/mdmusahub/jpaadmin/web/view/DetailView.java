package io.github.mdmusahub.jpaadmin.web.view;

import java.util.List;
import java.util.Objects;

/**
 * Data for the detail page of one record.
 *
 * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
 *
 * @param title       page title, for example the record's label
 * @param entityLabel singular display name of the entity
 * @param id          record id as text
 * @param fields      labelled values, sensitive ones as masked cells
 * @param images      images to show in a gallery strip
 * @param actions     what the page offers
 */
public record DetailView(String title, String entityLabel, String id, List<Field> fields, List<Image> images,
                         Actions actions) {

    /** Template (view) name. */
    public static final String TEMPLATE = "jpa-admin/detail";

    public DetailView {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(entityLabel, "entityLabel");
        Objects.requireNonNull(id, "id");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        images = List.copyOf(Objects.requireNonNull(images, "images"));
        Objects.requireNonNull(actions, "actions");
    }

    /**
     * A labelled value.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param label field label
     * @param cell  formatted value
     */
    public record Field(String label, Cell cell) {
        public Field {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(cell, "cell");
        }
    }

    /**
     * An image shown on the page.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param url   image link, a local path under the base path
     * @param label alternative text
     */
    public record Image(String url, String label) {
        public Image {
            Hrefs.require("url", url);
            Objects.requireNonNull(label, "label");
        }
    }

    /**
     * What the page offers.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param canEdit      whether "Edit" is offered
     * @param canDuplicate whether "Duplicate" is offered
     * @param canDelete    whether "Delete" is offered
     */
    public record Actions(boolean canEdit, boolean canDuplicate, boolean canDelete) {
    }
}
