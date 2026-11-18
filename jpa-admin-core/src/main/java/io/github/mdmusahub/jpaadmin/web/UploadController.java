package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.storage.UploadLimits;
import io.github.mdmusahub.jpaadmin.storage.UploadMessages;
import io.github.mdmusahub.jpaadmin.storage.UploadService;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage;
import io.github.mdmusahub.jpaadmin.web.view.UploadResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

/**
 * {@code POST {base}/uploads}: takes one file for one upload field and answers where it was stored (UPL-1, UPL-2, UPL-2a, UPL-5).
 *
 * <p>The request is {@code multipart/form-data} with the parts {@code file}, {@code ticket}, {@code entity} and {@code field}
 * (and {@code record}, the id of the record being edited, for the plain-form flow). The session and the CSRF token are checked
 * by the console's security chain before this method runs, so a request without them never gets here: the token comes in the
 * {@code X-CSRF-TOKEN} header from the uploader script, or in the {@code _csrf} field of a plain form. The ticket is checked by
 * {@link UploadService}, first of all.
 *
 * <p><b>Two ways to answer</b> (see {@link UploadAnswers}): JSON for a request that says {@code Accept: application/json}, and for a
 * plain form (option A, no script) a redirect back to the form page with a message, or the console's error page. A redirect target
 * is built by {@link ListLinks} from the console's own metadata; nothing in the request is ever used as an address (D-65).
 *
 * <p>The controller is thin (ARCH-2): every decision is in {@link UploadService}. It reads the host's multipart limits through
 * Spring's own exceptions and never changes them; a file over them is reported with a message that says so.
 *
 * <p>Internal, not stable API. Public only so the auto-configuration module can create it.
 */
@Controller
public class UploadController {

    private final UploadService uploads;
    private final ConsoleChromeFactory chromeFactory;
    private final ConsoleViews views;
    private final BasePath basePath;

    public UploadController(UploadService uploads, ConsoleChromeFactory chromeFactory, ConsoleViews views, BasePath basePath) {
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        this.chromeFactory = Objects.requireNonNull(chromeFactory, "chromeFactory");
        this.views = Objects.requireNonNull(views, "views");
        this.basePath = Objects.requireNonNull(basePath, "basePath");
    }

    /**
     * @param entity the entity address the form was for; untrusted, checked against the ticket
     * @param field  the field the form was for; untrusted, checked against the ticket
     * @param ticket the signed ticket; never logged
     * @param record the id of the record being edited, for the way back after a plain-form upload; untrusted, used only after
     *               {@link ListLinks#addressable}
     * @param file   the file
     */
    @PostMapping("/uploads")
    public ModelAndView upload(@RequestParam(name = "entity", required = false) String entity,
                               @RequestParam(name = "field", required = false) String field,
                               @RequestParam(name = "ticket", required = false) String ticket,
                               @RequestParam(name = "record", required = false) String record,
                               @RequestParam(name = "file", required = false) MultipartFile file,
                               HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean chosen = file != null && !(file.isEmpty() && (file.getOriginalFilename() == null
                || file.getOriginalFilename().isBlank()));
        UploadService.Outcome outcome = uploads.handle(new UploadService.Upload(entity, field, ticket,
                chosen ? file.getContentType() : null, chosen ? file.getOriginalFilename() : null,
                chosen ? file.getSize() : 0, chosen ? file::getInputStream : null));
        if (outcome instanceof UploadService.Stored stored) {
            return stored(stored, record, request, response);
        }
        UploadService.Refused refused = (UploadService.Refused) outcome;
        return handled(UploadAnswers.refuse(request, response, views, chromeFactory, basePath, refused.status(),
                refused.message()));
    }

    private ModelAndView stored(UploadService.Stored stored, String record, HttpServletRequest request,
                                HttpServletResponse response) throws IOException {
        UploadResult result;
        try {
            result = UploadResult.success(stored.url(), stored.name(), stored.size());
        } catch (IllegalArgumentException badLink) {
            // The provider returned an address that breaks the link rule: a bug in a replacement provider, never the client's doing.
            return handled(UploadAnswers.refuse(request, response, views, chromeFactory, basePath, 500,
                    UploadMessages.storageFailed()));
        }
        if (UploadAnswers.wantsJson(request)) {
            UploadAnswers.writeJson(response, 200, result);
            return null;
        }
        ListLinks links = new ListLinks(basePath.value(), stored.entity());
        // The result waits in the visitor's own session for the form page it is redirected to (option A, D-95). The record id is
        // used only if it can be written into an address, exactly as the redirect does, so an entry and its page always agree.
        PendingUploads.add(request, new PendingUploads.Entry(stored.entity(), stored.field(),
                record != null && ListLinks.addressable(record) ? record : "", result.url(), result.name(), result.size()));
        ConsoleFlash.add(request, new FlashMessage(FlashMessage.Level.SUCCESS,
                "The file " + result.name() + " (" + UploadLimits.describe(result.size()) + ") was uploaded."));
        return ConsoleRedirect.to(basePath, links.afterUpload(record));
    }

    /**
     * A request the multipart support refused before the console saw it, with lazy resolution switched on by the host: the file is
     * over the host's limit, or the request is not a file upload at all.
     */
    @ExceptionHandler(MultipartException.class)
    public ModelAndView multipartRefused(MultipartException failure, HttpServletRequest request, HttpServletResponse response) {
        return handled(UploadAnswers.refuseMultipart(request, response, views, chromeFactory, basePath, failure));
    }

    /** A JSON answer is already written, which the dispatcher must not render again: it wants {@code null} for "done". */
    private static ModelAndView handled(ModelAndView answer) {
        return answer.isEmpty() ? null : answer;
    }

    /** Tells whether the exception is about the size of the request, for the resolver and the handler alike. */
    static boolean isTooLarge(MultipartException failure) {
        return failure instanceof MaxUploadSizeExceededException;
    }
}
