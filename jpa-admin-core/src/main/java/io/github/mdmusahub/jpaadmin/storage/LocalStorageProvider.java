package io.github.mdmusahub.jpaadmin.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Stores files in a folder on this server's disk, one root folder per local storage.
 *
 * <p><b>How a file name and a path are made (UPL-3, UPL-4).</b> The name is 128 random bits from {@link SecureRandom} written
 * as 32 hex characters, plus an extension the caller takes from the <em>detected</em> type; the client's file name is not an
 * argument anywhere and is never logged. The folder is {@code <root>/<folder>/<yyyy>/<MM>/}. Every path is built from parts
 * that {@link StoragePaths} already checked (no dot, slash, backslash, colon or control character), is then normalised, and
 * must start with the real path of the root; a symbolic link anywhere below the root is refused, and a file is created with
 * {@code CREATE_NEW}, so two uploads can never share a name or overwrite one another.
 *
 * <p>The root folder is created and probed (a small file is written and removed) by {@link #verify} at startup, only for storages
 * that an upload field names. A root that lies inside the web root (the servlet context's folder, {@code src/main/resources},
 * {@code src/main/webapp}, {@code target/classes} and similar) is refused, so no stored file can ever be served by the host's
 * static resource handling.
 *
 * <p>Internal, not stable API as a class; the {@link StorageProvider} interface it implements is the extension point. Public
 * because the auto-configuration module creates it.
 */
public final class LocalStorageProvider implements StorageProvider {

    private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]{2,4}");
    private static final int NAME_ATTEMPTS = 5;
    private static final int COPY_BUFFER = 64 * 1024;

    private final Map<String, StorageSettings> storages;
    private final String consoleFilesPath;
    private final Collection<Path> webRoots;
    private final SecureRandom random;
    private final Clock clock;

    /**
     * @param storages         the configured storages by name; only those of type {@code local} are served
     * @param consoleFilesPath the console's serving path, for example {@code /admin/files}
     * @param webRoots         folders the host's web server may serve; a storage inside one is refused
     * @param random           the source of file names
     * @param clock            the clock that decides the year and month folders (UTC)
     */
    public LocalStorageProvider(Map<String, StorageSettings> storages, String consoleFilesPath, Collection<Path> webRoots,
                                SecureRandom random, Clock clock) {
        this.storages = Map.copyOf(Objects.requireNonNull(storages, "storages"));
        this.consoleFilesPath = Objects.requireNonNull(consoleFilesPath, "consoleFilesPath");
        this.webRoots = List.copyOf(Objects.requireNonNull(webRoots, "webRoots"));
        this.random = Objects.requireNonNull(random, "random");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ---- startup --------------------------------------------------------------------------------------------------------------

    @Override
    public boolean supports(String storage) {
        StorageSettings settings = storages.get(storage);
        return settings != null && settings.supportedType();
    }

    @Override
    public String location(String storage) {
        return supports(storage) ? rootOf(storages.get(storage)).toString() : null;
    }

    @Override
    public List<String> verify(String storage) {
        if (!supports(storage)) {
            return List.of("it is not a local storage.");
        }
        StorageSettings settings = storages.get(storage);
        Path root = rootOf(settings);
        List<String> problems = new ArrayList<>();
        String property = "jpa.admin.storage." + storage + ".directory";
        for (Path web : webRoots) {
            Path normalized = web.toAbsolutePath().normalize();
            if (root.startsWith(normalized)) {
                problems.add("the folder " + root + " is inside the web root " + normalized + ", where the web server could "
                        + "serve stored files without the console's checks. Set " + property + " to a folder outside the "
                        + "application's resources, for example /var/lib/app/uploads.");
                return problems;
            }
        }
        try {
            Files.createDirectories(root);
        } catch (IOException | RuntimeException failure) {
            problems.add("the folder " + root + " cannot be created or is not a folder (" + failure.getClass().getSimpleName()
                    + "). Create it, or set " + property + " to a folder the application may create.");
            return problems;
        }
        if (!Files.isDirectory(root)) {
            problems.add("the path " + root + " is not a folder. Set " + property + " to a folder.");
            return problems;
        }
        Path probe = root.resolve(".probe-" + randomName());
        try (OutputStream out = Files.newOutputStream(probe, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            out.write(0);
        } catch (IOException | RuntimeException failure) {
            problems.add("the folder " + root + " cannot be written to (" + failure.getClass().getSimpleName() + "). Give the "
                    + "application permission to write there, or set " + property + " to another folder.");
            return problems;
        } finally {
            try {
                Files.deleteIfExists(probe);
            } catch (IOException ignored) {
                // The probe is a one-byte file with a random name; nothing serves a dot file here.
            }
        }
        return problems;
    }

    // ---- store ------------------------------------------------------------------------------------------------------------------

    @Override
    public StoredFile store(String storage, String folder, String extension, InputStream content, long maxBytes)
            throws IOException {
        Objects.requireNonNull(content, "content");
        StorageSettings settings = storages.get(storage);
        if (settings == null || !settings.supportedType()) {
            throw new IllegalArgumentException("unknown storage");
        }
        if (StoragePaths.folderProblem(folder).isPresent()) {
            throw new IllegalArgumentException("unusable folder");
        }
        if (extension == null || !EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("unusable extension");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        ZonedDateTime now = clock.instant().atZone(ZoneOffset.UTC);
        List<String> parts = new ArrayList<>(StoragePaths.folderParts(folder));
        parts.add(String.format("%04d", now.getYear()));
        parts.add(String.format("%02d", now.getMonthValue()));

        Path realRoot = ensureRoot(settings);
        Path directory = ensureDirectories(realRoot, parts);
        for (int attempt = 0; attempt < NAME_ATTEMPTS; attempt++) {
            String name = randomName() + "." + extension;
            Path target = directory.resolve(name);
            if (!target.normalize().startsWith(realRoot)) {
                throw new IOException("the path left the storage");
            }
            try {
                copy(content, target, maxBytes);
            } catch (FileAlreadyExistsException taken) {
                continue;
            }
            parts.add(name);
            return new StoredFile(storage, String.join("/", parts));
        }
        throw new IOException("no free file name");
    }

    /** Copies at most {@code maxBytes}; a larger or failing copy leaves no file behind. */
    private static void copy(InputStream content, Path target, long maxBytes) throws IOException {
        // Opened before the try: if the name is taken, CREATE_NEW fails here and the existing file must not be touched.
        OutputStream stream = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        boolean done = false;
        try (OutputStream out = stream) {
            byte[] buffer = new byte[COPY_BUFFER];
            long total = 0;
            int read;
            while ((read = content.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("the file is larger than the limit");
                }
                out.write(buffer, 0, read);
            }
            done = true;
        } finally {
            if (!done) {
                Files.deleteIfExists(target);
            }
        }
    }

    /** The real path of the root, created if needed. A root that is itself a link is the operator's choice and is followed. */
    private Path ensureRoot(StorageSettings settings) throws IOException {
        Path root = rootOf(settings);
        Files.createDirectories(root);
        return root.toRealPath();
    }

    /** Creates {@code parts} below {@code realRoot} one level at a time, refusing a link at any level. */
    private static Path ensureDirectories(Path realRoot, List<String> parts) throws IOException {
        Path current = realRoot;
        for (String part : parts) {
            current = current.resolve(part);
            if (!current.normalize().startsWith(realRoot)) {
                throw new IOException("the path left the storage");
            }
            if (Files.isSymbolicLink(current)) {
                throw new IOException("a symbolic link is not allowed inside a storage");
            }
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(current);
            }
            if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)
                    || !current.toRealPath().startsWith(realRoot)) {
                throw new IOException("the path left the storage");
            }
        }
        return current;
    }

    // ---- open and delete ----------------------------------------------------------------------------------------------------------

    @Override
    public Optional<StoredContent> open(String storage, String key) throws IOException {
        Optional<Path> path = existingFile(storage, key);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        try {
            long size = Files.size(path.get());
            return Optional.of(new StoredContent(Files.newInputStream(path.get(), LinkOption.NOFOLLOW_LINKS), size));
        } catch (IOException vanished) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(StoredFile file) throws IOException {
        Objects.requireNonNull(file, "file");
        Optional<Path> path = existingFile(file.storage(), file.key());
        if (path.isPresent()) {
            Files.deleteIfExists(path.get());
        }
    }

    /**
     * The real file for a storage and a key, or empty: the storage must be a local one, the key must have exactly the shape of
     * a key we made, no component may be a link, and the real path must be inside the real root and be a regular file.
     */
    private Optional<Path> existingFile(String storage, String key) {
        StorageSettings settings = storage == null ? null : storages.get(storage);
        if (settings == null || !settings.supportedType()) {
            return Optional.empty();
        }
        Optional<List<String>> parts = StoragePaths.keyParts(key);
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        try {
            Path root = rootOf(settings);
            if (!Files.isDirectory(root)) {
                return Optional.empty();
            }
            Path realRoot = root.toRealPath();
            Path current = realRoot;
            for (String part : parts.get()) {
                current = current.resolve(part);
                if (!current.normalize().startsWith(realRoot) || Files.isSymbolicLink(current)) {
                    return Optional.empty();
                }
            }
            if (!Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS) || !current.toRealPath().startsWith(realRoot)) {
                return Optional.empty();
            }
            return Optional.of(current);
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    // ---- url ------------------------------------------------------------------------------------------------------------------------

    @Override
    public String url(StoredFile file) {
        Objects.requireNonNull(file, "file");
        StorageSettings settings = storages.get(file.storage());
        if (settings != null && settings.publicPath() != null) {
            return settings.publicPath() + "/" + file.key();
        }
        return consoleFilesPath + "/" + file.storage() + "/" + file.key();
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------------------------

    private static Path rootOf(StorageSettings settings) {
        return Path.of(settings.directory().strip()).toAbsolutePath().normalize();
    }

    private String randomName() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
