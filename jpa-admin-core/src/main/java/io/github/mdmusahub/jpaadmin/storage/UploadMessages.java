package io.github.mdmusahub.jpaadmin.storage;

import java.util.List;

/**
 * The sentences a person reads when an upload is refused. Each one says what is wrong and what to do (UI-6). None repeats a
 * file name, a declared type or anything else the client sent (SEC-9); the only numbers are limits, and the only type names are
 * the ones the console itself detected or the field accepts.
 *
 * <p>Internal, not stable API.
 */
public final class UploadMessages {

    private UploadMessages() {
    }

    public static String ticketInvalid() {
        return "This upload form is no longer valid. Open the form page again and choose the file again.";
    }

    public static String ticketExpired() {
        return "The upload form was open for too long. Open the form page again and choose the file again.";
    }

    public static String noFile() {
        return "No file was received. Choose a file and upload it again.";
    }

    public static String empty() {
        return "The file is empty. Choose a file that has content.";
    }

    public static String tooLarge(long limitBytes) {
        return "The file is larger than the " + UploadLimits.describe(limitBytes) + " allowed for this field. Choose a smaller "
                + "file, or make it smaller and upload it again.";
    }

    public static String hostLimit(long limitBytes) {
        String limit = limitBytes > 0 ? " (" + UploadLimits.describe(limitBytes) + ")" : "";
        return "The file is larger than this server accepts" + limit + ", so it never reached the console. Choose a smaller "
                + "file, or ask whoever runs the application to raise spring.servlet.multipart.max-file-size and "
                + "spring.servlet.multipart.max-request-size.";
    }

    public static String notMultipart() {
        return "The upload was not sent as a file upload (multipart form data), or the server's file upload support is switched "
                + "off (spring.servlet.multipart.enabled). Use the upload button on the form page.";
    }

    public static String notAllowed(String detectedMime, List<String> accepted) {
        return "This kind of file (" + describeType(detectedMime) + ") is not accepted here. This field accepts "
                + describeTypes(accepted) + ". Choose a file of one of those types.";
    }

    public static String unknownType(List<String> accepted) {
        return "The content of this file is not one of the types this field accepts (" + describeTypes(accepted) + "), whatever "
                + "its name says. Choose a real file of one of those types.";
    }

    public static String declaredMismatch() {
        return "The file's content does not match the type your browser reported for it. Save the file again in its real format "
                + "and upload it again.";
    }

    public static String hiddenMarkup() {
        return "The file contains page or script markup hidden inside it, so it cannot be accepted. Export a clean copy of the "
                + "file and upload that.";
    }

    public static String storageFailed() {
        return "The file could not be stored. Try again; if it keeps failing, ask whoever runs the application to check the "
                + "upload folder.";
    }

    /** A person's name for a MIME type the console can recognise; the type itself for anything else. */
    public static String describeType(String mime) {
        return switch (mime) {
            case "image/png" -> "PNG image";
            case "image/jpeg" -> "JPEG image";
            case "image/gif" -> "GIF image";
            case "image/webp" -> "WebP image";
            case "application/pdf" -> "PDF document";
            default -> mime;
        };
    }

    private static String describeTypes(List<String> mimes) {
        return String.join(", ", mimes.stream().map(UploadMessages::describeType).toList());
    }
}
