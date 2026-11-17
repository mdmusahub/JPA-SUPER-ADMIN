package io.github.mdmusahub.jpaadmin.service;

/**
 * The database refused a delete, most often because other records still refer to the record by a foreign key. The
 * transaction is rolled back, so nothing was deleted. The message is fixed and holds no SQL, table name or value
 * (SEC-12); the cause is kept for the server log only and never shown.
 *
 * <p>Internal, not stable API.
 */
public final class RecordInUseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RecordInUseException(Throwable cause) {
        super("The database refused the delete.", cause, false, false);
    }
}
