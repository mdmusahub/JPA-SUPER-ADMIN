package io.github.mdmusahub.jpaadmin.service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The ids a bulk delete was asked for, as the text they arrived in. It is the one way into
 * {@link DeleteService#deleteMany}, and its constructor refuses more than {@value DeleteService#MAX_BULK_DELETE} ids
 * (the same pattern as the filter cap of the list, D-62): the request parser and the service both meet this limit, so no
 * code path can ask for more. A text is not checked here beyond its count; {@code IdConverter} decides, per entity,
 * whether it is an id.
 *
 * <p>Internal, not stable API.
 *
 * @param ids the ids as sent, in order; duplicates and texts that are not ids are allowed here (the service counts them)
 * @throws TooManyRecordsException if there are more than {@value DeleteService#MAX_BULK_DELETE}
 */
public record BulkDeleteIds(List<String> ids) {

    public BulkDeleteIds {
        ids = List.copyOf(Objects.requireNonNull(ids, "ids"));
        if (ids.size() > DeleteService.MAX_BULK_DELETE) {
            throw new TooManyRecordsException(DeleteService.MAX_BULK_DELETE);
        }
    }

    /**
     * The ids of a request's {@code ids} parameter values; {@code null} (nothing sent) is no ids. The count is checked
     * before any text is looked at.
     *
     * @param values the values of the request parameter; untrusted
     * @throws TooManyRecordsException if there are more than {@value DeleteService#MAX_BULK_DELETE}
     */
    public static BulkDeleteIds parse(String[] values) {
        if (values == null) {
            return new BulkDeleteIds(List.of());
        }
        if (values.length > DeleteService.MAX_BULK_DELETE) {
            throw new TooManyRecordsException(DeleteService.MAX_BULK_DELETE);
        }
        return new BulkDeleteIds(Arrays.stream(values).filter(Objects::nonNull).toList());
    }

    /** How many ids were sent. */
    public int size() {
        return ids.size();
    }
}
