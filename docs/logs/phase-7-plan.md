# Phase 7 plan: detail page, relation links, delete, bulk delete, row checkboxes, duplicate checks

Written 2026-11-17 on branch `phase/7-detail-delete` (from `main`, tag `v0.0.6`). One session on both tracks (the recorded exception to AI-11 of
Phases 2 to 6, D-33, D-43, D-50, D-64; recorded again as D-66). The change log of this phase is `phase-7-change-log.md`.

Sources read: `CLAUDE.md` (root, core, ui), `docs/PLAN.md`, `RULES.md`, `UI_PLAN.md`, `FIXTURES.md`, `PROJECT_DOCS.md`, `DECISIONS.md` (D-1 to D-64),
`docs/logs/phase-7-scope.md`, `phase-6-report.md`, `phase-5-report.md`, and the code of `jpa-admin-core`, `jpa-admin-autoconfigure`, `jpa-admin-ui`,
`jpa-admin-ui-preview`, `sample-app`.

## 0. The one contract change (asked first, approved by the maintainer on 2026-11-17)

A delete form posts to a fixed route, and after the delete the visitor must come back to the list **with the list state** (search, filters, sort, page,
size). The templates may not rebuild the query, the console sends `Referrer-Policy: no-referrer` (so no Referer arrives), and the frozen records had no
field for a form address that the backend builds. Approved change (D-65):

* `ListView.Actions` gets `bulkDeleteHref`: present exactly when `canBulkDelete` is true. It is the bulk-delete route plus the list state in its query.
* `DetailView.Actions` gets `deleteHref`: present exactly when `canDelete` is true. The delete route plus the list state the detail page was opened with.

Both are built by `ListLinks` (the one writer of a query string), checked by `Hrefs` in the record constructors. On the POST the controller re-reads the
state **with the same parser as the list** (`ListQueryService.parse` and `parseFilters`), so what came back is a validated `ListQuery`, and the return
address is rebuilt by `ListLinks`: it is never taken raw from a request. Every fixture, sample and test that constructs the two records is updated.
No other record changes. (The Duplicate and Edit links stay template-built fixed routes; Thymeleaf's parameter syntax encodes the id.)

## 1. Decisions the scope file left to this plan (each is recorded in `DECISIONS.md`)

| # | Question | Choice | Reason |
|---|---|---|---|
| a | Bulk delete: partial or all or nothing | **All or nothing**, one `@Transactional` method. Ids that are malformed or have no record are *ignored and counted* (they are not failures); an existing record that cannot be removed (foreign key) makes the **whole batch** roll back and nothing is deleted | A half-done batch is the worst result for an admin: the page no longer says which rows went. The recommendation of the scope file |
| b | Cap | **100 ids per request** (`DeleteService.MAX_BULK_DELETE`), equal to the largest page size, so the UI can never exceed it (the header checkbox selects only the page), while a hand-made request can. Enforced in `BulkDeleteIds` (the parser's result record, so no code path builds more) and again in `DeleteService.deleteMany` (the same constant). Refused with a message, nothing deleted | Same pattern as the Phase 6 filter cap (D-62): the limit is stated, tested, and not only in the parser |
| c | Selection across page, filter, search or sort changes | **Cleared.** The checkboxes live in the page; every one of those changes is a navigation (a link or the GET form), so a new page starts with nothing selected. A browser "Back" restores form state, so `bulk.js` recounts on `pageshow` and the server only deletes the ids actually posted | A selection that survived a filter change would delete rows the visitor can no longer see |
| d | "Select all rows matching the filter" | **Out of Phase 7.** The header checkbox selects the rows of this page only, and says so in its label | Different feature; the cap and the all-or-nothing rule would both need a design |
| e | Duplicate | **Phase 7: the route `GET {path}/{entity}/new?copyFrom={id}` and its checks, the id conversion, the copy seed that never holds a sensitive value, the template link and `DetailView.Actions.canDuplicate`. The real controller sets `canDuplicate = false`** until Phase 8 delivers the form, so no real page shows a link that leads nowhere (D-57). A valid `copyFrom` answers 303 to the source's detail page with an INFO notice; unknown, malformed, hidden, read-only: the console 404 / 403 | The route checks and the seed are testable now; the form is Phase 8. Phase 8 flips one flag and replaces the redirect |
| f | Who builds state-carrying links | **The backend.** `ListLinks` builds the return-to-list link, the detail link of a row, the relation link, the delete and bulk-delete addresses. `ListViewFactory` adds the relation and row links (not the core service, which does not know the base path). The templates keep only links that carry no state | D-29 follow-up; one writer of a query string |
| g | A delete confirmation without script | **A `details` element with a plain form inside it** ("Delete record" summary, a sentence, a "Yes, delete this record" button). Same for the bulk bar. Script only enhances (count, header checkbox). No confirmation page, so no new record or template | UI-5, UI-13. A `details` works in every browser without script |
| h | Page after a delete | **Page 0 when at least one record was deleted** (the list's content changed: the old page number would mean other rows, the rule of D-62 for any change of what the list holds); the same page when nothing was deleted. Search, filters, sort and size always stay. The list itself still clamps a page past the end (D-51) | The brief's instruction |
| i | Flash across a redirect | A console-only session attribute `JPA_ADMIN_FLASH` (class `ConsoleFlash`), read once by `ConsoleChromeFactory` and removed. Plain text, escaped on output. Not Spring's `FlashMapManager` (a host bean) | D-35: everything the console keeps in the shared session has its own name; logout already clears `JPA_ADMIN_*` |
| j | 403 or 404 for a read-only entity | Hidden, unknown, unsupported entity and unknown or malformed id: **identical console 404**. A visible entity that is read-only or not deletable: **403** (it is visible, so nothing is revealed) | SEC-2, SEC-11 |

## 2. Annotation (public API, names taken from RULES SEC-11 and PLAN section 6)

`@AdminResource(readOnly = false, deletable = true)` on an entity, new in package `io.github.mdmusahub.jpaadmin.annotation`, `@Inherited`, documented. Only
these two attributes now; the others of PLAN section 6 come with their phases. Effective delete permission: `!readOnly && deletable`. `readOnly` also
means no create and no update (Phase 8 will read it). The scanner reads it once into `AdminEntity.access()` (a nested record, ARCH-1). The four-argument
`Permissions` of the dashboard gets `delete = entity.canDelete()`; create, update and export stay false until their phases. This is a **new public
annotation**, but its two names are the ones SEC-11 and PLAN section 6 already name, so it is not a rename (no pause needed); it is listed in the report.

## 3. Files (backend track, then UI track)

### jpa-admin-core (backend)
| File | Change |
|---|---|
| `annotation/AdminResource.java`, `package-info.java` | new annotation |
| `metadata/AdminEntity.java`, `EntityScanner.java` | `Access(readOnly, deletable)`, `canDelete()`, the nine-argument constructor stays (defaults to full access); scanner reads the annotation |
| `service/IdConverter.java` | id text to the id's Java type (`Long`, `Integer`, `Short`, `Byte`, `BigInteger`, `UUID`, `String`); package-private |
| `service/Relations.java` | the `Relation` record and its lookup moved out of `ListQueryService` (shared with the detail service) |
| `service/DetailQueryService.java`, `DetailRecord.java`, `CopySeed.java` | the one-record read (one statement, attributes only) and the copy seed |
| `service/DeleteService.java`, `BulkDeleteIds.java`, exceptions `RecordNotFoundException`, `ActionRefusedException`, `RecordInUseException`, `TooManyRecordsException` | delete, bulk delete, SEC-11 |
| `service/ListPage.java`, `ListColumn.java`, `ListQueryService.java` | rows carry the relation target ids and columns carry the target's URL name and an identifier flag (internal records, old constructors stay) |
| `web/view/ListView.java`, `DetailView.java` | the two approved hrefs |
| `web/ListLinks.java` | `detail`, `delete`, `bulkDelete`, `afterDelete`, `copy` (one writer) |
| `web/ListViewFactory.java` | row id link, relation links, `bulkDeleteHref`; new overload with the bulk-delete flag |
| `web/DetailViewFactory.java`, `web/DeleteMessages.java`, `web/ConsoleFlash.java`, `web/ConsoleRedirect.java`, `web/RecordController.java` | the detail, delete, bulk-delete and copy routes |
| `web/EntityController.java`, `web/ConsoleNavigation.java`, `web/ConsoleChromeFactory.java` | real flags, flash, permissions |

### jpa-admin-autoconfigure
`JpaAdminAutoConfiguration`: beans for `DetailQueryService`, `DeleteService`, `RecordController`; no new property, no new dependency.

### jpa-admin-ui (UI) and jpa-admin-ui-preview
`detail.html`, `fragments/table.html` (checkbox column), `fragments/bulk-bar.html`, `fragments/cell.html` (row id as a link), `list.html` (bulk form),
`layout.html` (loads `bulk.js`), `assets/js/bulk.js`, `assets/css/pages.css` and `components.css`; preview: `DetailFixtures`, `ListFixtures` additions,
`PreviewController` page `detail`, tests.

### sample-app, host-check
Integration tests (`DetailPagesTest` and the delete tests, both open-in-view modes); `host-check` gets a detail and delete test of its own entities (no
library change).

## 4. Order of work (the module build after each step)

0 housekeeping; 1 this plan; 2 id conversion; 3 detail service and page (record, factory, controller, 404 sameness, open-in-view); 4 relation and row
links; 5 permission flags, delete service, controller endpoint, redirect with state; 6 bulk delete; 7 UI (templates, JS, CSS); 8 duplicate route;
9 preview fixtures; 10 tests; 11 mutation checks (a) to (m); 12 docs; 13 final checks and the live curl run; `phase-8-scope.md`.

## 5. Id conversion rules (D-67)

Applied by `IdConverter` to every id that arrives as text (path, bulk `ids`, `copyFrom`). Result: the id as the Java type of `AdminEntity.idAttribute()`'s
field, or nothing. Nothing is handled exactly like "no such record" (the same 404).

* `Long`, `Integer`, `Short`, `Byte` (and the primitives): optional leading `-`, digits only, at most 19 characters, parsed with `parseLong`/`parseInt`,
  and the canonical form must equal the text (so `007`, `+1`, `-0`, ` 1` are rejected: one address per record). Out of range is rejected.
* `BigInteger`: the same shape, at most 40 digits.
* `UUID`: exactly 36 characters, the strict 8-4-4-4-12 hex layout (`UUID.fromString` alone accepts `1-1-1-1-1`).
* `String`: not empty, at most 255 characters, no control character, not `.` or `..`; `/` cannot occur in a path segment. (A `String` id with a `/` is not
  addressable in v0.1: Tomcat refuses `%2F`; its row has no link. Listed in the limitations.)
* Any other id type (a `char`, a date, a number type not listed): nothing converts, so the entity's detail and delete answer 404 (listed in the limitations).
* The value always reaches the query as a bound parameter (SEC-3); conversion is a shape check, not the defence.

## 6. Detail page rules (D-69)

* One read-only transaction, one tuple statement: the identifier, every visible non-sensitive attribute, for each to-one relation a `LEFT JOIN` selecting
  the target's id and label (the list's rule), and the entity's own label attribute. No entity, proxy or collection is created (ARCH-3, ARCH-4); the filter
  choice query is not called.
* A sensitive field is `Cell.masked()` and is **not selected**. A hidden field is absent. A to-many, embedded or binary field is shown with its label and
  the text "(not shown)". Text keeps line breaks and is cut at 5,000 characters with an ellipsis; the page breaks long words (`overflow-wrap`).
* Title: the label attribute's value when there is one (single line, cut at 120), else `Entity #id`. The breadcrumb is Dashboard, the list (a link with the list
  state the page was opened with), the title.
* `canEdit` false (Phase 8), `canDuplicate` false (decision e), `canDelete` = `entity.canDelete()`; `deleteHref` from `ListLinks`.

## 7. Tests to write (negative ones in bold)

Core: id conversion per type and hostile text; detail service (every kind of field, **sensitive value in no response nor statement**, hidden absent, collection
not selected, one statement, no entity loaded); **hostile ids**; link tests (relation link only for a visible target, **none for a hidden or label-less
target**, none for a null relation, base path, `Hrefs`, escaped label, no extra statement, the id equals the filter dropdown's id); delete service (removes,
**unknown/hidden/read-only/non-deletable refused in the service**, **foreign key keeps the row and gives the friendly exception with no SQL**, label escaped in
the message); bulk (only the ids sent, ids of no record ignored and counted, **over the cap refused**, **one failing row rolls back the batch**, duplicates
counted once); controller (CSRF is a security-chain matter and is tested in sample-app; **a GET deletes nothing**; redirects carry the list state, page 0 after a
deletion, same page after none, return address rebuilt not raw); duplicate route (**unknown/hidden/read-only copyFrom**, seed never holds a sensitive value).
Sample app (both open-in-view modes): detail of every entity, 404 sameness (unknown id, `abc`, `1 or 1=1`, `../x`, hidden entity), **POST without CSRF is 403**,
real delete with token, foreign key failure (friendly), bulk delete, cap, statement counts of list and detail. Preview: each new fixture renders with its key
content; **no `<form>` contains another**; the template scan for hand-built query strings gains `filter.` and the return link; HTML rules for changed templates.
Host-check: detail and delete of its own entities.

## 8. Mutation checks (13): a to m as listed in the brief, each restored byte-identically (MD5).
