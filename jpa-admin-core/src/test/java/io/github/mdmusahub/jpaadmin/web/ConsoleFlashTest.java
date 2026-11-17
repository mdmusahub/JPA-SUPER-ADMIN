package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage.Level;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

/**
 * The messages that survive a redirect: kept in the session under the console's own name, shown once, plain text, never
 * created for a visitor without a session.
 */
class ConsoleFlashTest {

    private static MockHttpServletRequest withSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        return request;
    }

    @Test
    void aMessageIsKeptForTheNextPageAndShownOnce() {
        MockHttpServletRequest request = withSession();
        ConsoleFlash.add(request, new FlashMessage(Level.SUCCESS, "Deleted."));

        assertThat(ConsoleFlash.take(request)).containsExactly(new FlashMessage(Level.SUCCESS, "Deleted."));
        assertThat(ConsoleFlash.take(request)).isEmpty();
    }

    @Test
    void messagesComeBackInTheOrderTheyWereAdded() {
        MockHttpServletRequest request = withSession();
        ConsoleFlash.add(request, new FlashMessage(Level.INFO, "one"));
        ConsoleFlash.add(request, new FlashMessage(Level.ERROR, "two"));

        assertThat(ConsoleFlash.take(request)).extracting(FlashMessage::text).containsExactly("one", "two");
    }

    @Test
    void textWithColonsAndMarkupComesBackUnchanged() {
        MockHttpServletRequest request = withSession();
        ConsoleFlash.add(request, new FlashMessage(Level.WARNING, "a: b <b>c</b> “d”"));

        assertThat(ConsoleFlash.take(request).get(0).text()).isEqualTo("a: b <b>c</b> “d”");
    }

    @Test
    void takingNeverCreatesASessionForAVisitorWithoutOne() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(ConsoleFlash.take(request)).isEmpty();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void onlyTheLastFiveMessagesAreKeptAndEachIsCutToItsLimit() {
        MockHttpServletRequest request = withSession();
        for (int i = 0; i < 8; i++) {
            ConsoleFlash.add(request, new FlashMessage(Level.INFO, "m" + i));
        }
        ConsoleFlash.add(request, new FlashMessage(Level.INFO, "x".repeat(5000)));

        var taken = ConsoleFlash.take(request);
        assertThat(taken).hasSize(5);
        assertThat(taken.get(0).text()).isEqualTo("m4");
        assertThat(taken.get(4).text()).hasSize(400);
    }

    @Test
    void theAttributeStartsWithTheConsolesPrefixSoSigningOutClearsIt() {
        assertThat(ConsoleFlash.ATTRIBUTE).startsWith("JPA_ADMIN_");
    }

    @Test
    void whatTheSessionHoldsIsPlainStringsSoTheHostCanSerializeTheSession() throws Exception {
        MockHttpServletRequest request = withSession();
        ConsoleFlash.add(request, new FlashMessage(Level.ERROR, "Nothing was deleted."));

        Object stored = request.getSession().getAttribute(ConsoleFlash.ATTRIBUTE);
        try (ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream())) {
            out.writeObject(stored);
        }
    }

    @Test
    void aForeignOrBrokenValueUnderOurNameIsIgnoredNotShown() {
        MockHttpServletRequest request = withSession();
        request.getSession().setAttribute(ConsoleFlash.ATTRIBUTE, java.util.List.of("NOPE:x", "no-colon", 42));

        assertThat(ConsoleFlash.take(request)).isEmpty();
        request.getSession().setAttribute(ConsoleFlash.ATTRIBUTE, "just a string");
        assertThat(ConsoleFlash.take(request)).isEmpty();
    }
}
