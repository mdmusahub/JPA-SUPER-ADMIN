package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.storage.FileSignatures;
import io.github.mdmusahub.jpaadmin.storage.StoragePaths;
import io.github.mdmusahub.jpaadmin.storage.StoredContent;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

/**
 * Writes a stored file to the browser, the same way for the console's own route and for a storage's public path (UPL-6).
 *
 * <p><b>What every answer carries.</b>
 * <ul>
 *   <li>the content type the console <em>detected</em> from the file's first bytes (never the file name, never what a client
 *       said when uploading); a file that is none of the known types, for example an HTML file that reached the folder some
 *       other way, is {@code application/octet-stream};</li>
 *   <li>{@code X-Content-Type-Options: nosniff}, so a browser takes that type at its word;</li>
 *   <li>{@code Content-Disposition: attachment} for everything that is not an image (PNG, JPEG, GIF, WebP), so a document or a
 *       page is downloaded and never rendered in the console's origin; an image is {@code inline};</li>
 *   <li>{@code Content-Security-Policy: default-src 'none'; sandbox}, so even a file opened by its address can run nothing.</li>
 * </ul>
 * The name offered for a download is the stored name (32 random hex characters and the extension), never a client's name.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates the public-path handler.
 */
public final class FileResponder {

    /** The policy on every file answer: nothing may load or run, and the document is sandboxed. */
    public static final String CONTENT_SECURITY_POLICY = "default-src 'none'; sandbox";

    private static final String OCTET_STREAM = "application/octet-stream";
    private static final int BUFFER = 32 * 1024;

    private FileResponder() {
    }

    /**
     * Sets the headers that every answer about a file carries, a refusal included. Called first, before anything can fail.
     */
    public static void baseHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
    }

    /**
     * Splits {@code <storage>/<key>} (what follows the files path in an address) into its two parts, or empty when it has not
     * exactly that shape. Every part is checked against {@link StoragePaths}: no dot except the one in the file name, no
     * percent sign, no backslash, no semicolon, nothing that could climb out of a folder.
     */
    public static Optional<List<String>> parse(String rest) {
        if (rest == null) {
            return Optional.empty();
        }
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            return Optional.empty();
        }
        String storage = rest.substring(0, slash);
        String key = rest.substring(slash + 1);
        if (!StoragePaths.validStorageName(storage) || !StoragePaths.validKey(key)) {
            return Optional.empty();
        }
        return Optional.of(List.of(storage, key));
    }

    /**
     * Writes the file.
     *
     * @param response     the response; {@link #baseHeaders} was already applied
     * @param content      the opened file; the caller closes it
     * @param storedName   the last part of the key, offered as the name of a download
     * @param cacheControl the {@code Cache-Control} to send, or {@code null} to leave the security chain's default (no store)
     * @param withBody     {@code false} for HEAD
     */
    public static void write(HttpServletResponse response, StoredContent content, String storedName, String cacheControl,
                             boolean withBody) throws IOException {
        InputStream in = new BufferedInputStream(content.stream(), BUFFER);
        in.mark(FileSignatures.HEAD_BYTES);
        byte[] head = in.readNBytes(FileSignatures.HEAD_BYTES);
        in.reset();
        Optional<FileSignatures.Detected> detected = FileSignatures.detect(head, head.length);

        baseHeaders(response);
        response.setContentType(detected.map(FileSignatures.Detected::mime).orElse(OCTET_STREAM));
        boolean inline = detected.map(FileSignatures.Detected::showInline).orElse(false);
        response.setHeader("Content-Disposition", inline ? "inline" : "attachment; filename=\"" + storedName + "\"");
        if (cacheControl != null) {
            response.setHeader("Cache-Control", cacheControl);
        }
        response.setContentLengthLong(content.size());
        if (withBody) {
            OutputStream out = response.getOutputStream();
            in.transferTo(out);
            out.flush();
        }
    }
}
