# Phase 11 report: CSV export

Branch `phase/11-export`, from `main` (tag `v0.1.0-rc.10`). One session on both tracks (the recorded exception to AI-11, D-105). Nothing is committed. Phase 12 is not started (its scope is in `docs/logs/phase-12-scope.md`). Plan: `docs/logs/phase-11-plan.md`. Change log: `docs/logs/phase-11-change-log.md`. Decisions: `docs/DECISIONS.md` D-105 to D-111.

## 1. Files

`git diff --stat`: 28 tracked files changed, 503 insertions, 65 deletions. 27 files are new (plus this report). Nothing was deleted.

**Modified (28)**

| Track | Files |
|---|---|
| Backend, main | `metadata/AdminEntity` (`Access.exportable`, `canExport()`), `metadata/EntityScanner`, `annotation/AdminResource` (`exportable`), `service/ListQueryService` (shared `project`, `exportPlan`, `exportRows`), `web/ConsoleNavigation`, `web/EntityController`, `web/ListLinks` (`export`), `web/ListViewFactory` (export overload, `exportLimitNotice`), `autoconfigure/JpaAdminAutoConfiguration` (three beans), `autoconfigure/JpaAdminProperties` (three properties) |
| Backend, tests | `ListTestPersistence`, `persistence.xml` (core), `ConsoleEntityPagesTest`, `ConsoleListPageTest` (comments about "export comes later") |
| UI | `templates/jpa-admin/list.html`, `fragments/icons.html`, `assets/css/pages.css`; preview `ChromeFixtures`, `FilterFixtures`, `ListFixtures`, tests `PreviewListPagesTest`, `PreviewPagesTest` (the exact list of chrome variants gained `export-limited`) |
| Docs | `CHANGELOG.md`, `docs/DECISIONS.md`, `docs/FIXTURES.md`, `docs/PLAN.md`, `docs/PROJECT_DOCS.md`, `docs/UI_PLAN.md` |

**Created (27 + this report)**

- Core main: `service/CsvWriter`, `ExportValues`, `ExportSettings`, `ExportPlan`, `ExportService`; `web/ExportController`.
- Core tests: `metadata/ExportableOptionTest`, `service/CsvWriterTest`, `ExportValuesTest`, `ExportServiceTest`, `ExportCeilingTest`, `web/ExportControllerTest`, `testsupport/Csv`, `testsupport/listmodel/Shielded`.
- Autoconfigure tests: `ExportAutoConfigurationTest`, `JpaAdminExportPropertiesTest`, `exportmodel/{Receipt,Withheld,package-info}`.
- Preview test: `PreviewExportPagesTest`.
- Sample-app tests: `AbstractExportPagesTest`, `ExportPagesOpenInViewDisabledTest`, `ExportPagesOpenInViewEnabledTest`, `ExportPagesLogTest`, `ExportSwitchedOffTest`.
- Host-check test: `ConsoleExportsThisHostsEntitiesTest`.
- Docs: `docs/logs/phase-11-plan.md`, `phase-11-change-log.md`, `phase-12-scope.md`, `phase-11-report.md`.

## 2. Tests and builds

| Module | Before | After | Change |
|---|---|---|---|
| jpa-admin-core | 3404 (1 skipped) | 3562 (1 skipped) | +158 |
| jpa-admin-autoconfigure | 505 | 539 | +34 |
| jpa-admin-ui-preview | 2087 | 2269 | +182 |
| sample-app | 881 | 933 | +52 |
| **Reactor total** | **6877** | **7303** | **+426** |
| host-check (outside the reactor) | 23 | 28 | +5 |

- `mvn -B clean verify`: BUILD SUCCESS, 4:48 min (the third full run; see "What the build found").
- `mvn -B -DskipTests install`: BUILD SUCCESS.
- `mvn -B -f host-check/pom.xml verify`: BUILD SUCCESS, 28 tests.
- Packaged run (PowerShell `Start-Process`, `curl.exe` with a cookie jar): the product file has the header `ID,Category,Created at,Featured,Name,Price,Sku,Status,Stock` and 25 rows; headers `Content-Disposition: attachment; filename="product-2026-11-19.csv"`, `X-Content-Type-Options: nosniff` (once), `Cache-Control: no-store`, `Content-Type: text/csv;charset=UTF-8`, chunked; `?q=sku-0003` gives one row; `?sort=price&dir=desc` starts at 96.99; the customer file has `ID,Active,Created at,Email,Full name` and 12 rows, no "password" in any case and no placeholder secret; an unknown entity and a hidden one are both 404; anonymous is a 302 to the sign-in page; a POST without a token is 403; the list page's link is `/admin/product/export.csv?q=sku&sort=price&dir=desc` (no page, no size). The process was stopped by PID; ports 8080, 8081 and 8090 were free before and after.
- ARCH-12: the demo names in the library modules are only the three allow-listed files (Spring's `@Order`, D-42); the name check is part of the green verify.

## 3. Mutation checks (one at a time; each file restored byte-identical, MD5 checked; script and log kept in the scratchpad)

| | Break | Result |
|---|---|---|
| (a) | `CsvWriter`: no guard for `@` | CAUGHT, 5 failures (`CsvWriterTest`, `ExportServiceTest`) |
| (b) | `CsvWriter`: no guard for tab or carriage return | CAUGHT, 5 failures (a different five: the tab, the carriage return, the six-character test, the end-to-end file) |
| (c) | export a sensitive column (header, selection and row all include the masked column) | CAUGHT, 13 failures |
| (d) | export a hidden column (`AdminEntity.visibleFields()` returns every field) | CAUGHT, 5 failures |
| (e1) | the 50,000 ceiling removed from `ExportSettings.cap()` | CAUGHT, 1 failure in core (`aSettingAboveTheCeilingStillCutsAtTheCeiling`); the same rule is also tested in the autoconfigure module (`JpaAdminExportPropertiesTest`), which this run did not include |
| (e2) | the cap ignored by `ExportService.open` | CAUGHT, 5 failures (including the 50,001-record table) |
| (f) | no paging (`setMaxResults` removed) | CAUGHT, 8 failures |
| (g) | the `exportable` check skipped | CAUGHT, 4 failures |
| (h) | the `export-enabled` check skipped | CAUGHT, 7 failures |
| (i) | no quoting for commas | CAUGHT, 2 failures |
| (j) | the truncation header dropped | CAUGHT, 1 failure (`ExportControllerTest`; the real-chain header test in `ExportAutoConfigurationTest` was not part of this run) |

Baseline MD5s: CsvWriter 9fbdf7d1, ListQueryService 153c9758, AdminEntity 1289ffde, ExportSettings 9d3b550c, ExportService c0e7c49c, ExportController e6d2710c; all identical after every check. Each mutation was run against the core tests only.

## 4. Decisions and deviations (full text in `docs/DECISIONS.md` D-105 to D-111)

- **The notice (item 6, D-110): stateless, on the list page, not a session flash.** A download started by a link never replaces the page, so a flash set on the export request would reach the visitor only when they next open some page (maybe another entity's). The list page instead shows an INFO notice, "Export is limited to the first 50,000 of 120,345 matching records. Narrow the search or the filters to export the rest.", whenever export is offered and the total is above the cap. It is there before the download and after it. This differs from the wording "a notice on the list page after the download" in the approved cap-note decision; the brief allowed the choice. The headers `X-Export-Truncated: true` and `X-Export-Row-Limit` are sent only when the file is cut. Nothing is written to the session (tested).
- **Public option added (D-106): `@AdminResource(exportable)`, default `true`.** `readOnly` does not imply `exportable=false` (an export reads); if you want read-only entities to be unexportable, that is one line. Refusals: 404 for an unknown or hidden entity, 403 for a visible one that is not exportable or when `export-enabled=false`, enforced in `ExportService`.
- **Cell formats the brief did not name (D-109), please overrule if you disagree:** booleans `true`/`false`; an enum is its constant name (`IN_TRANSIT`), not the list's "In transit"; text is complete (not cut at 120 characters, line breaks kept); control characters other than tab, CR and LF are dropped; rows end with `\r\n`; a `java.util.Date`/`Timestamp`/`Calendar` is written as an instant in UTC.
- **Formula guard on numbers (D-105):** the rule has no exceptions, so a negative number arrives as text (`'-2.50`, tested). A spreadsheet will not sum such a column until the quote is removed. Exempting genuinely numeric columns would be a small change in `CsvWriter` plus a decision.
- **`HEAD` answers with the headers only** (D-111): Spring maps HEAD to the GET handler and would otherwise stream a whole file to be thrown away.
- **Truncation is decided by the list's count before the first byte** (a header must come first). A record added during a large export does not raise the cap, but the header reflects the count at the start.
- **`POST {entity}/export.csv`** is the existing update route (`POST {entity}/{id}`): 403 without the CSRF token, the console's 404 with one, as for `api/stats` (D-102). An entity whose identifier is the text `export.csv` can no longer open its detail page.
- **One shared `project(...)` in `ListQueryService`** now serves the list and the export, so they cannot disagree about what is selected. The export selects the identifier only when it is an exported column; an entity whose identifier is a sensitive field is exported without it and its SQL never names it (tested).
- **The disabled state is the absent button** (D-57). A busy state does not exist (a link starts a download without leaving the page).
- **What a host should know:** (1) the export load: up to 100 page statements and a count per file, offset paging gets slower toward the end, and a search is a full scan as on the list; (2) the file holds the stored addresses of upload fields, not the files; (3) the formula prefix and its effect on negative numbers (above); (4) the list's relation labels come from the list's join, so a relation to a hidden entity is exported as its plain id; (5) rows are not locked, so a busy table can shift a row between pages; (6) a failure after the first byte cuts the connection, so a half file is not taken for a whole one.
- **What the build found.** (1) The first full run failed one preview test: the exact list of chrome variants had to gain `export-limited` (a legitimate update, not a loosened assertion). (2) The second full run failed `FormPagesLogTest`: my new `ExportPagesLogTest` turns Hibernate's bind logging to TRACE and the level is global to the test JVM, so a later class that expects it off saw typed values. My test now restores the levels it changed, and has a context of its own (its properties were identical to `ListPagesLogTest`'s, so they shared a cached context and my first reset removed that test's levels). (3) `host-check`'s first run failed my own new search test (MockMvc encoded `%20` twice); fixed in the test. (4) Several of my first test assertions were wrong, not the code (a crate id of 10 colliding with "#10" in a note, a decimal scale, a tied sort); the code was not changed for any of them.
- **CODE-8, honestly.** I verified APIs by compiling and running against the pinned 4.1.2 jars; I did not read the online reference pages in this session. Packages used: `jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}` (`setContentType(String)`, `setCharacterEncoding(String)`, `setHeader`, `getOutputStream`, `flushBuffer`, `getMethod`), `jakarta.persistence.{EntityManager, Tuple, TypedQuery}` (`setFirstResult`, `setMaxResults`), `jakarta.persistence.criteria.*`, `org.springframework.transaction.annotation.Transactional`, `org.springframework.web.bind.annotation.{GetMapping, PathVariable, RequestParam}`, `org.springframework.web.server.ResponseStatusException`, `org.springframework.boot.logging.LoggingSystem`, `org.springframework.boot.test.system.{CapturedOutput, OutputCaptureExtension}`, `org.hibernate.stat.Statistics`, `org.hibernate.Session#getStatistics`. The servlet calls were checked by tests against MockHttpServletResponse, the real chain (MockMvc with the console's filters) and the packaged jar over HTTP (content type and charset, one `nosniff`, `no-store`, chunked).

## 5. Not done, manual checks, contract and Phase 12

**Not done**
- The browser pass of the export button and its notice: both themes, phone width, keyboard only, script off. No browser driver (AI-8). The CSS adds one rule (the icon does not shrink).
- Opening a file in **Excel and LibreOffice**: accents, a comma, a quote, a line break, a cell starting with `=`, a negative number. Not done by any test. Check also whether Excel needs `jpa.admin.export-bom=true` for the accents on your machine.
- PostgreSQL and MySQL runs of the export query (Phase 14).
- Mutations ran against the core tests only (the sample-app and autoconfigure tests were not re-run for each).

**Manual checks** (`mvn -pl sample-app -am package -DskipTests`, run the jar): the Export CSV button on every list; click it with a search and a filter and open the file; set `jpa.admin.export-max-rows=5` and see the notice and the two headers; set `jpa.admin.export-enabled=false` and see the button go.

**Contract change proposed:** none. `ListView.Actions` and `DashboardView.Permissions` carried everything. No new dependency.

**Phase 12 scope:** `docs/logs/phase-12-scope.md` (not implemented).

## 6. Suggested commits (PowerShell-safe, by path, never `git add .`)

```powershell
# 1. metadata, services, controller, properties and auto-configuration (backend)
git add jpa-admin-core/src/main jpa-admin-autoconfigure/src/main
git commit -m "feat(core): CSV export - ExportService, CsvWriter, GET /{entity}/export.csv, exportable option and export properties

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 2. backend tests
git add jpa-admin-core/src/test jpa-admin-autoconfigure/src/test sample-app/src/test host-check/src/test
git commit -m "test: CSV writer, export service on 50,001 records, route over the real chain, sample app, host-check

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 3. UI and preview (U8, export part)
git add jpa-admin-ui jpa-admin-ui-preview
git commit -m "feat(ui): export button, cap notice states and preview fixtures

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 4. docs and logs
git add CHANGELOG.md docs/DECISIONS.md docs/FIXTURES.md docs/PLAN.md docs/PROJECT_DOCS.md docs/UI_PLAN.md docs/logs/phase-11-plan.md docs/logs/phase-11-change-log.md docs/logs/phase-11-report.md docs/logs/phase-12-scope.md
git commit -m "docs: Phase 11 decisions D-105 to D-111, plan ticks, changelog and logs

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

Commit 1 compiles only together with commit 2's test changes (the existing tests use the changed `Access` record's new constructors and the new beans) and commit 3 only together with commit 1 (the preview fixtures call the new factory overload), so build after commit 4, or make it one commit. The merge to `main` and the tag `v0.0.11` are yours (GIT-2).
