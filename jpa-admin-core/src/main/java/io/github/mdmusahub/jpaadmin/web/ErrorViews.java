package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.web.view.ErrorView;

/**
 * The fixed texts of the console's error pages. A text says what went wrong and what to do next (UI-6), and it is
 * chosen only by the status code. Nothing from an exception, a request or the database ever reaches it, so no SQL,
 * table name, stack trace or user input can leak into an error page (SEC-12).
 *
 * <p>Internal, not stable API. Public only so the preview app and the tests use the same texts.
 */
public final class ErrorViews {

    /** The one message shown after a failed sign-in. It never says which of the two fields was wrong. */
    public static final String SIGN_IN_FAILED =
            "The username or password is not correct. Check both and try again.";

    private ErrorViews() {
    }

    /**
     * The error page for {@code status}. A status without its own text falls back to the nearest one: other 4xx
     * statuses use the "bad request" text, everything else uses the "something went wrong" text.
     *
     * @param status HTTP status code, 100 to 599
     */
    public static ErrorView forStatus(int status) {
        return switch (status) {
            case 400 -> new ErrorView(400, "Bad request",
                    "The console could not understand this request. Go back and check the address or the values "
                            + "you entered, then try again.");
            case 403 -> new ErrorView(403, "Access denied",
                    "You cannot do this, or the page has expired. Reload the page and try again. If it keeps "
                            + "happening, sign out and sign in again.");
            case 404 -> new ErrorView(404, "Page not found",
                    "This address does not exist in the console. Check the address, or go to the dashboard and "
                            + "choose from there.");
            case 405 -> new ErrorView(405, "Action not allowed",
                    "This address does not accept that kind of request. Go to the dashboard and choose from there.");
            case 500 -> new ErrorView(500, "Something went wrong",
                    "The console could not finish this request. Try again. If it keeps happening, ask the person "
                            + "who runs this application to check its log.");
            default -> status >= 400 && status < 500
                    ? new ErrorView(status, "Request not accepted",
                            "The console cannot do this. Go back, check what you asked for, and try again.")
                    : new ErrorView(status, "Something went wrong",
                            "The console could not finish this request. Try again. If it keeps happening, ask the "
                                    + "person who runs this application to check its log.");
        };
    }
}
