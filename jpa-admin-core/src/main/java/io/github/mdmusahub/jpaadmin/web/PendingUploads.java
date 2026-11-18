package io.github.mdmusahub.jpaadmin.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The hand-off of the no-script upload path (option A, D-95). A plain form posts a file to the upload endpoint, which redirects
 * back to the form page; the page has to show the new address as the field's value, and the redirect carries nothing. So the
 * endpoint leaves the result here, in the visitor's own session, and the form page takes it out again, once.
 *
 * <p>An entry holds what the page needs and nothing else: the entity and field it is for, the record it was sent from (empty for a
 * create form), the stored address, the display name and the size. It is kept as plain text so the session stays serializable
 * whatever the host does with sessions; at most {@value #MAX_ENTRIES} entries are kept; the attribute name starts with the
 * console's prefix, so signing out removes it. The page checks the address with the storage again before it shows it, so an entry
 * is never trusted.
 *
 * <p>Internal, not stable API. Public only so the tests share it.
 */
public final class PendingUploads {

    /** The session attribute. Starts with the console's prefix, so sign-out clears it (D-39). */
    public static final String ATTRIBUTE = "JPA_ADMIN_UPLOADS";

    private static final int MAX_ENTRIES = 10;
    private static final char SEPARATOR = '\u001f';

    private PendingUploads() {
    }

    /**
     * One uploaded file waiting for its form page.
     *
     * @param entity the entity address the form belongs to
     * @param field  the upload field
     * @param record the id of the record being edited, or empty for a create form
     * @param url    the stored address
     * @param name   the display name
     * @param size   the size in bytes
     */
    public record Entry(String entity, String field, String record, String url, String name, long size) {
    }

    /** Keeps {@code entry} for the next form page this visitor opens. The newest entry for a field replaces an older one. */
    public static void add(HttpServletRequest request, Entry entry) {
        HttpSession session = request.getSession(true);
        List<String> kept = new ArrayList<>(stored(session));
        kept.removeIf(text -> sameField(text, entry.entity(), entry.field(), entry.record()));
        kept.add(String.join(String.valueOf(SEPARATOR), entry.entity(), entry.field(), entry.record(), Long.toString(entry.size()),
                entry.url(), entry.name()));
        while (kept.size() > MAX_ENTRIES) {
            kept.remove(0);
        }
        session.setAttribute(ATTRIBUTE, new ArrayList<>(kept));
    }

    /**
     * Removes and returns the entry for this field of this form. Never creates a session, and leaves the entries of other fields
     * and other records alone.
     */
    public static Optional<Entry> take(HttpServletRequest request, String entity, String field, String record) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        List<String> kept = new ArrayList<>(stored(session));
        Optional<Entry> found = Optional.empty();
        for (String text : kept) {
            if (sameField(text, entity, field, record)) {
                found = parse(text);
            }
        }
        if (kept.removeIf(text -> sameField(text, entity, field, record))) {
            if (kept.isEmpty()) {
                session.removeAttribute(ATTRIBUTE);
            } else {
                session.setAttribute(ATTRIBUTE, new ArrayList<>(kept));
            }
        }
        return found;
    }

    private static boolean sameField(String text, String entity, String field, String record) {
        String[] parts = text.split(String.valueOf(SEPARATOR), -1);
        return parts.length == 6 && parts[0].equals(entity) && parts[1].equals(field) && parts[2].equals(record);
    }

    private static Optional<Entry> parse(String text) {
        String[] parts = text.split(String.valueOf(SEPARATOR), -1);
        if (parts.length != 6) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Entry(parts[0], parts[1], parts[2], parts[4], parts[5], Long.parseLong(parts[3])));
        } catch (NumberFormatException broken) {
            return Optional.empty();
        }
    }

    private static List<String> stored(HttpSession session) {
        Object value = session.getAttribute(ATTRIBUTE);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> entries = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (entry instanceof String text) {
                entries.add(text);
            }
        }
        return entries;
    }
}
