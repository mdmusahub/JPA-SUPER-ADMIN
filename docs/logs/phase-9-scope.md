# Phase 9 scope: file and image uploads

Written at the end of Phase 8 (2026-11-18). **Nothing in this file is implemented.** It lists what `docs/PLAN.md` gives Phase 9
(row 9: "`@AdminUpload`, storage SPI, local provider, upload endpoint, uploader UI, startup check"; done when "Upload, preview,
replace, remove work. Dangerous files rejected. Startup fails on missing storage"), what Phase 8 hands over, what each item must be
tested with, and what Phase 8 leaves in place and what it does not. Phase 9 starts with its own plan and approval (AI-2).

Sources: `docs/PLAN.md` sections 5, 6, 7 ("Uploads"), 8, 9 and the Phase 9 row; `docs/RULES.md` UPL-1 to UPL-10; `docs/UI_PLAN.md` U6;
`docs/FIXTURES.md` sections 7 (`upload-states`) and 8; D-5, D-9, D-10, D-73, D-79, D-83; `docs/logs/phase-8-report.md`.

## Items

### 1. `@AdminUpload` and the properties

*Plan:* a public annotation on a `String` field (storage name, folder, allowed MIME types, max size); properties
`jpa.admin.storage.default.type` (`local` only), `.directory` (default `./uploads`, outside the web root), `.max-size` (default `5MB`),
`.public-path` (off by default) and `jpa.admin.upload.signing-key` (random per start). The scanner reads the annotation once (ARCH-1).
Document every option with its default (DOC-2) and add it to the annotation table of `docs/PROJECT_DOCS.md` B9.

*Expected tests:* defaults; a bad value stops startup with a message that names the property and never prints a value (SEC-6); the
annotation on the field or its getter; `FormView.Field.help` or a field type for it is not needed (the record has `InputType.UPLOAD`).

### 2. Storage SPI and the local provider (UPL-3, UPL-4, UPL-10)

*Plan:* `StorageProvider` (`store`, `delete`, `open`, `url`); the local provider stores under a server-generated random name in
`folder/yyyy/MM/`; the final path is resolved and checked to lie inside the storage root, so traversal is impossible by construction;
the client's file name is never a path and is never logged.

*Expected tests:* a name with `..`, `/`, `\`, a NUL, an absolute path, a very long name and a name that looks like a device; the
resolved path always inside the root; two uploads never collide; a provider that is replaced by the host (`@ConditionalOnMissingBean`).

### 3. Upload tickets (UPL-2a, D-10)

*Plan:* `UploadTicketService` issues a signed token per upload field when a form page is rendered (about 10 minutes, bound to the
entity and field, carrying the maximum size and an expiry, signed with `jpa.admin.upload.signing-key`). `FormView.Field.uploadTicket`
is already in the frozen record and its constructor already insists on it for `UPLOAD` fields only.

*Expected tests:* expired, tampered, wrong entity, wrong field and wrong key are refused; the ticket is not a session; a restart with
a random key invalidates old tickets; the ticket never appears in a log.

### 4. The upload endpoint (UPL-1, UPL-2, UPL-5)

*Plan:* `POST {path}/uploads` needs the session, the CSRF token (in a header for script) and a valid ticket. The server checks the type
by an allow-list **and** by content sniffing, the size, and rejects SVG and HTML unless the field explicitly allows them; it answers
the frozen `UploadResult` (`url`, `name`, `size`, `error`). Errors say what is wrong and what to do (UI-6).

*Expected tests:* a disguised file (an executable named `.png`, HTML named `.jpg`, a polyglot), an empty file, a file over the field's
limit and over the global ceiling, a wrong `Content-Type` header with a right body, the multipart limits of the host, CSRF 403, no
ticket 403, and that nothing is stored when anything fails.

### 5. Serving files (UPL-6, UPL-7)

*Plan:* `GET {path}/files/**` for the local provider, session authentication only unless `public-path` is set; the detected content
type, `X-Content-Type-Options: nosniff`, `Content-Disposition: attachment` for anything that is not an image; the console's CSP stays.

*Expected tests:* anonymous 401/redirect, traversal in the path (`%2e%2e`, `..;/`), a file outside the root, an HTML file stored
earlier is served as an attachment, headers on every answer, the host's own routes untouched (host isolation tests stay green).

### 6. Startup check (UPL-8)

*Plan:* a field that names a storage that is not configured stops startup, with a message that lists the fields.

*Expected tests:* one and several offending fields; no entity name hard-coded (ARCH-12); the second host project keeps starting.

### 7. Forms: the upload input and the rule of D-73 (carried from Phase 8)

*Plan:* `EditableFields` gets the upload field (today a `String` field with `@AdminUpload` would be a plain text input, because the
rule does not know the annotation): it becomes `InputType.UPLOAD` with a ticket. **The posted value of an upload field must be a URL
the console's own storage issued; any other text is refused for that field** (otherwise a form could store an arbitrary path or
address). **`DetailQueryService#copyable` never copies an upload field**, so two records never share one file (the rule Phase 8 left
in place and could not test before the annotation exists). `FormQueryService.record` reads the stored URL as the field's value
(it is not sensitive). The update service keeps the stored file on a blank value only if the form says "keep"; replace and remove
keep the old file on disk in v0.1 (UPL-9, document it).

*Expected tests:* the copy of a record with an image does not carry its file (the test Phase 8 listed as waiting for this phase);
a posted URL that is not the storage's is refused; a blank, a replace and a remove; a stale form; mass assignment still impossible;
open-in-view on and off.

### 8. Cells and the detail page

*Plan:* `Cell.Type.IMAGE` and `FILE` already exist in the frozen record (`href` is the URL); the list and detail pages render them,
`DetailView.images` is filled, thumbnails are the original scaled by CSS (D-9, no server-side resizing).

*Expected tests:* the formatter; an image cell and a file cell under the base path; a missing file shows alt text; escaped names.

### 9. UI task U6

*Plan:* `uploader.js` and the uploader fragment: idle, dragging, uploading with progress, done, error; type and size checked in the
browser, preview, replace and remove; **the form waits for uploads before saving** (and `form-guard.js` treats a finished upload as a
change); the `form.html` case that prints nothing for `UPLOAD` today (`th:case="'UPLOAD'"` in `fragments/field-input.html`) is
filled; preview fixtures `upload-states` (FIXTURES.md section 7) and the stub endpoint with the six states of section 8
(`success-image`, `success-file`, `error-too-large`, `error-type`, `error-ticket-expired`, `slow`).

*Expected tests:* HTML rules (labels, nothing inline, no nested form), the script is a small external file that stores nothing, it
works without script as far as a form can (a plain file input falls back is **not** possible with a ticket: decide and document),
both themes and phone width are manual items.

## What Phase 8 leaves in place

* The frozen records: `FormView.Field.InputType.UPLOAD` with `uploadTicket` (present exactly for upload fields), `UploadResult`,
  `Cell.Type.IMAGE` and `FILE`, `DetailView.Image`.
* The editable-field rule in one class (`EditableFields`), the write service (`RecordWriteService`) with its "ask the request only for
  editable names" rule, the typed read (`FormQueryService`) and `FormPage.reshown`, the copy seed and its single rule
  (`DetailQueryService#copyable`, with the Phase 9 note), `ValueConverter`, `FieldMessages`, `SaveMessages`.
* The CSRF pattern for forms, `ConsoleFlash`, `ConsoleRedirect`, `ListLinks` (the one writer of links), `form.html`,
  `fragments/field-input.html` (with an empty `UPLOAD` case), `form-guard.js`, the `details` question, `HtmlRules`.
* The provided `jakarta.validation-api` dependency and the validation mapping (an upload field's constraint, for example `@Size`,
  is mapped like any other).

## What Phase 8 does not leave

* Any storage, ticket, endpoint, file serving or annotation. No `multipart` handling exists in the console.
* A way to carry a file in `POST {path}/{entity}`: the form is `application/x-www-form-urlencoded`; the file travels through the
  upload endpoint first and the form posts the returned URL.
* A decision on a form without script for an upload field (a ticketed upload needs script). Record it as a decision.

## Cross-cutting facts to carry into Phase 9

* A posted value of an upload field is untrusted text: check it against the storage, do not "just store the string".
* The client's file name is untrusted and is never logged, stored as a path or echoed unescaped (SEC-9, UPL-3).
* A typed value never reaches the console's own log (D-79); a file name or a content type is not a sensitive value, but a ticket is.
* Hibernate's own logging (bind and extract) is the host's; document it again where uploads are described.
* Every link the page prints is built by the backend (D-74): the file URL comes from the provider, under the base path, and passes
  the link rule of the records (`Hrefs`).
* The library modules must not name the demo entities (ARCH-12); the sample app gets an entity with an upload field for the tests.
