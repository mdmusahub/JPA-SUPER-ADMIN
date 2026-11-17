# Phase 5 report: Sort and search quality (D-60, D-61)

Branch `phase/5-sort-search`, from `main` at `v0.0.4`. One session on both tracks (the recorded exception to AI-11, as in
Phases 2 to 4). Nothing is committed or staged. Phase 6 is not started. Plan: `docs/logs/phase-5-plan.md`. Log of every file
and command: `docs/logs/phase-5-change-log.md` (77 entries). New decision: **D-61**.

**In one paragraph:** all four approved items are built and proven. A to-one relation whose target has a label now sorts by that
label through the join the list already makes (no extra join, no extra statement). Every sort puts records with no value last in
both directions and then orders by the identifier. A sort proof runs over every sortable column of the five sample entities,
both directions, with and without a search, in both open-in-view modes, and checks the real row order and the `aria-sort` of
every header. A search that finds nothing names the columns it looked in; a search cut at 100 characters says so. No dependency,
no property, no annotation and no `web.view` record changed, and no template or stylesheet needed a change. The full build, the
install and `host-check` pass, and all eight mutation checks are caught.

**APIs verified (CODE-8).** With `javap` on the jars on the classpath: `jakarta.persistence:jakarta.persistence-api:3.2.0`, package
`jakarta.persistence.criteria` (`Nulls` with `NONE`, `FIRST`, `LAST`; `CriteriaBuilder.asc(Expression<?>, Nulls)` and
`desc(Expression<?>, Nulls)`; `Order.getNullPrecedence()`), plus the packages the list query already used (`jakarta.persistence`,
`jakarta.persistence.criteria`, `jakarta.persistence.metamodel`). The Criteria implementation is `org.hibernate.orm:hibernate-core`
7.4.13.Final; its SQL is asserted in the tests rather than assumed. No Spring Boot, Spring Security or Thymeleaf type entered
`jpa-admin-core`.

## 1. Files created, modified, deleted

Nothing deleted. `git status --short` (taken before this report was created):

```
 M CHANGELOG.md
 M docs/DECISIONS.md
 M docs/FIXTURES.md
 M docs/PLAN.md
 M docs/PROJECT_DOCS.md
 M docs/UI_PLAN.md
 M host-check/src/test/java/com/example/bookstore/ConsoleListsThisHostsEntitiesTest.java
 M jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/ListColumn.java
 M jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/ListColumns.java
 M jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/ListPage.java
 M jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/ListQuery.java
 M jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/ListQueryService.java
 M jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/EntityController.java
 M jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ListViewFactory.java
 M jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/service/ListQueryServiceSearchSafetyTest.java
 M jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/service/ListQueryServiceTest.java
 M jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/service/ListQueryTest.java
 M jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/web/ConsoleListPageTest.java
 M jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/web/ListViewFactoryTest.java
 M jpa-admin-ui-preview/src/main/java/io/github/mdmusahub/jpaadmin/preview/ChromeFixtures.java
 M jpa-admin-ui-preview/src/main/java/io/github/mdmusahub/jpaadmin/preview/ListFixtures.java
 M jpa-admin-ui-preview/src/test/java/io/github/mdmusahub/jpaadmin/preview/PreviewListPagesTest.java
 M jpa-admin-ui-preview/src/test/java/io/github/mdmusahub/jpaadmin/preview/PreviewPagesTest.java
 M sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/AbstractListPagesTest.java
?? docs/logs/phase-5-change-log.md
?? docs/logs/phase-5-plan.md
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/AbstractSortProofTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/SortProofOpenInViewDisabledTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/SortProofOpenInViewEnabledTest.java
```

`git diff --stat` for the 24 modified files: 960 insertions, 133 deletions. Production code is seven files in
`jpa-admin-core` (`ListColumn`, `ListColumns` +55, `ListPage`, `ListQuery`, `ListQueryService` +87, `EntityController`,
`ListViewFactory` +34) and two preview fixture files (`ChromeFixtures` +5, `ListFixtures` +86); the rest is tests and docs. This report and the three new sample-app files were created
after that output; `AbstractSortProofTest.java` is 503 lines. Nothing under `jpa-admin-ui` (templates, CSS, JavaScript) changed,
nor `jpa-admin-autoconfigure`, `jpa-admin-starter`, any `pom.xml`, or `.github`.

Outside the repository: helper scripts in the session scratchpad and `/tmp` (string-replace helpers, the PowerShell demo
check), and one memory note in `~/.claude/projects/.../memory/` about tool quirks on this machine.

## 2. Test counts and build results

| Module | Before | After | Change |
|---|---:|---:|---:|
| jpa-admin-core | 1816 | 1854 | +38 |
| jpa-admin-autoconfigure | 449 | 449 | 0 |
| jpa-admin-ui-preview | 599 | 748 | +149 (8 new tests, and every new fixture is rendered in each of the 13 chrome variants by the generic HTML-rule test) |
| sample-app | 301 | 531 | +230 (the sort proof: 115 tests in each of the two open-in-view modes) |
| jpa-admin-ui, jpa-admin-starter | no tests | no tests | |
| **Reactor total** | **3165** | **3582** | **+417** |
| host-check (outside the reactor) | 7 | 8 | +1 |

- `mvn -B clean verify`: BUILD SUCCESS, 0 failures, 0 skipped. Run after the last edit of the tree. The 8 "APPLICATION FAILED
  TO START" blocks in the output are the same 8 as in the baseline (startup-failure tests that expect them).
- `mvn -B -DskipTests install`: BUILD SUCCESS.
- `mvn -B -f host-check/pom.xml verify`: BUILD SUCCESS, 8 tests.
- Host isolation (`HostIsolationTest` and the other autoconfigure tests) and the ARCH-12 name check (`NameCheckTest`) pass inside
  the full build; the HTML rules (`HtmlRulesTest`, `TemplateRulesTest`, and the per-fixture rules in `PreviewPagesTest`) pass for
  every new fixture in every chrome variant. By hand: a search for the five demo names (whole word, case-sensitive) over the
  sources of core, autoconfigure, ui and starter finds 7 lines, exactly the 7 allow-listed Spring `@Order` lines, none new.
- Live run (PowerShell, `curl.exe`, java PID 21340, stopped by PID; ports 8080, 8081 and 8090 free before and after; the log has
  no ERROR, WARN or Exception line and no password):
  - `/admin/order?sort=customer&dir=asc`: 30 rows, `aria-sort="ascending"` on Customer with a link to `desc`; the customers in
    label order (Ava Stone x3, Ben Carter x3 ... Liam Gray x2); equal customers in identifier order; identifier order would
    have been different.
  - `/admin/product?sort=category&dir=desc`: `aria-sort="descending"`; rows Toys > Sports > Home > Garden > Electronics > Books.
  - `/admin/order?sort=note` ascending and descending: the 8 orders with a note are rows 1 to 8, the other 22 follow.
  - `/admin/customer?sort=password` (asc and desc): status 200, no header sorted, the Password header has no `aria-sort` and no
    link, no `sort=password` anywhere, 12 masked cells, no stored value in the page, rows in identifier order.
  - Empty searches: "No products match your search. The search looked in these text columns: Name, Sku. ...", customers
    "Email, Full name" (Password not named), orders "Note".
  - A 101-character search: the notice "Your search was longer than 100 characters, so only the first 100 were used." and a
    search box that holds 100 characters; with exactly 100 characters, no notice.
  - The first run of the script had a bug of mine in check E (PowerShell read `$entity?q` as one variable); it is fixed and the
    whole script was run again. Both runs are in the change log.

## 3. Mutation checks (one at a time; each file restored from a copy; MD5 identical before and after; then green)

| | Break | Caught by | Restored MD5 |
|---|---|---|---|
| a | Relations sort by the target's id instead of its label (`ListQueryService`) | core 2 (`aRelationIsOrderedByLabelNotByTheTargetsIdentifier`, `theRelationSortOrdersByTheLabelColumn...`); sample-app 5 (product by category, single page and paged) | `50081bbf...` |
| b | Nulls-last removed from both `asc` and `desc` | core 6; sample-app 4 (`Order.note`, `Category.description`, ascending). On H2 only ascending changes, because H2 already puts nulls last in a descending order | `50081bbf...` |
| c | Id tie-breaker dropped | first run: core 9, sample-app 1. After I strengthened the sweep to read every case ten rows at a time (see section 4): core 9, **sample-app 21** | `50081bbf...` |
| d | A sensitive column made sortable (needs two files: the `ListColumn` guard and `ListColumns`) | core 12 (`noSensitiveColumnOfAnyEntityIsSortable...`, `aSensitiveFieldIsAMaskedColumnThatCannotBeSorted`, the view and service tests); sample-app: `thePasswordColumnCannotBeSortedOrSearched...` and the sweep on every Password header (the list I kept was cut at 30 lines, so I did not count them) | `feb2e73f...`, `c8198741...` |
| e | `aria-sort="none"` removed from relation headers (`table.html`) | sample-app sweep on every Order and Product page (the Customer and Category headers lose it) | `ad7bdf0a...` |
| f | The search-scope sentence removed (`ListViewFactory`) | core 5; sample-app 1 | `ba9c6cc0...` |
| g | The cut notice removed (`EntityController`) | core 2; sample-app 2 | `c97c2e54...` |
| h | A second statement when sorting by a relation | core 3 (`sortingByARelationAddsNoJoinAndNoStatement` and two that read the second statement); sample-app 1 (`aListPageCostsTwoStatements...` for `sort=category`) | `50081bbf...` |

Mutation (e) was run through the sample-app sweep only; the preview tests were not part of that run (the command named a
preview test class, but that module is not in the sample-app build, so it did not run). The sweep alone caught it.

## 4. Decisions and deviations, with reasons

Recorded as **D-61** (D-53 and D-60 untouched, nothing renumbered). In short:

1. **Relation sort (D-60 applied).** One rule for "can this column sort": `ListColumns.of(entity, registry)`. The label rule of
   D-54 moved from `ListQueryService` to `ListColumns`, so the parser, the service and the page cannot disagree. The query
   keeps the label path of the join it already makes and orders by it. Counted in tests: the same number of joins and two
   statements with and without a relation sort.
2. **Hibernate and the null rule (found while building).** Hibernate 7.4 writes `nulls last` into the SQL only where the
   database's default differs. On H2: ascending is `order by 6 asc nulls last,1`; descending is `order by 6 desc,1` (H2 already
   puts nulls last there). So the SQL test asserts the clause for ascending and "no `nulls first`" for descending, and the data
   tests prove the effect in both directions. I did not force the clause into every statement.
3. **Internal signature changes** (the types are marked internal, not stable; no documented annotation or property changed, so I
   did not stop): `ListQuery.parse` takes the `AdminEntityRegistry` (and `ListQueryService.parse` wraps it, so the controller
   stays thin and does not hold the registry); `ListPage` has a new component `searched`; new `ListQuery.searchWasCut`,
   `ListQueryService.parse` and `ListViewFactory.searchCutNotice`; `ListColumns.of` takes the registry. If you read "public API"
   to include these public-but-internal types, say so and I will treat the next such change as a stop point.
4. **Existing tests that I changed, and why none was weakened.** Seven tests asserted the clause D-60 reverses ("a relation is
   not sortable"): `ListQueryTest.anyOtherSortKeyMeansNoSort` (keys `depot`, `courier`),
   `ListQueryTest.aRelationColumnIsNotSortableAndTheOthersAre`, `ListQueryServiceTest.anUnsortableKeyIsIgnored...`,
   `...theServiceDropsAnUnsortableKey...`, `ConsoleListPageTest.aBadSortKeyIsIgnoredWithoutAnError`, the SQL-order test, and the
   key `category` in `AbstractListPagesTest.anUnsortableKeyIsIgnored...`. Each now uses a key that stays unsortable (`crate`, a
   target with no text; `vault`, a hidden target) or states the new rule (the relation test became
   `aRelationColumnIsSortableExactlyWhenItsTargetHasALabel` and checks all four relations). The SQL-order test expects the null rule.
5. **Search scope message.** In `Empty.hint`: "The search looked in these text columns: Name, Sku." (header labels in display order,
   at most ten, then "and N more"), or "no text column that a search can look in". The names come from the same rule as the
   search, so a sensitive or hidden label cannot appear (tested in core, the preview and the real app). D-52 is unchanged.
6. **Cut notice** is an INFO message in `chrome.flash` (the existing role=status toast), shown only when text was cut, on any
   list page, repeating nothing that was typed. Not a new record field.
7. **UI track: no change.** The existing `table.html` already prints `aria-sort` for every sortable header, `list.html` prints the
   hint with `th:text`, and the toast fragment prints flash messages without JavaScript. The UI step was therefore a check, proven by
   the preview tests, and by mutation (e). The preview article fixtures now have a sortable Author (a labelled relation); the
   `long-text` fixture keeps an unsortable relation.
8. **The sample sweep was strengthened mid-way.** Mutation (c) showed that reading each sort on one page does not expose a missing
   tie-breaker on H2 (equal values come back in insertion order). The sweep now also reads every case ten rows at a time.
9. **A defensive branch with no test.** `ListQueryService.plan` marks a relation unsortable if the metadata says it has a label but
   the query cannot join to it. With a valid metamodel this cannot happen (such targets are not in the registry), so I could not
   write a honest test for it; it only prevents a wrong query.

**What a host should know.**

- **Missing values.** Databases differ (H2 and MySQL put nulls first in an ascending sort, PostgreSQL last); the console does not
  follow them: nulls last in both directions, then the identifier. Hibernate emulates `nulls last` on databases that lack it
  (MySQL), which changes the SQL shape. This is proven on H2 only; PostgreSQL and MySQL are Phase 14 (already in its row).
- **Relation labels are ordered by the database's text order** (case and accents follow its collation). A relation whose target
  has an empty or missing label sorts as missing (last).
- **Cost.** A relation sort adds no join, but every page still has one `LEFT JOIN` per relation column (G4, Phase 14, Phase 12).
- **Searching** still looks at the entity's own text columns only; a relation's label is not searched (a Phase 6 filter).

## 5. Not done, to check manually, contract change

- **Contract change: none proposed, none made.** No `web.view` record changed; the hint and the flash list were enough.
- **Not done (out of scope, as instructed):** filters (Phase 6), relation links (Phase 7), the page-size property (Phase 13), any
  browser test (Phase 14), PostgreSQL and MySQL runs (Phase 14).
- **To check manually in a browser (U9, Phase 13):** the two new messages at phone width, in dark theme and with the keyboard
  (the toast and the empty state are existing components and their rules are tested, but no browser was driven here).
- **Merge and tag `v0.0.5`** is yours (GIT-2); the PLAN checklist has an unticked line for it.
- The `[Unreleased]` changelog has the Phase 5 entries; the Phase 4 entries were already rotated into `[0.0.4]`.

## 6. Suggested commit groups (PowerShell-safe, by path; never `git add .`)

Add the attribution line your tooling requires at the end of each message.

**Commit 1: backend** (relation sort, null rule, search scope, cut notice, with their tests)

```powershell
git add jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/ListColumn.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/ListColumns.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/ListPage.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/ListQuery.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service/ListQueryService.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/EntityController.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ListViewFactory.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/service jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/web/ConsoleListPageTest.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/web/ListViewFactoryTest.java
git commit -m "feat: sort by a relation's label, nulls last in every sort, say what a search covers (D-60, D-61)"
```

**Commit 2: preview fixtures** (UI track)

```powershell
git add jpa-admin-ui-preview/src/main/java/io/github/mdmusahub/jpaadmin/preview/ChromeFixtures.java jpa-admin-ui-preview/src/main/java/io/github/mdmusahub/jpaadmin/preview/ListFixtures.java jpa-admin-ui-preview/src/test/java/io/github/mdmusahub/jpaadmin/preview/PreviewListPagesTest.java jpa-admin-ui-preview/src/test/java/io/github/mdmusahub/jpaadmin/preview/PreviewPagesTest.java
git commit -m "test: preview fixtures for relation sort, nulls last, search scope and the cut notice"
```

**Commit 3: sort proof on the sample app and host-check**

```powershell
git add sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/AbstractSortProofTest.java sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/SortProofOpenInViewEnabledTest.java sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/SortProofOpenInViewDisabledTest.java sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/AbstractListPagesTest.java host-check/src/test/java/com/example/bookstore/ConsoleListsThisHostsEntitiesTest.java
git commit -m "test: sort proof over every sortable column of the sample entities, in both open-in-view modes"
```

**Commit 4: docs and logs**

```powershell
git add CHANGELOG.md docs/DECISIONS.md docs/FIXTURES.md docs/PLAN.md docs/PROJECT_DOCS.md docs/UI_PLAN.md docs/logs/phase-5-plan.md docs/logs/phase-5-change-log.md docs/logs/phase-5-report.md
git commit -m "docs: Phase 5 delivered (D-61), plan, change log and report"
```

(Commits 1 to 3 can be one commit if you prefer a single "Phase 5" commit; the groups are independent of each other
only after commit 1, since the tests need the production code.)
