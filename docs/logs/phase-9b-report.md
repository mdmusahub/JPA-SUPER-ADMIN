# Phase 9b report: forms and UI for uploads

Branch `phase/9b-uploads-ui` (from `main` after 9a). Built 2026-11-18 in one session on both tracks (D-95). Nothing is committed. The step-by-step record is `docs/logs/phase-9b-change-log.md`; the plan is `docs/logs/phase-9b-plan.md`.

APIs used were checked against the project's own jars and sources (CODE-8): Spring Boot 4.1.2, Spring Framework 7 (`org.springframework.web.multipart`, `org.springframework.mock.web`), Spring Security 7 (`org.springframework.security.web.csrf`), Thymeleaf 3.1 (`org.thymeleaf`, `th:replace` runs before `th:if`), Jakarta Servlet (`jakarta.servlet.http.HttpSession`). No new dependency, no new property, no annotation change, no change to a frozen view-model record.

## 1. Files created, modified, deleted

Deleted: none. Created (23 files, untracked) and modified (39 files, `git diff --stat`: 845 insertions, 80 deletions) as in `git status --short`:

Created
- `docs/logs/phase-9b-plan.md`, `phase-9b-change-log.md`, `phase-9b-report.md`
- core main: `service/UploadFiles.java`, `service/NoUploadFiles.java`, `storage/StorageUploadFiles.java`, `web/PendingUploads.java`
- core tests: `service/UploadFormTest.java`, `web/UploadFormPagesTest.java`, `web/DetailImagesTest.java`
- UI: `templates/jpa-admin/fragments/uploader.html`, `assets/js/uploader.js`
- preview: `UploadStub.java`, test `PreviewUploadPagesTest.java`
- sample-app tests: `AbstractUploadFormOverHttpTest.java`, `UploadFormOverHttpOpenInViewDisabledTest.java`, `UploadFormOverHttpOpenInViewEnabledTest.java`

Modified
- core main: `StorageProvider`, `LocalStorageProvider`, `UploadMessages`, `EditableFields`, `FormQueryService`, `FormPage`, `FieldMessages`, `RecordWriteService`, `ListQueryService`, `DetailQueryService`, `DetailViewFactory`, `UploadController`, `FormController`
- autoconfigure: `JpaAdminAutoConfiguration`
- UI: `form.html`, `fragments/field-input.html`, `fragments/cell.html` (comments), `layout.html`, `pages.css`, `form-guard.js`
- preview: `FormFixtures`, `RecordFixtures`, `ListFixtures`; tests `HtmlRules`, `HtmlRulesTest`, `PreviewFormPagesTest`, `PreviewListPagesTest`
- other tests: core `LocalStorageProviderTest`, `TransactionalRecordWriteService`; sample-app `AbstractFormPagesTest`, `FormPagesLogTest`; `host-check` `ConsoleFormsOfThisHostTest` (selector only)
- docs: `CHANGELOG.md`, `DECISIONS.md` (D-95 to D-100), `FIXTURES.md`, `PLAN.md`, `PROJECT_DOCS.md`, `UI_PLAN.md`, `logs/phase-9b-scope.md`

## 2. Test counts and final checks

| Module | Before | After |
|---|---|---|
| jpa-admin-core | 3101 (1 skipped) | 3190 (1 skipped) |
| jpa-admin-autoconfigure | 498 | 498 |
| jpa-admin-ui-preview | 1714 | 1882 |
| sample-app | 791 | 819 |
| **Total** | **6104** | **6389** |
| host-check (separate project) | 21 | 21 |

- `mvn -B clean verify`: BUILD SUCCESS.
- `mvn -B -DskipTests install`: BUILD SUCCESS.
- `mvn -B -f host-check/pom.xml verify`: first run 1 failure of 21, because its helper found the form by the old class `card form-card`; I had searched the sample app for that selector but not `host-check`. Selector changed to `<form id="record-form"` (no assertion changed); second run BUILD SUCCESS, 21 of 21.
- Manual run of the packaged jar (PowerShell, `curl.exe`, cookie jar, port 8095, PID 20572): edit page has the record form, the upload section, a file input, `uploader.js`, a ticket and no inline script; a real PNG posted with the form's ticket and the hidden `_csrf` field gave 303 back to the edit page; the address appeared once in the field; saving gave 303 to the detail page; the list and detail page show the thumbnail; the file URL with the session gave 200, `nosniff`, `image/png`, bytes equal; anonymous gave 302 to the login; a foreign address was refused with 422 and the sentence. Process stopped by PID. Nothing listens on 8080, 8081, 8090 or 8095.
- Stray `sample-app/uploads` (empty, gitignored, made by a test run) removed. Demo-name grep of the library modules: only `@Order` (Spring) and a Phase 7 placeholder word "Author" in `table.html`.

## 3. Mutation checks (each restored byte-identical by MD5, then green again)

| | Mutation | Result |
|---|---|---|
| a | `RecordWriteService`: skip the accepts check | caught by `UploadFormTest` (refused-address cases, the no-change edit, the log test); the exact failure total was not captured at the time; restored, 55 green |
| b | `DetailQueryService.copyable`: drop the upload guard (a copy carries the file) | caught, 2 failures of 59; restored, 59 green |
| c | `FormQueryService`: a ticket on a non-upload field | caught, but by the `FormView.Field` constructor ("uploadTicket is required for upload fields and only for them"), so every form-rendering test fails. The test of the brief is the second line of defence, not the first; restored, 55 green |
| d | `cell.html`: file name with `th:utext` | caught, 2 failures of 108 (`TemplateRulesTest`, `PreviewUploadPagesTest`); restored, 108 green |
| e | `form.html`: upload form nested inside the record form | caught: the no-form-in-form rule fails for all 8 upload fixtures plus the structure tests; restored, 73 green |
| f | `cell.html`: thumbnail without alt text | caught, 6 failures; restored, 142 green |
| g | `RecordWriteService.upload`: a blank value clears the field | caught, 2 failures of 43; restored, 43 green |
| h | `uploader.js`: `submitButton.disabled = false` | caught by the script-as-text test, 1 failure of 32. **Only as text**: the script is not run in a browser (no driver, AI-8) |
| i | `StorageUploadFiles`: accept `svg` as an image | caught, 1 failure of 43; restored, 43 green |

All seven restored files matched the baseline MD5 (`ALL_MD5_IDENTICAL`).

## 4. Decisions and deviations

Full text in `docs/DECISIONS.md` D-95 to D-100.
- **No contract change (D-96).** The frozen `FormView.Field` carries an upload field by convention: `options` `accept` and `maxBytes`, the entity from `chrome.activeEntity`, the fixed route `{basePath}/uploads`, the remove marker `<field>-remove`. A cleaner `FormView.Field.upload` record is proposed for Phase 13, not made.
- **No-script path, option A (D-95).** The upload section is a separate multipart form (HTML forbids a form inside a form), so the record form is a form inside a card `div`, and Save belongs to it through `form="record-form"`. A plain upload redirects back to the form page and the result waits in a console-only session attribute `JPA_ADMIN_UPLOADS` (cleared at sign-out, at most 10, shown once, re-checked). **Its limit: the page reloads, so unsaved text in the record form is lost.** The section says so. Recorded in DECISIONS and PROJECT_DOCS.
- **Replace and remove rule (D-97).** A posted address is stored only if it parses as the provider's own address, in the field's own storage and folder, with an accepted type, and the file exists. Otherwise that field alone gets "The file is not one this console uploaded. Upload it again." Blank keeps; `<field>-remove` clears; a new address replaces. **No file is ever deleted (UPL-9).**
- **Cells (D-99).** Image cell text is the field label (alt text). A missing file is words, not a broken picture. A foreign value or an SVG is escaped text. Thumbnails are the original file scaled by CSS.
- **`uploader.js` (D-98).** XHR for progress, CSRF header, advisory checks only, `textContent` only, no storage, Save disabled while uploading.
- **Selector changes in older tests (D-100).** Four tests (`PreviewFormPagesTest`, `AbstractFormPagesTest`, `FormPagesLogTest`, `ConsoleFormsOfThisHostTest`) found the form by its old class; only the selector changed.
- **Port 8095** for the manual run, so the brief's ports stay free.

What a host should know (also in PROJECT_DOCS B7, B9, B12 and CHANGELOG)
- **Upload form:** works without script; a plain upload reloads the page and loses unsaved text; a ticket lasts ten minutes and is issued at each render.
- **Orphan files:** replacing or removing a file leaves the old one on disk; a cleanup is a Phase 14 decision.
- **Thumbnails:** the full original is downloaded for each row; the list does one file test per row; a slow network share makes lists slow. A custom `StorageProvider` that does not implement `parse` and `owns` owns no address, so every posted address is refused.

## 5. Not done, manual items, proposals, Phase 9 status

- **Manual item: a browser pass for `uploader.js` and `form-guard.js`** (drag and drop, progress, Save disabled while uploading, focus, both themes, phone width). The automated real-browser test is a Phase 14 item (needs a driver dependency, approval first).
- Orphan-file cleanup (Phase 14, UPL-9). S3 provider (v0.2). Image resizing is out of scope.
- Mutation (a): the failure total was not captured. Mutation (h): text only.
- Proposed, not made: `FormView.Field.upload` record (D-96).
- **Phase 9 status:** 9a and 9b are both built and connected in the sample app; PLAN boxes ticked, UI_PLAN U6 delivered. Merge to `main` and tag `v0.0.9` are the maintainer's step (GIT-2).

## 6. Suggested commits

```powershell
git add jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/storage/StorageProvider.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/storage/LocalStorageProvider.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/storage/UploadMessages.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/storage/StorageUploadFiles.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/storage/LocalStorageProviderTest.java
git commit -m "feat(storage): StorageProvider.parse and owns, local provider recognises its own files" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

git add jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web jpa-admin-autoconfigure/src/main/java/io/github/mdmusahub/jpaadmin/autoconfigure/JpaAdminAutoConfiguration.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/service/UploadFormTest.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/web/UploadFormPagesTest.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/web/DetailImagesTest.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/testsupport/TransactionalRecordWriteService.java
git commit -m "feat(core): upload fields in forms, posted-address rules, upload cells and detail strip" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

git add jpa-admin-ui/src/main/resources/templates/jpa-admin jpa-admin-ui/src/main/resources/jpa-admin/assets/css/pages.css jpa-admin-ui/src/main/resources/jpa-admin/assets/js/form-guard.js jpa-admin-ui/src/main/resources/jpa-admin/assets/js/uploader.js
git commit -m "feat(ui): uploader section, uploader.js, image and file cells (U6)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

git add jpa-admin-ui-preview/src sample-app/src/test host-check/src/test/java/com/example/bookstore/ConsoleFormsOfThisHostTest.java
git commit -m "test: upload fixtures, stub endpoint, over-HTTP upload form tests, new form selector" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

git add CHANGELOG.md docs/DECISIONS.md docs/FIXTURES.md docs/PLAN.md docs/PROJECT_DOCS.md docs/UI_PLAN.md docs/logs/phase-9b-scope.md docs/logs/phase-9b-plan.md docs/logs/phase-9b-change-log.md docs/logs/phase-9b-report.md
git commit -m "docs: Phase 9b (D-95 to D-100), plan, change log and report" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

Check `git status --short` shows nothing left over before the merge; then tag `v0.0.9` (maintainer).
