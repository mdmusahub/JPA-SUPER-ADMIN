package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.service.DeleteService;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage.Level;
import java.util.Locale;

/**
 * The words of the messages after a delete. A message says what happened and what to do next (UI-6). It is plain text and
 * the page prints it with {@code th:text}, so a stored label in it is escaped (SEC-9). It never holds SQL, a table name,
 * an exception text or an id the visitor typed (SEC-12): the only stored value is the deleted record's label, which the
 * visitor chose to delete and which comes from the database, not from the request.
 *
 * <p>The preview app uses these same texts for its fixtures.
 *
 * <p>Internal, not stable API.
 */
public final class DeleteMessages {

    private DeleteMessages() {
    }

    /** One record was deleted. {@code recordLabel} may be {@code null}. */
    public static FlashMessage deleted(String entityLabel, String recordLabel) {
        return new FlashMessage(Level.SUCCESS, recordLabel == null ? "Deleted the " + lower(entityLabel) + "."
                : "Deleted " + lower(entityLabel) + " “" + recordLabel + "”.");
    }

    /** The database refused the delete; the record is still there. */
    public static FlashMessage inUse(String entityLabel) {
        return new FlashMessage(Level.ERROR, "The " + lower(entityLabel) + " was not deleted. Other records still depend "
                + "on it, or the database refused the change. Remove or change those records first, then try again.");
    }

    /** A bulk delete finished. */
    public static FlashMessage bulkDeleted(String entityPlural, int deleted, int notDeleted) {
        String plural = lower(entityPlural);
        if (deleted == 0) {
            return new FlashMessage(Level.WARNING, "No " + plural + " were deleted. " + skipped(notDeleted)
                    + " Reload the list and select the records again.");
        }
        String done = "Deleted " + deleted + (deleted == 1 ? " record." : " records.");
        return notDeleted == 0 ? new FlashMessage(Level.SUCCESS, done)
                : new FlashMessage(Level.WARNING, done + " " + skipped(notDeleted));
    }

    /** A bulk delete was rolled back; nothing was deleted. */
    public static FlashMessage bulkInUse(int selected) {
        return new FlashMessage(Level.ERROR, "Nothing was deleted. At least one of the " + selected + " selected records "
                + "is still used by other records, or the database refused the change. Remove or change those records "
                + "first, or select fewer records, then try again.");
    }

    /** Nothing was selected. */
    public static FlashMessage nothingSelected() {
        return new FlashMessage(Level.WARNING, "No records were selected. Tick the records to delete, then choose "
                + "Delete selected.");
    }

    /** More than the cap were sent. */
    public static FlashMessage tooMany() {
        return new FlashMessage(Level.ERROR, "You can delete at most " + DeleteService.MAX_BULK_DELETE
                + " records at a time, and nothing was deleted. Select fewer records and try again.");
    }

    /** The duplicate route was reached for a record that exists; the create form arrives with Phase 8. */
    public static FlashMessage duplicateComesLater() {
        return new FlashMessage(Level.INFO, "Duplicating a record needs the create form, which this version of the "
                + "console does not have yet. Nothing was copied.");
    }

    private static String skipped(int notDeleted) {
        return notDeleted == 1 ? "1 selected record was skipped because it no longer exists."
                : notDeleted + " selected records were skipped because they no longer exist or were not valid.";
    }

    private static String lower(String label) {
        return label.toLowerCase(Locale.ROOT);
    }
}
