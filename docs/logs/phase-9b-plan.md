# Phase 9b plan: forms and UI for uploads

Branch `phase/9b-uploads-ui` (from `main`, which holds the merged 9a, the dot-segment fix `0467faa` and tag `v0.0.8`). One session on both tracks (the recorded exception to AI-11).
Nothing is committed. Phase 10 is not started. A Java process (PID 4160, not mine) was already listening on port 8080 when the session began; it is left alone and the manual run uses another port.

## Step 0 (documents only)

* 9a decisions D-85 to D-94 confirmed present in `docs/DECISIONS.md`. The dot-segment fix (`ConsoleChainGuard` ignoring out-of-console dot-segment requests) is on `main` as `0467faa`: nothing to do here.
* `docs/logs/phase-9b-scope.md` exists. It is read as the starting point and corrected where this brief differs (the flash carrying the address, `owns`).
* D-95 records the no-script decision (option A) with the design below.

## Design

**Frozen records, no change.** A `FormView.Field` of type `UPLOAD` already has `uploadTicket`, `value` (the stored or pending address), `help` and `options`. The form needs three more facts: the entity (the template reads `chrome.activeEntity`, which the form controller already sets), the endpoint (a fixed route, `{basePath}/uploads`, which carries no state), and the advisory limits for the script (`accept` and `maxBytes`). The limits travel as two `Option`s of the UPLOAD field (`accept`, `maxBytes`), a convention like the version in the form address (D-75). A cleaner `FormView.Field.upload` record is written down as a proposal for a later contract change (D-96).

**One collaborator, `UploadFiles` (service package, an interface, `none()` by default).** It answers four questions for the services: the settings of an upload field (`accept`, `maxBytes`, or empty when the field is not a usable upload target), a fresh ticket, whether a posted address is acceptable for a field, and the cell for a stored address. `StorageUploadFiles` implements it from `UploadTargets`, `UploadTicketService` and the `StorageProvider`. Without it (no support, or tests that do not need it) an upload field is left out of the forms and shows as plain text, which is the 9a behaviour, so every existing test keeps its meaning.

**SPI.** `StorageProvider.parse(url)` (default empty) turns an address into a `StoredFile` without touching the disk; `owns(url)` (default `parse(url).isPresent()`) is the public question of the brief. `LocalStorageProvider` answers `owns` true only for an address that is exactly the one `url()` would make for a key of its own storage (the console route or the storage's public path) and whose file exists in its root. A field also demands its own storage and folder (`UploadFiles.accepts`), so an address of another storage or folder is refused.

**Posted values.** The form posts for an upload field: `key` (the address, hidden), and optionally `key-remove=true`. Update: remove marker → the field is cleared (a required one is an error); blank → keep; the stored value again → unchanged, no check; anything else must be accepted by `UploadFiles` or that field alone is refused with "The file is not one this console uploaded. Upload it again." Create: blank → empty. The service asks the request only for the editable names and `key-remove` of an upload field, so mass assignment stays impossible. No file is ever deleted (UPL-9).

**No-script path (option A).** The page has the record form (`id="record-form"`), then a section "Files" with one **separate** plain `multipart/form-data` form per upload field posting to `{base}/uploads` (hidden `_csrf`, `entity`, `field`, `ticket`, `record`, a labelled file input, an "Upload file" button), then the actions bar whose Save button belongs to the record form through `form="record-form"`. So the upload sections sit above the submit and no form is inside another. A redirect loses what was typed in the record form: the section says so. On success the endpoint stores the address, name and size in a console-only session attribute (`JPA_ADMIN_UPLOADS`, read once by the form render path, checked with `owns` before it is shown) next to the flash message; the form page shows the address as the field's value.

**Script.** `uploader.js` enhances each upload section: advisory type and size check, XHR upload with progress and the CSRF header, `Accept: application/json`, states idle / dragging / uploading / done / error, a hidden input in the record form for the address, preview, replace and remove buttons with `aria-label`, the record form's Save disabled while any upload runs, a custom event so `form-guard.js` counts a finished upload as a change. It stores nothing.

**Cells.** `UploadFiles.cell(url, label)`: an address that parses and whose file exists is an `IMAGE` cell (PNG, JPEG, GIF, WebP by the stored extension) or a `FILE` cell; an address that parses but whose file is missing is a plain cell "label: file not found"; anything that is not a stored address is escaped text, never a link or an image source. SVG is never previewed. `DetailView.images` gets the image fields.

**Preview.** `form/upload-states` fixtures (empty, existing image, existing file, error) and a stub `POST /admin-preview/uploads?state=` with the six states of `docs/FIXTURES.md` section 8. It stores nothing and lives only in `jpa-admin-ui-preview`.

## Steps

1 SPI: `parse`, `owns`; 2 `UploadFiles`, form render and tickets; 3 posted values; 4 copy and read, pending upload, endpoint addition; 5 templates, CSS, `uploader.js`, `form-guard.js`; 6 cells and detail; 7 preview; 8 sample app and real-HTTP tests; 9 mutation checks (a) to (i); 10 docs; 11 final checks. A module build after every step.

## Stop conditions

A dependency; a public API, annotation or property rename; a frozen record that cannot carry the form or the cells (none expected: see "Frozen records"); a conflict in the documents; a security rule that cannot be met.
