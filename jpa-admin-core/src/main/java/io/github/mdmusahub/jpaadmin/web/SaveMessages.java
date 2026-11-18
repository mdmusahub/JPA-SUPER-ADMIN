package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage.Level;
import java.util.Locale;

/**
 * The words of the messages after a create or an edit, and on a duplicate form. A message says what happened and what to do
 * next (UI-6). It is plain text and the page prints it with {@code th:text}, so a stored label in it is escaped (SEC-9). It
 * never holds SQL, a table name, an exception text or a value the visitor typed other than the record's own label (SEC-12).
 * <b>No message ever holds a sensitive value</b> (SEC-1): the label is never a sensitive attribute (see the label rule of the
 * list), and nothing else of the record is named.
 *
 * <p>The preview app uses these same texts for its fixtures.
 *
 * <p>Internal, not stable API.
 */
public final class SaveMessages {

    private SaveMessages() {
    }

    /** One record was created. {@code recordLabel} may be {@code null}. */
    public static FlashMessage created(String entityLabel, String recordLabel) {
        return new FlashMessage(Level.SUCCESS, recordLabel == null ? "Created the " + lower(entityLabel) + "."
                : "Created " + lower(entityLabel) + " “" + recordLabel + "”.");
    }

    /** One record was changed. {@code recordLabel} may be {@code null}. */
    public static FlashMessage updated(String entityLabel, String recordLabel) {
        return new FlashMessage(Level.SUCCESS, recordLabel == null ? "Saved the changes to the " + lower(entityLabel) + "."
                : "Saved the changes to " + lower(entityLabel) + " “" + recordLabel + "”.");
    }

    /** The create form was filled from another record (Duplicate). */
    public static FlashMessage copied(String entityLabel, String sourceId) {
        return new FlashMessage(Level.INFO, "This form starts from a copy of " + lower(entityLabel) + " #" + sourceId
                + ". The identifier, secret values and anything the console does not copy are left empty. Nothing is "
                + "saved until you choose Create record.");
    }

    private static String lower(String label) {
        return label.toLowerCase(Locale.ROOT);
    }
}
