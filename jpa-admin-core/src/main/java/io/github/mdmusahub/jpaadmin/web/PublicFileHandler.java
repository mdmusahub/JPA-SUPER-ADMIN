package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.storage.StorageProvider;
import io.github.mdmusahub.jpaadmin.storage.StoragePaths;
import io.github.mdmusahub.jpaadmin.storage.StoredContent;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.HttpRequestHandler;

/**
 * Serves one storage's files at its {@code public-path}, <em>without a session</em>: the explicit opt-in of UPL-7, off by default.
 *
 * <p>It is not a console controller (no path prefix, no security chain of the console's): it is registered, only when a storage
 * has a {@code public-path}, for that path and nothing else, and a route of the host's own that matches first keeps it. The host's
 * own security applies to the path as to any other route of the host: the console adds none and removes none (SEC-4). The answer
 * is the same as the console's, with the same safe headers (see {@link FileResponder}), plus a public cache lifetime.
 *
 * <p>Only {@code GET} and {@code HEAD}. The address after the public path must be exactly a stored key; anything else is a 404
 * with the safe headers.
 *
 * <p>Internal, not stable API. Public only so the auto-configuration module can create it.
 */
public final class PublicFileHandler implements HttpRequestHandler {

    /** Files have random names and never change, so a browser may keep them for an hour. */
    static final String CACHE_CONTROL = "public, max-age=3600";

    private final String storage;
    private final String publicPath;
    private final StorageProvider provider;

    /**
     * @param storage    the storage whose files are served; a valid storage name
     * @param publicPath the path they are served at, for example {@code /files}
     * @param provider   the storage provider
     */
    public PublicFileHandler(String storage, String publicPath, StorageProvider provider) {
        this.storage = Objects.requireNonNull(storage, "storage");
        if (!StoragePaths.validStorageName(storage)) {
            throw new IllegalArgumentException("not a storage name");
        }
        this.publicPath = Objects.requireNonNull(publicPath, "publicPath");
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        FileResponder.baseHeaders(response);
        String method = request.getMethod();
        boolean head = "HEAD".equals(method);
        if (!head && !"GET".equals(method)) {
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        String key = keyOf(request);
        Optional<StoredContent> opened = key == null ? Optional.empty() : provider.open(storage, key);
        if (opened.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try (StoredContent content = opened.get()) {
            FileResponder.write(response, content, key.substring(key.lastIndexOf('/') + 1), CACHE_CONTROL, !head);
        }
    }

    /** The key after the public path in the raw address, or {@code null}. */
    private String keyOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
        String prefix = publicPath + "/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        String key = path.substring(prefix.length());
        return StoragePaths.validKey(key) ? key : null;
    }
}
