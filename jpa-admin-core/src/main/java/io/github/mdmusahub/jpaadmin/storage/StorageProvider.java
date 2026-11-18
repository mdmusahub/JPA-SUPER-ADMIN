package io.github.mdmusahub.jpaadmin.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Where uploaded files live. The console's controllers talk to this interface only (UPL-10), so another provider (S3 is planned
 * for v0.2) can be added without touching them. A host can replace the local provider by defining a bean of this type.
 *
 * <p><b>What an implementation must guarantee:</b>
 * <ul>
 *   <li>{@link #store} chooses the name itself (random, never from a client) and puts the file under {@code folder/yyyy/MM/}
 *       of the storage; nothing outside the storage can be reached whatever the arguments are (UPL-3, UPL-4);</li>
 *   <li>{@link #open} and {@link #delete} accept only keys of the shape {@link StoragePaths#validKey} describes and answer "not
 *       there" (never an exception that reveals a path) for anything else;</li>
 *   <li>nothing here logs or returns a client's file name; the caller never passes one.</li>
 * </ul>
 *
 * <p>Part of the console's public API (ARCH-9).
 */
public interface StorageProvider {

    /** Whether this provider serves the storage called {@code storage} (as configured). */
    boolean supports(String storage);

    /**
     * Checks at startup that the storage can be used, and prepares it (a local provider creates its folder and writes a probe
     * file). Called only for storages that an {@code @AdminUpload} field names.
     *
     * @return one full sentence per problem, empty when the storage is ready
     */
    default List<String> verify(String storage) {
        return List.of();
    }

    /** Where the storage keeps its files, for the startup log (a local provider returns the absolute folder), or {@code null}. */
    default String location(String storage) {
        return null;
    }

    /**
     * Stores a file under a name the provider makes up.
     *
     * @param storage   the storage's name
     * @param folder    the field's folder (already validated, possibly empty)
     * @param extension the extension for the stored name, taken from the detected type (for example {@code png})
     * @param content   the bytes; the provider reads at most {@code maxBytes + 1} of them
     * @param maxBytes  the largest size the provider may write
     * @return what was stored
     * @throws IOException if the file cannot be written or is larger than {@code maxBytes}; nothing is left behind
     */
    StoredFile store(String storage, String folder, String extension, InputStream content, long maxBytes)
            throws IOException;

    /** Removes a stored file. A file that is not there is not an error. */
    void delete(StoredFile file) throws IOException;

    /**
     * Opens a stored file for reading.
     *
     * @param storage the storage's name, from an address (untrusted)
     * @param key     the key, from an address (untrusted)
     * @return the content, or empty when the storage or the key is unknown or invalid, or there is no such file
     */
    Optional<StoredContent> open(String storage, String key) throws IOException;

    /** The address under which the file is served: under the console, or under the storage's public path when it has one. */
    String url(StoredFile file);

    /**
     * Reads one of this provider's own addresses back: the exact text {@link #url} makes for a stored file, and nothing else.
     * It looks at the text only and never at the disk, so it can be asked about any posted value. A value that is not an
     * address of this provider (another site, another storage, a traversal, anything that {@link #url} would not have written)
     * is empty.
     *
     * <p>The default is empty, so a provider that does not implement it owns no address and the console refuses every
     * address a form posts for it (fail closed).
     *
     * @param url an address, from a form or a database; untrusted
     */
    default Optional<StoredFile> parse(String url) {
        return Optional.empty();
    }

    /**
     * Whether {@code url} is an address this provider made for a file that is still stored. The console accepts a posted
     * upload address only if this is true (the file really is one the console uploaded). The default is "it parses"; a provider
     * that can ask its storage whether the file exists should say so, as the local provider does.
     *
     * @param url an address, from a form or a database; untrusted
     */
    default boolean owns(String url) {
        return parse(url).isPresent();
    }
}
