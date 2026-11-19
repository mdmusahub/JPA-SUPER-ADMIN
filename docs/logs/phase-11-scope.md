# Phase 11 scope: CSV export (written at the end of Phase 10, nothing implemented)

Source: `docs/PLAN.md` (Phase 11 row, sections 2, 5, 8, 9), `docs/RULES.md` (SEC-1, SEC-2, SEC-10, SEC-11, ARCH-10), `docs/DECISIONS.md` (D-12), `docs/UI_PLAN.md` (U8). This file lists what the phase has to deliver and the questions to settle first. It is a summary, not a plan; the plan is written at the start of the phase and approved.

## What the phase delivers

1. **`ExportService`** (core, `service` package): streams the rows of the list query that the list page uses, in pages, never loading all rows (ARCH-10). Same validated query pipeline as `ListQueryService`: search, filters, sort and the visible columns are checked against the metadata (SEC-2). Reads inside read-only transactions (ARCH-5). Criteria API only.
2. **Route** `GET {path}/{entity}/export.csv` with the list's query parameters (`q`, `filter.*`, `sort`, `dir`), a `Content-Disposition: attachment` with a safe, generated file name, `text/csv;charset=UTF-8`, `X-Content-Type-Options: nosniff`. Session required. GET only; it changes nothing. An unknown or hidden entity is the console's 404, identical in both cases (D-47).
3. **Cap:** at most `jpa.admin.export-max-rows` rows, never above the fixed internal ceiling of 50,000 (D-12). The property is new in the properties class (default 50000, validated by plain code, may only lower the cap). When the cap cuts the export the file says so (a note, never a silent cut): decide where (a last comment row is not valid CSV for every consumer; a response header and the page after the download are the candidates).
4. **Escaping (SEC-10):** a cell that begins with `=`, `+`, `-` or `@` is prefixed so a spreadsheet does not run it; decide on tab and carriage return as first characters too (the OWASP list has them). Standard CSV quoting for commas, quotes and line breaks.
5. **Sensitive data (SEC-1):** a sensitive column is not selected and not in the header, a hidden column is absent, collections and binary values are absent, a relation is exported as its label (the list's join, no extra query per row, ARCH-6). Upload fields export the stored address, not the file.
6. **Switches (SEC-11):** `jpa.admin.export-enabled` (new property, default true) and the per-entity `exportable` option of `@AdminResource` (the annotation delivers `readOnly`, `creatable`, `updatable`, `deletable` today; `exportable` is the next option, a public API addition that needs approval). Enforced in the service, not only hidden in the page. `DashboardView.Permissions.export` becomes real (it is `false` today, `ConsoleNavigation.permissions`).
7. **UI (task U8, export part):** the export button in the list header, built from `ListView.Actions.exportHref` and `canExport` (both already in the contract, D-28; the list fixtures already carry them); button states (idle, and the cap notice). The link carries the current search, filters, sort and visible columns, built by `ListLinks`.
8. **Tests:** every column type, quoting, formula cells for each of the four characters, the cap and its note, search/filter/sort respected, a sensitive and a hidden column absent from header, rows, log and error, an unknown sort or filter name ignored, session required, GET only, `export-enabled=false`, `exportable=false`, memory bound (a table larger than the page size streams), `spring.jpa.open-in-view` on and off, host-check exports its own entities with no library change, mutation checks.

## Questions to settle before coding (each with a recommendation)

| Question | Options | Recommendation |
|---|---|---|
| Where does the "cut at the cap" note go? | a trailing comment row; a response header plus a notice on the list page; both | Header plus the page notice: the file stays valid CSV |
| Byte order mark for Excel | none; UTF-8 BOM | BOM off by default, documented; an Excel user can import as UTF-8 |
| Which columns | all visible, non-sensitive; only the list's visible columns | The list's visible columns (the brief of the phase says so); Phase 12 adds hiding |
| Formula escape: which first characters | `= + - @`; also tab and carriage return | All six; the rule is one line |
| Date and number format | the list's display text; ISO and plain numbers | ISO dates and plain numbers: machine-readable; the list text is for people |
| Relation cell | label; id; both | Label (as the list), documented |
| `exportable` option name and default | `exportable = true` on `@AdminResource` | As written in PLAN section 6; public API, ask first |
| File name | entity name plus date; fixed | `<entity>-<yyyy-MM-dd>.csv` from the injected clock, ASCII only |

## Carried facts from Phase 10 that matter here

- The `Clock` bean decides dates; the injected-clock pattern and the 5-second cache are in `StatsService` and `CountService`.
- The chart's reader selects a single column through the Criteria API and reads at most a fixed number of rows: the export can reuse that style (select only the allowed attributes, page with `setFirstResult`/`setMaxResults`, never an entity).
- Hand-written output (as `UploadAnswers` and `ChartJson`) keeps the host's JSON or message-converter settings out of the way; the CSV writer should write straight to the servlet output stream for the same reason.
- `Hrefs` already accepts a link such as `{base}/{entity}/export.csv?q=...`; the builder is `ListLinks`.
- Not in scope: XLSX, server-side saved export jobs, exports of related collections (all out of scope for v0.1).
