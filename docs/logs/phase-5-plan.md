# Phase 5 plan: Sort and search quality (D-60)

Branch `phase/5-sort-search`, from `main` at `v0.0.4`. One session on both tracks (the recorded exception to AI-11 as in
Phases 2 to 4). Nothing is committed. Phase 6 is not started. The log of every file and command is
`docs/logs/phase-5-change-log.md`.

## Scope (approved in the re-scope, 2026-11-11; nothing else)

1. **Relation-aware sort** (reverses the relation clause of D-53, recorded as D-60). A to-one relation column whose target
   has a label becomes sortable and is ordered by that label through the join the list already makes (no second join, no
   second statement). A relation shown as `#id` (the target has no usable text attribute, or the target is hidden) stays
   unsortable. A sensitive attribute of the target is never the label, so it is never a sort key.
2. **Nulls last in both directions**, then the identifier ascending as the last tie-breaker, with the JPA 3.2 `Nulls`
   ordering on the Criteria API; the same on every database.
3. **A sort proof on real data**: every sortable column of the five sample entities, both directions, with and without a
   search, open-in-view true and false, `aria-sort` on every header; each assertion checks the real order of the rows.
4. **Say what the search covers**: an empty search result names the searched columns (labels only, escaped); a search
   cut at 100 characters tells the visitor, with the limit. The search rules themselves do not change.

Out of scope (not touched): filters (Phase 6), relation links (Phase 7), the page-size property (Phase 13), browser tests
(Phase 14), multi-column sort.

## APIs verified (CODE-8)

Checked with `javap` on the jars on the classpath, not from memory:

- `jakarta.persistence:jakarta.persistence-api:3.2.0`: `jakarta.persistence.criteria.Nulls` (`NONE`, `FIRST`, `LAST`) and
  `jakarta.persistence.criteria.CriteriaBuilder.asc(Expression<?>, Nulls)` / `desc(Expression<?>, Nulls)`; `Order` has
  `getNullPrecedence()`.
- `org.hibernate.orm:hibernate-core:7.4.13.Final` (the Criteria implementation behind the above); the SQL it writes is
  asserted in the tests, so the behaviour is proven and not assumed.

Packages used: `jakarta.persistence.criteria.*`, `jakarta.persistence.metamodel.*` (already used), `org.hibernate.*` only in
tests (`StatementInspector`, already in use). No Spring Boot, Spring Security or Thymeleaf type enters `jpa-admin-core`.

## Design

- `ListColumns.of(entity, registry)`: a TO_ONE column is sortable when the registry has its target entity and
  `ListQueryService.labelAttributeOf(target)` finds a label. The label rule is moved to `ListColumns` (one rule for the
  parser, the service and the view) and stays the one of D-54.
- `ListQuery.parse(entity, registry, ...)`: takes the registry so the parser knows which relation columns can be sorted.
  This changes an internal, not-stable signature (not a documented annotation or property; flagged in the report).
- `ListColumn`: only a masked column is refused as sortable (relations may be).
- `ListQueryService`: the `LEFT JOIN` that is already made for a relation column is remembered by column key; the order
  uses `join.get(labelAttribute)`. Order: `sort expression` with `Nulls.LAST`, then the identifier ascending. When the
  sort is the identifier itself, the identifier alone (no nulls clause needed, it cannot be null).
- `ListPage` gets `searched` (the labels of the searched columns) so the view factory can name them.
- `ListQuery.searchWasCut(String raw)`: the same cleaning as `normalizeSearch`, reports whether text was lost at the limit.
- `ListViewFactory`: the NO_MATCHES hint names the searched columns (or says that nothing is searchable); a public static
  `searchCutNotice()` builds the `PageChrome.FlashMessage` (INFO) that the controller puts in `chrome.flash` only when
  the search was cut. No view-model record changes: `Empty.hint` and `PageChrome.flash` already exist.
- Templates: `table.html` already prints `aria-sort` for every sortable header (`none`, `ascending`, `descending`) and none
  for the others; the toast fragment already prints flash messages without JavaScript. They are checked, and changed only
  if a check shows a gap.
- Preview: fixtures built by the real `ListViewFactory` for a relation sort, nulls last, the search-scope empty state and the
  cut-off notice.

## Ordered steps

0. Housekeeping: confirm the Phase 4 notice and the filter-link exception are documented, no live "not available yet"
   text, ports 8080, 8081, 8090 free. Baseline `mvn -B clean verify` for the before-counts.
1. Sort model (core): relation columns sortable by rule; nulls-last; unit tests (`ListQueryTest`, `ListColumnsTest`).
2. Query (core): relation sort through the existing join; statement and join counting; open-in-view on and off.
3. Controller and view model: `aria-sort` on headers, search-scope hint, cut-off notice, links built by the backend.
4. UI (`jpa-admin-ui`): messages in the empty state and the notice area; no inline script or style; keyboard and themes.
5. Preview (`jpa-admin-ui-preview`): the four fixtures, each rendered with its key content.
6. Tests: the four items above plus negative tests (sensitive not sortable, invalid sort ignored, the cut message only when
   cut), host-isolation and ARCH-12 green, HTML rules green.
7. Mutation checks (a) to (h), one at a time, each restored byte-identical (MD5).
8. Docs: PLAN, UI_PLAN, DECISIONS (next free number is D-61), CHANGELOG, PROJECT_DOCS.
9. Final checks: `mvn -B clean verify`, `mvn -B -DskipTests install`, `mvn -B -f host-check/pom.xml verify`, the sample app
   run with curl checks, ports free, ARCH-12 grep.

## Test plan

| Where | What |
|---|---|
| `ListQueryTest` | relation keys with a label are accepted, without a label or hidden are ignored; sensitive never; the cut helper |
| `ListColumnsTest` (new) | which relation columns are sortable; none masked |
| `ListQueryServiceTest` | relation sort by label asc and desc with the id tie-breaker; nulls last (nullable scalar and nullable relation) in both directions; one join, two statements; SQL shows `nulls last`; a relation without label or a hidden target is ignored; sensitive attribute of the target not in the SQL |
| `ListViewFactoryTest` | hint names labels (escaped), nothing-searchable text, cut notice, sort links for relations |
| `ConsoleListPageTest` | cut notice only when cut; scope message on no matches |
| sample `AbstractListPagesTest` (both OIV modes) | sweep of every sortable column of the five entities, both directions, with and without a search, real row order and `aria-sort`; no sensitive column sortable; the cut and scope messages |
| host-check | relation sort of the bookstore, unchanged otherwise |
| preview | four fixtures render |
