package io.github.mdmusahub.jpaadmin.web.view;

import java.util.List;
import java.util.Objects;

/**
 * Data for the create and edit form.
 *
 * <p>A field for a sensitive property has input type {@link Field.InputType#MASKED} and no value; a blank submit on
 * edit keeps the stored value (SEC-1). The constructor rejects a masked field that has a value.
 *
 * <p>The backend builds both links; the template only prints them. Each is a local path under the base path.
 *
 * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
 *
 * @param mode        whether the form creates or edits a record
 * @param title       page title, for example "New article" or the record's label
 * @param entityLabel singular display name of the entity
 * @param recordId    id of the record being edited as text; {@code null} exactly when {@code mode} is
 *                    {@link Mode#CREATE}
 * @param actionHref  where the form posts to
 * @param cancelHref  where "Cancel" goes
 * @param fields      form fields in display order
 * @param formErrors  errors that belong to the whole form, not to one field
 */
public record FormView(Mode mode, String title, String entityLabel, String recordId, String actionHref,
                       String cancelHref, List<Field> fields, List<String> formErrors) {

    /** Template (view) name. */
    public static final String TEMPLATE = "jpa-admin/form";

    public FormView {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(entityLabel, "entityLabel");
        Hrefs.require("actionHref", actionHref);
        Hrefs.require("cancelHref", cancelHref);
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        formErrors = List.copyOf(Objects.requireNonNull(formErrors, "formErrors"));
        if ((mode == Mode.EDIT) != (recordId != null)) {
            throw new IllegalArgumentException("recordId is required exactly when the form edits a record");
        }
    }

    /**
     * Whether the form creates or edits a record.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     */
    public enum Mode { CREATE, EDIT }

    /**
     * One form field.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param key          field key, a name from the entity metadata
     * @param label        field label
     * @param inputType    kind of input
     * @param value        current value as text, or {@code null}; always {@code null} for {@link InputType#MASKED}
     * @param required     whether the field is required
     * @param help         help text, or {@code null}
     * @param options      choices for {@link InputType#SELECT}, empty for the others
     * @param error        validation message for this field, or {@code null}
     * @param uploadTicket signed upload ticket; present exactly for {@link InputType#UPLOAD} fields
     */
    public record Field(String key, String label, InputType inputType, String value, boolean required, String help,
                        List<Option> options, String error, String uploadTicket) {
        public Field {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(inputType, "inputType");
            options = List.copyOf(Objects.requireNonNull(options, "options"));
            if (inputType == InputType.MASKED && value != null) {
                throw new IllegalArgumentException("A masked field carries no value");
            }
            if ((inputType == InputType.UPLOAD) != (uploadTicket != null)) {
                throw new IllegalArgumentException("uploadTicket is required for upload fields and only for them");
            }
        }

        /**
         * Kind of form input.
         *
         * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
         */
        public enum InputType { TEXT, TEXTAREA, NUMBER, BOOLEAN, DATE, DATETIME, SELECT, MASKED, UPLOAD }
    }

    /**
     * A choice in a select input or a filter.
     *
     * <p>Draft, internal, not stable API (ARCH-9 exception). Holds no sensitive values (SEC-1).
     *
     * @param value value sent when the choice is picked
     * @param label text shown to the user
     */
    public record Option(String value, String label) {
        public Option {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(label, "label");
        }
    }
}
