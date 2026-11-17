package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One-time messages that must survive a redirect ("Deleted ..." after a delete): the console's own small version
 * of a flash map, kept in the HTTP session under a name that starts with {@code JPA_ADMIN_}.
 *
 * <p>It is not Spring MVC's {@code FlashMapManager}, which is the host's bean and shares one attribute with the host's own
 * redirects (D-35: everything the console keeps in the shared session has its own name). Signing out removes every
 * attribute with the console's prefix, so a message never outlives the console's login.
 *
 * <p>A message is stored as plain text, so the session stays serializable whatever the host does with sessions, and it is
 * printed with {@code th:text}, which escapes it (SEC-9). At most {@value #MAX_MESSAGES} messages of at most
 * {@value #MAX_LENGTH} characters are kept. A message is shown once: {@link #take} removes it.
 *
 * <p>Internal, not stable API. Public only so the preview and the tests share it.
 */
public final class ConsoleFlash {

    /** The session attribute. Starts with the console's prefix, so sign-out clears it. */
    public static final String ATTRIBUTE = "JPA_ADMIN_FLASH";

    private static final int MAX_MESSAGES = 5;
    private static final int MAX_LENGTH = 400;

    private ConsoleFlash() {
    }

    /** Keeps {@code message} for the next page this visitor opens. A visitor without a session cannot keep one. */
    public static void add(HttpServletRequest request, FlashMessage message) {
        HttpSession session = request.getSession(true);
        List<String> kept = new ArrayList<>(stored(session));
        String text = message.text().length() > MAX_LENGTH ? message.text().substring(0, MAX_LENGTH) : message.text();
        kept.add(message.level().name() + ":" + text);
        while (kept.size() > MAX_MESSAGES) {
            kept.remove(0);
        }
        session.setAttribute(ATTRIBUTE, new ArrayList<>(kept));
    }

    /** The messages kept for this visitor, in order, removed so they show once. Never creates a session. */
    public static List<FlashMessage> take(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return List.of();
        }
        List<String> kept = stored(session);
        if (kept.isEmpty()) {
            return List.of();
        }
        session.removeAttribute(ATTRIBUTE);
        List<FlashMessage> messages = new ArrayList<>(kept.size());
        for (String entry : kept) {
            int colon = entry.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                messages.add(new FlashMessage(FlashMessage.Level.valueOf(entry.substring(0, colon).toUpperCase(Locale.ROOT)),
                        entry.substring(colon + 1)));
            } catch (IllegalArgumentException notALevel) {
                // Something else wrote to our attribute: ignore that entry.
            }
        }
        return messages;
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
