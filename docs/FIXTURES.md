# FIXTURES.md

The fixture list for `jpa-admin-ui-preview` (see `docs/UI_PLAN.md` section 6). This file lists fixtures only. The fixture code is written by the UI track from task U0 onward, not in Phase 0.

A **fixture** is a named, fake instance of a view-model record. The preview app renders a real template with it, with no database and no login.

- **URL:** `/admin-preview/{page}?state={fixture}`. Pages are `login`, `dashboard`, `list`, `detail`, `form`, `error`.
- **Chrome variant:** every page also takes `&chrome={variant}` (see section 1). If it is missing, `default` is used.
- **Theme:** light and dark are chosen in the browser by the theme toggle, so they are not fixtures. The `theme` field of `PageChrome` has its own chrome variants.
- **Fake data only:** no real names, no real secrets. Masked values are never given a value, even in a fixture (SEC-1).
- **Generic names:** fixtures use invented entities such as Article, Member and Invoice, never entity names from `sample-app` (ARCH-12).
- **Links:** every link in a fixture is a local path under the chrome's base path (`docs/UI_PLAN.md` section 3, "Links"). Fixtures are built through the real record constructors, so a link that breaks the rule cannot be written. A fixture never contains an external link. Templates print the links the fixture gives them and never build sort, page, page-size, export or form links themselves.
- **Status:** each fixture below belongs to the UI task that first needs it (U0 to U9, `docs/UI_PLAN.md` section 7). A fixture is part of "done" for that task. After Phase 4 the list fixtures of task U2 exist; those of U3 (filters) exist since Phase 6; those of U4 (detail, row links, checkboxes, the pages after a delete) exist since Phase 7; those of U5 (the form) exist since Phase 8; those of U6 (uploads) and U8 (export, saved views) do not yet.

Fixture names are lower-case with hyphens. A fixture listed under "Covers" proves that the template handles that state.

## 1. Chrome variants (record: `PageChrome`)

Used with any page through `&chrome=`.

| Variant | Fills | Covers | Task |
|---|---|---|---|
| `default` | `PageChrome` with 3 groups, 8 entities, one active entity, 2 breadcrumbs, no flash | Normal sidebar, active item, breadcrumb with a current page that has no link | U0 |
| `long-sidebar` | `PageChrome.nav` with 8 groups and 60 entities, counts from 0 to 1,000,000 | Sidebar scrolling, long names, wide count numbers, collapsed groups | U1 |
| `empty-sidebar` | `PageChrome.nav` empty | Login page and a console with no entities | U0 |
| `no-active-entity` | `activeEntity` is `null` | Dashboard and error pages | U0 |
| `deep-breadcrumbs` | 5 breadcrumbs with long labels | Breadcrumb wrapping and truncation on a phone | U0 |
| `flash-all-levels` | One `FlashMessage` of each level (success, info, warning, error) plus one very long message | Flash area, toasts, long text | U0 |
| `theme-dark`, `theme-light`, `theme-system` | `PageChrome.theme` set to each value | Initial theme before first paint, no flash on load | U0 |
| `custom-base-path` | `basePath` set to a different console path, with breadcrumbs under it | Every link and asset URL starts with that base path, whether the fixture or the template made it | U0 |
| `no-user` | `PageChrome.user` is `null` | A page with no signed-in user: no user chip, no sign-out (the error pages) | U1 |
| `member-active` | the Members entity active, breadcrumbs Dashboard and Members, no flash | The list of a second entity (the masked-column list fixture uses it by default) | U2 |
| `search-cut` | the Articles entity active, one INFO flash message built by `ListViewFactory.searchCutNotice()` | The notice for a search cut at the limit (the `search-cut` list fixture uses it by default) | U2 |
| `filters-ignored` | the Articles entity active, `flash` holds the notices built by `ListViewFactory.filterNotices(...)` from real `FilterNote`s: four WARNING notes and one INFO note | The notices for ignored and cut filters, above the content (the `filter-ignored` list fixture uses it by default) | U3 |
| `article-detail` | the Articles entity active, breadcrumbs Dashboard, Articles (a link), "Guide on onboarding 7" (delivered, task U4) | The shell of a detail page (the `detail` fixtures use a chrome built from their own record and list state by default) | U4 |
| `delete-done`, `delete-blocked`, `bulk-done`, `bulk-partial`, `bulk-over-cap`, `bulk-blocked`, `bulk-nothing-selected` | the Articles entity active, `flash` holds one message built by the real `DeleteMessages` (SUCCESS, ERROR, SUCCESS, WARNING, ERROR, ERROR, WARNING); the two on a record page carry the record's breadcrumbs (delivered, task U4) | The messages after a delete, shown once above the content: role `status` for success and information, role `alert` for a refusal or a warning; no SQL, table name or stack trace | U4 |

## 2. Login (record: `LoginView`)

| Fixture | Fills | Covers | Task |
|---|---|---|---|
| `normal` | `LoginView` with title, no error, no notice | Empty form, focus on the first field | U0 |
| `bad-credentials` | `error` set to the generic message (it never says which field was wrong) | Error linked to the form, labelled inputs | U0 |
| `logged-out` | `logoutNotice` true | Notice shown above the form | U0 |
| `long-title` | `title` of 80 characters | Title wrapping on a phone | U0 |

## 3. Dashboard (record: `DashboardView`)

| Fixture | Fills | Covers | Task |
|---|---|---|---|
| `normal` | `DashboardView` with 3 groups, 8 entities, `chartAvailable` true | Stat cards, entity cards with counts and permissions, chart card placeholder | U1 |
| `no-chart` | `chartAvailable` false | Chart card hidden | U1 |
| `empty-database` | All counts 0, `largestTable` null | Stat cards with zeros, empty-state hint | U1 |
| `minimal` | `DashboardView` with zero stats, no groups, `chartAvailable` false (what the Phase 2 dashboard really shows) | Shell plus a welcome state, no data | U0 |
| `no-entities` | `groups` empty, `entityCount` 0 | Empty state that explains how entities are discovered | U1 |
| `many-groups` | 8 groups and 60 entities | Card grid layout and wrapping | U1 |
| `count-unavailable` | one entity's `count` is `null` | The dashboard card and the sidebar item show "unavailable", and that entity is left out of the totals | U1 |
| `read-only-entities` | `Permissions` with some flags false | Actions hidden or disabled per permission (no action is offered yet in Phase 3) | U1 |

## 3b. Entity page (removed in Phase 4)

The Phase 3 entity page had no record of its own: the shell plus a "not available yet" notice. Phase 4 replaced it with the list page (section 5), and deleted the notice, the page `entity`, its fixture `not-available` and the chrome variant `entity-active`.

## 4. Chart data (record: `ChartSeries`, JSON)

The preview serves these from a stub endpoint, so the chart can be built without the real statistics endpoint.

| Fixture | Fills | Covers | Task |
|---|---|---|---|
| `range-7`, `range-30`, `range-90` | `ChartSeries` for each range with 3 entities | Range switch, axis labels at each density | U7 |
| `single-entity` | 1 entity | Legend with one item, no stacking | U7 |
| `all-zero` | Every count 0 | Flat chart, empty-state text | U7 |
| `spike` | One day with a very large count | Scaling, tooltip values | U7 |
| `many-entities` | 12 entities | Legend wrapping, colour reuse | U7 |

## 5. List (records: `ListView`, `Cell`)

| Fixture | Fills | Covers | Task |
|---|---|---|---|
| `normal` | `ListView` with 6 columns (6 sortable with `sortHref` and `SortState.NONE` (the Author relation is sortable since Phase 5, because its target has a label)), 25 rows of 94, page 0 of 4, sizes 10, 25, 50, 100 each with a link and 25 `current`, export and "New record" on (the real backend offers neither yet) | Table, header links, pager range text, row checkboxes, bulk bar when rows are selected. First page: next and last links, no first or previous link | U2 |
| `empty-no-records` | No rows, `Empty.Reason.NO_RECORDS` | Empty state that explains how to create the first record | U2 |
| `empty-no-matches` | No rows, `NO_MATCHES`, search and filters active | Empty state with a way to clear search and filters | U2, U3 |
| `empty-search-scope` | `NO_MATCHES` on a table with a masked column; `hint` names the searched text columns only | The scope sentence, no masked column named (Phase 5) | U2 |
| `empty-search-nothing-searchable` | `NO_MATCHES` on a table with no text column | The "no text column a search can look in" wording (Phase 5) | U2 |
| `empty-search-escaped` | `NO_MATCHES` with column labels that look like markup | The labels are printed as text, escaped (SEC-9) | U2 |
| `search-cut` | A 100-character search, with the chrome variant `search-cut` whose `flash` holds the real cut notice | The INFO notice with the limit, above the content (Phase 5) | U2 |
| `long-text` | Cells with 300-character values, long words without spaces, long column labels | Truncation, wrapping, `title` hover text | U2 |
| `many-columns` | 20 columns (the 5 hidden ones belong to column visibility, U8) | Horizontal scroll on a phone, sticky first column, column visibility | U2 |
| `all-cell-types` | One column per `Cell.Type`: text, number, boolean, enum with each `Tone`, relation with `href`, date, image, file, masked, empty | Cell renderer for every type | U2 |
| `masked-column` | A column of `MASKED` cells, `sortable` false and `sortHref` `null` | Dots only, no value, no tooltip, no sorting or filter control | U2 |
| `search-active` | `search` set | Search box shows the term, empty-state wording | U2 |
| `sorted-asc`, `sorted-desc` | `sort` and `dir` set, that column's `SortState` is `ASCENDING` or `DESCENDING`, its `sortHref` is the toggled direction | Sort indicator, `aria-sort`, header link | U2 |
| `relation-sort-asc`, `relation-sort-desc` | Sorted by the Author relation: `SortState` of that column is `ASCENDING` or `DESCENDING`, labels in order, equal labels in identifier order | `aria-sort` and the flip link on a relation header (Phase 5, D-60) | U2 |
| `nulls-last`, `nulls-last-desc` | Sorted by Author with 100 rows per page, ten articles with no author | The empty relation cells are the last ten in both directions (Phase 5, D-60) | U2 |
| `middle-page` | `page` 1 of 4, so `firstHref`, `prevHref`, `nextHref` and `lastHref` all present | All four pager controls enabled, range text | U2 |
| `last-page` | `page` is the last page, fewer rows than the size, `firstHref` and `prevHref` present, `nextHref` and `lastHref` `null` | Pager range text, first and previous enabled, next and last disabled | U2 |
| `single-page` | `total` less than `size`, all four page links `null` | Pager hidden or minimal | U2 |
| `page-size-50` | `size` 50, the 50 option `current`, every option with a link | Page-size control shows the current size and links the others | U2 |
| `filters-open` | `Filters.definitions` of every `Kind` (text, enum, boolean, relation, number range, date range), none active, no chips, `clearAllHref` `null` (delivered, task U3) | The closed panel (a `details` element), one labelled control for each kind inside the list's own GET form, "Any" first in every select, "Apply filters" | U3 |
| `filters-active` | Six active filters, one of each kind, six `Chip` entries and a `clearAllHref` (delivered, task U3) | One chip per filter with a remove link that has an accessible name, "Clear all filters", the count on the "Filters" button, every control showing its value | U3 |
| `relation-many-options` | A relation filter with 200 options, the first four with long, unicode and markup labels (delivered, task U3) | One long select (201 options with "Any"), labels escaped, keyboard use | U3 |
| `filter-text`, `filter-enum`, `filter-boolean`, `filter-relation` | One active filter of that kind (delivered, task U3) | The control shows its value (`value`, or the `selected` option), one chip, the remove link is the plain address | U3 |
| `filter-number-range`, `filter-number-min-only`, `filter-date-range` | A number range with both bounds, a number range with only a minimum, a date range (delivered, task U3) | Both bounds filled, or one; the chip says "1000 to 5000", "at least 8000", "2026-09-01 to 2026-10-15" | U3 |
| `filter-combined` | A search, a sort by views descending, a page size of 10 and three filters (enum, number range, date range) (delivered, task U3) | Each chip's remove link drops only its filter and keeps the search, the sort and the size, and has no page; "Clear all filters" keeps the other three | U3 |
| `filter-middle-page` | One filter, page 2 of 10 rows, sorted by title (delivered, task U3) | Next, previous, sort and page-size links all carry the filter; only the page links carry a page | U3 |
| `filter-ignored` | One valid filter, with the chrome variant `filters-ignored` whose `flash` holds the real notices for ignored filters (delivered, task U3) | WARNING notices (role alert) for what was ignored and why, an INFO notice (role status) for a value that was cut, no value repeated | U3 |
| `filter-no-matches`, `filter-no-matches-with-search` | Filters that match nothing, without and with a search (delivered, task U3) | "No articles match the filters", or "match your search and filters"; "Clear all filters" and, with a search, "Clear search and filters" | U3 |
| `filter-relation-dropdown` | A relation filter with 40 choices, one selected, labels with markup, unicode and a very long name (delivered, task U3) | The select lists every choice, labels are escaped, nothing is run | U3 |
| `filter-chips-escaped` | A text filter whose value is markup, quotes and unicode (delivered, task U3) | The value is escaped in the control, the chip and the remove link's `aria-label` | U3 |
| `filter-sensitive-absent` | Members with a masked password column and filters for name, email and joined only (delivered, task U3) | The masked column has no filter and the word "password" is not in the panel | U3 |
| `filter-hidden-entity-absent` | A table with a relation column to an entity that is hidden from the console (shown as `#id`) and a masked column; the panel has title and author only (delivered, task U3) | No control for the hidden relation or the masked column, and nothing of the hidden entity is listed | U3 |
| `read-only` | `Actions` with create, export and bulk delete all false, `exportHref` and `bulkDeleteHref` `null` | No "New record", no export, no checkboxes | U2 |
| `export-enabled` | `Actions.canExport` true and `exportHref` carrying the current search, filters, sort and visible columns | Export button links to `exportHref`, plus its disabled and busy states | U8 |
| `export-disabled` | `canExport` false and `exportHref` `null`, create and bulk delete on | No export button while the other actions stay | U8 |
| `page-size-10` | page 2 of pages of 10, the 10 option `current` | Page-size control, range "21-30 of 94" | U2 |
| `relation-cells` | Relation cells with and without an `href`, and an empty relation | Link or text, "No value" | U2 |
| `unicode` | Titles in Turkish, Russian, Japanese, Greek and Arabic, an emoji with a combining mark, and a title that is markup | Text is shown as text and escaped | U2 |
| `saved-views` | `ListView` with search, sort and filters set, plus page size 50 and hidden columns; every link keeps that state | Saved views menu applies and restores this exact state from the URL | U8 |
| `bulk-delete` | 25 articles of 94 built by the real factory with bulk delete offered: the identifier cell and the author link, a checkbox column, the bulk bar, `bulkDeleteHref` (delivered, task U4) | A first column of checkboxes (name `ids`, a label per row such as "Select Article 1"), a header checkbox for this page (script only), a POST form with the CSRF token around the table and the bar, the GET search form closed before it, the bar with "Delete selected" and "Yes, delete selected" | U4 |
| `bulk-delete-state` | search `guide`, sorted by title, page 2, size 10, bulk delete offered (delivered, task U4) | The bulk form's address carries `q`, `sort`, `dir`, `page` and `size`, built by `ListLinks`, so the answer returns to this list | U4 |
| `row-links` | 8 articles, no bulk delete, size 10: every identifier a link, six authors links to members, a reviewer whose entity is hidden (text `#n`), two missing authors (delivered, task U4) | The row link carries `?size=10`; a relation to a hidden entity and an empty relation have no link; no checkbox column | U4 |
| `bulk-read-only` | the same rows with no bulk delete (delivered, task U4) | A read-only entity: row links, no checkbox, no bar, no form, no "Delete selected" | U4 |
| `bulk-escaped` | four rows whose identifiers are markup, quotes and `&` (delivered, task U4) | The checkbox label, its value and the title are escaped; an identifier that cannot be an address segment has no link | U4 |
| `bulk-last-page` | page 4 of 4 with bulk delete offered (delivered, task U4) | 19 checkboxes on a partial last page | U4 |
| `delete-done`, `bulk-done`, `bulk-partial`, `bulk-over-cap`, `bulk-blocked`, `bulk-nothing-selected` | the `bulk-delete` list with the chrome variant of the same name (delivered, task U4) | The list after a delete, with the real message above it: success, warning, the refusal over the cap of 100, a rolled-back batch, no selection | U4 |

## 6. Detail (records: `DetailView`, `Cell`)

Delivered with task U4 (Phase 7). Every state is built by the real `DetailViewFactory` from a fake `DetailRecord`, with a chrome built from the record and the list state (`/admin-preview/detail?state=...`).

| Fixture | Fills | Covers | Task |
|---|---|---|---|
| `normal` | `DetailView` with 8 fields, Edit, Duplicate and Delete offered | Detail grid, Edit and Duplicate links with the id as a path variable and a link parameter, the delete `details` question with a POST form, the CSRF token, "Yes, delete record" | U4 |
| `real-actions` | the same record with only Delete offered (what the real console offers in Phase 7) | No Edit and no Duplicate link | U4 |
| `read-only` | `Actions` all false | No action, no delete card, no form, no token | U4 |
| `empty-fields` | every field but the id empty, no label | A dash with "No value" for a screen reader; the title falls back to "Article #12" | U4 |
| `long-text` | a body of more than 300 characters with line breaks and a very long word, a medium text, a very long label | The summary of 200 characters ending in "...", the whole text in `detail-long` with `pre-wrap`, a medium text printed in the grid with its line break | U4 |
| `relations` | a member link, a label-less target (`#4`, still a link), a hidden target (`#9`, no link), an empty relation, a label that is markup | Links only where the cell has an `href`; the label escaped | U4 |
| `masked-field` | two `MASKED` cells | Dots only, "Hidden value" for a screen reader, no value anywhere | U4 |
| `with-images` | three `Image` entries, an image cell and a file cell | Image strip under the base path, escaped alternative text, a missing picture's alt text | U4, U6 |
| `all-cell-types` | one field per `Cell.Type` | Cell renderer in the detail grid | U4 |
| `collection-not-shown` | three fields that say "(not shown)" | A collection, an embedded value and binary data show their label only | U4 |
| `no-label` | `label` null | Title "Article #7" | U4 |
| `escaped` | title, labels and values that are markup, quotes, unicode | Everything printed as text | U4 |
| `with-list-state` | the page opened with search, sort, page and size | The breadcrumb for the list and the delete form's address carry `q`, `sort`, `dir`, `page`, `size`, encoded | U4 |
| `delete-blocked` | the page with the chrome variant `delete-blocked` (the real `DeleteMessages.inUse` text) | The friendly message after a refused delete, no SQL | U4 |

## 7. Form (records: `FormView`, `FormView.Field`, `FormView.Option`)

Delivered with task U5 (Phase 8). Every state is built by the real `FormViewFactory` (the addresses, the title, the breadcrumbs) and the real `FieldMessages` (the sentences) from a fake `FormPage`, with a chrome built from the page (`/admin-preview/form?state=...`). The entity is an invented "Article"; no value is real and a masked field has none.

| Fixture | Fills | Covers | Task |
|---|---|---|---|
| `create-empty` | `Mode.CREATE`, `title` "New article", `recordId` `null`, `actionHref` the collection path, `cancelHref` the list, one field per `InputType` except upload, no values (delivered, task U5) | Every input kind with its label, required markers, help text, the heading, the form posts to `actionHref` with the CSRF token, "Create record" and the Cancel question | U5 |
| `edit-prefilled` | `Mode.EDIT`, `recordId` 12, version 3, values set (delivered, task U5) | Prefilled inputs, the post address carries `?jpa-admin-version=3`, selected options, a checked box, a text area with line breaks and markup shown as text, "Save changes", Cancel goes to `cancelHref` | U5 |
| `long-identity` | `title` of 120 characters, `entityLabel` of 40 characters, a UUID as `recordId` (delivered, task U5) | Heading wrapping, a long action path | U5 |
| `validation-errors` | `Field.error` on five fields, the typed values kept, `formErrors` with the count (delivered, task U5) | Error text next to each field, linked to the input with `aria-describedby`, `aria-invalid`, the summary (role alert) with a link to each field | U5 |
| `form-errors` | `formErrors` set (the stale-form sentence), no field errors (delivered, task U5) | The summary at the top with no field marked and no links | U5 |
| `select-options` | `SELECT` fields for an enum (4 options and a blank), a required enum, and a relation (50 options), one selected (delivered, task U5) | Selects, selected state, the blank choice ("None" or "Choose...") | U5 |
| `masked-input` | `MASKED` fields with no value, in edit mode (delivered, task U5) | A password input with no `value` attribute, "Leave blank to keep the current value." | U5 |
| `masked-input-create` | `MASKED` fields in create mode, one required (delivered, task U5) | The same input without the "leave blank" help, `required` on the one that is | U5 |
| `long-form` | 30 fields, with long labels and long help text (delivered, task U5) | Form grid, layout on a phone | U5 |
| `duplicate` | `Mode.CREATE` prefilled, `recordId` `null`, `actionHref` the collection path, no masked value, and the INFO notice built by the real `SaveMessages` (delivered, task U5) | A filled create form that posts as a new record, no version in its address, the notice that says what a copy leaves out | U5 |
| `relation-notice` | `SELECT` fields with no options: one with more than 200 records, one required with none yet (delivered, task U5) | A notice instead of a control, with its help text | U5 |
| `escaped` | title, labels, help, an error and values that are markup, quotes, unicode (delivered, task U5) | Everything printed as text, escaped | U5 |
| `upload-states` | `UPLOAD` fields with a ticket: one empty, one with an existing image, one with an existing file, one with a field error | Server-provided upload states and the preview, replace and remove controls | U6 |

## 8. Uploader behaviour (record: `UploadResult`, JSON)

The preview serves these from a stub upload endpoint, chosen with the `state` fixture name. They let `uploader.js` show the states that only exist in the browser: idle, dragging, uploading with progress, done and error.

| Fixture | Fills | Covers | Task |
|---|---|---|---|
| `success-image` | `UploadResult.success` with an image URL | Done state, image preview, form can be saved | U6 |
| `success-file` | `UploadResult.success` with a document URL | Done state, file chip, download link | U6 |
| `error-too-large` | `UploadResult.failure` that says the limit and what to do | Error state, form blocked until fixed | U6 |
| `error-type` | `UploadResult.failure` for a disallowed or disguised file | Error state with a clear message | U6 |
| `error-ticket-expired` | `UploadResult.failure` for an expired ticket | Message that tells the user to reload the form | U6 |
| `slow` | `success-image` after a 5-second delay | Progress bar and "form waits for uploads before saving" | U6 |

## 9. Error pages (record: `ErrorView`)

| Fixture | Fills | Covers | Task |
|---|---|---|---|
| `not-found` | `ErrorView` with status 404 | Unknown entity or record, link back to the dashboard | U0 |
| `forbidden` | Status 403 | Message about the action not being allowed | U0 |
| `server-error` | Status 500 with a friendly heading and hint | No SQL, table names or stack trace (SEC-12) | U0 |
| `bad-request` | Status 400 | Invalid sort, filter or id parameter | U0 |
| `method-not-allowed` | Status 405 | Message that the address does not accept that kind of request | U0 |
| `long-hint` | A 400-character hint | Wrapping on a phone | U0 |

## 10. Cross-page checks (no new fixtures)

These reuse the fixtures above and are part of the acceptance checklist in `docs/UI_PLAN.md` section 10.

| Check | Fixtures used |
|---|---|
| Phone width, drawer sidebar | `long-sidebar` with `list/many-columns` |
| Keyboard only, Escape closes dialogs | `detail/normal` (the delete question), `list/bulk-delete` (the bulk bar) |
| `localStorage` unavailable | `list/saved-views`, `chrome/theme-system` |
| No sensitive value in HTML | `list/masked-column`, `detail/masked-field`, `form/masked-input` |
| No external requests | every page |
| Links come from the record | `list/middle-page`, `list/sorted-asc`, `list/export-enabled`, `form/edit-prefilled` with `chrome/custom-base-path`: every sort, page, page-size, export and form link starts with the custom base path. Also a search of the templates finds no `sort=`, `page=` or `size=` text |
| Docs screenshots | `dashboard/normal`, `list/normal`, `list/filters-active`, `detail/normal`, `form/validation-errors`, `form/upload-states`, `error/not-found` |
