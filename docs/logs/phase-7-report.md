# Phase 7 report: detail page, relation links, delete, bulk delete, row checkboxes, duplicate checks

Branch `phase/7-detail-delete` (from `main`, tag `v0.0.6`). One session on both tracks (D-66). Nothing is committed. Plan:
`phase-7-plan.md`. Every file and command: `phase-7-change-log.md` (45 entries). Decisions: D-65 to D-74. Phase 8 is not started;
`phase-8-scope.md` lists its items.

## 1. Files

`git status --short` lists 94 paths before this report was written: 51 modified (`git diff --stat`: 955 insertions,
129 deletions) and 43 new. The full list is in the change log; by area:

**New, backend** (`jpa-admin-core`): `annotation/AdminResource`; `service/`: `IdConverter`, `Relation`, `DetailQueryService`, `DetailRecord`,
`CopySeed`, `DeleteService`, `BulkDeleteIds`, `RecordNotFoundException`, `ActionRefusedException`, `RecordInUseException`,
`TooManyRecordsException`; `web/`: `RecordController`, `DetailViewFactory`, `DeleteMessages`, `ConsoleFlash`, `ConsoleRedirect`.
**Modified, backend:** `AdminEntity`, `EntityScanner`, `ListColumn(s)`, `ListPage`, `ListQueryService`, `CellFormatter`, `ListLinks`,
`ListViewFactory`, `EntityController`, `ConsoleNavigation`, `ConsoleChromeFactory`, `ListView` and `DetailView` (the approved contract
change), `JpaAdminAutoConfiguration`.
**New and modified, UI:** `detail.html`, `fragments/bulk-bar.html`, `bulk.js`; `table.html`, `cell.html`, `list.html`, `layout.html`,
`components.css`, `pages.css`; preview `RecordFixtures`, `ChromeFixtures`, `PageFixtures`, `PreviewController`.
**Tests (new):** core `IdConverterTest`, `DetailQueryServiceTest`, `DeleteServiceTest`, `ConsoleRecordPagesTest`, `ConsoleRecordLinksTest`,
`ConsoleRedirectTest`, `ConsoleFlashTest`, `TransactionalDeleteService` and five neutral test entities; preview `PreviewRecordPagesTest`
and a nested-form rule in `HtmlRules`; sample `AbstractRecordPagesTest` with its two open-in-view subclasses, `RecordPagesLogTest`;
host-check `ConsoleRecordsOfThisHostTest`. **Tests (changed, nothing weakened):** 12 files that built the two changed records or asserted
the old "no link, no action yet" truth, and the three table readers that skip the new checkbox column (entries 24, 37).
**Docs:** `DECISIONS`, `PLAN`, `UI_PLAN`, `FIXTURES`, `PROJECT_DOCS`, `CHANGELOG`, and in `docs/logs`: plan, change log, this report,
`phase-8-scope`.

## 2. Tests

| Module | Before | After |
|---|---|---|
| jpa-admin-core | 2102 | 2495 |
| jpa-admin-autoconfigure | 449 | 449 |
| jpa-admin-ui-preview | 1080 | 1530 |
| sample-app | 591 | 679 |
| host-check (outside the reactor) | 9 | 16 |
| **Reactor total** | **4222** | **5153** |

`mvn -B clean verify`: BUILD SUCCESS (run twice: once after the code, once at the end). `mvn -B -DskipTests install`: BUILD SUCCESS.
`mvn -B -f host-check/pom.xml verify`: BUILD SUCCESS, 16 tests. The live run (entry 44) passed. Ports 8080, 8081 and 8090 are free, no
java process of mine is left (the sample app was stopped by PID 19912).

APIs verified against the pinned jars (CODE-8): `jakarta.persistence` 3.2.0 (`EntityManager`, `PersistenceException`, `Tuple`,
`Expression.in`, the Criteria types), Hibernate 7.4.13 (`ConstraintViolationException` is a `PersistenceException`), Spring Framework
7.0.10 (`RedirectView` constructors and `setExpandUriTemplateVariables`, `@Transactional`).

## 3. Mutation checks

Each: break one thing, run the named tests, see a failure, restore from a saved copy, compare MD5, log. All thirteen were caught.

| | Break | Caught by | Result |
|---|---|---|---|
| a | skip the SEC-11 check in `DeleteService` | `DeleteServiceTest`, `ConsoleRecordPagesTest` | 5 failures |
| b | CSRF ignored for `*/delete` in the security chain | sample `postingADeleteWithoutTheCsrfToken...` | 1 failure |
| c | the delete mapped for GET too | `thereIsNoGetRouteThatDeletesAndEveryDeleteRouteIsAPost` | 1 failure |
| d | a message in the 404 of an unknown id | `ConsoleRecordPagesTest` sameness tests | 15 failures |
| e | id conversion bypassed (`Optional.of(text)`) | id, detail, delete and controller tests | 98 failures and errors |
| f | a sensitive value selected and shown on the detail page | `DetailQueryServiceTest`, `ConsoleRecordPagesTest` | 3 failures |
| g | a relation cell linked without a visible target | `ConsoleRecordLinksTest` | 1 failure |
| h | the cap raised to 100000 | `DeleteServiceTest`, `ConsoleRecordPagesTest` | 2 failures |
| i | per-record delete that swallows a failure (partial bulk) | `DeleteServiceTest`, `ConsoleRecordPagesTest` | 2 failures |
| j | the return address taken from a request field | `theReturnAddressIsNeverTakenFromTheRequest` | 1 failure and 4 errors |
| k | the filter form's `</form>` moved after the table (bulk form nested) | `PreviewRecordPagesTest` (nested-form rule, every record fixture) | 27 failures |
| l | the checkbox label no longer names its row | `PreviewRecordPagesTest` | 2 failures |
| m | `sensitive()` dropped from the copy rule | `DetailQueryServiceTest` | 2 failures |

Two honest notes: (h) with `Integer.MAX_VALUE` the tests crashed the test JVM (a test allocates an array of the cap), which is a crash and
not a clean catch, so a realistic value was used. (c) is caught by the route-inventory test in core; the sample test `aGetNeverDeletes`
was not run against that mutation.

## 4. Decisions, deviations and what a host should know

* **Settled in the plan** (all recorded): all or nothing (D-71); cap **100**, in `BulkDeleteIds` and again in `deleteMany` (D-71);
  selection cleared whenever a page is shown (D-72); "select all matching the filter" out; duplicate = route and checks now, form in
  Phase 8, `canDuplicate` false on real pages (D-73); state-carrying links built by the backend, relation link in `ListViewFactory` (D-70, D-74);
  delete confirmation by a `details` element holding a plain form (D-72).
* **Contract change, asked first, approved** (D-65): `ListView.Actions.bulkDeleteHref`, `DetailView.Actions.deleteHref`.
* **New public annotation** `@AdminResource(readOnly, deletable)` (D-68). Its two names are those of RULES SEC-11 and PLAN section 6, so I did
  not treat it as a rename and did not stop to ask; I list it here so you can veto it.
* **Deviation to confirm: page 0 after a deletion.** The brief said to follow D-62 ("reset when the deletion changes what the list holds"), which
  I read as: page 0 whenever at least one record was deleted, the same page when none was. An admin deleting one row on page 7 lands on page 1.
  One line (`ListLinks#afterDelete`) changes it.
* **A delete reads the whole row** (D-71): I used `EntityManager#remove` so the host's cascades and `@PreRemove` apply; Hibernate therefore loads the
  record, and with its own `jdbc.extract` logger at TRACE a sensitive column of the deleted record would appear in *Hibernate's* log. The console's
  own lines never hold a value (tested). Alternative: a Criteria delete (no load, but no cascades or callbacks). Your call.
* **A sensitive identifier** (a field named like `token`): such an entity is listed, but has no detail page, link, checkbox or delete
  (`AdminEntity.addressable()`), because every address would print the value. Not in the brief; a security consequence of putting ids in URLs.
* **Spring Security's firewall answers a plain 400**, not a console page, for an id with an encoded slash, NUL or `..` (tested, documented). All other
  hostile ids are the console's 404, with the same body (six cases compared byte for byte in the live run).
* **The two GET routes with a second segment exclude the entity name `assets`**, because a controller mapping would have hidden every file directly
  under `{path}/assets/` (D-74).
* **Statement counts** (open-in-view on and off, asserted in the sample app): the detail page is **1** statement and loads no entity and no
  collection; a list page is unchanged (2, plus one per filterable relation: 3 for products and orders, 2 for customers); the links cost none. A
  delete is a small label read, a load and the delete; a bulk delete is one select of the records plus the deletes.
* **Foreign keys:** a refused delete rolls back and shows "The X was not deleted. Other records still depend on it, or the database refused the
  change ..." with no SQL, table name or stack trace; the server log gets one line with the entity address and the exception class. A single
  delete returns to the record's page with the message; a bulk delete returns to the list.
* **Flags:** `readOnly`/`deletable` are enforced in `DeleteService` (403 for a visible entity, 404 for a hidden one); the dashboard's
  `Permissions.delete` and `Actions.canBulkDelete` are real; create, update and export stay false.
* Existing tests changed because the product changed (a relation cell now links, bulk delete is offered, one more table column), listed in
  entries 24 and 37.

## 5. Not done, to check by hand, proposals

* **Not done:** "select all matching the filter" (out by decision); the visible Duplicate link and the create form (Phase 8); an automated
  real-browser test of `bulk.js` (Phase 14: the script is checked by tests of its text and of the markup, not executed).
* **Manual browser pass** (not done in this session, no browser driver): in light and dark and at 390 px: the list with checkboxes (header
  checkbox selects only the page, the bar appears and floats, Escape closes the question, Back clears the selection, with script off the
  bar is a row and the `details` question works); the detail page (long text summary, delete question, keyboard order and focus rings); the
  messages after a delete; nothing scrolls sideways; the fixed bar does not cover the pager on a phone. Preview URLs:
  `/admin-preview/list?state=bulk-delete`, `.../list?state=bulk-escaped`, `.../detail?state=normal`, `.../detail?state=long-text`,
  `.../detail?state=delete-blocked`.
* **Contract change proposed for later:** none beyond D-65. Phase 8 will want `creatable`/`updatable` on `@AdminResource`.
* **Phase 8 scope** (`phase-8-scope.md`): create and edit forms and their validation display (U5), the duplicate form fill, `creatable` and `updatable`,
  each with its expected tests and a check against what Phase 7 leaves in place. Uploads are Phase 9.

## 6. Suggested commits (by path, PowerShell-safe; never `git add .`)

```powershell
# 1 core: annotation, metadata, services, links, factories, controller, wiring
git add jpa-admin-core/src/main jpa-admin-autoconfigure/src/main
git commit -m "Add detail page, delete, bulk delete and relation links (Phase 7 backend)"

# 2 UI: templates, script, styles
git add jpa-admin-ui
git commit -m "Add detail page, row checkboxes and bulk bar (Phase 7, task U4)"

# 3 preview: fixtures and their tests
git add jpa-admin-ui-preview
git commit -m "Add detail and bulk-delete preview fixtures and a nested-form rule"

# 4 tests: core, sample app, second host
git add jpa-admin-core/src/test sample-app host-check
git commit -m "Test id conversion, detail, delete, bulk delete and links (Phase 7)"

# 5 docs
git add docs CHANGELOG.md
git commit -m "Document Phase 7 (D-65 to D-74), U4 and the Phase 8 scope"
```

Each commit message ends with the attribution line the session was given. The first two commits do not build alone (the controller needs the
templates, and the tests need both); if you want every commit to build, squash 1 to 4.
