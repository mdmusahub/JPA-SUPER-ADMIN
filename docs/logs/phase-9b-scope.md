# Phase 9b scope: forms and UI for uploads

> **Delivered 2026-11-18** (D-95 to D-100). `docs/logs/phase-9b-report.md` says what was built, what was tested and what is left (the browser pass). The text below is the scope as written at the end of Phase 9a.

Written at the end of Phase 9a (2026-11-18). **Nothing in this file is implemented.** It is items 7 to 9 of `docs/logs/phase-9-scope.md`,
with what 9a delivered and what 9b can rely on. Documents only; 9b starts with its own plan and approval (AI-2).

## What 9a delivers for 9b

* `@AdminUpload` and `AdminField.upload()` / `isUpload()` (the spec: `storage`, `folder`, `accept`, `maxSize` as written);
  `UploadTargets` (the resolved fields, `find(entityUrlName, field)`, with the effective `maxBytes`, `accept`, `storage`, `folder`).
* `UploadTicketService` (a bean): `issue(entityUrlName, fieldName, maxBytes)`, 10 minutes. The form render path calls it for each upload
  field with the target's `maxBytes` and puts the result in the frozen `FormView.Field.uploadTicket`.
* `POST {path}/uploads` (parts `file`, `ticket`, `entity`, `field`, optional `record`), JSON for `Accept: application/json`, the redirect
  or the error page otherwise (option A). The CSRF token: the header `X-CSRF-TOKEN` (`PageChrome.Csrf.headerName`) or the field `_csrf`.
* `GET {path}/files/{storage}/{key}`, and the public path. `StorageProvider.url(StoredFile)` is the address that goes into the field.
* `EditableFields` leaves an upload field out (so it is not in the form today); `DetailQueryService#copyable` never copies it.
* `UploadMessages`, `UploadNames.display`, the JSON writer (`UploadAnswers`), `ListLinks.afterUpload` / `newForm` / `editForm`.

## Items

### 7. Forms: the upload input

*Plan:* `EditableFields` gives an upload field `InputType.UPLOAD` with a ticket (today it returns nothing for it); `FormQueryService`
puts the ticket in `FormView.Field.uploadTicket` and the stored address in `value` (it is not sensitive). **The posted value of an upload
field must be an address the console's own storage issued** (`StorageProvider` needs a way to recognise its own addresses: a `parse(url)` or
`owns(url)` method, or `StoredFile` from a URL; a SPI change to propose, with the public-path form as well as the console form); any other
text is refused for that field, a blank means "none" on a nullable field. Replace and remove keep the old file on disk (UPL-9).
`RecordWriteService` must read the upload name only through `EditableFields`, as every other name (mass assignment stays impossible).

*Expected tests:* a posted address that is not the storage's is refused (another storage, another host, `javascript:`, a traversal, a
file of another field); blank, replace and remove; a stale form; the copy of a record with an image carries no file (the 9a unit test
exists; add the form-level one); open-in-view on and off; `host-check` unaffected.

### 8. Cells and the detail page

*Plan:* `Cell.Type.IMAGE` and `FILE` already exist (`href` is the address, checked by `Hrefs`); the list and detail pages render them
(`DetailView.images` is filled), thumbnails are the original scaled by CSS (D-9). A missing file shows alt text. File names are escaped.

*Expected tests:* the formatter; an image cell and a file cell under the base path (or the public path); a missing file; escaped names.

### 9. UI task U6

*Plan:* `uploader.js` and the uploader fragment (idle, dragging, uploading with progress, done, error; type and size checked in the
browser; preview, replace, remove; **the form waits for uploads before saving**; `form-guard.js` treats a finished upload as a change);
the `UPLOAD` case of `fragments/field-input.html`; the **no-script form of option A** is a **separate** form (never inside the record form,
no nested form) with hidden fields `_csrf`, `ticket`, `entity`, `field` and `record`, a file input and a button, posting to the same
endpoint; its success redirect returns to the form page, so 9b must decide **how the new address reaches the field after the redirect**
(a console-only session attribute named `JPA_ADMIN_...`, read once by the form render path, is the natural answer; today the endpoint
puts only a flash message in the session). Preview fixtures `upload-states` and the stub endpoint with the six states of
`docs/FIXTURES.md` section 8.

*Expected tests:* HTML rules (labels, nothing inline, no nested form); the script is a small external file that stores nothing; both
themes and phone width are manual items.

## Things 9b must know

* **Over the host's limit, a plain form gets the server's own 413 page** (the limit is hit while the CSRF filter reads the body); the
  script flow gets the console's JSON message. Put the file input **last** in the form so the hidden fields come first, though this does not change the 413.
* Spring Boot's default multipart limits (1MB per file, 10MB per request) are below the console's 5MB default; the sample app raises them in its own `application.yml`.
* The console route sends the chain's no-store caching, so thumbnails are refetched on each page view; a private cache lifetime is a Phase 13 item.
* The upload endpoint answers 403 for a ticket that is missing, bad or expired; the uploader should say "open the form again" for 403
  with the expired text (`error-ticket-expired` fixture).
* `host-check` has no upload field and must keep starting with no new property.
* A file symlink test is skipped on a Windows account without the symlink privilege; run the module tests on a machine that has it before the release (Phase 14).
