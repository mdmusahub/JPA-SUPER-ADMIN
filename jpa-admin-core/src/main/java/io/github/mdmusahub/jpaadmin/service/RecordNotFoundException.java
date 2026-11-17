package io.github.mdmusahub.jpaadmin.service;

/**
 * The record, or the entity it belongs to, is not there for the console: an id that is not the shape of an id, an id that
 * has no record, an entity that is hidden or unknown. All of these are one exception with no message, so the caller can
 * only answer one thing (the console's 404) and nothing here can echo what the visitor typed (SEC-2).
 *
 * <p>Internal, not stable API.
 */
public final class RecordNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RecordNotFoundException() {
        super(null, null, false, false);
    }
}
