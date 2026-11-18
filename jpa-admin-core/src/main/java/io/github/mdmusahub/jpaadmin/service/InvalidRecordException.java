package io.github.mdmusahub.jpaadmin.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A create or an update that was refused because of what was typed (or because the form is stale). It is a runtime exception,
 * so the {@code @Transactional} service method that throws it rolls back (ARCH-5), and nothing was saved.
 *
 * <p>It carries plain sentences only, never a value, an exception text, SQL or a table name (SEC-1, SEC-12): one sentence per
 * field (by the field's key) and sentences for the whole form. The cause is deliberately not kept, so a log line or a
 * handler can never print a provider's message by accident.
 *
 * <p>Internal, not stable API.
 */
public class InvalidRecordException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> fieldErrors;
    private final transient List<String> formErrors;

    /**
     * @param fieldErrors one sentence per field key
     * @param formErrors  sentences about the whole form
     */
    public InvalidRecordException(Map<String, String> fieldErrors, List<String> formErrors) {
        super("The record was not saved.", null, false, false);
        this.fieldErrors = java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(fieldErrors, "fieldErrors")));
        this.formErrors = List.copyOf(Objects.requireNonNull(formErrors, "formErrors"));
    }

    /** The sentence for each field that was wrong, by the field's key. */
    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }

    /** The sentences that belong to the whole form. */
    public List<String> formErrors() {
        return formErrors;
    }
}
