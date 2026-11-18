package io.github.mdmusahub.jpaadmin.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides whether an uploaded file is accepted, and stores it only if it is (UPL-1, UPL-2, UPL-2a, UPL-5). It knows nothing of
 * the web: the controller hands it the ticket, the entity and field the form was for, and the file's bytes and claims.
 *
 * <p><b>The sequence is the safety.</b> Every check runs, in this sequence, before the provider is asked to store anything, so a
 * refused file leaves nothing behind:
 * <ol>
 *   <li>the <em>ticket</em>, bound to this entity and field (an expired or foreign one is refused before the body is read);</li>
 *   <li>the field exists and is an upload field of the console;</li>
 *   <li>the <em>size</em> the client claims against the limit (the lower of the ticket's and the field's);</li>
 *   <li>the <em>real type</em> by the file's first bytes, which must be one of the field's types (UPL-1);</li>
 *   <li>the <em>declared type</em>, which may be missing or generic but must not contradict the content;</li>
 *   <li>one pass over the whole file: the bytes that really arrive (not the claim), against the limit again, an empty file, and
 *       markup hidden inside the file (a polyglot);</li>
 *   <li>only now the provider stores it, from a fresh read of the same bytes.</li>
 * </ol>
 * SVG and HTML are never accepted: they have no signature in the table, and a field that lists them does not start (UPL-5).
 *
 * <p>The client's file name is used for nothing but the display name in the answer (UPL-3). It is not logged; a failure is logged
 * with the entity and field of the target and the exception's class only.
 *
 * <p>Internal, not stable API. Public because the web package and the auto-configuration use it.
 */
public final class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    /** Where the bytes of a file come from. May be asked for more than once (a check, then the store). */
    @FunctionalInterface
    public interface ContentSource {
        InputStream open() throws IOException;
    }

    /**
     * One upload, as the web layer saw it. Everything here is untrusted.
     *
     * @param entity       the entity address the form was for
     * @param field        the field the form was for
     * @param ticket       the signed ticket
     * @param declaredType the {@code Content-Type} the client gave the file part, or {@code null}
     * @param clientName   the file name the client gave, display only
     * @param declaredSize the size the client's request claims
     * @param content      the file's bytes
     */
    public record Upload(String entity, String field, String ticket, String declaredType, String clientName, long declaredSize,
                         ContentSource content) {
    }

    /** What became of an upload. */
    public sealed interface Outcome permits Stored, Refused {
    }

    /**
     * A stored file.
     *
     * @param entity the entity address of the target (from the console's own metadata, not from the request)
     * @param field  the field of the target
     * @param url    the file's address, from the provider
     * @param name   the display name
     * @param size   the size in bytes, as counted
     */
    public record Stored(String entity, String field, String url, String name, long size) implements Outcome {
    }

    /**
     * A refused upload.
     *
     * @param status  the HTTP status that fits (403 ticket, 400 nothing to store, 413 too big, 415 wrong content, 500 not stored)
     * @param message what is wrong and what to do
     */
    public record Refused(int status, String message) implements Outcome {
    }

    private final UploadTargets targets;
    private final UploadTicketService tickets;
    private final StorageProvider provider;

    public UploadService(UploadTargets targets, UploadTicketService tickets, StorageProvider provider) {
        this.targets = Objects.requireNonNull(targets, "targets");
        this.tickets = Objects.requireNonNull(tickets, "tickets");
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    /** Checks the upload and stores it if every check passes. Never throws for a bad upload. */
    public Outcome handle(Upload upload) {
        Objects.requireNonNull(upload, "upload");
        UploadTicketService.Verdict ticket = tickets.verify(upload.ticket(), upload.entity(), upload.field());
        if (ticket.status() == UploadTicketService.Status.EXPIRED) {
            return new Refused(403, UploadMessages.ticketExpired());
        }
        Optional<UploadTargets.Target> found = ticket.valid() ? targets.find(upload.entity(), upload.field()) : Optional.empty();
        if (found.isEmpty()) {
            // The same answer for a bad ticket and for a field that is not an upload field: nothing is revealed about which.
            return new Refused(403, UploadMessages.ticketInvalid());
        }
        UploadTargets.Target target = found.get();
        long limit = Math.min(ticket.maxBytes(), target.maxBytes());

        if (upload.content() == null) {
            return new Refused(400, UploadMessages.noFile());
        }
        if (upload.declaredSize() > limit) {
            return new Refused(413, UploadMessages.tooLarge(limit));
        }
        try {
            return check(upload, target, limit);
        } catch (IOException | RuntimeException failure) {
            log.warn("An upload for {}.{} could not be processed ({}).", target.entity(), target.field(),
                    failure.getClass().getSimpleName());
            return new Refused(500, UploadMessages.storageFailed());
        }
    }

    private Outcome check(Upload upload, UploadTargets.Target target, long limit) throws IOException {
        FileSignatures.Detected detected;
        try (InputStream in = upload.content().open()) {
            byte[] head = in.readNBytes(FileSignatures.HEAD_BYTES);
            if (head.length == 0) {
                return new Refused(400, UploadMessages.empty());
            }
            Optional<FileSignatures.Detected> type = FileSignatures.detect(head, head.length);
            if (type.isEmpty()) {
                return new Refused(415, UploadMessages.unknownType(target.accept()));
            }
            detected = type.get();
        }
        if (!target.accept().contains(detected.mime())) {
            return new Refused(415, UploadMessages.notAllowed(detected.mime(), target.accept()));
        }
        if (contradicts(upload.declaredType(), detected.mime())) {
            return new Refused(415, UploadMessages.declaredMismatch());
        }

        ContentScan.Result scan;
        try (InputStream in = upload.content().open()) {
            scan = ContentScan.scan(in, limit);
        }
        if (scan.tooLarge()) {
            return new Refused(413, UploadMessages.tooLarge(limit));
        }
        if (scan.bytes() == 0) {
            return new Refused(400, UploadMessages.empty());
        }
        if (scan.markup()) {
            return new Refused(415, UploadMessages.hiddenMarkup());
        }

        // Every check passed. Only now is anything written.
        StoredFile stored;
        try (InputStream in = upload.content().open()) {
            stored = provider.store(target.storage(), target.folder(), detected.extension(), in, limit);
        }
        return new Stored(target.entity(), target.field(), provider.url(stored), UploadNames.display(upload.clientName()),
                scan.bytes());
    }

    /**
     * Whether the type the client declared contradicts what the file is. No declaration, or a generic one
     * ({@code application/octet-stream}), claims nothing; any other must be exactly the detected type, parameters aside.
     */
    static boolean contradicts(String declared, String detectedMime) {
        if (declared == null || declared.isBlank()) {
            return false;
        }
        String type = declared.strip().toLowerCase(Locale.ROOT);
        int semicolon = type.indexOf(';');
        if (semicolon >= 0) {
            type = type.substring(0, semicolon).strip();
        }
        return !type.isEmpty() && !type.equals("application/octet-stream") && !type.equals(detectedMime);
    }
}
