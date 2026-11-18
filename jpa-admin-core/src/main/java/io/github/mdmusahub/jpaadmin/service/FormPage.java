package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.FormView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * What a create or edit form is made of, as display values: the fields of {@link FormView} with their inputs, values and
 * choices. It holds no entity and no sensitive value (SEC-1): a masked field has no value, and a value typed into a masked
 * input is never copied back by {@link #reshown}.
 *
 * <p>Internal, not stable API.
 *
 * @param recordId the identifier of the record being edited as text, or {@code null} for a create form
 * @param version  the record's {@code @Version} as text when the entity has one, else {@code null}; it travels in the
 *                 form's address and is compared at save time to refuse a stale form
 * @param label    the record's label on one line, or {@code null}
 * @param fields   the fields in display order
 */
public record FormPage(String recordId, String version, String label, List<FormView.Field> fields) {

    public FormPage {
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
    }

    /**
     * The same form after a save that was refused: the values the visitor typed are put back (a masked field gets none, and
     * a field without choices gets none) and each error is attached to its field.
     *
     * @param posted the request's values by field name; {@code null} for a name that was not sent
     * @param errors the error sentence by field key; a key that is not a field is ignored here
     */
    public FormPage reshown(Function<String, String> posted, Map<String, String> errors) {
        List<FormView.Field> shown = new ArrayList<>(fields.size());
        for (FormView.Field field : fields) {
            String value = field.value();
            if (field.inputType() == FormView.Field.InputType.UPLOAD) {
                // An address that was refused is not put back (it would only be refused again): the field shows what the
                // page read for it. An address that was accepted stays when another field of the form was wrong, and a
                // file the visitor removed stays removed.
                if (errors.get(field.key()) == null) {
                    String typed = posted.apply(field.key());
                    if ("true".equals(posted.apply(field.key() + RecordWriteService.REMOVE_SUFFIX))) {
                        value = null;
                    } else if (typed != null && !typed.isBlank()) {
                        value = typed.strip();
                    }
                }
            } else if (field.inputType() != FormView.Field.InputType.MASKED) {
                String typed = posted.apply(field.key());
                if (typed != null) {
                    value = typed;
                }
            }
            if (field.inputType() == FormView.Field.InputType.SELECT && field.options().isEmpty()) {
                value = null;
            }
            shown.add(new FormView.Field(field.key(), field.label(), field.inputType(), value, field.required(),
                    field.help(), field.options(), errors.get(field.key()), field.uploadTicket()));
        }
        return new FormPage(recordId, version, label, shown);
    }
}
