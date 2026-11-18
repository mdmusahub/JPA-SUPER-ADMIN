package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.Cell;
import java.util.List;
import java.util.Optional;

/**
 * What the services need to know about uploaded files, in four questions, so that they depend on no storage class (Phase 9b):
 * <ul>
 *   <li>{@link #settings}: is this field a usable upload field, and what does it allow?</li>
 *   <li>{@link #ticket}: a fresh upload ticket for it, for the form being rendered;</li>
 *   <li>{@link #accepts}: may this posted address be stored in this field? Only an address that the field's own storage
 *       made, in the field's own folder, of a type the field accepts, for a file that still exists;</li>
 *   <li>{@link #cell}: how a stored address is shown in a list or on a detail page.</li>
 * </ul>
 * {@link #none()} knows no upload field: an upload field is then left out of the forms and shown as plain text, which is how
 * a console without upload support (or a test that does not need it) behaves.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates the real implementation.
 */
public interface UploadFiles {

    /**
     * What an upload field allows.
     *
     * @param maxBytes the largest file (the lowest of the field's and the storage's limit)
     * @param accept   the accepted MIME types
     * @param help     one plain sentence for the form: what the field takes and how large
     */
    record Settings(long maxBytes, List<String> accept, String help) {

        public Settings {
            accept = List.copyOf(accept);
        }
    }

    /** The field's settings, or empty when the field is not an upload field the console can use. */
    Optional<Settings> settings(String entityUrlName, String field);

    /** A new upload ticket for the field, valid from now (never reused from an earlier page). */
    String ticket(String entityUrlName, String field, long maxBytes);

    /** Whether {@code url}, posted for this field, is a stored file this field may hold. */
    boolean accepts(String entityUrlName, String field, String url);

    /**
     * The cell for a stored address: an image or a file link when the address is one the console made and the file is there,
     * a plain note when it is one but the file is gone, and empty when the text is not a stored address at all (the caller then
     * shows it as ordinary escaped text, never as a link or an image).
     *
     * @param url   the stored text
     * @param label the field's label, used as the alternative text of an image
     */
    Optional<Cell> cell(String url, String label);

    /** No upload support. */
    static UploadFiles none() {
        return NoUploadFiles.INSTANCE;
    }
}
