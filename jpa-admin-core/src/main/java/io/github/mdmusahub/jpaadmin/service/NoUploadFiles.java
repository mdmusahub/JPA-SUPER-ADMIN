package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.Cell;
import java.util.Optional;

/** No upload support: no field is an upload field, nothing is accepted, no address is a file. Fails closed. */
final class NoUploadFiles implements UploadFiles {

    static final NoUploadFiles INSTANCE = new NoUploadFiles();

    private NoUploadFiles() {
    }

    @Override
    public Optional<Settings> settings(String entityUrlName, String field) {
        return Optional.empty();
    }

    @Override
    public String ticket(String entityUrlName, String field, long maxBytes) {
        throw new UnsupportedOperationException("no upload support");
    }

    @Override
    public boolean accepts(String entityUrlName, String field, String url) {
        return false;
    }

    @Override
    public Optional<Cell> cell(String url, String label) {
        return Optional.empty();
    }
}
