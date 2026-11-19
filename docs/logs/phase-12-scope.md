# Phase 12 scope: saved views and column visibility (written at the end of Phase 11, nothing implemented)

Source: `docs/PLAN.md` (Phase 12 row, section 7 "Saved views", UI-10, UI-11), `docs/UI_PLAN.md` (U8: the saved views menu and column visibility), `docs/FIXTURES.md` (`saved-views`, `many-columns`), `docs/DECISIONS.md` (D-105 for what the export already does with columns, D-108), `docs/logs/phase-11-report.md`. This file lists what the phase has to deliver and the questions to settle first. It is a summary, not a plan; the plan is written at the start of the phase and approved.

## What the phase delivers

1. **Saved views, stored in the browser** (`localStorage`, wrapped in try/catch, the page works when storage is unavailable, UI-11). A view is `{ name, q, filters, sort, dir, pageSize, hiddenColumns }` per entity (PLAN section 7). Save the current state under a name, apply, rename, delete. Applying a view rebuilds the query string, so a view is also a shareable URL (UI-10). No server-side storage (out of scope for v0.1).
2. **Column visibility.** A way to hide columns of the list, kept in the address (a repeated or comma-separated parameter, to be decided) so Back, reload and sharing keep it. The hidden set is untrusted: only names of visible, non-sensitive columns count, anything else is ignored in the same words as an unknown sort key (SEC-2). The identifier column and at least one column must stay (a list with no column is not a list).
3. **Hidden columns are not queried.** A column hidden by a view is neither selected nor joined by the list query, so hiding relation columns lowers the cost of a page (the Phase 4 join-per-relation-column limit). The recorded SQL of a page with hidden relation columns has fewer joins. This is `ListQueryService.Plan`/`project` territory: the plan becomes per request (or the plan is filtered), while the sort and the search stay valid (a sort by a hidden column is dropped or the column is kept for the sort only: decide).
4. **The export follows the visible columns.** Phase 11 exports "exactly the list's visible, non-sensitive, non-hidden columns, in list order" (D-105) and built `ListLinks.export` so that the visible columns can join the address (its Javadoc says so). When a view hides a column, the export link must carry the same hidden set, and `ExportService` must drop those columns from the header, the rows and the SQL, with the same validation as the list. The export tests of Phase 11 (header equals the list's visible columns; sensitive and hidden attributes absent from the SQL) extend naturally.
5. **UI task U8, the rest** (`docs/UI_PLAN.md`): the saved views menu and the column visibility control, a script (`views.js`, one small file, no inline script), keyboard and both themes, phone width, an empty state ("no saved views yet"), the states in `docs/FIXTURES.md` (`saved-views`, and `many-columns` whose five hidden columns belong to this task). The browser passes of the list and of the export button (both skipped in Phases 10 and 11 for lack of a browser driver) are natural to do here, by hand.
6. **Tests:** a view restores the exact state (search, filters, sort, page size, hidden columns) and survives a reload; applying a stale view (a column that no longer exists, a filter that no longer applies) degrades to the valid part with a notice; hidden-column names are checked against the metadata; the SQL of a page with hidden relation columns has fewer joins; `spring.jpa.open-in-view` on and off; `host-check` with its own entities; the export of a page with hidden columns; mutation checks.

## Questions to settle before coding (each with a recommendation)

| Question | Options | Recommendation |
|---|---|---|
| How is the hidden set carried in the address? | `hide=a,b` (one parameter); `hide=a&hide=b`; the visible set instead (`cols=a,b`) | `hide=a&hide=b`: it reads like the filter parameters, an unknown name is ignored one by one, and "show everything" is the empty address. The visible set breaks when the entity gains a field |
| What can be hidden? | any visible column; any but the identifier; any but the identifier and the label column | Any but the identifier, and never the last column. The identifier is the row's link and the tie-breaker of every sort |
| A sort or filter on a hidden column | drop the sort; keep the column in the query for the sort only; refuse | Keep the sort and the filter working (they are conditions of the query, not display), select and join only what is shown. A hidden relation column that is the sort key keeps its join for the `ORDER BY` only |
| Where does the "columns" menu live? | in the toolbar next to Filters; in the table header | A `details` panel next to Filters (the same pattern, works without a script as a GET form) |
| Do saved views carry the page number? | yes; no | No: the page size yes, the page number no (the old page would mean other rows, D-62) |
| What happens to a saved view when an entity changes (a column is renamed)? | silently drop what no longer exists; keep and warn | Apply the valid part and show one plain notice that names what was ignored |
| A shared URL with a hidden set for a user whose browser has other views | nothing to do: the address is the state | Nothing: views are only names for addresses |

## Carried facts from Phase 11 that matter here

- `ListLinks.export(ListQuery)` builds the export link from `q`, the filters, `sort` and `dir`; adding the hidden set is one more parameter there and one more in the controller's parsing.
- `ListQueryService` has one shared `project(Root, Plan, selectId)` used by the list and the export; hiding columns means a per-request filtered plan fed to the same method, so the two cannot disagree about what is selected.
- The export's `ExportPlan.columns()` is already "the list's columns minus the masked ones"; with hiding it becomes "minus the hidden ones" by the same filter.
- The export tests assert the SQL with the `RecordingStatementInspector` (no sensitive or hidden attribute in the select list) and the number of statements per page; the hiding tests can reuse both.
- The list's relation-column cost (one `LEFT JOIN` per relation column) is documented under Limitations; Phase 12 is where it is lowered for a visitor who hides those columns.
- Not in scope: server-side views, shared views between admins, per-user views (all need multiple admins, v0.4), XLSX export, saving the export itself.
