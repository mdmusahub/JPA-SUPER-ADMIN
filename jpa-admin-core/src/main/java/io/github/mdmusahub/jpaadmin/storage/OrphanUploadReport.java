package io.github.mdmusahub.jpaadmin.storage;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A <b>read-only</b> report of the files in a local storage folder that no record holds any more (Phase 14a, UPL-9, D-132).
 *
 * <p>Replacing or removing a file in the console keeps the old file on disk (UPL-9, D-93), so files pile up that no record points
 * at. This class lists them. It never changes anything: it has no method that deletes, moves or writes, it opens no file for
 * writing, and it does not follow a symbolic link. Whether to offer a cleanup is the maintainer's decision, not part of this class.
 *
 * <p><b>What counts as an orphan.</b> A file below the root of a local storage, whose path has exactly the shape the console gives
 * its own files ({@link StoragePaths#validKey}), and whose address no upload field of any entity holds. The addresses held are
 * read with one Criteria query per upload field ({@code select distinct}, names from the metadata, never from text, SEC-3) and each
 * is turned back into a storage and a key with the provider's own {@link StorageProvider#parse}, so a file served at the console's
 * path and one served at a storage's public path are the same file. A file that is not in the console's shape (a probe file, a file
 * somebody put there by hand) is counted and never listed as an orphan, because the console did not make it.
 *
 * <p><b>Cost.</b> One walk of each storage folder and one query per upload field; memory is one string per distinct stored address.
 * Run it in a read-only transaction (ARCH-5) and not on a request thread.
 *
 * <p>Internal, not stable API. It has no bean, no route and no property: nothing in the application calls it.
 */
public final class OrphanUploadReport {

    private OrphanUploadReport() {
    }

    /**
     * One file that no record holds.
     *
     * @param storage the storage's name
     * @param key     the file's key inside the storage
     * @param bytes   its size
     * @param address the address the console would serve it at
     */
    public record Orphan(String storage, String key, long bytes, String address) {

        public Orphan {
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(address, "address");
        }
    }

    /**
     * What the report found.
     *
     * @param orphans           the files no record holds, by storage and key
     * @param filesScanned      the files of the console's shape that were looked at
     * @param filesHeld         how many of them a record holds
     * @param notConsoleFiles   the files and links that are not in the console's shape (never listed as orphans)
     * @param missingFiles      the addresses a record holds whose file is not in the storage
     * @param storagesNotScanned the storages an upload field names that this report cannot read (not a local folder)
     */
    public record Report(List<Orphan> orphans, long filesScanned, long filesHeld, long notConsoleFiles, long missingFiles,
                         List<String> storagesNotScanned) {

        public Report {
            orphans = List.copyOf(Objects.requireNonNull(orphans, "orphans"));
            storagesNotScanned = List.copyOf(Objects.requireNonNull(storagesNotScanned, "storagesNotScanned"));
        }

        /** The bytes the orphans take together. */
        public long orphanBytes() {
            return orphans.stream().mapToLong(Orphan::bytes).sum();
        }

        /**
         * The report as plain text, one orphan per line: the storage, the key and the size. It holds no record value and no
         * client file name (the console never stored one).
         */
        public String describe() {
            StringBuilder text = new StringBuilder();
            text.append("Upload files that no record holds (read-only report; nothing was changed)\n");
            text.append(String.format(Locale.ROOT, "  files looked at: %d, held by a record: %d, held by no record: %d (%d bytes)%n",
                    filesScanned, filesHeld, orphans.size(), orphanBytes()));
            text.append(String.format(Locale.ROOT, "  files that are not in the console's shape (not listed): %d%n", notConsoleFiles));
            text.append(String.format(Locale.ROOT, "  addresses a record holds whose file is missing: %d%n", missingFiles));
            for (String storage : storagesNotScanned) {
                text.append("  storage not scanned (not a local folder): ").append(storage).append('\n');
            }
            for (Orphan orphan : orphans) {
                text.append(String.format(Locale.ROOT, "  %s %s (%d bytes)%n", orphan.storage(), orphan.key(), orphan.bytes()));
            }
            return text.toString();
        }
    }

    /**
     * Finds the orphans.
     *
     * @param provider      the storage provider; only a provider that knows the folder of a storage ({@link StorageProvider#location})
     *                      can be read
     * @param targets       the resolved upload fields
     * @param registry      the entities of the console
     * @param entityManager the console's entity manager; the caller opens a read-only transaction around the call
     * @throws UncheckedIOException if a storage folder cannot be read
     */
    public static Report find(StorageProvider provider, UploadTargets targets, AdminEntityRegistry registry,
                              EntityManager entityManager) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(entityManager, "entityManager");

        Set<String> held = heldKeys(provider, targets, registry, entityManager);
        Map<String, Path> roots = new LinkedHashMap<>();
        Set<String> notScanned = new LinkedHashSet<>();
        for (UploadTargets.Target target : targets.all()) {
            String location = provider.supports(target.storage()) ? provider.location(target.storage()) : null;
            if (location == null) {
                notScanned.add(target.storage());
            } else {
                roots.putIfAbsent(target.storage(), Path.of(location));
            }
        }

        List<Orphan> orphans = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long scanned = 0;
        long heldFound = 0;
        long notConsole = 0;
        for (Map.Entry<String, Path> root : roots.entrySet()) {
            Walk walk = walk(provider, root.getKey(), root.getValue(), held, seen, orphans);
            scanned += walk.scanned;
            heldFound += walk.held;
            notConsole += walk.notConsole;
        }
        long missing = held.stream().filter(key -> !seen.contains(key)).filter(key -> roots.containsKey(storageOf(key))).count();
        orphans.sort(Comparator.comparing(Orphan::storage).thenComparing(Orphan::key));
        return new Report(orphans, scanned, heldFound, notConsole, missing, List.copyOf(notScanned));
    }

    // ---- what the records hold -----------------------------------------------------------------------------------------------

    /** The storage and key of every file some record holds, as {@code storage:key}. */
    private static Set<String> heldKeys(StorageProvider provider, UploadTargets targets, AdminEntityRegistry registry,
                                        EntityManager entityManager) {
        Set<String> held = new HashSet<>();
        for (UploadTargets.Target target : targets.all()) {
            Optional<AdminEntity> entity = registry.find(target.entity());
            if (entity.isEmpty()) {
                continue;
            }
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaQuery<String> query = builder.createQuery(String.class);
            Root<?> root = query.from(entity.get().type());
            query.select(root.<String>get(target.field())).distinct(true).where(builder.isNotNull(root.get(target.field())));
            for (String address : entityManager.createQuery(query).getResultList()) {
                provider.parse(address).ifPresent(file -> held.add(file.storage() + ":" + file.key()));
            }
        }
        return held;
    }

    private static String storageOf(String heldKey) {
        return heldKey.substring(0, heldKey.indexOf(':'));
    }

    // ---- the walk ---------------------------------------------------------------------------------------------------------------

    private static final class Walk {
        long scanned;
        long held;
        long notConsole;
    }

    /** Looks at every file below {@code root} and adds to {@code orphans} the ones no record holds. Reads only. */
    private static Walk walk(StorageProvider provider, String storage, Path root, Set<String> held, Set<String> seen,
                             List<Orphan> orphans) {
        Walk result = new Walk();
        if (!Files.isDirectory(root)) {
            return result;
        }
        try {
            // No FileVisitOption.FOLLOW_LINKS: a symbolic link is never followed.
            Files.walkFileTree(root, Set.<FileVisitOption>of(), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    String key = root.relativize(file).toString().replace('\\', '/');
                    if (!attributes.isRegularFile() || !StoragePaths.validKey(key)) {
                        result.notConsole++;
                        return FileVisitResult.CONTINUE;
                    }
                    result.scanned++;
                    String id = storage + ":" + key;
                    seen.add(id);
                    if (held.contains(id)) {
                        result.held++;
                    } else {
                        orphans.add(new Orphan(storage, key, attributes.size(), provider.url(new StoredFile(storage, key))));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException failure) {
                    result.notConsole++; // a file that cannot be read is not reported as an orphan
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException failure) {
            throw new UncheckedIOException("The storage folder of " + storage + " could not be read.", failure);
        }
        return result;
    }
}
