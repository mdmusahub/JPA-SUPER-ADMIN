# Phase 7 scope: detail page, relation links, delete, bulk delete, row checkboxes

Written at the end of Phase 6 (2026-11-17). **Nothing in this file is implemented.** It lists what `docs/PLAN.md` gives
Phase 7 (row 7, with the items moved in by D-60), what each item must be tested with, and, for each, what Phase 6 leaves in
place and what it does not. Phase 7 starts with its own plan and approval (AI-2).

Sources: `docs/PLAN.md` Phase 7 row and section 9 (routes); `docs/UI_PLAN.md` U4; D-28, D-29 (link rule, fixed routes),
D-54 (relation cells have no link until a detail page exists), D-60 (moves), D-62 to D-64 (Phase 6).

## Items

### 1. Detail page: `GET {path}/{entity}/{id}`

*Plan:* template `jpa-admin/detail` filled with `DetailView` (frozen), a read service, a console 404 for an unknown or hidden
entity, an unknown id and an id of the wrong shape (the three must look the same, SEC-2).

*Expected tests:* every sample entity's detail shows the right values; a sensitive field is masked and its value is in no
response, log or link; a hidden field is absent; a collection is not rendered (ARCH-4) and shows labels only; an unknown id, a
text that is not an id (`abc`, `1 or 1=1`, `../x`) and a hidden entity all answer the same console 404; no entity or lazy proxy
reaches the model (open-in-view on and off, D-29); every link lies under the base path; a preview fixture each for normal, empty
fields, long text, relations, masked, images and files.

*What Phase 6 leaves in place:* `ListView.Row.id` (the id as text) and `Hrefs`; the registry lookup that makes a hidden
entity and an unknown one identical; the cell formatter, which turns a stored value into display text (it can be reused for
the detail grid); `AdminEntity.idAttribute()`; the chrome and breadcrumbs. *Not in place:* no service reads one record; there
is **no conversion from the id text in the address to the id's Java type** (Phase 6 never needs one, because a relation filter
takes its typed id from the choices it has just read). Phase 7 has to write that conversion, with a test per id type
(`Long`, `Integer`, `String`, `UUID`) and with hostile text. `DetailView` and the route `/{entity}/{id}` do not exist yet; the
list's `GET /{entity}` mapping is a single segment, so a second segment does not clash.

### 2. Relation cells as links to the related record's detail page

*Plan:* a relation cell gets `Cell.href` to `{path}/{target}/{id}` exactly when the target is in the console, and none when it
is hidden or unsupported (D-54).

*Expected tests:* a link for a visible target; no link and `#id` text for a hidden target; no link for a missing relation
(empty cell); every such link under the base path and passing `Hrefs`; the link text is the label, escaped; the page still
costs the same number of statements (no extra query for the link: the target id is already selected).

*What Phase 6 leaves in place:* `AdminField.targetEntity()` is the target's URL name, and is `null` for a hidden target, so
the rule "hidden target, no link" is already a metadata fact; the list query already selects the target's id with its label,
and the relation label rule is shared by sort, filter and cell (`ListColumns`). `CellFormatter.relation` has no link
parameter yet and the service does not know the base path; Phase 7 must pass the link prefix in (or build the link in
`ListViewFactory`, which knows the base path; the second keeps `jpa-admin-core`'s service free of web concerns and is the
recommendation). The relation *filter* (Phase 6) and the relation *link* use the same id text, so one test can assert that the
id in a link and the id in the filter dropdown are the same value.

### 3. Delete (one record), with confirmation

*Plan:* `POST {path}/{entity}/{id}/delete`, CSRF token, a confirm dialog that works without script (a confirmation page or a
`details` form first, script only enhances it, UI-5), `@Transactional` delete, a friendly message for a foreign key failure
(SEC-12), `SEC-11` enforced in the service, a redirect back to the list **with the visitor's list state**.

*Expected tests:* delete removes the row and the count; a missing CSRF token is refused (403); a GET deletes nothing; a delete
of an unknown, hidden or read-only entity is refused in the service even if the UI hid the button; a foreign key failure shows
the friendly text and no SQL, table name or stack trace; rollback leaves the row; the deleted record's label is escaped in the
message; every link under the base path.

*What Phase 6 leaves in place:* the list state is a plain address (`ListLinks.to(query)` is the one writer of a query string)
and the effective query after a request is available (`ListPage.query()`), so "back to the list with the same search,
filters, sort and page" is one link built by the same class: Phase 7 should add one method there and **not** let a template or
a hidden form field rebuild the query. The return address must pass `Hrefs` (a relative path under the base path), never a
value taken raw from a form field. After a delete the page may be past the end: the service already clamps a page past the end
to the last page (D-51), so the redirect is safe even when the last row of the last page was deleted. *Not in place:* the
permission flags (`Permissions`, `AdminResource` read-only and deletable) do not exist; `ListView.Actions.canBulkDelete` and
the detail `Permissions` are all false today, and the list shows no delete control.

### 4. Bulk delete

*Plan:* `POST {path}/{entity}/bulk-delete` with the selected ids, one transaction, a count of what was deleted and what was
not, confirmation, a floating bar that shows the number selected.

*Expected tests:* only the ids sent are deleted; ids of another entity or of no record are ignored and counted; one failing
row rolls back the whole batch (or the documented partial behaviour, decided in the plan: recommendation, all or nothing); a
batch over a stated cap is refused (the cap is the same style of bound as the Phase 6 filter cap: stated, tested, with a
message); CSRF required; a hidden or read-only entity refused in the service; the message names how many were deleted and
repeats no stored value unescaped.

*What Phase 6 leaves in place:* the unit "a stated limit that is enforced in the record as well as in the parser" (the
filter cap) is a pattern to follow; the id-text conversion from item 1 is shared. **Decision for Phase 7's plan:** selecting
"all rows matching the filter" (not only the rows on the page) is a different feature; Phase 6 filters could feed it (the
count and the conditions are shared), but it is **not** in the Phase 7 row and should stay out unless asked.

### 5. The row checkbox behaviour

*Plan:* a first column of checkboxes in the table when `Actions.canBulkDelete` is true, a header checkbox for "select this
page", the floating bulk bar, keyboard use, an accessible name for each checkbox that says which row.

*Expected tests (preview and sample app):* no checkbox column when bulk delete is not offered; each checkbox has a label that
names its row, escaped; the header checkbox selects only the rows on the page; the count in the bar follows; with scripts off
the bar's form still works (a plain form with checkboxes and a submit button); selection is **cleared when the page, the
filters, the search or the sort change** (decide in the plan and document it: the recommendation is "cleared", because a
selection that survives a filter change would delete rows the visitor can no longer see); HTML rules (labels, nothing inline).

*What Phase 6 leaves in place, and one thing to watch:* `ListView.Row.id` is the row's id; `table.html` has no checkbox
column yet. **The filter form is a separate `<form method="get">` around the toolbar only, and the table is outside it**, so a
second `<form method="post">` around the table (checkboxes, CSRF token, the bar) does not nest forms, and the checkboxes can
never be sent with a filter request (and `filters.js` only touches controls named `filter.*` inside `[data-filter-form]`). Keep
that separation: putting the table inside the filter form, or the filter controls inside the bulk form, would send filter
parameters to the delete endpoint or ids to the list. A test should assert that no `<form>` contains another.

### 6. Duplicate (named in the Phase 7 row of `docs/PLAN.md`)

*Plan:* `GET {path}/{entity}/new?copyFrom={id}` fills a create form (the form itself is Phase 8). The Phase 7 row lists
duplicate, and the Phase 8 row lists the create form. The two rows overlap and **the plan should settle which phase owns the
button**: recommendation, Phase 7 adds the link and the route's 404 and id checks, Phase 8 fills the form.

*Expected tests:* an unknown or hidden `copyFrom` is the console 404; a sensitive field is never copied into the form (it is
`masked` and carries no value, SEC-1); an upload field's stored file is not copied.

### 7. Routes the templates still build themselves (D-29 follow-up)

*Plan:* decide whether the backend should also build detail, edit, duplicate, delete, bulk delete, logout and "New record"
(today the templates build the fixed ones from `chrome.basePath`, `chrome.activeEntity` and the id).

*Recommendation:* the backend builds every link that carries state (the return-to-list link, the detail link of a relation
cell) and the template keeps the fixed ones that carry none. *Expected tests:* a test that searches the templates for a
hand-built query string (the Phase 4 test does this for sort and page links; add `filter.`).

## Check against what Phase 6 leaves in place

| Item | Ready from Phase 6 | Missing, to plan in Phase 7 |
|---|---|---|
| Detail | row id, registry lookup, cell formatter, link rule | id text to id type, a read service, `DetailView` filling, template, fixtures |
| Relation links | `targetEntity` (null for hidden), label rule, ids already selected | link built in the factory, one test tying link id to filter choice |
| Delete | one writer for list addresses, page clamp after delete, CSRF chain | permission flags, service, friendly errors, confirm flow |
| Bulk delete | the "stated cap enforced twice" pattern | cap, transaction behaviour, id conversion, selection rule |
| Row checkboxes | table outside the GET form, `filters.js` scoped to `filter.*` | checkbox column, bar, selection rules, a "no nested form" test |
| Duplicate | none | which phase owns the button |

Two cross-cutting facts to carry into Phase 7: the console now reads **one small statement per filterable relation per list
page** (D-63), so a detail or delete page that reuses the list code should not call the filter-choice query; and **every
change of a filter set resets the page** (D-62), which is the rule a post-delete redirect should follow when the deletion
changes what the list contains.
