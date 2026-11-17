# Phase 6 report: Filters

Branch `phase/6-filters` (from `main` at `v0.0.5`). One session on both tracks (D-64). Nothing is committed. Plan:
`docs/logs/phase-6-plan.md`. Every file and command: `docs/logs/phase-6-change-log.md`. Decisions: D-62, D-63, D-64.
Phase 7 scope note: `docs/logs/phase-7-scope.md` (docs only, nothing implemented).

## 1. Files

Created (28): in `jpa-admin-core` `service/ActiveFilter`, `FilterControl`, `FilterField`, `FilterNote`, `FilterParse`,
`FilterPredicates`, `ListFilters`; tests `ListFiltersTest`, `ListQueryFiltersTest`, `ListQueryServiceFilterTest`,
`ListQueryServiceFilterSafetyTest`, `FilterSafetyContract`, `web/ConsoleFilterPageTest`, `testsupport/listmodel/Ledger`; in
`jpa-admin-ui` `fragments/filter-panel.html`, `fragments/filter-chips.html`, `assets/js/filters.js`; in `jpa-admin-ui-preview`
`FilterFixtures`, `PreviewFilterPagesTest`; in `sample-app` `AbstractFilterPagesTest`, `FilterPagesOpenInViewDisabledTest`,
`FilterPagesOpenInViewEnabledTest`; docs `docs/logs/phase-6-plan.md`, `phase-6-change-log.md`, `phase-6-report.md`,
`phase-7-scope.md`.

Modified (26): `ListColumns`, `ListPage`, `ListQuery`, `ListQueryService`, `web/ListLinks`, `web/ListViewFactory`,
`web/EntityController`; core test support (`ListTestPersistence`, `persistence.xml`) and `ListQueryServiceTest`;
`list.html`, `layout.html`, `pages.css`; `ListFixtures`, `ChromeFixtures`, `PreviewListPagesTest`, `PreviewPagesTest`;
`AbstractListPagesTest`, `AbstractSortProofTest`; `host-check` `ConsoleListsThisHostsEntitiesTest`; `CHANGELOG.md`,
`docs/PLAN.md`, `UI_PLAN.md`, `FIXTURES.md`, `PROJECT_DOCS.md`, `DECISIONS.md`. Deleted: none. (`git diff --stat`: 26 files,
812 insertions, 112 deletions, plus the new files.) No `web.view` record, annotation, property or dependency changed.

## 2. Tests

| Module | Before | After |
|---|---|---|
| jpa-admin-core | 1854 | 2102 (+248) |
| jpa-admin-autoconfigure | 449 | 449 |
| jpa-admin-ui-preview | 748 | 1080 (+332) |
| sample-app | 531 | 591 (+60) |
| total | 3582 | 4222 |
| host-check (outside the reactor) | 8 | 9 |

`mvn -B clean verify`: BUILD SUCCESS. `mvn -B -DskipTests install`: BUILD SUCCESS. `mvn -B -f host-check/pom.xml verify`:
BUILD SUCCESS (9 tests). Ports 8080, 8081 and 8090 were free before and after; the demo process I started (PID 14604) was
stopped by PID. The name check (ARCH-12) finds exactly the seven allow-listed Spring `@Order` lines.

APIs checked against the jars (CODE-8): Jakarta Persistence 3.2.0 (`jakarta.persistence.criteria`: CriteriaBuilder,
CriteriaQuery, Root, Path, Predicate, ParameterExpression, Expression, `Nulls`; `jakarta.persistence`: EntityManager, Parameter,
Tuple, TypedQuery; `jakarta.persistence.metamodel`: EntityType, SingularAttribute) and Hibernate ORM 7.4.13. No Spring Boot,
Security or Thymeleaf API was added to `jpa-admin-core`.

## 3. Mutation checks (each: break, see tests fail, restore, MD5 equal, green again at the end)

| | Break | Result |
|---|---|---|
| (a) | a sensitive field is filterable | caught by 16 tests |
| (b) | a hidden field is filterable | caught by 8 tests |
| (c) | filter SQL built by concatenation (test-only copy, deleted) | the copy fails 8 of the 9 contract tests; the real service passes all 9 |
| (d) | the from-after-to check removed | caught (core; the sample test would too) |
| (e) | the cap removed | caught by the cap test (the record test also ran out of memory building the filters) |
| (f) | a hidden entity's rows in a relation dropdown | caught by 14 tests, among them the leak test (a first, cruder try was caught only by crashes) |
| (g) | no page reset on a filter change | caught by 4 tests |
| (h) | a chip's remove link removes nothing | caught by 3 tests |
| (i) | the ignored-filter notice only in one case | caught by 17 tests |

All files restored byte-identically (MD5 before and after in the change log, entries 48 to 56).

## 4. Decisions, deviations, and what a host should know

* **Rules** are in D-62 and `docs/PROJECT_DOCS.md` ("Filter rules"): fields, parameters, each kind, ignored values, the
  cap of 10, combination. The identifier is not filterable (my choice, not in the brief: it keeps the panel short and a record
  is reached by search or a relation filter).
* **Extra statement.** A relation filter's dropdown is one small tuple query per filterable relation per list page
  (identifier and label, 201 rows at most). A list with two such relations costs four statements, not two. This changed
  the Phase 4/5 assertions that counted exactly two (see below). No join is added, nothing grows with the rows.
* **The 200 bound, runtime reading (please confirm).** The brief says to STOP and ask if a target has more than 200
  records. That cannot happen during this phase (the sample app has at most 25 per table, `host-check` at most 6), so I did not
  stop. For a host that does have such a target I chose: no filter is offered, a notice on every load says so, and a
  `filter.x` for it is ignored. Alternatives: a typed-id box, or a searchable picker. This is the one behaviour I would like you
  to confirm.
* **Pre-existing tests changed, and why (AI-6).** `ListQueryServiceTest` (5 tests): the page is now found among the
  statements that read `parcel`, and a new test states the full cost. `AbstractListPagesTest`: statements 2 become "2 plus one
  per relation filter" (3 for products and orders, 2 for customers). `AbstractSortProofTest`: the assertion that the words
  Price, Stock, Status and Featured are on the page nowhere is now read on the empty-state sentence, because the filter panel
  legitimately names those columns. `PreviewPagesTest` (new chrome variant in the catalog), `PreviewListPagesTest` (the form has
  `data-filter-form`). Rules (ARCH-12 name check, UI-4 labels, UI-2 no external address) were not weakened: I renamed a
  placeholder `Category` in a template, gave range inputs `label for`, and removed a literal `http://x` from a rendered fixture.
* **Search and filters are one form**, so a search never drops the filters. "Clear search" says "Clear search and filters"
  while filters are active.
* **`filters.js`** (about 30 lines) is the only script added; the page works without it.
* **Dates** accept years 1000 to 9998 (the day after must exist in every database); instants are cut into days in UTC.
* **Not proven:** PostgreSQL and MySQL (Phase 14), and a real browser (below).
* Process notes (no effect on the product): two edit helpers misbehaved (a heredoc with apostrophes; one compiled a Japanese
  literal with the Windows charset and wrote mojibake into a fixture, found in the diff and fixed). A stray `python -` waited on
  stdin and was killed by PID.

## 5. Not done, to check by hand, contract changes, Phase 7

* **Not done:** the manual browser pass. I did not drive a browser. Please check `/admin/product` in light and dark, at
  phone width (the panel should stack, the page must not scroll sideways, the chips wrap), with the keyboard only (open
  "Filters" with Enter, tab through the controls, remove a chip with Enter), and with scripts off (the address then carries
  empty `filter.x=` parameters, which the server ignores silently).
* To try: `/admin/product?filter.category=<id>`, `?filter.price.min=20&filter.price.max=60`, `/admin/order?filter.createdAt.from=...`,
  `?filter.createdAt.from=2026-11-10&filter.createdAt.to=2026-11-01` (notice), `/admin/customer?filter.password=x` (generic notice,
  no value). I ran these with `curl.exe` against the running jar: 4 Electronics products and 4 table rows; 11 products between 20
  and 60; 16 orders in the date range; 30 orders and the "from is after to" notice; 1 product for the combined request with
  both remove links keeping `q`, `sort`, `dir` and `size`; 12 customers, one generic notice, no placeholder text and no
  `filter.password` control; an unknown category id ignored and said; a literal `http://x` kept as text.
* **Contract change proposed:** none (D-64).
* **Phase 7 scope:** `docs/logs/phase-7-scope.md`: detail page, relation links, delete, bulk delete, row checkboxes,
  duplicate, the routes the templates build. What Phase 6 leaves: the row id, the link writer, the hidden-target rule
  (`targetEntity` is null), the separate filter form. What it does not: id text to id type, permission flags, a detail
  service, a checkbox column. The plan should also decide which phase owns the duplicate button.

## 6. Suggested commits (by path, never `git add .`)

```powershell
# 1. backend: filter model, parser, query, links, controller, core tests
git add jpa-admin-core
git commit -m "Add field filters: model, parser, bound predicates, chips and clear-all links (Phase 6)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 2. UI: filter panel, chips, filters.js, styles, preview fixtures and tests
git add jpa-admin-ui jpa-admin-ui-preview
git commit -m "Add the filter panel, chips and clear-all to the list page, with preview fixtures (U3)" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 3. sample app and host-check tests
git add sample-app host-check
git commit -m "Test filters in the sample app (open-in-view on and off) and in host-check" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 4. docs
git add CHANGELOG.md docs/PLAN.md docs/UI_PLAN.md docs/FIXTURES.md docs/PROJECT_DOCS.md docs/DECISIONS.md docs/logs/phase-6-plan.md docs/logs/phase-6-change-log.md docs/logs/phase-6-report.md docs/logs/phase-7-scope.md
git commit -m "Document Phase 6: filter rules, decisions D-62 to D-64, report and Phase 7 scope" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

The first commit builds on its own only with the tests it carries; the sample-app test edits (statement counts, the sort-proof
sentence) belong with commit 3, so commit 3 should follow 1 and 2 before the full build is run. After the merge, tag `v0.0.6`.
