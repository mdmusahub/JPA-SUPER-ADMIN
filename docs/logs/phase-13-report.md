# Phase 13 report: polish

Branch `phase/13-polish`, from `main` (tag `v0.1.0-rc.12`). One session on both tracks (the recorded exception to AI-11, as Phases 2 to 12, D-118). **Nothing is committed. Phase 14 is not started.** Plan: `docs/logs/phase-13-plan.md`. Every file and command: `docs/logs/phase-13-change-log.md` (64 entries). Decisions: D-118 to D-126. Phase 14 scope (written, not implemented): `docs/logs/phase-14-scope.md`.

**Result in one paragraph.** The list view-model carries the validated state of the page (`ListView.state`: the address without a page, the default page size, a "Show all columns" link that exists exactly when a column is hidden, and a "Clear search" link that keeps the hidden columns), so the saved views script no longer reads the page-size link. `jpa.admin.page-size` (10, 25, 50, 100) is validated at startup and travels through the parser, the links and the script. `@AdminResource(plural)`, `@AdminBadge` (four tones, never a colour) and `@AdminLookup` exist, are checked at startup with a report that repeats nothing the host wrote, and are re-checked where they are used so a sensitive property can never become a label. The `/` key (enhancement only), a plain "Skip to search" link, a sticky table header, a drawer focus trap and several focus, contrast and structure fixes are in. The build now runs **30 real-browser tests** (layout of every page type at 1280 and 390 pixels in both themes, contrast, accessibility structure, the content security policy, behaviour and keyboard) and all ran. The full build, the install and the second host are green; ten mutation checks all fail a test when the code is broken. One new dependency: none.

**Packages and APIs used (CODE-8).** No new Spring, Boot, Hibernate or Thymeleaf API in the library. `java.lang.annotation` for the two new annotations; `jakarta.persistence.metamodel` as before (Hibernate 7.4.13, Spring Boot 4.1.2, Spring Framework 7.0.10, Thymeleaf 3.1.5, Servlet 6.1). Thymeleaf: a `null` literal as a fragment argument, `th:attr`, `th:with`, `th:if` (standard dialect, as before). Tests: `org.springframework.boot.logging.LoggingSystem` and `LogLevel` (Boot 4.1 core), `org.springframework.boot.SpringApplication`, `OutputCaptureExtension`, `javax.imageio.ImageIO` (JDK). Browser: DevTools protocol commands `Input.dispatchKeyEvent`, `DOM.setFileInputFiles`, `Emulation.setFocusEmulationEnabled`, `Emulation.setDeviceMetricsOverride`, `Emulation.setEmulatedMedia`, `Page.setBypassCSP` (test-only, used once by the older layout test, never by the CSP test); page APIs `KeyboardEvent`, `Element.checkVisibility()`, `Element.closest`, `matchMedia`, `securitypolicyviolation`, `getComputedStyle(el, '::placeholder')`, CSS `position: sticky`, `:focus-within`, `:focus-visible`. I did not run `javap` on these: each was **exercised for real** (it compiled, or it ran in Chrome 157), which is what the tests assert.

## 1. Files

`git status --short`: **62 modified, 1 deleted, 36 new entries** (several are folders). `git diff --stat`: 63 files changed, 973 insertions, 178 deletions (new files not counted).

| Area | Modified | New | Deleted |
|---|---|---|---|
| Core, main | `AdminResource`, `annotation/package-info`, `AdminEntity`, `AdminField`, `EntityScanner`, `Names`, `CellFormatter`, `DetailQueryService`, `ListColumns`, `ListQuery`, `ListQueryService`, `Relation`, `ConsoleNavigation`, `EntityController`, `FormController`, `ListLinks`, `ListViewFactory`, `RecordController`, `web/view/ListView` | `AdminBadge`, `AdminLookup`, `BadgeSpec`, `AnnotationProblems` | `web/Plurals` |
| Autoconfigure, main | `JpaAdminProperties`, `JpaAdminAutoConfiguration` | `JpaAdminAnnotationCheck` | |
| UI | `components.css`, `pages.css`, `nav.js`, `views.js`, `layout`, `list`, `dashboard`, `detail`, `form`, `error`, `column-panel`, `field-input`, `views-panel` | `shortcuts.js` | |
| Preview | `ListFixtures`, `PreviewFilterPagesTest`, `PreviewListPagesTest`, `ViewsScriptTest` | `PreviewPolishPagesTest` | |
| Core tests | `ListQueryServiceHiddenTest` (one order-dependent assertion), `ConsoleEntityPagesTest`, `ListViewFactoryTest`, `Samples`, `ViewHrefRuleTest`, `ViewNullRejectionTest`, `persistence.xml` | `BadgeSpecTest`, `AnnotationProblemsTest`, `BadgeAndLookupServiceTest`, `ListQueryDefaultSizeTest`, `ListViewFactoryStateTest`, `ListViewStateTest`, `ToneTestPersistence`, `testsupport/tonemodel/*` (5) | |
| Autoconfigure tests | | `JpaAdminPageSizePropertyTest`, `AnnotationStartupTest`, `tonemodel/*` (6) | |
| Sample app | `Product` (annotations), 8 existing tests (`AbstractListPagesTest`, `ConsoleDiscoversEntitiesTest`, `ConsoleHidesEntitiesTest`, five `*LogTest`) | `PolishPagesTest`, `AbstractPageSizePagesTest`, `PageSizeFiftyPagesTest`, `PageSizeTenPagesTest`, `PageSizeStartupTest`, `SqlLogLevels`, `ConsoleBrowser`, `PageLayoutBrowserTest`, `ContrastBrowserTest`, `AccessibilityBrowserTest`, `CspBrowserTest`, `ConsoleBehaviourBrowserTest` | |
| host-check | `ConsoleListsThisHostsEntitiesTest` | `ConsolePolishOfThisHostTest` | |
| Docs | `README.md` (rewritten as the project README, the author lines kept at the end), `CHANGELOG.md`, `docs/DECISIONS.md`, `FIXTURES.md`, `PLAN.md`, `PROJECT_DOCS.md`, `UI_PLAN.md` | `docs/images/` (4 screenshots), `docs/logs/phase-13-plan.md`, `phase-13-change-log.md`, `phase-13-report.md`, `phase-14-scope.md` | |

## 2. Tests and checks

| Module | Before (Phase 12) | After | Change |
|---|---|---|---|
| jpa-admin-core | 3699 (1 skipped) | **4033** (1 skipped, the same one) | +334 |
| jpa-admin-autoconfigure | 539 | **581** | +42 |
| jpa-admin-ui-preview | 2619 | **2788** | +169 |
| sample-app | 990 | **1039** (0 skipped) | +49 |
| reactor total | 7847 | **8441** | +594 |
| host-check (outside the reactor) | 33 | **37** | +4 |

- `mvn -B clean verify`: **BUILD SUCCESS** (final run: core 31 s, autoconfigure 45 s, preview 1 min 47 s, sample app 3 min 18 s). Earlier runs found real fallout, each fixed: a sidebar expectation (plural), my own Javadoc examples that used the demo words `Customer` and `Category` in a library module (ARCH-12), two tests that pinned the exact markup of the search form, one test that checked the whole page for the word `Tags`, and an **order dependence** between the log tests (see section 4). Details: change log entries 38 to 45.
- `mvn -B -DskipTests install`: **BUILD SUCCESS**. `mvn -B -f host-check/pom.xml verify`: **BUILD SUCCESS**, 37 tests.
- **Live, with the packaged jar** (PowerShell, `Start-Process`, `curl.exe` with a cookie jar, stopped by PID 27888; the bad-property run exited on its own): `--jpa.admin.page-size=7` stops startup with "jpa.admin.page-size must be one of 10, 25, 50, 100" and the password is not in the output; login answers 302; `GET /admin/product?size=50` has 25 rows (all there are) and the title "Products"; `?size=7` falls back to 25 rows; the status badges are `Archived=badge-grey, Draft=badge-amber, Published=badge-green` with no inline style; the page references `<script src="/admin/assets/js/shortcuts.js" defer>` (served as `text/javascript`, status 200) and has 0 inline scripts; the "Skip to search" link, `aria-keyshortcuts="/"` and the key-cap hint are there; the policy header is `default-src 'none'; script-src 'self'; style-src 'self'; ...`; the dashboard names `Products` and `Categories`; `?hide=sku&q=zzzz` gives "Show all columns" `/admin/product?q=zzzz` and "Clear search" `/admin/product?hide=sku`; the password is in no page. **Ports 8080, 8081 and 8090 are free; no java process is left.** The library modules hold only the seven allow-listed `@Order` lines (ARCH-12; `NameCheckTest` is green).
- **Which browser tests ran or skipped.** All of them ran: Chrome 157.0.8103.52 (`C:/Program Files/Google/Chrome/Application/chrome.exe`), JDK 17.0.20, headless, through the JDK-only `HeadlessChrome` driver; the sample-app module reports 1039 tests, 0 skipped. The 30 browser tests are `PageLayoutBrowserTest` (1: 52 page measurements), `ContrastBrowserTest` (1), `AccessibilityBrowserTest` (1), `CspBrowserTest` (3), `ConsoleBehaviourBrowserTest` (12), `ListLayoutBrowserTest` (1) and `SavedViewsBrowserTest` (11). On a machine with no Chrome or Edge each is skipped with the reason "SKIPPED, not passed: no Chrome or Edge was found" and is never reported as passed. Set `-Dtest.chrome=<path>` or `CHROME_BIN` to point at a browser.

## 3. Mutation checks

Each: back up the file and record its MD5, break one thing, run the focused tests, restore (the MD5 is compared), run them again. All ten broke at least one test, all restores were byte-identical, all reruns green. (A first attempt of (a) showed the restored run still red: `Files.copy` on Windows keeps the old modification time, so Maven did not recompile the restored source. The harness now sets the time to now, content unchanged, and (a) was run again from scratch.)

| | Break | Tests that failed |
|---|---|---|
| (a) | `BadgeSpec` accepts any tone, a colour code included | 66 tests, **19 failures**: 17 cases of `BadgeSpecTest.anythingThatIsNotAPaletteNameIsRefusedAndNeverUsable`, `BadgeSpecTest.aFieldHasABadge...`, `AnnotationProblemsTest.everyWrongEntryIsReported...` |
| (b) | `jpa.admin.page-size` accepts 7 | 42 tests, **3 failures**: `anyOtherSizeStopsStartupAndNamesTheProperty(7)`, `aSizeThatIsNotOnOfferFailsTheRealStartup...`, `aBadSizeIsReportedTogether...` |
| (c1) | `@AdminLookup`: the label is the named property without the usability check (no fallback) | 34 tests, **14 errors** in `BadgeAndLookupServiceTest` (the property that does not exist or is sensitive was used) |
| (c2) | an empty relation label shows `null` instead of the record's number | **1 failure**: `aLookupNamesTheRelationsLabelAndAnEmptyValueShowsTheRecordsNumber` |
| (d) | the detail page's label colour is the light theme's literal, no dark variable | **1 failure**: `ContrastBrowserTest` (`/product/1` in dark) |
| (e) | the `aria-label` of the search landmark removed | **3 failures** in two modules: `PreviewFilterPagesTest`, `PreviewListPagesTest`, `AccessibilityBrowserTest` (unlabelled landmark) |
| (f) | `PageLayoutBrowserTest.WIDTHS = {1280}` (390 ignored) | **1 failure**: "the browser measured both a desktop and a phone width" |
| (g) | the test-only `Page.setBypassCSP` turned on in the browser | **1 failure**: `CspBrowserTest` ("neither injected style took effect") |
| (g2, extra) | the real policy gets `'unsafe-inline'` for styles | 24 tests, **5 failures**: 3 in `ConsoleSecurityTest`, 2 in `CspBrowserTest` |
| (h) | the "Show all columns" link is built when no column is hidden | 140 tests, **28 failures and 60 errors**: the record refuses it (`state.showAllColumnsHref is present exactly when a column is hidden`) |

## 4. Decisions and deviations

All in `docs/DECISIONS.md` D-118 to D-126. The ones to know:

- **The contract change** is the one the brief approved (`ListView.state`). `ListQuery` (a service record) also gained `defaultSize`; the older constructors keep 25.
- **"Clear search" keeps the hidden columns and nothing else** (the default of the brief). Sort and page size still reset, as "clear" has meant since Phases 4 and 6.
- **Per-entity page size** (`@AdminResource(pageSize)`, named in PLAN and the scope file) is **not added**: it was not in the brief's list of public additions. **`@AdminBadge` offers four tones, not six:** the scope file said `BLUE`; `Cell.Tone` has `NONE, GREEN, AMBER, RED, GREY` and no `BLUE`, and adding it is a record change beyond the brief (`.badge-blue` exists in the CSS, unused). **Only `/` is added** as a shortcut (the plan's scope table also says "new record"; the brief and the prototype differ).
- **The sidebar now names tables in the plural** (`Categories`), as the prototype does and as the brief says for the dashboard ("headings and the dashboard"). That changes three existing expectations (Phase 3's `ConsoleDiscoversEntitiesTest`, `ConsoleEntityPagesTest`, host-check's list test); they were updated for this deliberate change and nothing else about them. Tell me if you want the sidebar singular again; it is one line in `ConsoleNavigation`.
- **Toasts are not timed** (a timer loses the message for slow readers and screen readers); I did not add a dismiss button (nothing requires it).
- **README:** the existing `README.md` was a personal profile page. It is now the project README (what it is, screenshots, quick start, configuration, annotations, docs), with your profile lines kept as "About the author" at the end, including the `<your email or LinkedIn>` placeholder. The quick start says the artifact is not published yet and to `mvn -DskipTests install` and use `0.1.0-SNAPSHOT` until Phase 14.
- **Findings that cost a real defect, all fixed with a test:** the dark sidebar placeholder was **3.79 to 1** (the browser's own grey, no token colours it); the sidebar search and date and date-time fields had **no focus ring** (Chrome keeps focus on a part inside a date field, so `:focus-visible` does not reach it); an upload field printed its **help and error twice with the same ids** (`h-image`, a Phase 9b defect that breaks `aria-describedby`); the list's search form had no name.
- **A finding about the tests themselves:** `ChartPagesLogTest`, `ListPagesLogTest` and `RecordPagesLogTest` have identical properties, so they share one cached Spring context. Any new context with other properties created in between (my two page-size tests) re-runs Boot's logging initialisation and the reused context then logs nothing: `RecordPagesLogTest` failed for a reason that has nothing to do with what it checks. Each of the five log tests now sets the levels it needs in `@BeforeEach` (`SqlLogLevels`; the form test asks for no value logging because it writes secrets). Also `ListQueryServiceHiddenTest` assumed that the first generated identifier is odd; it now measures from the lowest identifier (same assertion, order-independent).
- **Measured contrast.** In the browser, every visible text, placeholder and chart label of nine page types, both themes, with every panel open: lowest **5.16 to 1** (normal text needs 4.5, large text 3), 5.70 to 1 for the focus ring in the light theme and 9.83 to 1 in the dark one. The token pairs, computed:

| Pair | Light | Dark |
|---|---|---|
| ink on panel | 17.79 | 14.46 |
| ink on page background | 16.45 | 15.67 |
| muted text on panel | 6.00 | 6.92 |
| muted text on page background | 5.55 | 7.50 |
| accent text on accent tint (green badge, links) | 5.18 | 5.87 |
| accent text on panel (links) | 5.95 | 7.71 |
| amber badge | 5.93 | 9.19 |
| red badge on tint | 6.05 | 8.29 |
| red text on panel (errors) | 6.57 | 8.99 |
| grey badge | 6.98 | 9.22 |
| blue alert text on tint | 5.57 | 8.27 |
| button text on filled accent | 5.21 | 5.21 |
| **input border on panel (non-text, WCAG 1.4.11 asks 3.0)** | **1.52** | **1.67** |

  **Known limits, not changed:** the input border is below 3 to 1 in both themes (each field has a visible label; darkening it changes every form, so it is a question for you, `docs/logs/phase-14-scope.md`); the measurements use the default accent `#0d9f6e`, and a very light or very dark `jpa.admin.primary-color` is not covered.
- **What the browser tests cannot cover:** a screen reader's speech and reading order; a physical keyboard or phone; dragging a file onto the drop zone; the native file dialog and the native "leave this page?" dialog (only the event that asks for it is checked); a real private window; hover colours and focus colours beyond the ring; print; the look of a design; a custom accent colour; PostgreSQL and MySQL (Phase 14). Chrome and Edge only: Firefox and Safari were not run.

## 5. Manual checks (exact steps) and Phase 14

Start the demo: `mvn -pl sample-app -am package -DskipTests`, set `JPA_ADMIN_USERNAME=admin` and `JPA_ADMIN_PASSWORD=<12 or more characters>` in that PowerShell session, `java -jar sample-app/target/sample-app-0.1.0-SNAPSHOT.jar`, open http://localhost:8080/admin. Stop it with Ctrl+C.

1. **Screen reader** (NVDA with Chrome or Firefox on Windows; VoiceOver with Safari on a Mac). On `/admin/product`: list the landmarks (Main navigation, Entities, Breadcrumb, "Search and filter products", main); Tab once and twice and hear "Skip to content" and "Skip to search"; press Enter on the second and hear the search box named "Search products" with the shortcut `/`; open Filters, Columns and Saved views and hear them expanded and collapsed; read the table (column headers, "sorted ascending" on a sorted header, row checkbox "Select Product 3"); tick a row and hear the bulk bar's count; page with the pager ("Page 2 of 3"). On `/admin/product/1/edit`: required fields announced, then clear Name and Save: the error summary is announced and focus moves to it. On `/admin/category/1/edit`: choose a file and hear the upload status; try a text file and hear the error.
2. **Physical keyboard.** Tab through `/admin/product` from the top: every stop shows a ring; Enter and Space open a panel; `/` jumps to the search box and is typed normally inside a field; Escape closes the delete question and the phone drawer. Check with Caps Lock, Shift+Tab and a non-English layout (is `/` reachable?).
3. **A real phone** (iOS Safari and Android Chrome, portrait and landscape, with the text size at 200%): open the drawer and close it with the button, the backdrop and the back gesture; the three panels; scroll the table sideways inside its box while the page does not move; pinch-zoom to 200%: no text overlaps; the sticky header is off below 900px, as designed.
4. **Drag and drop of a file** on `/admin/category/1/edit`: drag a PNG over the drop zone (the "dragging" look), drop it (progress, then "uploaded", the preview), then drag a text file (the plain sentence). Also the native file dialog: choose and cancel.
5. **The native "leave this page?" dialog:** on an edit form change a field and click a sidebar link: the browser asks. After Save, or "Yes, discard changes", it must not ask.
6. **A real private window** (Firefox private, Chrome incognito): saved views save and apply for that window only; with storage blocked the panel says so and the rest works.
7. **Script off** (browser setting, or a profile with JavaScript disabled): search, filters, columns (Apply columns), sort, paging, the forms, the delete confirmation and the no-script upload path work; the Saved views panel shows its one sentence; "Skip to search" works; no `/` hint is shown.
8. **A custom accent:** start with `--jpa.admin.primary-color=#ffd400` and again with `#1a1a2e`: check buttons, links and badges are readable in both themes (the contrast test only measures `#0d9f6e`).
9. **Reduced motion and print:** set the operating system to reduce motion (nothing animates); print preview of a list and a detail page (there is no print stylesheet: note what you see).
10. **Still open from earlier phases:** open the exported CSV in Excel and LibreOffice (Phase 11); the PostgreSQL and MySQL runs (Phase 14).

**Phase 14 scope** is in `docs/logs/phase-14-scope.md` (Testcontainers on PostgreSQL and MySQL, the cost and search measurements, the orphan-upload decision, CI that runs the browser tests and fails on the release job when no browser is found, the security-test pass, the MkDocs site, the release steps, and the questions to settle first). Nothing of it is implemented.

## 6. Suggested commits (PowerShell-safe, by path, never `git add .`; no trailer)

```powershell
# 1. backend: the contract change, the page size and the three annotations
git add jpa-admin-core/src/main jpa-admin-autoconfigure/src/main
git commit -m "feat: ListView.state, jpa.admin.page-size, @AdminBadge, @AdminLookup and @AdminResource(plural) (Phase 13 backend, D-118 to D-122)"

# 2. backend tests, the second host
git add jpa-admin-core/src/test jpa-admin-autoconfigure/src/test host-check/src/test
git commit -m "test: Phase 13 backend tests: state, default page size, badges, lookups, plural, startup checks, second host"

# 3. UI: shortcut, skip link, panels, sticky header, focus and contrast fixes, preview states
git add jpa-admin-ui jpa-admin-ui-preview
git commit -m "feat: the / shortcut, skip to search, show all columns, sticky table header and the accessibility fixes (Phase 13 UI, D-123, D-124)"

# 4. sample app and the real-browser tests
git add sample-app/src
git commit -m "test: Phase 13 acceptance in the sample app and 30 real-browser tests (layout, contrast, accessibility, CSP, behaviour)"

# 5. docs, README and screenshots
git add README.md CHANGELOG.md docs
git commit -m "docs: Phase 13 decisions D-118 to D-126, README with screenshots, plan, report and the Phase 14 scope"
```

After review, merge `phase/13-polish` into `main` and tag it (`v0.1.0-rc.13`): the maintainer's step (GIT-2).
