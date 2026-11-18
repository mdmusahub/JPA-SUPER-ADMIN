# Phase 9 scope: file and image uploads

Written at the end of Phase 8 (2026-11-18); **revised at the start of Phase 9a (2026-11-18)** to split the phase in two. Phase 9 is delivered as two
runs on two branches of the same phase: **9a, the backend** (items 1 to 6), and **9b, forms and UI** (items 7 to 9). The tag `v0.0.9` is made when
both are done and connected (GIT-2). `docs/PLAN.md` (row "9a" and "9b") is the source of truth for the acceptance criteria; this file lists what each
item must do and be tested with. `docs/logs/phase-9b-scope.md` repeats the 9b part with what 9a delivered.

Sources: `docs/PLAN.md` sections 5, 6, 7 ("Uploads"), 8, 9 and the Phase 9 rows; `docs/RULES.md` UPL-1 to UPL-10; `docs/UI_PLAN.md` U6;
`docs/FIXTURES.md` sections 7 (`upload-states`) and 8; D-5, D-9, D-10, D-73, D-79, D-83; `docs/logs/phase-8-report.md`.

## Decisions taken when the phase was split (2026-11-18)

1. **Startup check, precisely.** Startup fails, with one report that lists every offending field (or storage) and the reason, when:
   a field names a storage that is not configured; a configured storage has a type other than `local`; or the directory of a storage that
   an upload field uses cannot be created or written to at startup (or lies inside the web root). **Defaults alone must pass**: the `default`
   storage exists without any property, with `./uploads`, `5MB` and `local`. The directory is checked and created only for a storage that at least one
   upload field names, so a host without upload fields gets no folder. The absolute resolved directory is logged once at INFO.
2. **Multipart rule.** The console never changes the host's multipart settings. The host's limits apply first and the console reads them (the
   `MultipartConfigElement` the host's container uses) to report a file over them with the limit and the property to raise. Names verified in the
   Boot 4.1.2 jar (`org.springframework.boot.servlet.autoconfigure.MultipartProperties`): `spring.servlet.multipart.enabled` (true),
   `.max-file-size` (**1MB**), `.max-request-size` (**10MB**), `.file-size-threshold` (0B), `.location`, `.resolve-lazily` (false),
   `.strict-servlet-compliance` (false). Because the default per-file limit (1MB) is below the console's default (5MB), a host that wants
   larger uploads raises `max-file-size` and `max-request-size` itself; a startup WARN says so when a field's limit is above the host's.
3. **No script (option A).** A plain `multipart/form-data` form with a hidden ticket field and a hidden `_csrf` field posts to the same endpoint
   `POST {path}/uploads`; the endpoint answers with a redirect to the form page (built by the console's link builder, never from a field) and a
   flash message, or shows a plain error page. 9b builds the form; 9a makes the endpoint accept it. The script flow sends `Accept: application/json`
   and gets the JSON `UploadResult`.
4. **Replace and remove keep the old file on disk in v0.1 (UPL-9).** Documented as a limitation. A cleanup of orphaned files is a Phase 14 item.
5. **`public-path` is off by default** (D-5, UPL-7). When set, files are also served without a session at that path, by a separate controller,
   with the same safe headers.
6. **Sample app.** One nullable upload field on one entity; the seed rows are unchanged; tests use a storage folder under `target`.

## Items

### 1. `@AdminUpload` and the properties (9a)

*Plan:* a public annotation on a `String` field (storage name, folder, allowed MIME types, max size); properties
`jpa.admin.storage.default.type` (`local` only), `.directory` (default `./uploads`, outside the web root), `.max-size` (default `5MB`),
`.public-path` (off by default) and `jpa.admin.upload.signing-key` (random per start). The scanner reads the annotation once (ARCH-1).
Document every option with its default (DOC-2) and add it to the annotation table of `docs/PROJECT_DOCS.md` B9.

*Expected tests:* defaults; a bad value stops startup with a message that names the property and never prints a value (SEC-6); the
annotation on the field or its getter.

### 2. Startup check (9a, UPL-8)

*Plan:* as decision 1 above.

*Expected tests:* one and several offending fields; an unknown type; an unwritable directory; defaults alone pass; no entity name hard-coded
(ARCH-12); the second host project keeps starting.

### 3. Storage SPI and the local provider (9a, UPL-3, UPL-4, UPL-10)

*Plan:* `StorageProvider` (`store`, `delete`, `open`, `url`); the local provider stores under a server-generated random name in
`folder/yyyy/MM/`; the final path is resolved and checked to lie inside the storage root, so traversal is impossible by construction; a symbolic
link is refused; the client's file name is never a path and is never logged.

*Expected tests:* a name with `..`, `/`, `\`, a NUL, an absolute path, a very long name and a name that looks like a device; the
resolved path always inside the root; two uploads never collide; a provider that is replaced by the host (`@ConditionalOnMissingBean`); a symbolic link escape.

### 4. Upload tickets (9a, UPL-2a, D-10)

*Plan:* `UploadTicketService` issues a signed token per upload field (about 10 minutes, bound to the entity name, the field name and the maximum
size, signed with `jpa.admin.upload.signing-key`). In 9a the service is exposed and tested directly; the form render path that issues it is 9b
(`FormView.Field.uploadTicket` is already in the frozen record).

*Expected tests:* expired, tampered, wrong entity, wrong field, wrong key and malformed are refused; the ticket is not a session; a restart with
a random key invalidates old tickets; the ticket never appears in a log.

### 5. The upload endpoint (9a, UPL-1, UPL-2, UPL-5)

*Plan:* `POST {path}/uploads` needs the session, the CSRF token (header for script, hidden field for the plain form) and a ticket. The server
checks the size against the field limit and the global ceiling, the real type by file signature against the allow-list (the declared type must
not contradict it), rejects SVG and HTML, empty files and polyglots; it answers the frozen `UploadResult` or, for the plain form, a redirect
with a flash message (option A). Nothing is stored unless every check passed. Errors say what is wrong and what to do (UI-6).

*Expected tests:* an executable named `.png`, HTML named `.jpg`, a polyglot, an empty file, a file over the field's limit and over the global
ceiling, a wrong `Content-Type` with a right body, the multipart limits of the host, CSRF 403 (header and hidden field), no ticket 403, nothing
stored when anything fails, the option A redirect and the plain error page.

### 6. Serving files (9a, UPL-6, UPL-7)

*Plan:* `GET {path}/files/**` for the local provider, session authentication only unless `public-path` is set; the detected content
type, `X-Content-Type-Options: nosniff`, `Content-Disposition: attachment` for anything that is not an image; the console's CSP stays.

*Expected tests:* anonymous refused, traversal in the path (`%2e%2e`, `..;/`, encoded slashes), a file outside the root, an HTML file stored
earlier is served as an attachment, headers on every answer, the `public-path` behaviour, the host's own routes untouched.

### 7. Forms: the upload input and the rule of D-73 (9b, carried from Phase 8)

*Plan:* `EditableFields` gets the upload field (in 9a it leaves the field out, so a plain text input can never store an arbitrary string): it
becomes `InputType.UPLOAD` with a ticket issued by the form render path. **The posted value of an upload field must be a URL
the console's own storage issued; any other text is refused for that field.** **`DetailQueryService#copyable` never copies an upload field**, so
two records never share one file. `FormQueryService.record` reads the stored URL as the field's value (it is not sensitive). The update service keeps
the stored file on a blank value only if the form says "keep"; replace and remove keep the old file on disk in v0.1 (UPL-9, documented).

*Expected tests:* the copy of a record with an image does not carry its file; a posted URL that is not the storage's is refused; a blank, a replace
and a remove; a stale form; mass assignment still impossible; open-in-view on and off.

### 8. Cells and the detail page (9b)

*Plan:* `Cell.Type.IMAGE` and `FILE` already exist in the frozen record (`href` is the URL); the list and detail pages render them,
`DetailView.images` is filled, thumbnails are the original scaled by CSS (D-9, no server-side resizing).

*Expected tests:* the formatter; an image cell and a file cell under the base path; a missing file shows alt text; escaped names.

### 9. UI task U6 (9b)

*Plan:* `uploader.js` and the uploader fragment: idle, dragging, uploading with progress, done, error; type and size checked in the
browser, preview, replace and remove; **the form waits for uploads before saving** (and `form-guard.js` treats a finished upload as a
change); the `form.html` case that prints nothing for `UPLOAD` today (`th:case="'UPLOAD'"` in `fragments/field-input.html`) is
filled; the **no-script form of option A** (a separate form, never inside the record form); preview fixtures `upload-states` (FIXTURES.md section 7) and the stub endpoint with
the six states of section 8 (`success-image`, `success-file`, `error-too-large`, `error-type`, `error-ticket-expired`, `slow`).

*Expected tests:* HTML rules (labels, nothing inline, no nested form), the script is a small external file that stores nothing, both themes and phone width are manual items.

## What Phase 8 leaves in place

* The frozen records: `FormView.Field.InputType.UPLOAD` with `uploadTicket` (present exactly for upload fields), `UploadResult`,
  `Cell.Type.IMAGE` and `FILE`, `DetailView.Image`.
* The editable-field rule in one class (`EditableFields`), the write service (`RecordWriteService`) with its "ask the request only for
  editable names" rule, the typed read (`FormQueryService`) and `FormPage.reshown`, the copy seed and its single rule
  (`DetailQueryService#copyable`, with the Phase 9 note), `ValueConverter`, `FieldMessages`, `SaveMessages`.
* The CSRF pattern for forms, `ConsoleFlash`, `ConsoleRedirect`, `ListLinks` (the one writer of links), `form.html`,
  `fragments/field-input.html` (with an empty `UPLOAD` case), `form-guard.js`, the `details` question, `HtmlRules`.
* The provided `jakarta.validation-api` dependency and the validation mapping.

## What Phase 8 does not leave (and what 9a adds)

* Any storage, ticket, endpoint, file serving or annotation, and any `multipart` handling in the console. **9a adds all of them.**
* A way to carry a file in `POST {path}/{entity}`: the form is `application/x-www-form-urlencoded`; the file travels through the
  upload endpoint first and the form posts the returned URL (9b).
* A decision on a form without script: **decided above (option A)**.

## Cross-cutting facts to carry into Phase 9

* A posted value of an upload field is untrusted text: check it against the storage, do not "just store the string" (9b).
* The client's file name is untrusted and is never logged, stored as a path or echoed unescaped (SEC-9, UPL-3).
* A typed value never reaches the console's own log (D-79); a file name or a content type is not a sensitive value, but a ticket is.
* Hibernate's own logging (bind and extract) is the host's; document it again where uploads are described.
* Every link the page prints is built by the backend (D-74): the file URL comes from the provider, under the base path (or the configured public path),
  and passes the link rule of the records (`Hrefs`).
* The library modules must not name the demo entities (ARCH-12); the sample app gets an entity with an upload field for the tests.
