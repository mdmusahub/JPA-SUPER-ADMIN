package io.github.mdmusahub.jpaadmin.service;

/**
 * A bulk delete asked for more records than {@value DeleteService#MAX_BULK_DELETE} at once. Nothing is deleted. The
 * message holds only the limit, never an id the visitor sent.
 *
 * <p>Internal, not stable API.
 */
public final class TooManyRecordsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int limit;

    public TooManyRecordsException(int limit) {
        super("At most " + limit + " records can be deleted at once.", null, false, false);
        this.limit = limit;
    }

    /** The most records one request may delete. */
    public int limit() {
        return limit;
    }
}
