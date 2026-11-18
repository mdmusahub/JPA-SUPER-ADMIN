package io.github.mdmusahub.jpaadmin.storage;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Issues and checks the signed upload tickets (UPL-2a, D-10).
 *
 * <p>A ticket is {@code v1.<payload>.<signature>}: the payload says which entity and field it is for, the largest file it allows
 * and when it expires; the signature is HMAC-SHA256 of the payload with the signing key. A ticket is valid for
 * {@link #VALIDITY} (10 minutes) and only for the entity and the field it was issued for. It is checked with a constant-time
 * comparison of the signature <em>before</em> anything in the payload is trusted.
 *
 * <p><b>A ticket is not a session.</b> It carries no user and grants nothing by itself: the upload endpoint still needs the
 * signed-in admin and the CSRF token. It only limits what a stolen or replayed upload request can do (a certain field, a certain
 * size, a short time). Anyone who holds the signing key can make tickets, so the key is a secret and is never logged; neither is
 * a ticket, and this class logs nothing at all.
 *
 * <p>Without a configured key the auto-configuration makes a random one at every start ({@link #withRandomKey}), which makes a
 * restart invalidate every open form's tickets, and so does a second instance.
 *
 * <p>Internal, not stable API. Public because the web package and the auto-configuration use it.
 */
public final class UploadTicketService {

    /** How long a ticket lives: about 10 minutes, long enough to fill in a form, short enough to limit a replay. */
    public static final Duration VALIDITY = Duration.ofMinutes(10);

    /** The shortest key the service accepts, in bytes. */
    public static final int MIN_KEY_BYTES = 16;

    private static final String VERSION = "v1";
    private static final String ALGORITHM = "HmacSHA256";
    private static final int MAX_TICKET_LENGTH = 512;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** What a check found. */
    public enum Status {
        /** Signed with this service's key, not expired, and for the entity and field asked about. */
        VALID,
        /** Signed with this service's key and for the right entity and field, but its time is up. The form must be opened again. */
        EXPIRED,
        /** Anything else: malformed, tampered, another key, another entity, another field. */
        INVALID
    }

    /**
     * The answer of {@link #verify}.
     *
     * @param status   what the check found
     * @param maxBytes the largest file the ticket allows; {@code 0} unless the ticket is valid
     */
    public record Verdict(Status status, long maxBytes) {

        static final Verdict INVALID = new Verdict(Status.INVALID, 0);
        static final Verdict EXPIRED = new Verdict(Status.EXPIRED, 0);

        /** Whether the ticket may be used. */
        public boolean valid() {
            return status == Status.VALID;
        }
    }

    private final byte[] key;
    private final Clock clock;

    /**
     * @param key   the secret, at least {@value #MIN_KEY_BYTES} bytes
     * @param clock where "now" comes from (tests move it by hand)
     */
    public UploadTicketService(byte[] key, Clock clock) {
        Objects.requireNonNull(key, "key");
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("the signing key is too short");
        }
        this.key = key.clone();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A service with a new random 256-bit key: tickets from any earlier service (or another instance) are refused. */
    public static UploadTicketService withRandomKey(Clock clock) {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return new UploadTicketService(random, clock);
    }

    /** A service whose key is the UTF-8 bytes of a configured text. */
    public static UploadTicketService withKey(String configured, Clock clock) {
        return new UploadTicketService(Objects.requireNonNull(configured, "configured").getBytes(StandardCharsets.UTF_8), clock);
    }

    /**
     * Issues a ticket for one field.
     *
     * @param entity   the entity's address name
     * @param field    the field's name
     * @param maxBytes the largest file the ticket allows, more than zero
     * @return the ticket text (letters, digits, {@code -}, {@code _} and two dots)
     */
    public String issue(String entity, String field, long maxBytes) {
        requirePart(entity, "entity");
        requirePart(field, "field");
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        long expires = clock.instant().plus(VALIDITY).getEpochSecond();
        String payload = entity + "|" + field + "|" + maxBytes + "|" + expires;
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return VERSION + "." + ENCODER.encodeToString(payloadBytes) + "." + ENCODER.encodeToString(sign(payloadBytes));
    }

    /**
     * Checks a ticket that came from a request (untrusted).
     *
     * @param ticket the text
     * @param entity the entity the request is for
     * @param field  the field the request is for
     */
    public Verdict verify(String ticket, String entity, String field) {
        if (ticket == null || ticket.isEmpty() || ticket.length() > MAX_TICKET_LENGTH || entity == null || field == null) {
            return Verdict.INVALID;
        }
        String[] parts = ticket.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            return Verdict.INVALID;
        }
        byte[] payloadBytes;
        byte[] signature;
        try {
            payloadBytes = DECODER.decode(parts[1]);
            signature = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException notBase64) {
            return Verdict.INVALID;
        }
        // One text per ticket: Base64 lets the last character carry bits that decode to nothing, so two different texts could
        // otherwise stand for the same bytes. Not a forgery, but a ticket should have exactly one spelling.
        if (!ENCODER.encodeToString(payloadBytes).equals(parts[1]) || !ENCODER.encodeToString(signature).equals(parts[2])) {
            return Verdict.INVALID;
        }
        // The signature first, with a comparison whose time does not depend on where the bytes differ. Nothing in the payload is
        // looked at before this passes.
        if (!MessageDigest.isEqual(sign(payloadBytes), signature)) {
            return Verdict.INVALID;
        }
        String[] claims = new String(payloadBytes, StandardCharsets.UTF_8).split("\\|", -1);
        if (claims.length != 4) {
            return Verdict.INVALID;
        }
        long maxBytes;
        long expires;
        try {
            maxBytes = Long.parseLong(claims[2]);
            expires = Long.parseLong(claims[3]);
        } catch (NumberFormatException notNumbers) {
            return Verdict.INVALID;
        }
        if (maxBytes <= 0 || !claims[0].equals(entity) || !claims[1].equals(field)) {
            return Verdict.INVALID;
        }
        if (clock.instant().getEpochSecond() >= expires) {
            return Verdict.EXPIRED;
        }
        return new Verdict(Status.VALID, maxBytes);
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException impossible) {
            // HmacSHA256 is required of every Java platform, so this cannot happen. The message names nothing secret.
            throw new IllegalStateException("HMAC-SHA256 is not available");
        }
    }

    private static void requirePart(String value, String what) {
        if (value == null || value.isBlank() || value.indexOf('|') >= 0 || value.length() > 120
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(what + " cannot be part of a ticket");
        }
    }

    /** Never shows the key. */
    @Override
    public String toString() {
        return "UploadTicketService[key=<hidden>]";
    }
}
