package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.storage.StorageProvider;
import io.github.mdmusahub.jpaadmin.storage.StoredContent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET {base}/files/<storage>/<folder>/<yyyy>/<MM>/<name>}: serves a stored file to a signed-in admin (UPL-6, UPL-7).
 *
 * <p>The security chain lets only the admin reach this route (everything under the console path needs the admin authority, and
 * only {@code /login}, {@code /assets/**} and {@code /theme.css} are public). The path is read from the <em>raw</em> request
 * address, not the decoded one, and every part is matched against the strict name formats of {@link FileResponder#parse}: a
 * {@code ..}, a {@code %2e}, an encoded slash, a backslash, a {@code ;} or any other shape is not a file, so it is the console's
 * 404, the same answer as for a file that does not exist. Spring Security's firewall refuses most of these before they get here;
 * this check is a second, independent line (UPL-4).
 *
 * <p>The answer carries the detected content type, {@code nosniff}, {@code attachment} for anything that is not an image, and a
 * sandboxing policy (see {@link FileResponder}), on every answer including the refusals.
 *
 * <p>Internal, not stable API. Public only so the auto-configuration module can create it.
 */
@Controller
public class FileController {

    private final StorageProvider provider;
    private final BasePath basePath;

    public FileController(StorageProvider provider, BasePath basePath) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.basePath = Objects.requireNonNull(basePath, "basePath");
    }

    @GetMapping("/files/**")
    public void serve(HttpServletRequest request, HttpServletResponse response) throws IOException {
        FileResponder.baseHeaders(response);
        String rest = restOf(request);
        Optional<List<String>> parts = FileResponder.parse(rest);
        if (parts.isEmpty()) {
            throw notFound();
        }
        String storage = parts.get().get(0);
        String key = parts.get().get(1);
        Optional<StoredContent> opened = provider.open(storage, key);
        if (opened.isEmpty()) {
            throw notFound();
        }
        try (StoredContent content = opened.get()) {
            FileResponder.write(response, content, key.substring(key.lastIndexOf('/') + 1), null, !"HEAD".equals(request.getMethod()));
        }
    }

    /** What follows {@code {base}/files/} in the raw address, or {@code null} when the address is not under it. */
    private String restOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
        String prefix = basePath.value() + "/files/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : null;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
