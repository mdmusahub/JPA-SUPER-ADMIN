# Phase 5 re-scope report (documentation only)

Branch `phase/5-rescope`, from `main` at `v0.0.4`. No Java, POM, template, CSS, JavaScript or test file was touched; nothing is
committed or staged; Phase 5 is not started. Log: `docs/logs/phase-5-rescope-log.md`. New decision: **D-60**.

**In one paragraph:** Phase 4 delivered everything the original Phase 5 contained (search, sort, direction, paging, shareable
URLs, no sensitive field searched), each proven by tests I found and checked in the repository. It did **not** deliver one thing
its own row promised: the **filter links** (the `Filters` part of `ListView` is empty and no class builds a `filter.*` link). That
moves to Phase 6. The revised Phase 5 is "Sort and search quality", four small items, no new dependency, no contract change, no new
property. Everything else that the Phase 4 report deferred moved to Phases 6, 7, 12, 13 and 14.

## 1. Inventory

Evidence was checked in the code and in the test sources (all 35 cited test methods exist, found by grep; paths are under
`jpa-admin-core/src/test/.../service|web`, `jpa-admin-ui-preview/src/test/.../preview`, `sample-app/src/test/.../sample`).

### 1a. The original Phase 5 row: "`q`, `sort`, `dir` validated against metadata. Done when: correct results, shareable URLs, search never reads sensitive fields"

| Item | Status | Evidence |
|---|---|---|
| `q` validated against metadata | DELIVERED | `ListQueryService.plan()` takes the searchable set from `AdminEntity` fields (visible, non-sensitive, `String`); `ListQuery.normalizeSearch`; `ListQueryServiceTest.aHiddenFieldIsNotSearched`, `relationsNumbersAndOtherKindsAreNotSearched`; `SearchSafetyContract.anInjectionAttemptFindsNothingAndItsTextIsInNoStatement`; mutations c, f, k |
| `sort` validated against metadata | DELIVERED, two layers | `ListQuery.parse` and `Plan.checked`; `ListQueryTest.anyOtherSortKeyMeansNoSort`; `ListQueryServiceTest.anUnsortableKeyIsIgnoredAndTheListIsInIdentifierOrder`, `theServiceDropsAnUnsortableKeyEvenWhenTheQueryWasNotBuiltByTheParser`; mutations b1, b2, b3 |
| `dir` validated | DELIVERED | `ListQueryTest.aDirectionThatIsNotAscOrDescIsAscendingWhenTheSortKeyIsValid`; `ConsoleListPageTest.aBadDirectionIsAscendingWithoutAnError` |
| Correct results | DELIVERED in core, **PARTIAL on real data** | Core: `ListQueryServiceTest.everySortableColumnSortsAscendingAndDescendingWithTheIdentifierAsTieBreaker` (every column kind, both directions, ties by id), search match tests. Sample app: only `product` by `price`, `featured`, `name`, `id` and `customer` by `email` are sorted (`grep sort=` over `sample-app/src/test`). **Missing:** a sweep of every sortable column of the five sample entities, and a null rule (no `Nulls` handling in `ListQueryService`; the demo has nullable `Order.note` and `Category.description`) |
| Shareable URLs | DELIVERED; Back and reload **PARTIAL** | State is in the URL: `ListLinks`; `ListViewFactoryTest.everyValueIsPercentEncodedAndComesBackAsTyped` (26 values); `AbstractListPagesTest.theSearchIsKeptWhenSortingAndPaging`, `everyLinkLiesUnderTheConsolePathAndNothingTypedIsInAnAddressUnencoded`. Back and reload were proven only by the manual headless-Chrome pass (script outside the repo): no automated regression test |
| Search never reads sensitive fields | DELIVERED | `ListQueryServiceTest.aSensitiveValueCanNeverBeFoundBySearch` (SQL shows no sensitive column), `AbstractListPagesTest.thePasswordColumnCannotBeSortedOrSearchedAndItsValueIsNeverInALink`, `ListPagesLogTest.thePasswordValueIsNeverReadSoItIsNeverLoggedAndTheStatementsNeverNameTheColumn`; mutation c |

### 1b. The Phase 4 row (what to build) and "Done when"

| Item | Status | Evidence |
|---|---|---|
| Query service | DELIVERED | `ListQueryService`; `aPageCostsExactlyTwoQueriesWhateverTheRowsAndRelationColumns`; sample `aListPageCostsTwoStatementsWhateverTheRowsAndLoadsNoEntityAndNoCollection` |
| Formatter | DELIVERED | `CellFormatter`; `ListQueryServiceTest.valuesAreFormattedAsDisplayTextNotAsJavaObjects` |
| List page | DELIVERED | `list.html`, `table.html`, `cell.html`, `pager.html`; `PreviewListPagesTest`; sample pages |
| Paging and page sizes | DELIVERED | `ListQueryServiceTest` (sizes, clamp, partial last page); `AbstractListPagesTest.pageTwoAndPageThreeAreTheOtherRows...` |
| Masking | DELIVERED | `AbstractListPagesTest.thePasswordColumnIsMaskedInEveryRowAndTheValueIsNowhere`; `ListPagesLogTest`; mutations d, i |
| Link builders: sort, page, page-size | DELIVERED | `ListLinks`, `ListViewFactoryTest`; mutation g |
| **Link builders: filter links** | **NOT DELIVERED** | `ListViewFactory.java:42` builds `new ListView.Filters(List.of(), List.of(), null)`; no "filter" in `ListLinks`, `ListQuery` or `EntityController`. The Phase 4 report did not list this as undelivered (an omission) |
| Done when: right columns and values, password always masked | DELIVERED | `AbstractListPagesTest.theProductListHasTheDefaultTwentyFiveRowsOfFiftySixAndTheRightColumns`, `thePasswordColumnIsMaskedInEveryRow...` |
| Done when: links percent-encode every value (spaces, slashes, quotes, unicode, literal `http://x`) | DELIVERED | `ListViewFactoryTest.everyValueIsPercentEncodedAndComesBackAsTyped`, `aLiteralUrlInTheSearchStaysDataInTheQuery`; sample `aSearchForAnAddressStaysDataAndIsNotALink` |
| Done when: open-in-view true and false | DELIVERED | `ListPagesOpenInViewEnabledTest` and `...DisabledTest` run every test of `AbstractListPagesTest` |
| Phase 4 checklist: phone width and both themes | DELIVERED manually, **PARTIAL by test** | Manual headless pass in `phase-4-report.md`; in the repo only stylesheet-reading tests (`theTableScrollBoxContainsAndClipsAbsolutelyPositionedCellContent`, `aTextCellKeepsItsNaturalWidthAndOnlyBreaksARunawayWord`) |

## 2. Gap list and where each item went

| # | Gap | Source | Moved to | Why there |
|---|---|---|---|---|
| G1 | Filter links (`filter.*`, chip removal, clear all) and `ListView.Filters` data | Phase 4 row, not delivered | **Phase 6** | They are the filter feature; Phase 6 "done when" now requires them percent-encoded like the Phase 4 links |
| G2 | Finding records by a relation's label (search does not look at relations) | D-52 | **Phase 6** | D-52 already said "a Phase 6 filter" |
| G3 | Relation cell is not a link; row checkboxes and bulk delete absent | D-54, report | **Phase 7** | Needs the detail page; D-29 already schedules the route revisit there |
| G4 | One `LEFT JOIN` per relation column: cost grows with the number of relation columns | Report ("two statements", but a join each) | **Phase 14** (measure, bound, document or change strategy); **Phase 12** (a hidden column is not queried) | Needs a real database, real sizes and column visibility |
| G5 | Search is a full scan (`lower(col) like '%text%'`); `count(*)` is not free | Report, host-facing limits | **Phase 14** | Measure on PostgreSQL and MySQL at size; index advice in the docs. No code change is proposed |
| G6 | `lower(...)` on large-text columns, the `!` escape, null ordering on PostgreSQL and MySQL: all list tests ran on H2 only | Report | **Phase 14** | Phase 14 already owns Testcontainers; the list cases are now written into its row |
| G7 | Phone width proven by stylesheet-reading tests and a manual pass outside the repo; Back, reload and keyboard order have no automated test | Report | Manual responsive pass: **Phase 13** (U9). Automated real-browser test: **Phase 14** | Needs the whole product; an automated browser test needs a driver dependency, so approval then (AI-8) |
| G8 | "New record" and export are absent, not disabled | Report | **Phase 8** and **Phase 11** | Already placed in PLAN; nothing to move. Absent-not-disabled stays (D-57) |
| G9 | `jpa.admin.page-size` property not added | Report, D-51 | **Phase 13** | Judgement call (see section 5): fits `@AdminResource` page size and keeps Phase 5 to four items |
| G10 | Enum tone word list, English plural rule, relation label rule | D-54, D-55, D-57 | **Phase 13** | `@AdminBadge`, `@AdminResource`, `@AdminLookup` are Phase 13 annotations |
| G11 | `/` shortcut, sticky table header | U2 status | **Phase 13** (U9) | Already in UI_PLAN |
| G12 | Image cell has no picture in the preview | Report | **Phase 9** (U6) | Needs the uploader |
| G13 | Null ordering depends on the database (H2 first, PostgreSQL last in ascending) | New, found while checking (grep: no `Nulls`; JPA 3.2 offers it, checked with `javap`) | **Phase 5** item 2 | A sort-quality rule that can be set and tested without a new database |
| G14 | Relation columns cannot be sorted | D-53 | **Phase 5** item 1 (proposed; lifts D-53's relation clause) | Sort quality; the join already exists |
| G15 | Sort not proven on the real sample entities' columns; `aria-sort` not proven per sortable header on real pages | Inventory 1a | **Phase 5** item 3 | Real-data proof of Phase 4's sort |
| G16 | The search does not say what it covers; a search cut at 100 characters is cut silently | New | **Phase 5** item 4 | Search quality, no contract change (`Empty.hint` and `PageChrome.flash` exist) |

## 3. The revised Phase 5: "Sort and search quality"

No new dependency, no contract change, no new property. Verified: `jakarta.persistence.criteria.Nulls` and
`CriteriaBuilder.asc/desc(Expression, Nulls)` exist in the 3.2.0 jar already on the classpath; `ListColumn` (which forbids a sortable
relation today) is an internal `service` record, not one of the frozen `web.view` records.

| # | Item | Done when |
|---|---|---|
| 1 | **Relation-aware sort.** A relation column whose target has a label (D-54) becomes sortable, ordered by that label through the existing join: no extra join, no extra statement. A relation shown as `#id` (no label, or a hidden target) stays unsortable. *Proposed, lifts the relation clause of D-53.* | Sorting the sample products by category gives label order in both directions, ties by product id; the header has `aria-sort` and a link that flips the direction; the page still costs two statements; no sensitive attribute of the target is selected or ordered by; `sort=` for a relation without a label is ignored |
| 2 | **One null rule** in every sort, the same on every database: nulls last in both directions (*proposed*), with `Nulls`, always followed by the identifier. | The sample orders (nullable `note`) and a core test entity list nulls last for ascending and descending, with a stable order inside the nulls; the recorded SQL carries the rule (cross-database proof is Phase 14) |
| 3 | **A sort proof on real data.** | One parameterised sample-app test: every sortable column of the five entities, both directions, with and without a search, with open-in-view true and false. Rows in the right order; exactly the sorted header has the matching `aria-sort` and a link to the other direction; all other sortable headers say `none`; nothing errors |
| 4 | **Say what the search covers.** The empty state names the searched columns (never a sensitive or hidden one) and says so when nothing is searchable; a search cut at 100 characters tells the visitor. | Asserted in core and in the sample app; no sensitive or hidden label appears in any of these messages |

**Considered and rejected, with reasons** (also in D-60): multi-column sort (`ListView.sort` and `dir` are single values: contract
change, v0.2 at the earliest); "bad sort plus search never gives a 500" (already covered: `badOrOddParametersNeverBreakThePage`
in both open-in-view modes, `ConsoleListPageTest.everythingBadAtOnceStillRenders`, mutations a, b, f); sort direction and tie-breaker
in general (covered for every column kind and by mutation e; only the sample-entity sweep and `aria-sort` per header remain, which is
item 3); search error messages (there are none, bad input falls back, D-56; only the transparency gap remains, item 4); a browser
re-check of Phase 4 (done in Phase 4; automated version is Phase 14); searching numbers or relation labels (D-52, Phase 6).

## 4. Changed documents

```
$ git status --short
 M CHANGELOG.md
 M docs/DECISIONS.md
 M docs/PLAN.md
 M docs/PROJECT_DOCS.md
 M docs/UI_PLAN.md
?? docs/logs/phase-5-rescope-log.md
?? docs/logs/phase-5-rescope-report.md     (this file, created after the output above was taken)

$ git diff --stat
 CHANGELOG.md         |  1 +
 docs/DECISIONS.md    |  1 +
 docs/PLAN.md         | 33 +++++++++++++++++++++------------
 docs/PROJECT_DOCS.md | 15 +++++++++++++++
 docs/UI_PLAN.md      | 12 +++++++-----
 5 files changed, 45 insertions(+), 17 deletions(-)
```

Confirmed: nothing under `jpa-admin-core`, `jpa-admin-autoconfigure`, `jpa-admin-ui`, `jpa-admin-ui-preview`, `jpa-admin-starter`,
`sample-app`, `host-check`, `.github` or the root `pom.xml` changed or was created; nothing is staged; all files use LF.

What changed where: `docs/PLAN.md` (Phase 4 row; Phase 5 row replaced; Phase 6, 7, 12, 13, 14 rows extended; Phase 4 checklist
ticked and a carried-out list; new Phase 5 checklist; the "Parallel tracks" browser-check bullet; the Manual row of the testing
table; timeline rows 2 and 6), `docs/UI_PLAN.md` (section 7 rows U2, U3, U4, U8, U9 and a note that Phase 5 has no UI task),
`docs/PROJECT_DOCS.md` (B12: a "Known limits and the phase that addresses each" list; nothing removed), `docs/DECISIONS.md` (D-60
appended, no number changed), `CHANGELOG.md` (one line under `[Unreleased]`).

## 5. Decisions I need from you

None of these stopped the work (each has a stated default), but each is yours:

1. **Relation-aware sort lifts a recorded decision.** D-53 says relations are not sortable. Item 1 reverses that clause. If you say no,
   Phase 5 has three items.
2. **The null rule.** I proposed "nulls last in both directions". The alternative is "follow the database". Choose before Phase 5 starts.
3. **Where `jpa.admin.page-size` goes.** Phase 13 (my choice: it comes with the `@AdminResource` page size and keeps Phase 5 to four items) or
   Phase 5 (a small property, but a fifth item and a new public property).
4. **CHANGELOG housekeeping I did not do** because it is outside the brief: the Phase 4 entries still sit under `[Unreleased]` although
   `v0.0.4` is tagged (Phase 3's were rotated into `[0.0.3]` at the start of Phase 4). Say if you want them rotated into `[0.0.4] - 2026-11-11`.
5. **For Phase 14, later:** an automated real-browser test needs a driver dependency (for example Playwright or Selenium). That is an
   AI-8 approval at that time; nothing is added now.

Two honest notes: the Phase 4 report said a page costs "two statements whatever the number of relation columns", which is true of the
statement count but hid the join per column (now stated in B12 and in the Phase 14 row); and it did not list the filter links as
undelivered.

## 6. Suggested commit

Message: `docs: re-scope Phase 5 after Phase 4 delivered search and sort (D-60)` (end it with your attribution line if you use one).

```powershell
git add CHANGELOG.md docs/DECISIONS.md docs/PLAN.md docs/PROJECT_DOCS.md docs/UI_PLAN.md docs/logs/phase-4-report.md docs/logs/phase-5-rescope-log.md docs/logs/phase-5-rescope-report.md
git commit -m "docs: re-scope Phase 5 after Phase 4 delivered search and sort (D-60)"
```

## Addendum, 2026-11-11: the maintainer's decisions (the text above is left as written)

| Question | Decision | Where it is recorded |
|---|---|---|
| 1. Relation-aware sort | **Approved.** Reverses the relation clause of D-53; the order is by the target's label through the existing join, no extra join or statement; a relation shown as `#id` stays unsortable | D-60; D-53 is marked "Superseded by D-60" (not renumbered, not deleted); PLAN Phase 5 row and checklist; PROJECT_DOCS |
| 2. Nulls | **Approved:** nulls last in both directions, then the identifier as the final tie-breaker | D-60; PLAN Phase 5 row and checklist; PROJECT_DOCS |
| 3. `jpa.admin.page-size` | **Moved to Phase 13** (not Phase 5) | D-60; PLAN Phase 5 row (explicitly not a Phase 5 item) and Phase 13 row |
| 4. CHANGELOG | **Rotated:** the Phase 4 entries are now `[0.0.4] - 2026-11-11`; a fresh `[Unreleased]` above it holds only the two documentation entries that belong to the re-scope and the Phase 4 correction | `CHANGELOG.md`; D-60 |
| 5. Browser-driver dependency | **Deferred to Phase 14.** Nothing added | D-60; PLAN Phase 14 row and Phase 5 checklist |

**Phase 4 status corrected** (the finding in section 1b above): the filter links were not delivered, so Phase 4 is "delivered, with the
filter-links exception", not "complete". `docs/PLAN.md` (row, checklist heading and a new unticked item, marked a Phase 6 item), D-60 and
the dated "Correction, 2026-11-11" section of `docs/logs/phase-4-report.md` now say so. The Phase 4 report's history is not rewritten.

Section 5 above ("Decisions I need from you") is therefore answered. Still open for the maintainer: nothing in this re-scope.
