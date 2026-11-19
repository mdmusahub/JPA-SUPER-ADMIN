# Phase 12 report: saved views and column visibility

Branch `phase/12-saved-views`, from `main` (tag `v0.1.0-rc.11`). One session on both tracks (the recorded exception to AI-11, as Phases 2 to 11). Nothing is committed. Phase 13 is not started. Plan: `docs/logs/phase-12-plan.md`. Every file and command: `docs/logs/phase-12-change-log.md` (34 entries). Decisions: D-112 to D-117.

**Result in one paragraph.** A visitor can hide columns of a list (a Columns panel that works without script, the choice in the address as a repeated `hide=<column>`), and can save, apply, rename and delete named views of a list (a script-only Saved views panel, stored in the browser, at most 20 per entity). A hidden column is not queried (no select, no join), the CSV export follows the visible columns, a sensitive column can never be hidden and nothing reveals it, and at least one column always stays. No record, annotation, property or dependency changed. The full build, the install and the second host are green, the new behaviour was run in a real browser, and all nine mutation checks fail a test when the code is broken.

**Packages and APIs used (CODE-8).** No new Spring, Boot or Hibernate API. `jakarta.servlet.http.HttpServletRequest#getParameterValues` (the repeated `hide`), `org.springframework.web.bind.annotation` and `org.springframework.web.servlet.ModelAndView` as before (Spring Framework 7.0.10). Thymeleaf 3.1.5: `#strings.listJoin(List, String)` (found with `javap` on `thymeleaf-3.1.5.RELEASE.jar`), `th:with`, `th:each`, `th:checked`, `th:attr`, and the Spring Expression Language collection selection and projection `.?[ ]`, `.^[ ]`, `.![ ]` (`org.springframework.expression.spel.ast.Selection` in `spring-expression-7.0.10.jar`, `ALL`, `FIRST`, `LAST` seen with `javap`; the behaviour is proven by the rendering tests). Tests: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (the Boot 4 package, already used), `org.hibernate.stat.Statistics`. Browser APIs, all exercised in real Chrome: `localStorage`, `JSON`, `URLSearchParams`, `HTMLTemplateElement.content`, `Element.closest`, `encodeURIComponent`, `details` and `summary`.

## 1. Files

`git status --short`: **24 modified, 22 new, none deleted**; `git diff --stat`: 24 files changed, 696 insertions, 64 deletions (new files not counted).

Modified: `CHANGELOG.md`; `docs/DECISIONS.md`, `FIXTURES.md`, `PLAN.md`, `PROJECT_DOCS.md`, `UI_PLAN.md`; core `ListQuery`, `ListQueryService`, `EntityController`, `ExportController`, `ListLinks`, `ListViewFactory`, `RecordController`, test `ExportServiceTest`; UI `pages.css`, `layout.html`, `list.html`; preview `ChromeFixtures`, `FilterFixtures`, `ListFixtures`, `PreviewController`, tests `HtmlRules`, `HtmlRulesTest`, `PreviewPagesTest`.

New: `docs/logs/phase-12-plan.md`, `phase-12-change-log.md`, `phase-13-scope.md` (and this report); core `HiddenColumns`, `HiddenParse`; UI `fragments/column-panel.html`, `fragments/views-panel.html`, `assets/js/views.js`; preview `ViewsFixtures`; tests: core `HiddenColumnsTest`, `ListQueryHiddenTest`, `ListQueryServiceHiddenTest`, `ListViewFactoryHiddenTest`, `ConsoleHiddenColumnsTest`; preview `PreviewColumnPanelTest`, `PreviewViewsPanelTest`, `ViewsScriptTest`; sample-app `AbstractHiddenColumnsPagesTest`, `HiddenColumnsPagesOpenInViewEnabledTest`, `HiddenColumnsPagesOpenInViewDisabledTest`, `SavedViewsBrowserTest`; host-check `ConsoleHidesColumnsOfThisHostTest`.

## 2. Tests and checks

| Module | Before | After |
|---|---|---|
| jpa-admin-core | 3562 (1 skipped) | **3699** (1 skipped, the same one) |
| jpa-admin-autoconfigure | 539 | 539 |
| jpa-admin-ui-preview | 2269 | **2619** |
| sample-app | 933 | **990** (0 skipped: the 11 browser tests ran) |
| reactor total | 7303 | **7847** (+544) |
| host-check (outside the reactor) | 28 | **33** |

- `mvn -B clean verify`: **BUILD SUCCESS** (second run). The first run failed once, in `ConsoleRenderedPagesTest.everyRealPageObeysTheUiRules`: the rename field of the new row template had only an `aria-label`, and the sample app's stricter check wants a `label for`. That was a real defect of the new template, found by an existing test; fixed (change log 27 and 28).
- `mvn -B -DskipTests install`: BUILD SUCCESS. `mvn -B -f host-check/pom.xml verify`: BUILD SUCCESS, 33 tests.
- **Live, with the packaged jar** (`mvn -pl sample-app -am package -DskipTests`, the two properties set for that PowerShell session only, `Start-Process`, `curl.exe` with a cookie jar, stopped by PID 25780): `GET /admin/product` has ID, Category, Created at, Featured, Name, Price, Sku, Status, Stock; `?hide=sku` has no Sku column and the Sku box is ticked; `export.csv?hide=sku` has the header `ID,Category,Created at,Featured,Name,Price,Status,Stock`; `hide=<all nine columns>` shows "Every column was hidden, so all columns are shown. A list needs at least one column." and all nine columns remain; `GET /admin/customer?hide=password` has exactly the headers of the plain page (the masked Password column still there, as dots), no `value="password"` or `hide=password` in the page, no hash, the views panel's column list is `id active createdAt email fullName` (no `password`), and the customer export has no Password column; no session is a 302. Ports 8080, 8081 and 8090 are free; the library modules hold only the seven allow-listed `@Order` lines (ARCH-12, `NameCheckTest` green).
- **Real browser, two ways.** (1) In the build: `SavedViewsBrowserTest` (11 tests) drives headless Chrome or Edge through the existing JDK-only `HeadlessChrome` driver of Phase 7 (no dependency) against the real sample app: save stores exactly the server's validated state, applying the view restores search, filters, sort, page size and hidden columns and survives a reload, awkward values are percent-encoded, a name that looks like markup stays text, every invalid stored view is dropped quietly (unknown column, sensitive column, unknown filter, 61 characters, bad direction, direction without sort, bad size, not an object, repeated name, empty or non-string name, a view that hides every column), invalid JSON and other non-lists are dropped, the cap of 20 and the refusal of the 21st, rename and its clash, delete, blocked storage, no cookie and no request, Enter saves, and no sensitive key in a customer view. They are **skipped with a reason, never passed**, on a machine with no Chrome or Edge. (2) A throwaway DevTools script outside the repository (the scratchpad; Chrome and Node are on this machine but are not project dependencies): 40 behaviour checks against the preview, and 19 layout and keyboard checks: no sideways page scroll in light and dark at 1280 and 390 pixels, Enter opens a panel, Tab reaches the checkboxes, Space toggles, a 2px solid focus ring, the form is a GET with `hide` and no `page`. I read screenshots of the open panels in both themes.

## 3. Mutation checks

Each: back up the file and record its MD5, break one thing, run the focused tests, restore, compare MD5, run again. All nine broke at least one test, all restores were byte-identical, all reruns green.

| | Break | Tests that failed |
|---|---|---|
| (a) | `HiddenColumns.hideable` accepts a masked column | 17 failures: 10 in `HiddenColumnsTest` (by name, by annotation, beside a valid one, same answer as unknown, only-masked entity, every-column rule), 4 in `ConsoleHiddenColumnsTest`, 2 in `ListQueryServiceHiddenTest`, 1 in `ExportServiceTest` |
| (b) | hiding may remove the last visible column | 10 failures: 5 in `HiddenColumnsTest`, 3 in `ConsoleHiddenColumnsTest` (every column stays), 1 in `ListQueryServiceHiddenTest`, 1 in `ExportServiceTest` |
| (c) | `exportPlan` keeps a hidden column | 6 failures: 5 in `ExportServiceTest` and `ConsoleHiddenColumnsTest.theExportHasExactlyTheColumnsTheListShows` |
| (d) | `views.js` accepts an unknown column name in a stored view | `ViewsScriptTest` (the membership check) and `SavedViewsBrowserTest` (the dropped-views case and the sensitive-column case) |
| (e) | `MAX_VIEWS = 21` | `ViewsScriptTest.theLimitsAreTheDocumentedOnes` and `SavedViewsBrowserTest.aViewKeepsAtMostTwenty...` |
| (f) | `views.js` writes a cookie (f1), then sends a request (f2) | f1: `ViewsScriptTest.itNeverWritesACookie...` and the browser cookie test; f2: `ViewsScriptTest.itNeverSendsAnythingAnywhere` and the browser test (the spy saw a `fetch`) |
| (g) | the template hands a masked column's key to the script | `PreviewViewsPanelTest`, `AbstractHiddenColumnsPagesTest.aSavedViewScriptIsHandedNoSensitiveKey`, `SavedViewsBrowserTest` (customer) |
| (h) | `withHidden` keeps the page (no reset to page 1) | 3 failures: 2 in `ListQueryHiddenTest` and `ListViewFactoryHiddenTest.aViewLinkHasNoPageSoApplyingAViewStartsAtThePageOne` |
| (i) | an unencoded view link: `ListLinks` writes `hide=` raw (i1); `views.js` writes `q=` raw (i2) | i1: 27 failures, 26 of them awkward-name cases of `ListViewFactoryHiddenTest` (the link rule throws or the shape is wrong) and one error; i2: `ViewsScriptTest.everyValueInALinkGoesThroughTheEncoder` and the browser encoding test |

## 4. Decisions and deviations

**The hide format: repeated `hide=<column>`** (not comma separated): it reads like `filter.*`, each name is checked alone, "show everything" is the plain address, and a comma-joined value is one name that is not a column. At most 200 values are read; each at most 100 characters.

**Query cost.** A hidden column is not selected, and a hidden relation column is not joined, so the SQL of a page has fewer joins (4 relation joins become 2 when two are hidden, 0 when all four are; shown by counting `left join` in the recorded SQL). The page still costs a count, a page and the one small choices statement per filterable relation (D-63: hiding a column does not remove a filter). One exception, on purpose: a hidden relation column that is the sort key keeps its join for the ORDER BY only and nothing of it is selected. The search still looks in a hidden text column (a condition, not display).

**What a host should know.** Views are per browser: clearing site data removes them, a private window starts with none, they are not synced between browsers or devices or shared between administrators, and a shared link carries the whole view (the URL). The Saved views panel needs JavaScript; the Columns and Filters panels and the address do not. A hidden column is also left out of the export.

**Where the scope file and the approved decisions differ, I followed the decisions and did not stop** (recorded in D-112 point 6; tell me if you want the other reading): the identifier can be hidden (the scope says it must stay; the brief's final check hides every column), an invalid stored view is dropped quietly (the scope says apply the valid part with a notice), and the JSON says `pageSize` (PLAN) while the address says `size`. Because the identifier can be hidden, the row link moves to the row's first visible, non-empty text or number cell, and only on a page that has an identifier column.

Other deviations and findings, with reasons (D-117):
- **No contract change.** `ListView.Column.visible` and the template's skip of invisible columns already existed. Two things the brief asked for need a record field and are **not done**: the "remove-hidden-column links" and the "clear-all" link (there is no "Show all columns" link, and "Clear search" still returns to the plain address, which shows every column), and a backend-built link per saved view (the views are only in the browser, so `views.js` builds them with the server's encoding, as decision 7 allowed). The script reads the validated state from the `href` of the page-size option marked `current`, because no other field carries it. Proposed for Phase 13 as one small contract change (`docs/logs/phase-13-scope.md`).
- **The UI-12 test checker was extended, not weakened:** `HtmlRules` now understands SpEL selection and projection (`.?[ ]` and the like) as scoped to the collection's elements. The collection before the dot is still checked, the forbidden texts are still refused inside the brackets, and 8 new cases prove it still catches an unknown root, a bare unknown name after a selection, and forbidden text. Without it, `type`, `visible`, `current` and `key` in the templates were flagged.
- **One existing assertion changed, for a reason that is not about this feature:** `ExportServiceTest` checked that the embedded width and height (`1001`, `2001`) are not in the file; with more tests in the class the auto-generated ids reached 2001 and collided. The marker is now `7_700_000 + i` and `7_800_000 + i`, checked as `77000` and `78000`. The check means the same.
- **A finding that cost a real defect for a moment:** the editing tool turned `‪`-style escapes in `views.js` into raw invisible characters (the regular expression still looked right). The text test of the script caught it; the script now uses `\x` escapes and character codes, and a test keeps the file ASCII.
- `ListQuery.withoutHidden()` has no caller outside the tests today; it is the building block of the Phase 13 links and I left it.
- The detail and delete routes also read `hide`, so the way back from a record and the answer after a delete return to the list with the same columns (tested for the detail breadcrumb and for a bulk delete that deletes nothing).
- Chrome and Node were used on this machine for throwaway checks outside the repository; nothing from them is in the repo and no dependency was added. The in-build browser test uses only the JDK.

## 5. Not done, to check by hand, and Phase 13

- **Not done:** a pass with a real keyboard (not injected key events), a screen reader, a real private window and a phone; the "Show all columns" link, the "Clear search keeps the columns" behaviour and the dedicated state field (a record change, proposed, not made); PostgreSQL and MySQL runs (Phase 14).
- **Check in a browser** (`mvn -pl sample-app -am package -DskipTests`, set `JPA_ADMIN_USERNAME` and `JPA_ADMIN_PASSWORD`, run the jar, open `/admin/product`): open Columns, tick two columns, Apply; the address has two `hide=`, Back and reload keep them, the table and the next-page link keep them; open Saved views, save "Mine", change the sort, click "Mine": the first page with the saved state; reload; rename, delete (it asks); clear the site data: the views are gone; both themes; phone width (the open panel takes the row); Tab through the panels and Space on a box; script off (the Columns panel still applies; the Saved views panel says it needs script); a private window (no views, saving works for that window only); hide the identifier: a row still opens its record; `?hide=<every column>` shows the notice.
- **Contract change proposed** (not made): in `ListView`, one field for the page's validated state (`stateHref`) and one for "show all columns", and `clearAllHref` and the "Clear search" links keeping `hide`. Options and a recommendation are in `docs/logs/phase-13-scope.md`.
- **Phase 13 scope** is written in `docs/logs/phase-13-scope.md`, not implemented.

## 6. Suggested commits (PowerShell-safe, by path, never `git add .`)

```powershell
# 1. backend: the hidden set in the query, the export and the links
git add jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/HiddenColumns.java `
        jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/HiddenParse.java `
        jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/ListQuery.java `
        jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/ListQueryService.java `
        jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/EntityController.java `
        jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ExportController.java `
        jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ListLinks.java `
        jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ListViewFactory.java `
        jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/RecordController.java `
        jpa-admin-core/src/test
git commit -m "feat: hidden columns in the list query, the export and every list link (Phase 12 backend, D-112, D-113)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 2. UI: Columns panel, Saved views panel, views.js, preview
git add jpa-admin-ui jpa-admin-ui-preview
git commit -m "feat: Columns panel, Saved views panel and views.js, preview fixtures and stored-JSON states (Phase 12 UI, D-114, D-115)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 3. acceptance: demo app (both open-in-view modes, real browser) and the second host
git add sample-app/src/test host-check/src/test
git commit -m "test: Phase 12 acceptance in the sample app, in a real browser and in host-check" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 4. docs
git add CHANGELOG.md docs/DECISIONS.md docs/FIXTURES.md docs/PLAN.md docs/PROJECT_DOCS.md docs/UI_PLAN.md docs/logs/phase-12-plan.md docs/logs/phase-12-change-log.md docs/logs/phase-12-report.md docs/logs/phase-13-scope.md
git commit -m "docs: Phase 12 decisions D-112 to D-117, plan, report and the Phase 13 scope" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

After review, merge `phase/12-saved-views` into `main` and tag it (the earlier phases used `v0.1.0-rc.<phase>`, so `v0.1.0-rc.12`): the maintainer's step (GIT-2).
