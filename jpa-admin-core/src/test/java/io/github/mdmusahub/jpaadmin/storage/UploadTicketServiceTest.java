package io.github.mdmusahub.jpaadmin.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.mdmusahub.jpaadmin.storage.UploadTicketService.Status;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/**
 * UPL-2a: a ticket is bound to an entity, a field and a size, expires, and is refused when anything about it is wrong. The
 * clock is moved by hand (TEST-6).
 */
class UploadTicketServiceTest {

    private static final byte[] KEY = "the-test-signing-key-0123456789".getBytes(StandardCharsets.UTF_8);
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");

    /** A clock the test moves. */
    private static final class MovingClock extends Clock {

        private Instant now = START;

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovingClock clock = new MovingClock();
    private final UploadTicketService tickets = new UploadTicketService(KEY, clock);

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String signedWith(byte[] key, String payload) throws Exception {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return "v1." + b64(bytes) + "." + b64(mac.doFinal(bytes));
    }

    // ---- the good path ---------------------------------------------------------------------------------------------------------

    @Test
    void aTicketIsValidForItsEntityAndFieldAndCarriesTheSize() {
        String ticket = tickets.issue("widget", "photo", 2048);

        UploadTicketService.Verdict verdict = tickets.verify(ticket, "widget", "photo");

        assertThat(verdict.valid()).isTrue();
        assertThat(verdict.status()).isEqualTo(Status.VALID);
        assertThat(verdict.maxBytes()).isEqualTo(2048);
        assertThat(ticket).matches("v1\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    }

    @Test
    void aTicketCanBeUsedMoreThanOnceInsideItsTime() {
        String ticket = tickets.issue("widget", "photo", 2048);

        assertThat(tickets.verify(ticket, "widget", "photo").valid()).isTrue();
        assertThat(tickets.verify(ticket, "widget", "photo").valid()).isTrue();
    }

    @Test
    void theValidityIsTenMinutes() {
        assertThat(UploadTicketService.VALIDITY).isEqualTo(Duration.ofMinutes(10));
    }

    // ---- expiry --------------------------------------------------------------------------------------------------------------------

    @Test
    void aTicketIsStillValidJustBeforeTenMinutesAndExpiredAtTenMinutes() {
        String ticket = tickets.issue("widget", "photo", 2048);

        clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        assertThat(tickets.verify(ticket, "widget", "photo").status()).isEqualTo(Status.VALID);

        clock.advance(Duration.ofSeconds(1));
        UploadTicketService.Verdict expired = tickets.verify(ticket, "widget", "photo");
        assertThat(expired.status()).isEqualTo(Status.EXPIRED);
        assertThat(expired.valid()).isFalse();
        assertThat(expired.maxBytes()).isZero();
    }

    @Test
    void anExpiredTicketForAnotherFieldIsJustInvalid() {
        String ticket = tickets.issue("widget", "photo", 2048);
        clock.advance(Duration.ofHours(1));

        assertThat(tickets.verify(ticket, "widget", "other").status()).isEqualTo(Status.INVALID);
    }

    @Test
    void aTicketFromTheFutureClockOfAnotherInstanceStillExpires() {
        String ticket = tickets.issue("widget", "photo", 2048);
        clock.advance(Duration.ofDays(2));

        assertThat(tickets.verify(ticket, "widget", "photo").valid()).isFalse();
    }

    // ---- bound to entity and field -------------------------------------------------------------------------------------------------

    @Test
    void aTicketForAnotherEntityIsRefused() {
        String ticket = tickets.issue("widget", "photo", 2048);

        assertThat(tickets.verify(ticket, "gadget", "photo").status()).isEqualTo(Status.INVALID);
    }

    @Test
    void aTicketForAnotherFieldIsRefused() {
        String ticket = tickets.issue("widget", "photo", 2048);

        assertThat(tickets.verify(ticket, "widget", "scan").status()).isEqualTo(Status.INVALID);
    }

    @Test
    void theEntityAndFieldAreComparedExactly() {
        String ticket = tickets.issue("widget", "photo", 2048);

        assertThat(tickets.verify(ticket, "Widget", "photo").valid()).isFalse();
        assertThat(tickets.verify(ticket, "widget", "Photo").valid()).isFalse();
        assertThat(tickets.verify(ticket, "widget ", "photo").valid()).isFalse();
        assertThat(tickets.verify(ticket, "", "photo").valid()).isFalse();
        assertThat(tickets.verify(ticket, null, "photo").valid()).isFalse();
        assertThat(tickets.verify(ticket, "widget", null).valid()).isFalse();
    }

    // ---- the key --------------------------------------------------------------------------------------------------------------------

    @Test
    void aTicketSignedWithAnotherKeyIsRefused() {
        UploadTicketService other = new UploadTicketService("another-signing-key-0123456789".getBytes(StandardCharsets.UTF_8),
                clock);

        assertThat(tickets.verify(other.issue("widget", "photo", 2048), "widget", "photo").status()).isEqualTo(Status.INVALID);
    }

    @Test
    void aRestartWithARandomKeyInvalidatesEveryOldTicket() {
        UploadTicketService before = UploadTicketService.withRandomKey(clock);
        String ticket = before.issue("widget", "photo", 2048);
        assertThat(before.verify(ticket, "widget", "photo").valid()).isTrue();

        UploadTicketService afterRestart = UploadTicketService.withRandomKey(clock);

        assertThat(afterRestart.verify(ticket, "widget", "photo").status()).isEqualTo(Status.INVALID);
    }

    @Test
    void aConfiguredKeyKeepsTicketsValidAcrossInstances() {
        UploadTicketService one = UploadTicketService.withKey("a-configured-key-of-some-length-0123456789", clock);
        UploadTicketService two = UploadTicketService.withKey("a-configured-key-of-some-length-0123456789", clock);

        assertThat(two.verify(one.issue("widget", "photo", 100), "widget", "photo").valid()).isTrue();
    }

    @Test
    void aTooShortKeyIsRefused() {
        assertThatThrownBy(() -> new UploadTicketService(new byte[15], clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UploadTicketService(null, clock)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void theKeyIsCopiedSoLaterChangesToTheArrayDoNothing() {
        byte[] mine = KEY.clone();
        UploadTicketService copy = new UploadTicketService(mine, clock);
        String ticket = copy.issue("widget", "photo", 100);

        mine[0] ^= 1;

        assertThat(copy.verify(ticket, "widget", "photo").valid()).isTrue();
    }

    // ---- tampering -----------------------------------------------------------------------------------------------------------------

    @Test
    void changingAnyCharacterOfTheTicketMakesItInvalid() {
        String ticket = tickets.issue("widget", "photo", 2048);

        for (int i = 0; i < ticket.length(); i++) {
            char original = ticket.charAt(i);
            char changed = original == 'A' ? 'B' : 'A';
            String tampered = ticket.substring(0, i) + changed + ticket.substring(i + 1);
            if (tampered.equals(ticket)) {
                continue;
            }
            assertThat(tickets.verify(tampered, "widget", "photo").valid()).as("changed position " + i).isFalse();
        }
    }

    @Test
    void aLargerSizeCannotBeWrittenIntoATicket() throws Exception {
        String ticket = tickets.issue("widget", "photo", 100);
        String[] parts = ticket.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8).replace("|100|", "|100000000|");
        String forged = "v1." + b64(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThat(tickets.verify(forged, "widget", "photo").status()).isEqualTo(Status.INVALID);
    }

    @Test
    void aLaterExpiryCannotBeWrittenIntoATicket() {
        String ticket = tickets.issue("widget", "photo", 100);
        clock.advance(Duration.ofHours(1));
        String[] parts = ticket.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forged = "v1." + b64((payload.substring(0, payload.lastIndexOf('|') + 1) + "99999999999")
                .getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThat(tickets.verify(forged, "widget", "photo").status()).isEqualTo(Status.INVALID);
    }

    @Test
    void theSignatureOfOneTicketDoesNotFitAnother() {
        String one = tickets.issue("widget", "photo", 100);
        String two = tickets.issue("widget", "scan", 100);
        String spliced = one.substring(0, one.lastIndexOf('.')) + two.substring(two.lastIndexOf('.'));

        assertThat(tickets.verify(spliced, "widget", "photo").valid()).isFalse();
        assertThat(tickets.verify(spliced, "widget", "scan").valid()).isFalse();
    }

    // ---- malformed ------------------------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "v1", "v1.", "v1..", "v1.a.b", "v1.a.b.c", "v2.YQ.YQ", "V1.YQ.YQ", "..", ".", "a.b.c",
            "v1.%%%.%%%", "v1.YQ", "v1.YQ.", "v1..YQ", "v1.YQ.YQ.", "not a ticket at all", "v1.YQ==.YQ==",
            "javascript:alert(1)", "../../etc/passwd"})
    void anythingThatIsNotATicketIsInvalid(String text) {
        assertThat(tickets.verify(text, "widget", "photo").status()).isEqualTo(Status.INVALID);
    }

    @Test
    void aVeryLongTextIsInvalidWithoutWork() {
        assertThat(tickets.verify("v1." + "A".repeat(100_000) + ".AAAA", "widget", "photo").status()).isEqualTo(Status.INVALID);
    }

    @Test
    void aSignedPayloadOfTheWrongShapeIsInvalid() throws Exception {
        for (String payload : new String[] {"", "widget", "widget|photo", "widget|photo|100", "widget|photo|100|99999999999|x",
                "widget|photo|abc|99999999999", "widget|photo|100|abc", "widget|photo|0|99999999999",
                "widget|photo|-5|99999999999", "widget|photo|100|", "||||"}) {
            assertThat(tickets.verify(signedWith(KEY, payload), "widget", "photo").status()).as(payload).isEqualTo(Status.INVALID);
        }
    }

    @Test
    void aSignedPayloadOfTheRightShapeIsAccepted() throws Exception {
        long expires = START.plusSeconds(60).getEpochSecond();

        assertThat(tickets.verify(signedWith(KEY, "widget|photo|100|" + expires), "widget", "photo").valid()).isTrue();
    }

    @Test
    void theIssueArgumentsAreChecked() {
        assertThatThrownBy(() -> tickets.issue("wid|get", "photo", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tickets.issue("widget", "pho|to", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tickets.issue("", "photo", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tickets.issue("widget", null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tickets.issue("wid\nget", "photo", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tickets.issue("widget", "photo", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tickets.issue("a".repeat(121), "photo", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- not a session, never logged ----------------------------------------------------------------------------------------------------

    @Test
    void aTicketCarriesNoUserAndNoSessionAndTheServiceHasNoStateAboutPeople() {
        String ticket = tickets.issue("widget", "photo", 100);
        String payload = new String(Base64.getUrlDecoder().decode(ticket.split("\\.")[1]), StandardCharsets.UTF_8);

        // Only what the brief lists: entity, field, size, expiry. Nothing that names a person or a session.
        assertThat(payload.split("\\|")).hasSize(4);
        assertThat(payload).startsWith("widget|photo|100|");
        assertThat(ticket).doesNotContain("admin").doesNotContain("JSESSIONID");
    }

    @Test
    void neitherTheTicketNorTheKeyIsEverLogged() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        Level before = root.getLevel();
        root.setLevel(Level.TRACE);
        try {
            String ticket = tickets.issue("widget", "photo", 100);
            tickets.verify(ticket, "widget", "photo");
            tickets.verify(ticket + "x", "widget", "photo");
            tickets.verify(ticket, "gadget", "photo");
            clock.advance(Duration.ofHours(1));
            tickets.verify(ticket, "widget", "photo");
            tickets.toString();

            assertThat(logged.list).noneMatch(event -> event.getFormattedMessage().contains(ticket)
                    || event.getFormattedMessage().contains(new String(KEY, StandardCharsets.UTF_8)));
        } finally {
            root.detachAppender(logged);
            root.setLevel(before);
        }
    }

    @Test
    void toStringHidesTheKey() {
        assertThat(tickets.toString()).doesNotContain(new String(KEY, StandardCharsets.UTF_8)).contains("hidden");
    }
}
