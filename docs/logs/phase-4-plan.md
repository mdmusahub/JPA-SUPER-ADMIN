# Phase 4 plan: list page (both tracks, one session)

Branch `phase/4-list`, from tag `v0.0.3`. One session works on both tracks (the recorded exception to AI-11 of
D-33 and D-43, extended to Phase 4 by a new decision). Each change is labelled Backend or UI in
`phase-4-change-log.md`. Nothing is committed. Phase 5 is not started.

Maven is the copy bundled with IntelliJ (Maven 3.9.16, Temurin 17), put on `PATH` per command, as in Phase 3.

## Scope (from docs/PLAN.md Phase 4, docs/UI_PLAN.md task U2, the brief)

Query service, display formatting, list page, paging, masking, link builders for sort, page and page size. The
brief also asks for the search (`q`) and the sort (`sort`, `dir`) in this pass, although `docs/PLAN.md` puts them in
Phase 5. They are built here because the list page needs links that keep them. Phase 5 then only has to re-check and
document them (the PLAN table is updated honestly). Filters (Phase 6), detail, delete, create, edit, uploads, charts,
export and saved views are not touched. `Filters` stays empty; `Actions` offers nothing.

## Packages and APIs used (verified against the 4.1.2 jars, CODE-8)

- Jakarta Persistence 3.2.0 (`jakarta.persistence.criteria`: `CriteriaBuilder.createTupleQuery`, `like(Expression,
  Expression, char)`, `lower`, `parameter(Class)`, `CriteriaQuery.multiselect(List)`, `From.join(String, JoinType)`,
  `Tuple.get(int)`; `jakarta.persistence.metamodel`: `EntityType.getIdType`, `hasSingleIdAttribute`;
  `TypedQuery.setFirstResult/setMaxResults`). Checked with `javap` on `jakarta.persistence-api-3.2.0.jar`.
- Hibernate ORM 7.4.13 (only through the JPA API; `org.hibernate.stat.Statistics` in sample-app tests to count queries).
- Spring Framework 7.0.10: `org.springframework.transaction.annotation.Transactional` (already used by `CountService`,
  `spring-tx` provided), `org.springframework.web.bind.annotation.RequestParam`, `ModelAndView`.
- Spring Boot 4.1.2: `org.springframework.boot.hibernate.autoconfigure` and `org.springframework.boot.webmvc.test.autoconfigure`
  (tests), as before. **No new dependency anywhere.**

## Ordered steps

0. **Housekeeping.** Tag `v0.0.3` exists. Remove the tests that assert "This table view is not available yet."
   (core, sample-app, host-check, preview). Grep. Check the name-check allow-list still matches.
1. **Query and paging (core, Criteria API only).** `service/` package: `ListQuery` (untrusted strings in, a validated
   query out), `ListColumn`, `ListPage`, `CellFormatter` (package-private), `ListQueryService` (read-only transaction).
   One count query and one page query. Rows are built from a `Tuple` that selects only the id and the visible,
   non-sensitive, displayable attributes. A to-one relation is a LEFT JOIN that selects the target's label attribute in
   the same query (no N+1, no extra query; a batch query per column was the brief's example, a join is simpler and
   cheaper). Collections, embeddables and binaries are never selected or shown.
2. **Search and sort (same query).** `q` is trimmed, stripped of control characters, cut to 100 characters, lower-cased
   and matched with `lower(col) like :pattern escape '\'` on `String` attributes that are visible, non-sensitive and of
   kind TEXT; `\`, `%` and `_` are escaped; the pattern is a bound parameter (SEC-3). Sort: a key that is a sortable
   visible non-sensitive column; an unknown key means "no sort"; a bad or missing `dir` means `asc` when the key is
   valid; the order always ends with the id as a tie-breaker.
3. **Controller.** `EntityController` serves `GET {path}/{entity}`: unknown/hidden entity = the same 404 as now;
   bad `page`, `size`, `sort`, `dir`, long `q` fall back to defaults (no 500, no 400). Fills only `ListView`. Links come from one
   builder (`ListLinks`) that percent-encodes every value. Export is absent (`canExport` false), "New" is absent
   (`canCreate` false) until Phases 11 and 8. The temporary notice, its constant and `entity-notice.html` are deleted.
4. **UI U2.** `list.html`, fragments `table`, `cell`, `pager`; CSS for the table, the toolbar and the pager. No JavaScript
   is needed: search is a GET form, sort and paging are links, so nothing depends on a script and Back/reload keep the state.
5. **Preview.** A `list` page with fixtures from `docs/FIXTURES.md` section 5 for this phase plus the ones the brief names.
   Fixtures are built by the real `ListViewFactory` from fake data, so the real link rules are exercised.
6. **Tests.** Core (query, search, sort, security, relations, query count, links, controller), autoconfigure (wiring),
   sample-app (real pages, open-in-view true and false, query count with Hibernate statistics, sensitive value never
   present), preview (fixtures, HTML rules), host-check.
7. **Mutation checks (a) to (k)**, one at a time, each restored byte-identically (MD5).
8. **Docs.** PLAN, UI_PLAN, DECISIONS, CHANGELOG, PROJECT_DOCS, FIXTURES.

## Decisions I will record (D-50 onward)

| Topic | Rule |
|---|---|
| Sessions | One session, both tracks, Phase 4 (extends D-33, D-43). |
| Page sizes | 10, 25, 50, 100; default 25; anything else (including non-numeric) falls back to 25. `jpa.admin.page-size` is **not** added now (kept small); the default is one constant. |
| Page number | Zero-based in the URL. Not a whole number >= 0 falls back to 0; a number past the end becomes the last page; a number too large for an int is "past the end". |
| Search | Trim, drop control characters, cut at 100 characters (code points), case-insensitive contains, escaped wildcards, bound parameter, TEXT `String` attributes only; with no searchable attribute a search matches nothing. |
| Sort | Sortable = visible, non-sensitive, kind id/TEXT/NUMBER/BOOLEAN/ENUM/DATE/DATETIME. Relations are not sortable (their stored value is an id, their shown value is a label). Tie-breaker: id ascending, always last. No sort given = id ascending, shown with no indicator. |
| Relation cell | Label = the first visible, non-sensitive text attribute of the target named name/title/label/…; else the first visible non-sensitive text attribute; else `#id`. A relation link is **not** emitted (`href` null) until a detail page exists (Phase 7), so no link leads nowhere. |
| Cells | Text cut at 120 characters with the full text (up to 1000) in `title`; numbers as stored (no grouping); dates ISO; booleans Yes/No; enum badge tone by a small keyword heuristic until `@AdminBadge` (Phase 13); sensitive = `Cell.masked()` for every row, even a null value. |
| Invalid query | Falls back to defaults and renders 200 (no 400). |
| Plural | `Plurals` rule in the controller layer (`Category` becomes `Categories`) until `@AdminResource`. |
| Export / New | Absent, not disabled, until Phases 11 / 8 (no button that leads nowhere, SEC-11). |

## Things I already know are in the brief and do not match the repo

- The sample data holds 25 products, so the default page size 25 gives one page. "Page 2 and 3", "last page has 5
  rows" and "q=iphone" need either `size=10` (10 + 10 + 5) or extra rows. I use `?size=10` and `?q=phone` against the
  seed data and add rows in tests. The seed data is not changed.
- `docs/PLAN.md` puts search and sort in Phase 5; this pass builds them (brief), and the plan is updated.

## Pause conditions I am watching

A frozen record that cannot carry what the page needs (then: stop with options), a dependency, a public API/property name,
a doc conflict, a rule I cannot satisfy.
