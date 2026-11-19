# Phase 11 change log

One line per file or command that changed something. Track: B = backend, U = UI, D = docs. Newest at the bottom.

| # | Track | What | Why |
|---|---|---|---|
| 1 | D | `docs/logs/phase-11-plan.md` created | Plan first, as the brief asks |
| 2 | D | `docs/DECISIONS.md`: D-105 to D-110 appended (approved rules, `exportable`, properties, row reading, cell formats, the cap notice) | Step 0 |
| 3 | D | Step 0 checks: `/export.csv` is free in the route table (only the preview fixture builds a link to it); the text "export comes later" remains in `docs/PROJECT_DOCS.md:462`, the Javadoc of `ConsoleNavigation.permissions` and `EntityController`, and two test comments (to be changed with the code) | Step 0 |
| 4 | B | `metadata/AdminEntity` (`Access.exportable`, `canExport()`), `EntityScanner.access()`, `annotation/AdminResource` (`exportable`, Javadoc) | Step 1, D-106 |
| 5 | B | test: `testsupport/listmodel/Shielded` (+ `persistence.xml`, `ListTestPersistence.empty`), `metadata/ExportableOptionTest` (7) | Step 1 |
| 6 | B | `service/ExportSettings` (new); `autoconfigure/JpaAdminProperties` (`export-enabled`, `export-max-rows`, `export-bom`, validation); test `JpaAdminExportPropertiesTest` (17) | Step 2, D-107 |
| 7 | B | `service/CsvWriter`, `service/ExportValues` (new); tests `CsvWriterTest` (42), `ExportValuesTest` (15) | Step 3, D-105, D-109 |
| 8 | B | `service/ListQueryService`: `project(...)` extracted from `rows()`, `exportPlan`, `exportRows`; `service/ExportPlan`, `service/ExportService` (new); tests `ExportServiceTest` (47), `ExportCeilingTest` (2, a real table of 50,001 records), `testsupport/Csv` | Step 4, D-108 |
| 9 | B | `web/ExportController`, `ListLinks.export`, `ListViewFactory` (export overload, `exportLimitNotice`), `EntityController` (new constructor, cap notice), `ConsoleNavigation` (`exportEnabled`), `JpaAdminAutoConfiguration` (three beans); tests `ExportControllerTest` (44), `ExportAutoConfigurationTest` (17), `exportmodel/*` | Step 5, D-110 |
| 10 | U | `list.html` (export link: icon, `aria-label`, `title`), `fragments/icons.html` (`download`), `pages.css` (`.export-link .icon`) | Step 6, U8 |
| 11 | U | preview: `ListFixtures` (`export-enabled`, `export-disabled`, `export-limited`, real export link), `FilterFixtures.exportEnabled`, `ChromeFixtures` (`export-limited`); tests `PreviewExportPagesTest` (14), `PreviewListPagesTest` (one assertion) | Step 7 |
| 12 | B | sample-app tests `AbstractExportPagesTest` (+ two open-in-view subclasses, 24 each), `ExportPagesLogTest`, `ExportSwitchedOffTest`; host-check test `ConsoleExportsThisHostsEntitiesTest` | Step 8 |
| 13 | B | two test comments about "export arrives in Phase 11" updated (`ConsoleEntityPagesTest`, `ConsoleListPageTest`) | Step 0 grep |
| 14 | D | `docs/UI_PLAN.md` (U8 export status), `docs/FIXTURES.md`, `docs/PLAN.md`, `docs/PROJECT_DOCS.md`, `docs/logs/phase-12-scope.md` | Step 10 |
| 15 | B | mutation script run (a) to (j), see the report | Step 9 |
