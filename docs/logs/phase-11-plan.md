# Phase 11 plan: CSV export

Branch `phase/11-export`, from `main` (tag `v0.1.0-rc.10`). One session on both tracks (the recorded exception to AI-11, as Phases 2 to 10). Scope: `docs/logs/phase-11-scope.md`. The approved decisions are in the brief and are recorded as D-105 to D-110 in `docs/DECISIONS.md`. Nothing is committed. Phase 12 is not started.

## Facts found while reading (Step 0)

- The route `GET {path}/{entity}/export.csv` is **free**. Only the preview fixture builds a link to it. The only other route that matches the shape is `GET {path}/{entity}/{id}` (the detail page); a literal segment is more specific than `{id}`, so the export controller wins. An entity whose key is the text `export.csv` can no longer open its detail page (the same limit as `new` and `api/stats`, documented). `POST {path}/{entity}/export.csv` falls to the update route (`POST {entity}/{id}`): 403 without a CSRF token, the console's 404 with one (the precedent of `api/stats`, D-102 point 9). PUT, PATCH and DELETE are 405.
- "Export coming later" text that remains and is to be changed: `docs/PROJECT_DOCS.md` line 462, the Javadoc of `ConsoleNavigation.permissions` and of `EntityController`, two test comments (`ConsoleEntityPagesTest`, `ConsoleListPageTest`). `CHANGELOG.md` line 69 is history and stays.
- `list.html` already prints an "Export CSV" link when `view.actions.canExport`; the real controller passes `false` today. `ListView.Actions` and `DashboardView.Permissions.export` exist, so **no contract change is needed**.
- The list pipeline lives in `ListQueryService` (private `Plan`, `conditions`, `orderBy`, `filtering`). The export reuses it from inside that class so no rule is copied.

## Design

**Core (`jpa-admin-core`, no Spring Boot, Security or Thymeleaf)**

| Piece | Where | What |
|---|---|---|
| `@AdminResource.exportable` | `annotation/AdminResource` | `boolean exportable() default true`. Javadoc and the "options read" sentence updated (public API addition, D-106). `readOnly` does **not** imply `exportable = false` (an export reads, it never writes) |
| `AdminEntity.Access` | `metadata` | fifth component `exportable`; the old 2- and 4-argument constructors stay, so no caller changes. `canExport()`; `AdminEntity.canExport()` = supported and `access.canExport()` |
| `EntityScanner.access()` | `metadata` | reads `exportable` |
| `ExportSettings` | `service` | record `(enabled, maxRows, bom)`; `ceiling` 50,000; the effective cap is `min(maxRows, 50000)`; the constructor refuses a value outside 1..50,000 |
| `CsvWriter` | `service` (package-private) | RFC 4180 writer: `\r\n` between rows, a cell is quoted when it holds a comma, a quote, CR or LF (quotes doubled), the formula guard (a leading `=`, `+`, `-`, `@`, tab or CR gets a leading single quote, **before** quoting), `null` is an empty cell, an optional BOM |
| `ExportValues` | `service` (package-private) | typed value to text: numbers plain (`BigDecimal.toPlainString`), dates and times ISO-8601 in the stored value's own zone, booleans `true`/`false`, enums the constant name, text as stored (not cut, not collapsed), relation = the label or else the plain id, an upload field = the stored address text |
| `ListQueryService.exportPlan` / `exportRows` | `service` | `exportPlan` (one read-only transaction): checks the query with the list's `Plan.checked`, resolves the filters (`filtering`), counts with the list's `count`, returns an `ExportPlan` (entity, the exportable columns, the effective query, the filter specs, the total). `exportRows(plan, offset, limit)` (one read-only transaction per page): the list's selection (extracted into a shared `project` method), `conditions`, `orderBy`, `setFirstResult`/`setMaxResults`; **a sensitive column is never selected, and the identifier is selected only when it is an exported column** |
| `ExportService` | `service` | `open(entity, query)` (SEC-11: not the registry's entity is `RecordNotFoundException`; `export-enabled=false`, `exportable=false` or no exportable column is `ActionRefusedException`), then reads the first page **before** the controller sets a header, so a failure there is still a normal console error; `Export` has `fileName()`, `truncated()`, `rowLimit()` and `writeTo(Writer)` that streams the remaining pages (default 500 rows; a test can make it smaller). The cap is `min(export-max-rows, 50000)`. Truncation = the list's own count is above the cap (known before the first byte, which a header needs). The date in the file name comes from the injected `Clock` |
| `ExportController` | `web` | `GET {path}/{entity}/export.csv` only. Sets `Content-Type: text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="<urlname>-<yyyy-MM-dd>.csv"`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, and when cut `X-Export-Truncated: true` and `X-Export-Row-Limit: <cap>`; writes with a `BufferedWriter` over `response.getOutputStream()` in UTF-8 (no message converter); flushes at the end. A failure after the first byte is logged (entity key and row count only) and re-thrown, so the connection is cut and the client cannot take half a file for a whole one |
| `ListLinks.export` | `web` | `{base}/{entity}/export.csv` + `q`, the `filter.*` parameters, `sort`, `dir` (never `page` or `size`), every value percent-encoded; goes through the `Hrefs` check in `ListView.Actions` |
| `ListViewFactory` | `web` | an overload that offers export: sets `canExport` and `exportHref` |
| `EntityController` | `web` | new constructor with `ExportService` (the old one stays and offers nothing). The list offers export exactly when `exports.offers(entity)`. When the list's total is above the cap the page carries a plain INFO notice (see "Notice") |
| `ConsoleNavigation` | `web` | `Permissions.export` = export enabled and `entity.canExport()` (a constructor overload; the old one keeps `false`) |

**Autoconfigure**: `jpa.admin.export-enabled` (default `true`), `jpa.admin.export-max-rows` (default `50000`), `jpa.admin.export-bom` (default `false`) in `JpaAdminProperties` with the plain validation (a value below 1 or above 50,000 fails startup with a message that names the property and never repeats the value); beans `ExportSettings`, `ExportService`, `ExportController`; `EntityController` and `ConsoleNavigation` get them.

**UI**: `list.html` export link gets an `aria-label`, an icon and a title that says what the file holds; a `.export-note` style is not needed because the cap notice uses the existing flash area. The disabled case is the **absent** button (D-57: no button leads nowhere; a disabled control with no explanation is worse). No script. No inline style.

**Preview**: fixtures `export-enabled` (button, link with search/filter/sort), `export-disabled` (no button, other actions stay), `export-limited` (the cap notice), built by the real `ListViewFactory`.

## Notice decision (item 6)

The approved text says "a notice on the list page after the download". A download started from a link never replaces the page, so a flash set in the session on the request would not be shown until the visitor next opens *some* page (even another entity's), long after the file. **Decision: the notice is stateless and is on the list page itself**, shown whenever export is offered and the list's total is above the cap ("Export is limited to the first 50,000 of 120,345 matching records. Narrow the search or the filters to export the rest."), so it is there before the download and still there after it. The response headers stay for scripts. Nothing is written to the session. Tests: page notice at the cap and one row under it, none under; the headers on the file; no session attribute. Recorded as D-110.

## Steps and the tests that go with them

0. Documents: decisions D-105 to D-110, the route check, the "later" text. (Done first, logged.)
1. Metadata: `exportable`. Tests: annotation default and false, `Access` flags, scanner, `readOnly` does not imply `exportable=false`.
2. Properties: three properties + validation. Tests: defaults, a lower cap, 50,000, 50,001, 0, negative, a huge number, the message names the property and never the value, `export-bom`.
3. `CsvWriter`, `ExportValues`: every rule (the six formula characters, quoting, CRLF, BOM, dates, numbers, null, booleans, enums, text with line breaks, relation label or id).
4. `ListQueryService.export*` and `ExportService`: memory bound (a table larger than one page, page size 7 in the test), search, filter and sort respected, sensitive/hidden/collection/binary/embedded absent from the SQL and the output, unknown names ignored, the cap (a lowered cap and the ceiling), the truncation flag, statements per page bounded, open-in-view on and off (sample app), a failure before the first byte.
5. Controller: headers, file name, truncation headers, session required (sample app, real chain), GET only, unknown/hidden entity 404 sameness, `exportable=false` and `export-enabled=false` (403; hidden 404), the link and its encoding, links under the base path.
6. UI: list header, aria-label, both themes, phone width, keyboard (CSS reviewed; the real-browser pass stays a manual item). Template rules (no inline style, no script).
7. Preview fixtures + tests.
8. Sample app and `host-check` tests, ARCH-12 and host isolation.
9. Mutation checks (a) to (j), one at a time, files restored byte-identically (MD5).
10. Docs: PLAN, UI_PLAN, DECISIONS, CHANGELOG, PROJECT_DOCS, FIXTURES.
11. Final checks: `mvn -B clean verify`, `mvn -B -DskipTests install`, `mvn -B -f host-check/pom.xml verify`, the packaged run with `curl.exe`.

## Not in this phase

XLSX, saved export jobs, related collections, hiding columns (Phase 12), a progress bar, a busy state (needs script and the response is a plain download).

## Questions that would stop the work

None expected. The brief's three stop conditions (a new dependency, a frozen record that cannot carry what is needed, a conflict between documents or a rule that cannot be met) were checked against the code above: none applies.
