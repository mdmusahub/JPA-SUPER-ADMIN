# Phase 9a report: uploads, backend

Branch `phase/9a-uploads-backend` (from `main`, tag `v0.0.8`). One session on both tracks (the recorded exception to AI-11, D-85). **Nothing is committed.**
Phase 9b (the form input, the uploader script, cells, the detail page, previews) was not started. Log: `docs/logs/phase-9a-change-log.md` (66 entries). Plan: `docs/logs/phase-9a-plan.md`.

## 1. Files created, modified, deleted

`git status --short` (20 modified, 27 new paths with each new folder counted once, none deleted; `git diff --stat`: 20 files changed, 693 insertions, 86 deletions, tracked files only):

**Modified (20):** `CHANGELOG.md`, `docs/DECISIONS.md`, `docs/PLAN.md`, `docs/PROJECT_DOCS.md`, `docs/logs/phase-9-scope.md`, `JpaAdminAutoConfiguration.java`, `JpaAdminProperties.java`, `AdminField.java`, `EntityScanner.java`, `DetailQueryService.java`, `EditableFields.java`, `ConsoleExceptionResolver.java`, `ListLinks.java`, `jpa-admin-core/src/test/resources/META-INF/persistence.xml`, `sample-app/pom.xml`, `Category.java`, `sample-app/src/main/resources/application.yml`, `AbstractFilterPagesTest.java`, `AbstractSortProofTest.java`, `sample-app/src/test/resources/application.properties`.

**New, main code:** `annotation/AdminUpload`, `metadata/UploadSpec`, `storage/{package-info, UploadLimits, StorageSettings, StoragePaths, StoredFile, StoredContent, StorageProvider, FileSignatures, UploadTargets, UploadStartupCheck, LocalStorageProvider, UploadTicketService, ContentScan, UploadNames, UploadMessages, UploadService}`, `web/{UploadAnswers, UploadController, FileResponder, FileController, PublicFileHandler}` (all in `jpa-admin-core`); `JpaAdminUploadCheck`, `PublicPathConfigured` (in `jpa-admin-autoconfigure`).

**New, tests:** core `UploadScannerTest`, `UploadFieldsInFormsTest`, `storage/{UploadLimitsTest, StoragePathsTest, UploadStartupCheckTest, LocalStorageProviderTest, UploadTicketServiceTest, UploadServiceTest, ContentScanTest, FileSignaturesTest, UploadNamesTest}`, `storage/UploadTestFiles` (helper), `web/{UploadControllerTest, FileServingTest}`, `testsupport/UploadTestPersistence`, `testsupport/uploadmodel/{Dossier, Uploads}`; autoconfigure `JpaAdminUploadPropertiesTest`, `UploadStartupTest`, `uploadmodel/{good/Portrait, bad/Parchment}`; sample-app `AbstractUploadOverHttpTest`, `UploadOverHttpOpenInViewDisabledTest`, `UploadOverHttpOpenInViewEnabledTest`.

**New, docs:** `docs/logs/phase-9a-plan.md`, `phase-9a-change-log.md`, `phase-9a-report.md`, `phase-9b-scope.md`.

No template, stylesheet, script or `web.view` record was touched. No dependency was added. Throwaway helpers (a PNG and ticket maker, two PowerShell scripts, the mutation backups) are in the scratchpad, never in the repository.

## 2. Tests

| Module | Before | After | Change |
|---|---|---|---|
| `jpa-admin-core` | 2625 | 3101 (1 skipped) | +476 |
| `jpa-admin-autoconfigure` | 449 | 498 | +49 |
| `jpa-admin-ui-preview` | 1714 | 1714 | 0 |
| `sample-app` | 743 | 791 | +48 |
| **Reactor total** | **5531** | **6104** | **+573** |
| `host-check` (separate project) | n/a | 21 | not changed |

* `mvn -B clean verify`: BUILD SUCCESS (twice; the second run after the last change, the surefire property in `sample-app/pom.xml`).
* `mvn -B -DskipTests install`: BUILD SUCCESS. `mvn -B -f host-check/pom.xml verify`: BUILD SUCCESS, 21 tests (it has no upload field, so the check touches no disk and no property was needed).
* The one skipped test is the file symbolic link case (`aFileThatIsALinkToSomethingOutside...`): this Windows account may not create file symlinks. The folder link cases ran, with a junction (`mklink /J`).
* Existing tests changed: `AbstractSortProofTest`, `AbstractFilterPagesTest` (the sample entity `Category` gained a text column `image`, so the expected column list, the searched-columns sentence and the filter controls of `category` gained it; the exactness of every assertion is the same).
* Manual check of the real jar (PowerShell, `curl.exe`, a cookie jar, a ticket made by the real `UploadTicketService` because the jar has no route that issues one before 9b): a real 371 byte PNG uploaded (200, address `/admin/files/default/categories/2026/11/<32 hex>.png`); served to the session (200, `image/png`, `inline`, `nosniff`, sandbox CSP, SHA-256 equal); anonymous GET 302 to sign-in; a program named `.png` refused (415, still one file); no CSRF token 403 (still one file); plain form with the hidden `_csrf` 303 to `/admin/category/2/edit`. Processes stopped by PID; ports 8080, 8081, 8090 have no listener; no demo name in the library modules except the 7 allow-listed `@Order` lines.

## 3. Mutation checks (one at a time; each restored byte-identical, MD5 compared, then green)

| # | Break | Result |
|---|---|---|
| a | `UploadService` passes the client file name to the provider instead of the detected extension | **Caught**: 19 failures + 3 errors of 96. (The provider's own extension check is a second line that stops the name, so every upload becomes a 500 and the hostile-name test fails.) |
| b | `LocalStorageProvider.ensureDirectories` without the real-path root check | **Caught**: 2 failures (folder link and month folder link, as junctions) of 64 |
| c | `UploadTicketService.verify` no longer compares the field | **Caught**: 4 failures of 114 |
| d | `UploadTicketService.verify` without the expiry check | **Caught**: 3 failures of 114 |
| e | `UploadService` trusts the declared type (no signature check) | **Caught**: 13 failures of 96 |
| f | `FileResponder` serves everything `inline` | **Caught**: 10 failures of 73 |
| g | `FileResponder.baseHeaders` without `nosniff` | **Caught**: 65 failures of 73. Caught by tests that call the controller and the handler without the security chain, because the chain writes `nosniff` on every console response and would hide this mutation |
| h | Traversal allowed in the serving path | h1, the parser alone: caught only by the parser's own test (1 of 137), the other layers still answer 404 (defence in depth). h2, parser + `StoragePaths.keyParts` + the provider's root check: **caught**, 17 failures of 137 |
| i | `csrf.ignoringRequestMatchers({base}/uploads)` | **Caught**: 1 failure (real Tomcat, header and hidden field) |
| j | `UploadService` stores before the scan | **Caught**: 28 failures of 96 (all assert that nothing is stored) |

All seven mutated files restored with identical MD5 (`ALL_MD5_IDENTICAL`).

## 4. Decisions and deviations (D-85 to D-94 in `docs/DECISIONS.md`)

* **Names (D-85).** `@AdminUpload(storage, folder, accept, maxSize)`: `accept` because UPL-5 already says "the field's `accept` list"; the size is text because an annotation cannot hold a `DataSize`. No existing name changed. Packages used: `jakarta.servlet` (6.1), `org.springframework.web.multipart` (`MultipartFile`, `MultipartException`, `MaxUploadSizeExceededException`), `org.springframework.web.servlet.handler.SimpleUrlHandlerMapping`, `org.springframework.web.util.pattern.PathPatternParser`, `org.springframework.web.HttpRequestHandler`, `org.springframework.boot.autoconfigure.condition.SpringBootCondition`, `org.springframework.boot.context.properties.bind.Binder`, `org.springframework.util.unit.DataSize`, and the JDK (`java.nio.file`, `java.security`, `javax.crypto.Mac`, `javax.imageio` in tests only).
* **Multipart names, verified in the Boot 4.1.2 jar with `javap` (D-92).** `org.springframework.boot.servlet.autoconfigure.MultipartProperties`, prefix `spring.servlet.multipart`: `enabled` true, `max-file-size` **1MB**, `max-request-size` **10MB**, `file-size-threshold` 0B, `location`, `resolve-lazily` false, `strict-servlet-compliance` false; `MultipartAutoConfiguration` defines a `MultipartConfigElement` and a `StandardServletMultipartResolver`. The console never changes them (tested), reads the host's `MultipartConfigElement` for a startup WARN, and reports a file over them with the two property names (413). Spring MVC 7: `MaxUploadSizeExceededException` is a `MultipartException` and an `ErrorResponse`, thrown before a handler is chosen, so the console's exception resolver answers it for the upload route (and the controller's `@ExceptionHandler` for lazy resolution).
* **Magic-byte table (D-91).** PNG (8 byte signature and `IHDR`), JPEG (`FF D8 FF`), GIF (`GIF87a`/`GIF89a`), WebP (`RIFF`, 4 bytes, `WEBP`), PDF (`%PDF-` at byte 0). A type with no row stops startup, so **SVG and HTML are never accepted, even if listed** (UPL-5 cannot be met otherwise; documented). The declared `Content-Type` may be missing or `application/octet-stream`, else it must equal the detected type. A markup scan of the whole file (`<script`, `<html`, `<?php`, `<iframe`, `<svg`, `<!doctype html`, any case, across read boundaries) refuses polyglots; the price is that a PDF with such a tag in plain text is refused.
* **Symbolic links (D-88).** Refused anywhere below the root for store, open and delete (symlink check and real-path check, so Windows junctions too). The root itself may be a link (the operator's choice).
* **Startup check (D-87).** As the brief, plus: folders are created and probed only for storages that a field uses (a host without upload fields gets no folder), a storage folder inside the web root is refused, and an unsupported storage type fails even when no field uses it. The unknown-type test is the startup check's, not `JpaAdminProperties`', so the report can list the fields.
* **Ceiling (D-86).** 100 MB, a property may lower it and never raise it; a field may lower its storage's limit, never raise it (WARN). Signing key at least 32 characters.
* **Option A (D-90).** A script sends `Accept: application/json` and gets JSON; anything else gets a 303 back to the form page (built by `ListLinks`, `{id}/edit` or `new`) or the console's error page. The CSRF hidden field was verified over real Tomcat (it parses the multipart body before Spring Security's CSRF filter).
* **Public path (D-93).** A `SimpleUrlHandlerMapping` registered only when a storage sets `public-path`, after the host's controllers; the host's own security governs the path.
* **Two small things brought forward from 9b, as safety:** an upload field is left out of the generic forms (otherwise it would be a plain text input), and a duplicate never copies it (the D-73 rule). An entity whose address is exactly `uploads` is now unsupported (it would collide with the endpoint).
* **Sample app.** `Category.image` (nullable, images only, 2MB, folder `categories`); seed rows unchanged; `application.yml` raises the host's multipart limits to 5MB/6MB; a surefire system property keeps the test storage under `target` for every test JVM (two older tests start the app without the test properties file).

**What a host application should know.** (1) *New write path:* the console writes files under the configured folder (default `./uploads` relative to the working directory, created at startup only if an upload field exists; the absolute path is logged once). (2) *Disk use:* up to 5MB per file by default (100MB at most), files are never deleted by the console in v0.1 (replace and remove keep the old file, UPL-9; cleanup is a Phase 14 item); back up the folder with the database. (3) *Startup check:* it can stop the application, with a list; defaults pass. (4) *Boot's multipart limits* (1MB per file, 10MB per request) apply first and are the host's to raise. (5) *Orphan files* as in (2). (6) *Signing key:* set `jpa.admin.upload.signing-key` (32+ characters) when running several instances, or open forms fail after a restart. (7) *Public path* files are only as protected as the host protects that path. (8) Hibernate's own bind logging would show the stored address.

## 5. Not done, to check, proposals

* **Finding for a decision (not caused by uploads, not changed):** a raw address with dot segments that starts under the console path and is sent unnormalised (`curl --path-as-is http://localhost:8080/admin/category/../../etc/passwd`, also `/admin/../x`) makes Tomcat normalise it out of the console path; Spring Security's chain does not run, Spring MVC's interceptor still sees `/admin/...`, and Phase 2's fail-closed `ConsoleChainGuard` answers **500** and logs one ERROR line. Nothing is served and the next request is unaffected. Options: (A) leave it (safe, noisy); (B) **recommended:** let `ConsoleChainGuard` ignore a request whose normalised path is outside the console path, with a test. It is a change to a Phase 2 security class, so it needs your approval. Recorded in D-94 (7).
* **Plain form over the host's limit:** gets Tomcat's 413 and the host's error page (the limit is hit while the CSRF filter reads the body). Tested and documented; the script flow gets the console's message.
* **Not done on purpose:** no page shows an uploader; no ticket is issued by a page (9b); `FormView.Field.uploadTicket`, `Cell.Type.IMAGE`/`FILE` and `DetailView.images` are unused until 9b; no cleanup of orphan files; no S3 provider.
* **Check manually:** run the module tests on a machine that can create file symlinks (the skipped test); read D-86 (100MB ceiling, 32 character key minimum), D-90 (the `Accept` rule), D-91 (SVG and HTML never accepted) and the `accept` name, which are my choices within the brief; running the sample jar from the repository root now creates `./uploads` there (git-ignored).
* **Contract change:** none. **SPI proposal for 9b:** `StorageProvider` needs a way to recognise its own addresses (`owns(url)` or `parse(url)`) so a posted value can be checked; written down in `docs/logs/phase-9b-scope.md` (documents only), which also lists items 7 to 9, what 9a delivers, and the things 9b must know.

## 6. Suggested commits (PowerShell-safe, by path, never `git add .`)

Order matters (each builds on the one before).

```powershell
# 1. core: upload metadata, storage SPI, local provider, tickets, upload decision
git add -- jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/annotation/AdminUpload.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/metadata/UploadSpec.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/metadata/AdminField.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/metadata/EntityScanner.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/DetailQueryService.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/EditableFields.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/storage jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/storage jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/metadata/UploadScannerTest.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/service/UploadFieldsInFormsTest.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/testsupport/UploadTestPersistence.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/testsupport/uploadmodel jpa-admin-core/src/test/resources/META-INF/persistence.xml
git commit -m "feat(core): add @AdminUpload, storage SPI, local provider and upload tickets" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 2. core: upload endpoint and file serving
git add -- jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/UploadAnswers.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/UploadController.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/FileResponder.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/FileController.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/PublicFileHandler.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ConsoleExceptionResolver.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ListLinks.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/web/UploadControllerTest.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/web/FileServingTest.java
git commit -m "feat(core): add the upload endpoint and safe file serving" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 3. autoconfigure: properties, startup check, beans
git add -- jpa-admin-autoconfigure/src/main/java/io/github/mdmusahub/jpaadmin/autoconfigure/JpaAdminProperties.java jpa-admin-autoconfigure/src/main/java/io/github/mdmusahub/jpaadmin/autoconfigure/JpaAdminAutoConfiguration.java jpa-admin-autoconfigure/src/main/java/io/github/mdmusahub/jpaadmin/autoconfigure/JpaAdminUploadCheck.java jpa-admin-autoconfigure/src/main/java/io/github/mdmusahub/jpaadmin/autoconfigure/PublicPathConfigured.java jpa-admin-autoconfigure/src/test/java/io/github/mdmusahub/jpaadmin/autoconfigure/JpaAdminUploadPropertiesTest.java jpa-admin-autoconfigure/src/test/java/io/github/mdmusahub/jpaadmin/autoconfigure/UploadStartupTest.java jpa-admin-autoconfigure/src/test/java/io/github/mdmusahub/jpaadmin/autoconfigure/uploadmodel
git commit -m "feat(autoconfigure): add upload properties, the startup check and the upload beans" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 4. sample app: one upload field and the end-to-end tests
git add -- sample-app
git commit -m "feat(sample-app): add Category.image and upload tests over real HTTP" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 5. docs
git add -- CHANGELOG.md docs/PLAN.md docs/DECISIONS.md docs/PROJECT_DOCS.md docs/logs/phase-9-scope.md docs/logs/phase-9a-plan.md docs/logs/phase-9a-change-log.md docs/logs/phase-9a-report.md docs/logs/phase-9b-scope.md
git commit -m "docs: record Phase 9a (uploads backend), split Phase 9, scope Phase 9b" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

Tag: none yet. `v0.0.9` is made when 9a and 9b are connected (GIT-2).
