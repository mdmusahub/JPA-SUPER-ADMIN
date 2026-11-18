package io.github.mdmusahub.jpaadmin.storage;

import io.github.mdmusahub.jpaadmin.service.UploadFiles;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The upload knowledge of the services (Phase 9b), from the resolved upload fields, the ticket service and the storage provider.
 *
 * <p><b>A posted address is accepted for a field only when</b> the provider can read it back as its own address
 * ({@link StorageProvider#parse}), it is in the field's own storage, it lies in the field's own folder, its file extension is one
 * of the accepted types, and the file is still stored ({@link StorageProvider#owns}). Anything else (another site, another
 * storage, another field's folder, a type the field does not take, a file that was never uploaded) is refused for that field.
 *
 * <p><b>A stored address is shown</b> as an image only for PNG, JPEG, GIF and WebP: SVG is never an image here (it is not
 * even storable, D-91) and a stored value with any other extension is a file link, never a picture. A value that is not an address
 * of the provider is not a cell at all, so the caller shows it as escaped text.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it.
 */
public final class StorageUploadFiles implements UploadFiles {

    /** The extensions of the types that a browser may show in an {@code img}. */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "gif", "webp");

    private final UploadTargets targets;
    private final UploadTicketService tickets;
    private final StorageProvider provider;

    public StorageUploadFiles(UploadTargets targets, UploadTicketService tickets, StorageProvider provider) {
        this.targets = Objects.requireNonNull(targets, "targets");
        this.tickets = Objects.requireNonNull(tickets, "tickets");
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public Optional<Settings> settings(String entityUrlName, String field) {
        return targets.find(entityUrlName, field).map(target -> new Settings(target.maxBytes(), target.accept(),
                UploadMessages.fieldHelp(target.accept(), target.maxBytes())));
    }

    @Override
    public String ticket(String entityUrlName, String field, long maxBytes) {
        return tickets.issue(entityUrlName, field, maxBytes);
    }

    @Override
    public boolean accepts(String entityUrlName, String field, String url) {
        Optional<UploadTargets.Target> target = targets.find(entityUrlName, field);
        if (target.isEmpty() || url == null) {
            return false;
        }
        Optional<StoredFile> file = provider.parse(url);
        if (file.isEmpty() || !file.get().storage().equals(target.get().storage())) {
            return false;
        }
        List<String> parts = StoragePaths.keyParts(file.get().key()).orElse(List.of());
        List<String> folder = StoragePaths.folderParts(target.get().folder());
        if (parts.size() != folder.size() + 3 || !parts.subList(0, folder.size()).equals(folder)) {
            return false; // another field's folder, or a key that is not folder/yyyy/MM/name
        }
        Optional<FileSignatures.Detected> type = FileSignatures.forExtension(extension(parts.get(parts.size() - 1)));
        if (type.isEmpty() || !target.get().accept().contains(type.get().mime())) {
            return false;
        }
        return provider.owns(url);
    }

    @Override
    public Optional<Cell> cell(String url, String label) {
        Optional<StoredFile> file = url == null ? Optional.empty() : provider.parse(url);
        if (file.isEmpty() || FileSignatures.forExtension(extension(file.get().key())).isEmpty()) {
            // Not an address of the provider, or one of a type the console never stores (an SVG, a page, a program): plain
            // escaped text, never a picture and never a link.
            return Optional.empty();
        }
        try {
            if (!provider.owns(url)) {
                // Ours, but the file is gone: say so in words instead of a broken picture with nothing to read.
                return Optional.of(new Cell(Cell.Type.TEXT, label + ": file not found", Cell.Tone.NONE, null, null));
            }
            String name = file.get().key().substring(file.get().key().lastIndexOf('/') + 1);
            String extension = extension(name);
            if (IMAGE_EXTENSIONS.contains(extension)) {
                // The text of an image cell is its alternative text: the field's label, never a file name.
                return Optional.of(new Cell(Cell.Type.IMAGE, label, Cell.Tone.NONE, url, null));
            }
            return Optional.of(new Cell(Cell.Type.FILE, label + " (" + extension.toUpperCase(java.util.Locale.ROOT) + ")",
                    Cell.Tone.NONE, url, name));
        } catch (IllegalArgumentException breaksTheLinkRule) {
            // A replacement provider wrote an address that the contract's link rule refuses: show it as text, not a link.
            return Optional.empty();
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }
}
