package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.storage.UploadMessages;
import io.github.mdmusahub.jpaadmin.web.view.ErrorView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Breadcrumb;
import io.github.mdmusahub.jpaadmin.web.view.UploadResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.ModelAndView;

/**
 * How the upload endpoint answers. Two audiences, one rule: a script (the uploader) sends {@code Accept: application/json} and
 * gets the frozen {@link UploadResult} as JSON; a plain form (option A, no script) gets a redirect on success or the console's
 * error page on failure. Both the controller and the exception resolver (which sees a file that is over the host's multipart
 * limit before any controller runs) answer through this class, so the two cannot differ.
 *
 * <p>The JSON is written by hand: it has four fields, and writing it here keeps the answer independent of the host's JSON
 * configuration (naming strategies, modules, escaping). Every character that could matter in a page is escaped.
 *
 * <p>Package-private: it is a rule, not API.
 */
final class UploadAnswers {

    private static final String JSON = "application/json;charset=UTF-8";

    private UploadAnswers() {
    }

    /** Whether the client asked for JSON: the uploader script does, a browser submitting a form does not. */
    static boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.toLowerCase(Locale.ROOT).contains("application/json");
    }

    /** Writes the result as the body, with {@code status}. */
    static void writeJson(HttpServletResponse response, int status, UploadResult result) throws IOException {
        response.setStatus(status);
        response.setContentType(JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json(result));
    }

    /**
     * Answers a refusal in the way the client asked for. JSON: the body is written and an <em>empty</em> {@code ModelAndView} comes
     * back, which both the dispatcher (as an exception resolver's answer) and the callers here read as "already handled". A
     * person: the error page.
     */
    static ModelAndView refuse(HttpServletRequest request, HttpServletResponse response, ConsoleViews views,
                               ConsoleChromeFactory chromeFactory, BasePath basePath, int status, String message) {
        if (wantsJson(request)) {
            try {
                writeJson(response, status, UploadResult.failure(message));
            } catch (IOException cannotWrite) {
                // The client is gone; there is nobody to answer.
            }
            return new ModelAndView();
        }
        return errorPage(request, response, views, chromeFactory, basePath, status, message);
    }

    /**
     * The answer for a request that the multipart support refused: over the host's limit (413), or not a file upload at all
     * (400). The message never repeats anything the client sent.
     */
    static ModelAndView refuseMultipart(HttpServletRequest request, HttpServletResponse response, ConsoleViews views,
                                        ConsoleChromeFactory chromeFactory, BasePath basePath,
                                        MultipartException failure) {
        if (UploadController.isTooLarge(failure)) {
            long limit = ((MaxUploadSizeExceededException) failure).getMaxUploadSize();
            return refuse(request, response, views, chromeFactory, basePath, 413,
                    UploadMessages.hostLimit(limit));
        }
        return refuse(request, response, views, chromeFactory, basePath, 400,
                UploadMessages.notMultipart());
    }

    /** A refusal for a person who is looking at a page: the console's error page with the message as its hint. */
    static ModelAndView errorPage(HttpServletRequest request, HttpServletResponse response, ConsoleViews views,
                                  ConsoleChromeFactory chromeFactory, BasePath basePath, int status, String message) {
        response.setStatus(status);
        ErrorView view = new ErrorView(status, "The file was not uploaded", message);
        PageChrome chrome = chromeFactory.chrome(request,
                List.of(new Breadcrumb("Dashboard", basePath.value()), new Breadcrumb(view.heading(), null)));
        return views.page(request, ErrorView.TEMPLATE, chrome, view);
    }

    /** {@code {"url":...,"name":...,"size":...,"error":...}}, with {@code null} for the fields that are not set. */
    static String json(UploadResult result) {
        StringBuilder out = new StringBuilder(160);
        out.append("{\"url\":").append(string(result.url()));
        out.append(",\"name\":").append(string(result.name()));
        out.append(",\"size\":").append(result.size());
        out.append(",\"error\":").append(string(result.error())).append('}');
        return out.toString();
    }

    private static String string(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<', '>', '&', '\'' -> out.append(String.format("\\u%04x", (int) c));
                default -> {
                    if (c < 0x20 || c == 0x7f || c == 0x2028 || c == 0x2029) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
