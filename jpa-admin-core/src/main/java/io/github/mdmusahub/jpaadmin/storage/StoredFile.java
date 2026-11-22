package io.github.mdmusahub.jpaadmin.storage;

import java.util.Objects;

/**
 * A file a {@link StorageProvider} has stored: the storage it is in and its key, {@code folder/yyyy/MM/<32 hex>.<ext>}. The key is
 * made by the provider, never from anything a client sent, and the constructor refuses any other shape, so a value of this type
 * can be turned into a path or an address without a further check (UPL-3, UPL-4).
 *
 * <p>Part of the storage SPI (ARCH-9).
 *
 * @param storage the storage's name, for example {@code default}
 * @param key     the key inside the storage
 */
public record StoredFile(String storage, String key) {

    /** Refuses a storage name or a key of any other shape than the provider makes. */
    public StoredFile {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(key, "key");
        if (!StoragePaths.validStorageName(storage)) {
            throw new IllegalArgumentException("not a storage name");
        }
        if (!StoragePaths.validKey(key)) {
            throw new IllegalArgumentException("not a storage key");
        }
    }
}
