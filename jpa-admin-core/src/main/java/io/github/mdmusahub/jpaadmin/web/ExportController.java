package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.service.ActionRefusedException;
import io.github.mdmusahub.jpaadmin.service.ExportService;
import io.github.mdmusahub.jpaadmin.service.FilterParse;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.service.RecordNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET {base path}/{entity}/export.csv}: the CSV file of the list the visitor is looking at (Phase 11, D-105 to D-110).
 *
 * <p>The query parameters are the list's ({@code q}, {@code filter.*}, {@code sort}, {@code dir}) and are read with the list's
 * own parsers, so an unknown, hidden or sensitive name is ignored in exactly the same way (SEC-2). {@code page} and {@code size}
 * mean nothing here: the file holds every matching record up to the cap. The controller is thin (ARCH-2): it parses, asks
 * {@link ExportService}, and writes.
 *
 * <p><b>Same answers as the other record routes:</b> an entity that is unknown, hidden, excluded or unsupported is the console's
 * 404, identical in every case (D-47); an entity in the console whose export is switched off (the property
 * {@code jpa.admin.export-enabled}, or {@code @AdminResource(exportable = false)}) is 403 (SEC-11, enforced in the service).
 * Only GET is mapped. A session is required like for every console route; the security chain sees to that before this method
 * runs.
 *
 * <p><b>The response</b> is written straight to the servlet output stream in UTF-8 (no message converter, so the host's
 * converters and settings cannot touch it): {@code Content-Type: text/csv;charset=UTF-8}, {@code Content-Disposition:
 * attachment} with a generated ASCII file name, {@code X-Content-Type-Options: nosniff}, {@code Cache-Control: no-store}, and,
 * only when the file is cut at the cap, {@code X-Export-Truncated: true} and {@code X-Export-Row-Limit}. Nothing is put in the
 * session. The first page is read before any header is set, so a failure there is the console's normal error page.
 *
 * <p>The mapping is relative; the auto-configuration puts the base path in front of it (D-30). A literal segment beats the
 * detail route's {@code {id}}, so {@code export.csv} is never taken for a record id: an entity whose identifier is the text
 * {@code export.csv} cannot open its detail page (the same limit as {@code new}, D-102 point 9). Internal, not stable API.
 */
@Controller
public class ExportController {

    /** The same entity pattern as the record routes: it leaves {@code assets} to the console's asset handler. */
    private static final String ENTITY = "/{entity:(?!assets$)[a-z0-9-]+}";

    /** Header: present with the value {@code true} exactly when the file holds fewer records than matched. */
    public static final String TRUNCATED_HEADER = "X-Export-Truncated";

    /** Header: the cap the file was cut at; present exactly when {@link #TRUNCATED_HEADER} is. */
    public static final String ROW_LIMIT_HEADER = "X-Export-Row-Limit";

    private final ConsoleNavigation navigation;
    private final ListQueryService lists;
    private final ExportService exports;

    public ExportController(ConsoleNavigation navigation, ListQueryService lists, ExportService exports) {
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.lists = Objects.requireNonNull(lists, "lists");
        this.exports = Objects.requireNonNull(exports, "exports");
    }

    /**
     * @param entity the entity's URL name from the address; untrusted, only ever a key in the registry
     * @param q      search text; untrusted
     * @param sort   key of the column to sort by; untrusted
     * @param dir    {@code asc} or {@code desc}; untrusted
     * @throws ResponseStatusException 404 for an unknown or hidden entity, 403 when the export is switched off
     */
    @GetMapping(ENTITY + "/export.csv")
    public void export(@PathVariable("entity") String entity,
                       @RequestParam(name = "q", required = false) String q,
                       @RequestParam(name = "sort", required = false) String sort,
                       @RequestParam(name = "dir", required = false) String dir,
                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        AdminEntity found = navigation.find(entity)
                // No message: the exception's text would otherwise repeat what the visitor typed.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        FilterParse filters = lists.parseFilters(found, EntityController.filterParameters(request));
        ListQuery query = lists.parse(found, q, sort, dir, null, null).withParsedFilters(filters.filters());
        ExportService.Export export;
        try {
            export = exports.open(found, query);
        } catch (RecordNotFoundException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ActionRefusedException refused) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + export.fileName() + "\"");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        if (export.truncated()) {
            response.setHeader(TRUNCATED_HEADER, "true");
            response.setHeader(ROW_LIMIT_HEADER, String.valueOf(export.rowLimit()));
        }
        if ("HEAD".equals(request.getMethod())) {
            // Spring maps HEAD to a GET handler. The headers are the answer; do not stream a whole file to be thrown away.
            response.flushBuffer();
            return;
        }
        Writer out = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
        export.writeTo(out);
        out.flush();
        response.flushBuffer();
    }
}
