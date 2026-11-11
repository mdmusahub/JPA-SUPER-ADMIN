# Phase 4 report: list page (both tracks, one session)

Branch `phase/4-list`, from tag `v0.0.3`. Nothing is committed and nothing is staged. Phase 5 is not started. Every
file and command is in `docs/logs/phase-4-change-log.md` (entries 1 to 85); the plan is `docs/logs/phase-4-plan.md`.
The one-session exception to AI-11 is D-50. Decisions D-50 to D-59.

## Correction, 2026-11-11 (added after the Phase 5 re-scope, D-60; the text below is left as it was written)

1. **The filter links were not delivered.** The Phase 4 row of `docs/PLAN.md` promised "link builders for sort, page, page-size
   **and filter links**". `ListViewFactory` still builds an empty `ListView.Filters` (`ListViewFactory.java`, the call
   `new ListView.Filters(List.of(), List.of(), null)`), and no class builds a `filter.*` link: a search for "filter" in
   `ListLinks`, `ListQuery`, `ListViewFactory` and `EntityController` finds nothing else. This report did not list them as
   undelivered; it should have. They are a **Phase 6 item**. Phase 4's status is therefore **"delivered, with the
   filter-links exception"**, not "complete". Every other statement in this report stands.
2. **A clarification of "two statements whatever the number of relation columns"** (here and in the Phase 4 checklist of
   `docs/PLAN.md`): it is true of the number of statements, but each relation column adds one `LEFT JOIN` to the page query, so the
   cost of a page still grows with the number of relation columns. Phase 12 will stop querying hidden columns and Phase 14 will
   measure and bound the cost (D-60).
3. Section 5's list of what was "not done" omitted the filter links and the Phase 5 plan row it quotes was replaced by D-60; see
   `docs/logs/phase-5-rescope-report.md`.

**Result:** `mvn -B clean verify` passes for all 7 modules, **BUILD SUCCESS**, **3165 tests** (2510 before, +655),
0 failures, 0 errors, 0 skipped. `host-check` passes (7 tests). All 11 mutation checks are caught and every file was
restored byte-identically. The real jar behaves as specified over real HTTP and in a real browser. The browser pass found
three CSS defects, each fixed with a regression test written first.

## Environment note

Maven is not on this machine's PATH. I used the copy bundled with IntelliJ (Maven 3.9.16, Temurin 17.0.20.1), as in
Phase 3. `mvn -B -DskipTests install` writes the snapshot jars to `~/.m2` (needed by `host-check`, as in the CI step).

## 1. Files

From `git status --short` (nothing staged). `git diff --stat`: 23 tracked files changed, +682 / -168, plus the new files.

**Modified (23):** `CHANGELOG.md`, `docs/DECISIONS.md`, `docs/FIXTURES.md`, `docs/PLAN.md`, `docs/PROJECT_DOCS.md`,
`docs/UI_PLAN.md`; `host-check/.../ConsoleListsThisHostsEntitiesTest.java`; `jpa-admin-autoconfigure/.../JpaAdminAutoConfiguration.java`
and test `DiscoveryAutoConfigurationTest.java`; `jpa-admin-core/.../web/ConsoleViews.java`, `web/EntityController.java`, tests
`ConsoleEntityPagesTest.java`, `src/test/resources/META-INF/persistence.xml`; `jpa-admin-ui/.../css/pages.css`,
`fragments/icons.html`; `jpa-admin-ui-preview/.../ChromeFixtures.java`, `PageFixtures.java`, `PreviewController.java`, test
`PreviewPagesTest.java`; `sample-app` tests `ConsoleDiscoversEntitiesTest.java`, `ConsoleRenderedPagesTest.java`, `MockBrowser.java`.

**Deleted (1):** `jpa-admin-ui/.../templates/jpa-admin/entity-notice.html` (the temporary notice).

**Created:**
- core main: `service/` `CellFormatter`, `ListColumn`, `ListColumns`, `ListPage`, `ListQuery`, `ListQueryService`; `web/` `ListLinks`,
  `ListViewFactory`, `Plurals`.
- core tests: `service/` `ListQueryTest`, `ListQueryServiceTest`, `SearchSafetyContract`, `ListQueryServiceSearchSafetyTest`;
  `web/` `ConsoleListPageTest`, `ListViewFactoryTest`; `testsupport/` `ListTestPersistence`, `RecordingStatementInspector`,
  `listmodel/` (10 files: the neutral entity model).
- UI: `templates/jpa-admin/list.html`, `fragments/table.html`, `cell.html`, `pager.html`.
- preview: `ListFixtures.java` (main), `PreviewListPagesTest.java` (test).
- sample-app tests: `AbstractListPagesTest`, `ListPagesOpenInViewDisabledTest`, `ListPagesOpenInViewEnabledTest`, `ListPagesLogTest`.
- docs: `docs/logs/phase-4-plan.md`, `phase-4-change-log.md`, `phase-4-report.md`.

Outside the repository (scratchpad): `mutate.py`, `docs_update.py`, `verify-curl.ps1`, `verify-host.ps1`, `browser-pass.mjs`,
screenshots. The project still has no Node (CODE-6); the Node script is a throwaway.

## 2. Test counts

| Module | Before | After | New |
|---|---|---|---|
| jpa-admin-core | 1505 | 1816 | +311 |
| jpa-admin-autoconfigure | 446 | 449 | +3 |
| jpa-admin-ui-preview | 365 | 599 | +234 |
| sample-app | 194 | 301 | +107 |
| **Total** | **2510** | **3165** | **+655** |
| host-check (outside the reactor) | 5 | 7 | +2 |

Full `mvn -B clean verify`: BUILD SUCCESS, Enforcer rule passed. `mvn -B -DskipTests install`, then
`mvn -B -f host-check/pom.xml verify`: BUILD SUCCESS, 7 tests. ARCH-12 name check: passes; a `grep -rnw` of the four library
modules finds exactly the 7 allow-listed `@Order` lines, and no new entry was needed. The Phase 3 follow-up (an explicit page
test with `open-in-view=true`) is done: `ListPagesOpenInViewEnabledTest` and `...DisabledTest` run every list-page test.

## 3. Mutation checks

Each: break one thing, run the targeted tests (BUILD FAILURE = caught), restore from the backup, compare MD5 (identical every
time). Script: `scratchpad/mutate.py`.

| | Mutation | Caught by | Result |
|---|---|---|---|
| a | remove the page clamp | `ListQueryServiceTest.aPagePastTheEndIsTheLastPage...`, `ConsoleListPageTest` | 12 failures |
| b | allow an unknown sort column | parser only: `ListQueryTest.anyOtherSortKeyMeansNoSort`; service only: `ListQueryServiceTest.theServiceDropsAnUnsortableKey...`; both | 20; 5 + 2 errors; 32 + 15 errors |
| c | search a sensitive field | `ListQueryServiceTest.aSensitiveValueCanNeverBeFoundBySearch`, `ConsoleListPageTest` | 2 failures |
| d | render the raw password cell value | `ListQueryServiceTest.aSensitiveColumnIsMasked...`, `...NotInTheSql...`, `ConsoleListPageTest` | 4 failures |
| e | drop the id tie-breaker | `...WithTheIdentifierAsTieBreaker`, `...PagesWithoutRepeatsOrGaps`, `theSqlOrdersBySortColumnThenByIdentifier` | 3 failures |
| f | remove the search length limit | `ListQueryTest`, `ListQueryServiceTest`, `ConsoleListPageTest` | 4 failures |
| g | build a link without encoding the query | `ListViewFactoryTest`, `ConsoleListPageTest` | 20 failures + 12 errors |
| h | read a collection into the row | `ListQueryServiceTest.aCollectionIsNeitherReadNorShown`, columns tests | 6 failures |
| i | remove the masked cell's no-value check | `ViewMaskingTest.maskedCellRejectsAnyValueHrefTitleOrTone` | 1 failure |
| j | show the notice again on the list page | core `ConsoleListPageTest` (1); sample-app `noListPageCarriesTheRemovedNotice...` (1, run alone) | caught in both |
| k | search by concatenation, in a test-only copy | the search-safety contract run against `UnsafeConcatenatedSearchTest` (created, run, deleted) | 4 failures + 4 errors; file gone, never in main |

Notes: (b) and (j) were run in parts on purpose, to show each layer guards itself (parser and service; core and real flow). (i)
breaks the frozen record's constructor check, which the Phase 0 masking test already guards; the service half of "masked cells carry
no value" is guarded by (d).

## 4. Decisions, deviations, and what a host should know

**Decisions** (D-50 to D-59): one session and the early search and sort; page size and page number; search; sort and tie-breaker;
relation labels by join, no link; how a value becomes a cell; invalid values fall back; the notice is deleted and "New" and export
are absent (not disabled); the sensitive-column refinement; test support. Packages used (CODE-8, checked with `javap` on the
4.1.2 jars): `jakarta.persistence.criteria` and `.metamodel` (3.2.0), `org.hibernate.stat.Statistics` and
`org.hibernate.resource.jdbc.spi.StatementInspector` (7.4.13, tests), `org.springframework.transaction.annotation.Transactional`,
`org.springframework.boot.test.system` output capture. **No new dependency.**

**Things in the brief that did not match the repo, and what I did:**
- The seed has **25 products**, so the default page size gives one page. For "page 2 and 3" and "last page has 5 rows" I used
  `?size=10` (10, 10, 5). There is **no iPhone** in the seed, so I used `?q=phone` (Headphones, Phone stand) and show `?q=iphone`
  returning the empty state. Tests add their own rows (30 products and an iPhone) and remove them again. The seed is unchanged.
- `docs/PLAN.md` assigns search and sort to Phase 5; the brief asked for them now. Built now, PLAN updated (D-50).
- The brief said "export button disabled or absent": **absent**. So is "New record". Nothing on the page is disabled.

**Deviations and choices to review:**
- **Relation labels use a LEFT JOIN, not a batch query** (the brief said "for example"). Two statements per page, proven by
  counting SQL and by Hibernate statistics; no entity or collection is ever loaded. A relation shows its label with **no link**
  until a detail page exists (Phase 7), so no link ends in a 404.
- **Two Phase 3 test rules were refined, not weakened** (D-58, change-log entries 29 and 44): Phase 3 asserted that a sensitive
  field's *name* was on no page; from Phase 4 it is a column header with dots (PLAN: "dots everywhere"). The value rule is
  unchanged and now has far more tests (SQL, bound values, extracted values in the log, links, sort keys, search).
- **`jpa.admin.page-size` is not added** (public surface). The default is one constant.
- **Enum badge tones** come from a small word list until `@AdminBadge`. **Plurals** are a plain English rule (`Person` would be
  `Persons`) until `@AdminResource`.
- **The preview's `entity-active` chrome and `entity` page were removed** and `member-active` added; `ConsoleViews` lost an unused overload.
- **A masked cell is masked even for a null value**, so the dots never say whether a value exists.
- I accidentally staged the deletion of `entity-notice.html` with `git rm --cached` and unstaged it at once; the index is clean.

**Three defects found by the browser pass, all in the UI I wrote, all fixed test-first** (entries 75 to 77): (1) at phone width the
customer list made the whole page 522px wide, because the hidden text in masked and empty cells escaped the scroll box
(`position: relative` on `.table-scroll`); (2) e-mail addresses broke after every few letters (`overflow-wrap: anywhere` became
`break-word`); (3) rows were uneven because the table shrank columns (`min-width: max-content`). The tests that pin them read the
stylesheet, which is a weak guard: the real check is the browser pass, which you should repeat (section 5).

**What a host application should know:**
- Each list page runs **two queries**: a `count(*)` and one page query (with a join per to-one column). The sidebar's counts add at
  most one count per entity per 5 seconds. On a very large table the count is not free, and `lower(column) like '%text%'` cannot
  use an ordinary index: a search on a huge table can be slow. Nothing is queried outside console pages.
- The list reads inside `@Transactional(readOnly = true)` and never relies on open-in-view (tested both ways).
- The search covers every visible `String` attribute of the entity, including long text columns. A `@Lob` text column may not
  support `lower(...)` on every database; that would be a 500 page for that entity (logged, no detail in the page). I could only
  test H2.
- A request cannot make the list fail through its parameters; a failing database still shows the console's generic 500 page.

## 5. Not done, to check manually, contract

- **Contract change: none.** No record changed; no change proposed.
- **Not done (later phases):** filters, detail, create, edit, delete, bulk delete, export, saved views, `jpa.admin.page-size`,
  `@AdminLookup`, `@AdminBadge`, `@AdminResource`, keyboard shortcut `/`.
- **README:** untouched (it did not mention the table view).
- **Visual checks for you** (I did a headless-Chrome pass, below; screenshots are in my scratchpad, not in the repo): the look
  against `docs/prototype/admin-console.html`; light and dark; phone width (the table scrolls inside its box; nothing scrolls
  sideways); keyboard (Tab order, focus rings, Enter in the search box); Back and reload after a sort click; JavaScript off.
  Preview URLs: `/admin-preview/list?state=normal`, `...state=masked-column`, `...state=all-cell-types`, `...state=many-columns`,
  `...state=long-text`, `...state=unicode`, `...state=empty-no-matches`. The image cell has no picture in the preview (no image asset
  exists before U6): it shows its alt text.

### Manual verification: exact output

`mvn -B -pl sample-app -am package -DskipTests`, then (PowerShell, session-only fake credentials of 12+ characters)
`java -jar sample-app/target/sample-app-0.1.0-SNAPSHOT.jar`, then `curl.exe` with a cookie jar:

```
login page has a CSRF field: True
POST /admin/login: 302 -> http://localhost:8080/admin
GET /admin/product -> 200; rows: 25; heading: Products; count: 25 records; range: Showing 1–25 of 25
  headers: ID | Category | Created at | Featured | Name | Price | Sku | Status | Stock
  first row: 1 | Books | 2026-11-11 18:26 | Yes | Paperback novel | 5.99 | SKU-0001 | Draft | 0
size=10 page=0 -> 200, 10 rows, first id 1, Showing 1–10 of 25
size=10 page=1 -> 200, 10 rows, first id 11, Showing 11–20 of 25
size=10 page=2 -> 200, 5 rows, first id 21, Showing 21–25 of 25
size=10 page=3 -> 200, 5 rows (past the end: the last page), first id 21; same rows as page 2: True
size=10 page=99 -> 200, 5 rows, Page 3 of 3
pages 0, 1 and 2 are different: True
default size page=99 -> 200, 25 rows, Page 1 of 1
GET /admin/product?page=abc -> 200, 25 rows
GET /admin/product?page=-1 -> 200, 25 rows
GET /admin/product?page=99999999999999999999 -> 200, 25 rows
GET /admin/product?size=7 -> 200, 25 rows
GET /admin/product?size=abc -> 200, 25 rows
GET /admin/product?sort=nosuch -> 200, 25 rows
GET /admin/product?dir=sideways -> 200, 25 rows
q=iphone -> 200, rows: 0 (the seed has no iPhone), empty state: True
q=phone -> 200, rows: 2: Headphones, Phone stand
q=PHONE -> 200, rows: 2 (case does not matter)
q=%27 -> 200, rows: 0, box: &#39;
q=%22 -> 200, rows: 0, box: &quot;
q=http%3A%2F%2Fx -> 200, rows: 0, box: http://x
q=it%27s%20%22x%22 -> 200, rows: 0, box: it&#39;s &quot;x&quot;
q=%25 -> 200, rows: 0, box: %
q=_ -> 200, rows: 0, box: _
q=x%27%20or%20%271%27%3D%271 -> 200, rows: 0, box: x&#39; or &#39;1&#39;=&#39;1
q=<5000 characters> -> 200   (checked separately: 100 characters in the box, empty state shown)
sort=price&dir=desc -> 200; prices: 96.99, 89.99, 82.99, 78.99, 75.99, 71.99 ...; highest first: True
sort=price&dir=asc  -> 200; prices: 5.99, 8.99, 12.99, 15.99, 19.99, 22.99 ...; lowest first: True
sort=password (a masked column) -> 200; any aria-sort on the page: False; 'sort=password' in any link: False
GET /admin/customer -> 200, rows: 12, masked cells: 12, contains 'demo-placeholder-secret': False, contains 'demo-placeholder': False, header 'Password' present: True
GET /admin/customer?q=demo-placeholder -> 200, rows: 0, empty state: True
GET /admin/category -> 200, rows: 6
GET /admin/tag -> 200, rows: 10
GET /admin/order -> 200, rows: 25
GET /admin/internalauditentry -> 404, rows: 0
GET /admin/no-such-entity -> 404, rows: 0
sidebar link /admin -> 200
sidebar link /admin/category -> 200
sidebar link /admin/customer -> 200
sidebar link /admin/order -> 200
sidebar link /admin/product -> 200
sidebar link /admin/tag -> 200
links on the list page: 30; outside /admin: 0
```

(Checked separately in the saved pages: the sorted header carries `aria-sort="descending"` and links to `dir=asc`; the 5,000-character
search shows 100 characters in the box; the only mention of "password" on the customer page is the column header.)

Browser pass (headless Chrome over DevTools, 1280x900 and 390x844, light and dark, after the three fixes): no page scrolls sideways
(phone: product table 865px in a 360px box, customer 664px in a 360px box, both scrolling inside); the only origin contacted is
`http://localhost:8080`; no console or CSP message; every Tab stop has a visible focus ring (sidebar search, entity links, user chip,
theme button, search box, Search, the table box, 8 sort links, 3 page-size links, Next, Last); Enter in the search box gives
`/admin/product?q=phone&sort=name&dir=desc&size=10` (sort and size kept, 2 rows); sort click, second click, Back and reload give
`?sort=price&dir=asc`, `?sort=price&dir=desc`, back to `asc`, reload `asc` with `aria-sort="ascending"` on Price; with JavaScript off the
GET form, 8 sort links and the pager still work (`?q=phone&size=10`, 2 rows).

Host-check by hand on 8090 (`java -jar host-check/target/host-check-1.0.0-SNAPSHOT.jar`, rebuilt from clean):

```
POST /admin/login: 302 -> http://localhost:8090/admin
GET /admin -> 200; sidebar: Author, Book, Publisher
GET /admin/book -> 200; 5 rows; headers: ID | Author | Publisher | Secret token | Title
  first row: 1 | Author 1 | Publisher 1 | ••••••••Hidden value | Book 1
  contains 'not-a-real-secret': False
GET /admin/author -> 200; 3 rows; headers: ID | Name
GET /admin/publisher -> 200; 2 rows; headers: ID | Name
GET /admin/book?sort=title&dir=desc -> 200; titles: Book 5, Book 4, Book 3, Book 2, Book 1
GET /admin/book?q=book 3 -> 200; rows: 1: Book 3
GET /admin/book?q=not-a-real-secret -> 200; rows: 0 (a sensitive field is never searched)
GET /admin/product (a demo-shop name, not this host's) -> 404
```

Both processes were stopped by PID; ports 8080, 8081, 8090 (and my DevTools port 9333) are free; the user's own Chrome was not touched.

## 6. Suggested commits (PowerShell-safe, never `git add .`)

```powershell
# 1. Backend: query service, link builder, controller, wiring
git add jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ListLinks.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ListViewFactory.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/Plurals.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/EntityController.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ConsoleViews.java jpa-admin-autoconfigure/src/main
git commit -m "feat: list query service, list page controller and link builder (Phase 4)"

# 2. Backend tests
git add jpa-admin-core/src/test jpa-admin-autoconfigure/src/test sample-app/src/test host-check/src/test
git commit -m "test: cover paging, search, sort, masking, relations, links and query cost for the list page"

# 3. UI task U2 and preview
git add jpa-admin-ui jpa-admin-ui-preview/src/main
git commit -m "feat: add the list page, table, cells and pager; remove the temporary notice (U2)"

# 4. Preview tests
git add jpa-admin-ui-preview/src/test
git commit -m "test: render every list fixture and pin the table's scroll and wrapping rules"

# 5. Docs and logs
git add CHANGELOG.md docs/DECISIONS.md docs/FIXTURES.md docs/PLAN.md docs/PROJECT_DOCS.md docs/UI_PLAN.md docs/logs/phase-4-plan.md docs/logs/phase-4-change-log.md docs/logs/phase-4-report.md
git commit -m "docs: record Phase 4 decisions, update plan and add phase logs"
```

Each commit message ends with your attribution line if you use one. After review: merge and tag `v0.0.4`.
