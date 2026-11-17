package io.github.mdmusahub.jpaadmin.service;

/**
 * The entity is in the console but its flags forbid the action (read-only, or not deletable). Thrown by the service
 * itself, so a request is refused even when the page never offered the button (SEC-11). No message: nothing a visitor
 * typed is repeated.
 *
 * <p>Internal, not stable API.
 */
public final class ActionRefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ActionRefusedException() {
        super(null, null, false, false);
    }
}
