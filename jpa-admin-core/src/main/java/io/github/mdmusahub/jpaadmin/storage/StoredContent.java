package io.github.mdmusahub.jpaadmin.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * The bytes of a stored file, opened for reading. The caller closes it.
 *
 * <p>Part of the storage SPI (ARCH-9).
 *
 * @param stream the content
 * @param size   its size in bytes
 */
public record StoredContent(InputStream stream, long size) implements Closeable {

    public StoredContent {
        Objects.requireNonNull(stream, "stream");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
