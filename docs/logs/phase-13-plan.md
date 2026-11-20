# Phase 13 plan: polish

Branch `phase/13-polish`, from `main` (tag `v0.1.0-rc.12`). One session on both tracks (the recorded exception to AI-11, as Phases 2 to 12). Nothing is committed. Phase 14 is not started. Written 2026-11-20, before any code. Every file and command is logged in `docs/logs/phase-13-change-log.md`. Scope source: `docs/logs/phase-13-scope.md` and the brief. Decisions start at D-118 (D-117 is the last).

## What the phase delivers

1. **One contract change (approved in the brief):** `ListView` gets one new component, `state` (a nested record `ListView.State`): the validated list address for the saved-views script (`href`, no page), the page size an address without `size` uses (`defaultSize`), the "Show all columns" link (`showAllColumnsHref`, `null` when no column is hidden) and the "Clear search" link (`clearSearchHref`, which keeps the hidden columns). The script stops reading the page-size link. Javadoc, `docs/UI_PLAN.md` section 3, fixtures and tests change on both sides.
2. **`jpa.admin.page-size`** (10, 25, 50, 100; default 25), plain validation naming the property, never echoing the value. The default size travels in `ListQuery` (new component `defaultSize`, the old constructors keep 25), so a link omits `size` exactly when it equals the configured default and the script learns it from `view.state.defaultSize`.
3. **`@AdminResource(plural)`**, **`@AdminBadge`**, **`@AdminLookup`** (public API; names below). Startup check (same style as UPL-8) lists every offending field and the reason and stops startup; the services also re-check so a wrong annotation can never leak a sensitive attribute (SEC-1).
4. **The `/` shortcut** (`shortcuts.js`, external, enhancement only), a "Skip to search" link that works without script, the keyboard hint, the sticky table header on desktop widths.
5. **Visual consistency, dark mode, accessibility and responsive pass** against `docs/prototype/admin-console.html` and UI_PLAN section 5, measured, with a test for each fix.
6. **Automated real-browser tests** (HeadlessChrome, sample-app test scope, no dependency): layout of every page type at 1280 and 390, both themes; contrast; the CSP refusing inline styles; behaviour of chart links, export button, views panel, column control, uploader states the browser can drive, form guard, `/` shortcut and focus order. Skipped with a reason, never passed, when no Chrome exists.
7. **Manual-check list** in `docs/logs/phase-13-report.md` for what no automated browser can judge. Phase 14 scope in `docs/logs/phase-14-scope.md` (written, not implemented).

## Design choices (to be recorded as D-118 onward)

| Topic | Choice | Reason |
|---|---|---|
| State field | `ListView.State(href, defaultSize, showAllColumnsHref, clearSearchHref)`, required, constructor invariant: `showAllColumnsHref` is present exactly when some column is not visible; `href` has no `page` | One place for the script's data; the invariant makes mutation (h) fail a test at the record |
| Clear search | "Clear search" and "Clear search and filters" keep the hidden columns (`hide`) and nothing else (search, filters, sort and size still reset, as before). "Show all columns" is the way back to every column | Brief default; a visitor who chose columns did not ask to lose them |
| Page size | `ListQuery.defaultSize` component; `ListQuery.parse(..., defaultSize)`; `ListQueryService` gets the configured value; `ListLinks` omits `size` when it equals `query.defaultSize()`; `views.js` reads `data-views-default-size` | A link for 25 under a default of 50 must say `size=25` |
| `@AdminResource(plural)` | New option, default empty = the guess of `Plurals`; at most 60 characters, no control character; used in headings, breadcrumbs, search placeholder, empty state and the dashboard cards. Per-entity `pageSize` is **not** added (not in the brief's list; the global property covers it) | Smallest API; a public name beyond the list needs approval |
| `@AdminBadge` | `@AdminBadge({"PUBLISHED=green", "DRAFT=amber"})` on an enum or `String` field (or getter). Palette = the tones `Cell.Tone` already has: `green`, `amber`, `red`, `grey` (case-insensitive). An enum value must be a constant; a duplicate value, a blank value or any other tone (a `#rrggbb`, `red;x`, an unknown word) stops startup naming the field. Unmapped values of an annotated field are grey; an unannotated enum keeps the word list. A mapped `String` field is shown as an `ENUM` badge with the stored text. The tone is only ever a `Cell.Tone` constant, so an inline colour cannot reach the page | Brief: class names only, fixed palette. `BLUE` is not offered: `.badge-blue` exists in the CSS but `Cell.Tone` has no `BLUE`, and adding it is a record change not in the brief (the scope file wrongly said six tones; there are five) |
| `@AdminLookup` | `@AdminLookup("name")` on a to-one field; the target must be in the console and the property must be a visible, non-sensitive, non-identifier `String` attribute of it, else startup stops with the field named. At run time the same check is repeated; an unusable lookup falls back to the D-54 rule, and an empty value shows `#id` | Brief: must exist, falls back to id; SEC-1 even when misconfigured |
| Shortcut | `/` focuses the search box unless a field, select, textarea or editable element has focus or a modifier key is down. No other shortcut (the plan mentions "new record" once; the brief lists only `/`) | Smallest approved surface |
| Sticky header | At 900px and wider the table box gets a maximum height and the header row sticks inside it; below that nothing changes | A header can only stick inside a scroll box when the box also scrolls up and down |
| Toasts and drawer | Toasts stay until the next page (no timer: a timed message is lost to slow readers and screen-reader users, WCAG 2.2.1); the drawer gets a focus trap and returns focus to its button. Both recorded as decisions | U0 deferred both to U9; a timer would be an a11y regression |

## Steps

0. Documents only: this plan, the change log, the decisions written down, grep for "coming in Phase 13" (one code comment, `CellFormatter`), compare the open contract item with UI_PLAN section 3 (it says "proposed for Phase 13"; matches the scope file).
1. Contract change (core): `ListView.State`, `ListViewFactory`, `ListLinks`, `ListQuery.defaultSize`, tests, fixtures.
2. Properties and metadata: `jpa.admin.page-size`; `@AdminResource(plural)`; `@AdminBadge`; `@AdminLookup`; `BadgeSpec`; startup check bean; tests with valid and invalid values.
3. Controllers and view model: fill the fields, plural in headings, badge and lookup in cells and labels.
4. UI: templates (state, show-all, clear search, hint, skip link), `shortcuts.js`, sticky header, dark-mode review of every page, badge classes.
5. Accessibility and responsive fixes, each with a test.
6. Browser tests and the manual-check list.
7. Preview fixtures for the new states.
8. Tests for every item with a negative test; `HtmlRules`; ARCH-12; host isolation; host-check.
9. Mutation checks (a) to (h), one at a time, byte-identical restore (MD5).
10. Docs: PLAN, UI_PLAN, DECISIONS, CHANGELOG, PROJECT_DOCS, FIXTURES, README.
11. Final checks: full `mvn -B clean verify`, `mvn -B -DskipTests install`, `mvn -B -f host-check/pom.xml verify`, the packaged jar with PowerShell and curl.exe, ports 8080, 8081 and 8090 free, name check.

## Files expected to change

Core: `annotation/AdminResource`, new `annotation/AdminBadge`, `annotation/AdminLookup`, `annotation/package-info`; `metadata/AdminEntity` (plural), `AdminField` (badge, lookup), new `BadgeSpec`, `AnnotationProblems`, `EntityScanner`; `service/ListQuery`, `ListQueryService`, `ListColumns`, `ListColumn`, `CellFormatter`, `Relation`, `RelationChoices`, `DetailQueryService`, `FormQueryService`, `DeleteService`, `RecordWriteService`; `web/ListLinks`, `ListViewFactory`, `EntityController`, `FormController`, `RecordController`, `ConsoleNavigation`, `Plurals`; `web/view/ListView`. Autoconfigure: `JpaAdminProperties`, `JpaAdminAutoConfiguration`, new `JpaAdminAnnotationCheck`. UI: `list.html`, `layout.html`, `column-panel.html`, `views-panel.html`, `cell.html` (only if needed), `pages.css`, `components.css`, `tokens.css` (only if a contrast fix needs it), `views.js`, `nav.js`, new `shortcuts.js`. Preview: fixtures and tests. Sample app: `Product.status` gets `@AdminBadge`, `Product.category` gets `@AdminLookup`; new browser tests. Docs as in step 10.

## Verification rules used throughout

CODE-8: APIs are checked against the jars on the classpath (`javap`) or the official 4.1 documentation, and the packages used are listed in the report. No new dependency. No inline script or style. Never weaken a test (AI-6). Ports 8080, 8081 and 8090 free at the end. Only processes this session started are stopped, by PID.
