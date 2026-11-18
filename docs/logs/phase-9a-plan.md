# Phase 9a plan: uploads, backend only

Branch `phase/9a-uploads-backend` (from `main`, tag `v0.0.8`). One session on both tracks (the recorded exception to AI-11, as Phases 2 to 8).
Phase 9b (the form input, the uploader script, cells, detail page, previews, replace and remove in the page) is **not** part of this run. Nothing is committed.

Baseline before any change (`mvn -B clean verify`, 4 min): core 2625, autoconfigure 449, ui-preview 1714, sample-app 743 tests, all green.

## What is verified first (CODE-8)

* Boot 4.1.2 multipart: `org.springframework.boot.servlet.autoconfigure.MultipartProperties` (module `spring-boot-servlet`), prefix
  `spring.servlet.multipart`: `enabled` (true), `max-file-size` (1MB), `max-request-size` (10MB), `file-size-threshold` (0B), `location`,
  `resolve-lazily` (false), `strict-servlet-compliance` (false). `MultipartAutoConfiguration` defines the `MultipartConfigElement` and a
  `StandardServletMultipartResolver`. **The default per-file limit is 1MB, smaller than the console's 5MB default.** (read from the jar with `javap`)
* Spring MVC 7 multipart classes (`MultipartFile`, `MaxUploadSizeExceededException`, `MultipartException`) and where the exception is thrown (eager
  resolution fails before a handler is chosen, so only a `HandlerExceptionResolver` sees it): checked with `javap` before use.
* Spring Security 7.1.2: that `CsrfFilter` accepts the header `X-CSRF-TOKEN` and the parameter `_csrf` and, with real Tomcat, finds a hidden field
  of a `multipart/form-data` body: checked on real HTTP (the existing `ConsoleRealHttpTest` pattern), not assumed.

Packages used: `jakarta.servlet.*` (6.1), `org.springframework.web.multipart.*`, `org.springframework.web.bind.annotation.*`, `java.nio.file`,
`java.security.{SecureRandom,MessageDigest}`, `javax.crypto.Mac`, `org.springframework.util.unit.DataSize` (spring-core, only for properties parsing in the
autoconfigure module). No new dependency anywhere.

## Design (decisions to record, D-85 onward)

**Annotation.** `@AdminUpload` (public, `annotation` package, field or getter): `storage` ("default"), `folder` (""), `accept` (default `image/png, image/jpeg,
image/gif, image/webp, application/pdf`), `maxSize` (text such as `2MB`, blank means the storage's limit). The name `accept` is the one RULES UPL-5 already uses.
Read once by the scanner (ARCH-1) into `AdminField.upload` (an `UploadSpec`, `null` for any other field). A field with `@AdminUpload` that is not a
`String` is a startup problem. Until 9b the generic form does not edit an upload field (`EditableFields` leaves it out), so a plain text input can never store an
arbitrary string in it.

**Properties** (`JpaAdminProperties`): `jpa.admin.storage.<name>.{type,directory,max-size,public-path}` as a map (the `default` storage always exists with its
defaults), `jpa.admin.upload.signing-key`. Plain validation as in Phase 1: one report, every message names the property and never a value.
Max size has a fixed internal ceiling of 100MB (the "global ceiling" of UPL-2): a property may lower it, never raise it (the rule of D-12); a field may lower its
storage's limit, never raise it.

**Startup check** (`UploadStartupCheck`, pure function in core, run by an eager bean): lists every offending field with the reason, one report, startup fails.
Fails when: a field names a storage that is not configured; a configured storage has a type other than `local`; an `accept` entry has no known file
signature (so SVG and HTML cannot be stored in v0.1, UPL-5); the folder is not a safe relative path; `maxSize` is not a size; the field is not a `String`;
a storage that a field uses has a directory that cannot be created or written, or lies inside the web root (the servlet context's real path, `src/main/resources`,
`src/main/webapp`, `target/classes`). The directory is checked for storages that a field uses, so a host without upload fields gets no folder created. Defaults alone pass.
The absolute resolved directory is logged once at INFO. A WARN says when a field's limit is above the host's multipart limit, and when no signing key is set
(only when a field exists).

**Storage SPI** (`storage` package, public): `StorageProvider` (`supports`, `verify`, `store`, `delete`, `open`, `url`), `StoredFile(storage, key)`, `StoredContent`.
`LocalStorageProvider`: random 128-bit name from `SecureRandom` as 32 hex characters plus an extension taken from the **detected** type, under `folder/yyyy/MM/`;
the client's name is never a path and never logged; every path is resolved, normalised and must start with the real root; a symbolic link anywhere below the root is refused
(`NOFOLLOW_LINKS`, a walk of the components); `CREATE_NEW` so two uploads never collide; a partial file is deleted if the copy fails. Replaceable with `@ConditionalOnMissingBean`.

**Tickets** (`UploadTicketService`): `v1.<payload>.<hmac>`; HMAC-SHA256 over the payload `entity|field|maxBytes|expiry`; valid 10 minutes with an injected `Clock`;
verified with `MessageDigest.isEqual` and then compared with the expected entity and field. Result `VALID`, `EXPIRED` or `INVALID`. Key: `jpa.admin.upload.signing-key`
(32 characters at least) or 32 random bytes per start. A ticket carries no identity, so it is not a session; it is never logged.

**Endpoint** (`UploadController`, `POST {path}/uploads`, `multipart/form-data`): parts `file`, `ticket`, `entity`, `field`, optional `record`; session and CSRF come
from the chain (`X-CSRF-TOKEN` header or the `_csrf` hidden field). Order: ticket, field, size, signature, declared type, polyglot scan, then store. `UploadService`
(no web types) returns `Stored` or `Refused(status, message)`. **Answer:** JSON `UploadResult` when the request says `Accept: application/json`; otherwise the plain
form flow of option A: success redirects (303) to the form page built by `ListLinks` (`new`, or `{id}/edit` when `record` is a valid id) with a flash message; failure shows
the console error page with the message (status of the refusal). Host multipart limits: read from the host's `MultipartConfigElement` bean; a file over them is reported with
the limit; the console never changes them.

**File checks.** A fixed signature table: PNG (8-byte signature plus `IHDR`), JPEG (`FF D8 FF`), GIF (`GIF87a` / `GIF89a`), WebP (`RIFF....WEBP`), PDF (`%PDF-` at byte 0).
The declared `Content-Type` may be absent or `application/octet-stream`; any other value must equal the detected type. A polyglot scan over the whole file refuses
`<script`, `<html`, `<?php`, `<iframe`, `<svg`, `<!doctype html` (case-insensitive). Empty files refused. Size is counted while reading, not trusted from the header.

**Serving** (`FileController`, `GET {path}/files/**`): `{storage}/{folder...}/{yyyy}/{MM}/{32 hex}.{ext}`; every segment matched against a strict pattern (no dot except the
single one in the name), anything else is the console's 404. Headers: detected content type (from the file's first bytes, else `application/octet-stream`),
`X-Content-Type-Options: nosniff`, `Content-Disposition: attachment` unless the detected type is PNG, JPEG, GIF or WebP, `Content-Length`. `public-path`: when set, a second
controller outside the console package serves the same files at `{public-path}/**` without a session, with the same headers and `Cache-Control: public, max-age=3600`.

**Sample app.** One nullable `@AdminUpload String` on one entity; seed rows unchanged; test-only storage folder under `target`.

## Files (planned)

Core (`jpa-admin-core`): `annotation/AdminUpload`; `metadata/UploadSpec`, `AdminField` (+`upload`), `EntityScanner`; `storage/{StorageProvider, StoredFile, StoredContent,
StorageSettings, StoragePaths, LocalStorageProvider, FileSignatures, UploadLimits, UploadTicketService, UploadService, UploadOutcome, UploadMessages, UploadStartupCheck}`;
`web/{UploadController, FileController, PublicFileController (own package `io.github.mdmusahub.jpaadmin.publicfiles`)}`; `service/EditableFields` (leaves an upload field out);
`web/ConsoleExceptionResolver` (multipart errors on the upload route).
Autoconfigure: `JpaAdminProperties`, `JpaAdminAutoConfiguration` (beans), `JpaAdminUploadCheck`.
Sample app: one entity field, test storage properties, tests.
Docs: `PLAN`, `DECISIONS`, `CHANGELOG`, `PROJECT_DOCS`, `phase-9-scope`, `phase-9b-scope`, logs.

## Steps

0 documents only; 1 annotation, properties, metadata; 2 startup check; 3 storage SPI and local provider; 4 tickets; 5 endpoint; 6 serving; 7 sample app and end to end;
8 mutation checks (a) to (j); 9 docs; 10 final checks. A module build after every step.

## Stop conditions (ask only if)

A dependency outside the list is needed (none expected); a public name must change beyond the list; a frozen record cannot carry what the upload needs (`UploadResult` and `Cell`
are enough for 9a; `FormView.Field.uploadTicket` is 9b); the documents conflict; a security rule cannot be met.
