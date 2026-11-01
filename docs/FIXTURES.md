# FIXTURES.md

The fixture list for `jpa-admin-ui-preview` (see `docs/UI_PLAN.md` section 6). This file lists fixtures only. The fixture code is written by the UI track from task U0 onward, not in Phase 0.

A **fixture** is a named, fake instance of a view-model record. The preview app renders a real template with it, with no database and no login.

- **URL:** `/admin-preview/{page}?state={fixture}`. Pages are `login`, `dashboard`, `list`, `detail`, `form`, `error`.
- **Chrome variant:** every page also takes `&chrome={variant}` (see section 1). If it is missing, `default` is used.
- **Theme:** light and dark are chosen in the browser by the theme toggle, so they are not fixtures. The `theme` field of `PageChrome` has its own chrome variants.
- **Fake data only:** no real names, no real secrets. Masked values are never given a value, even in a fixture (SEC-1).
- **Generic names:** fixtures use invented entities such as Article, Member and Invoice, never entity names from `sample-app` (ARCH-12).
- **Links:** every link in a fixture is a local path under the chrome's base path (`docs/UI_PLAN.md` section 3, "Links"). Fixtures are built through the real record constructors, so a link that breaks the rule cannot be written. A fixture never contains an external link. Templates print the links the fixture gives them and never build sort, page, page-size, export or form links themselves.
- **Status:** each fixture below belongs to the UI task that first needs it (U0 to U9, `docs/UI_PLAN.md` section 7). A fixture is part of "done" for that task.

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
| `no-entities` | `groups` empty, `entityCount` 0 | Empty state that explains how entities are discovered | U1 |
| `many-groups` | 8 groups and 60 entities | Card grid layout and wrapping | U1 |
| `read-only-entities` | `Permissions` with some flags false | Actions hidden or disabled per permission | U1 |

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
| `normal` | `ListView` with 6 columns (5 sortable with `sortHref` and `SortState.NONE`, 1 not sortable with no link), 25 rows, page 0 of 4, sizes 10, 25, 50, 100 each with a link and 25 `current`, export on | Table, header links, pager range text, row checkboxes, bulk bar when rows are selected. First page: next and last links, no first or previous link | U2 |
| `empty-no-records` | No rows, `Empty.Reason.NO_RECORDS` | Empty state that explains how to create the first record | U2 |
| `empty-no-matches` | No rows, `NO_MATCHES`, search and filters active | Empty state with a way to clear search and filters | U2, U3 |
| `long-text` | Cells with 300-character values, long words without spaces, long column labels | Truncation, wrapping, `title` hover text | U2 |
| `many-columns` | 20 columns, 5 hidden | Horizontal scroll on a phone, sticky first column, column visibility | U2 |
| `all-cell-types` | One column per `Cell.Type`: text, number, boolean, enum with each `Tone`, relation with `href`, date, image, file, masked, empty | Cell renderer for every type | U2 |
| `masked-column` | A column of `MASKED` cells, `sortable` false and `sortHref` `null` | Dots only, no value, no tooltip, no sorting or filter control | U2 |
| `search-active` | `search` set | Search box shows the term, empty-state wording | U2 |
| `sorted-asc`, `sorted-desc` | `sort` and `dir` set, that column's `SortState` is `ASCENDING` or `DESCENDING`, its `sortHref` is the toggled direction | Sort indicator, `aria-sort`, header link | U2 |
| `middle-page` | `page` 1 of 4, so `firstHref`, `prevHref`, `nextHref` and `lastHref` all present | All four pager controls enabled, range text | U2 |
| `last-page` | `page` is the last page, fewer rows than the size, `firstHref` and `prevHref` present, `nextHref` and `lastHref` `null` | Pager range text, first and previous enabled, next and last disabled | U2 |
| `single-page` | `total` less than `size`, all four page links `null` | Pager hidden or minimal | U2 |
| `page-size-50` | `size` 50, the 50 option `current`, every option with a link | Page-size control shows the current size and links the others | U2 |
| `filters-open` | `Filters.definitions` of every `Kind` (text, enum, boolean, relation, number range, date range), none active | Filter panel inputs for each kind | U3 |
| `filters-active` | Several active `Chip` entries and `clearAllHref` | Removable chips, clear all, filter button count | U3 |
| `relation-many-options` | A relation filter with 200 options | Long select, keyboard use | U3 |
| `read-only` | `Actions` with create, export and bulk delete all false, `exportHref` `null` | No "New record", no export, no checkboxes | U2 |
| `export-enabled` | `Actions.canExport` true and `exportHref` carrying the current search, filters, sort and visible columns | Export button links to `exportHref`, plus its disabled and busy states | U8 |
| `export-disabled` | `canExport` false and `exportHref` `null`, create and bulk delete on | No export button while the other actions stay | U8 |
| `saved-views` | `ListView` with search, sort and filters set, plus page size 50 and hidden columns; every link keeps that state | Saved views menu applies and restores this exact state from the URL | U8 |

## 6. Detail (records: `DetailView`, `Cell`)

| Fixture | Fills | Covers | Task |
|---|---|---|---|
| `normal` | `DetailView` with 8 fields, all actions | Detail grid, Edit, Duplicate and Delete buttons, confirm dialog on delete | U4 |
| `all-cell-types` | One field per `Cell.Type` | Cell renderer in the detail grid | U4 |
| `with-images` | 3 `Image` entries | Image strip, alt text, broken-image fallback | U4, U6 |
| `masked-field` | A field with a `MASKED` cell | Dots only | U4 |
| `long-text` | A 2,000-character value and a long word | Wrapping, "show more" | U4 |
| `read-only` | `Actions` all false | No action buttons | U4 |
| `delete-blocked` | `normal` page with an error flash from a blocked delete (via the `flash-all-levels` chrome) | Friendly foreign-key message shown after a failed delete | U4 |

## 7. Form (records: `FormView`, `FormView.Field`, `FormView.Option`)

| Fixture | Fills | Covers | Task |
|---|---|---|---|
| `create-empty` | `Mode.CREATE`, `title` such as "New article", `recordId` `null`, `actionHref` the collection path, `cancelHref` the list, one field per `InputType` except upload, no values | Every input kind, required markers, help text, heading from `title` and `entityLabel`, form posts to `actionHref` | U5 |
| `edit-prefilled` | `Mode.EDIT`, `recordId` set, `actionHref` the record path, `cancelHref` the detail page, values set | Prefilled inputs, unsaved-changes guard when a value changes, Cancel goes to `cancelHref` | U5 |
| `long-identity` | `title` of 120 characters, `entityLabel` of 40 characters, a UUID as `recordId` | Heading wrapping, long action path | U5 |
| `validation-errors` | `Field.error` on several fields | Error text next to each field, linked to the input, focus on the first error | U5 |
| `form-errors` | `formErrors` set, no field errors | Error summary at the top of the form | U5 |
| `select-options` | `SELECT` fields for an enum (4 options) and a relation (50 options) | Selects, selected state, empty choice | U5 |
| `masked-input` | A `MASKED` field with no value, in create and in edit mode | Masked input, "leave blank to keep the current value" help | U5 |
| `long-form` | 30 fields, with long labels and long help text | Form grid, layout on a phone | U5 |
| `duplicate` | `Mode.CREATE` prefilled, `recordId` `null`, `actionHref` the collection path, no masked value | Duplicate flow starts from a filled create form and posts as a new record | U5 |
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
| `long-hint` | A 400-character hint | Wrapping on a phone | U0 |

## 10. Cross-page checks (no new fixtures)

These reuse the fixtures above and are part of the acceptance checklist in `docs/UI_PLAN.md` section 10.

| Check | Fixtures used |
|---|---|
| Phone width, drawer sidebar | `long-sidebar` with `list/many-columns` |
| Keyboard only, Escape closes dialogs | `detail/normal` (delete dialog), `list/normal` (bulk bar) |
| `localStorage` unavailable | `list/saved-views`, `chrome/theme-system` |
| No sensitive value in HTML | `list/masked-column`, `detail/masked-field`, `form/masked-input` |
| No external requests | every page |
| Links come from the record | `list/middle-page`, `list/sorted-asc`, `list/export-enabled`, `form/edit-prefilled` with `chrome/custom-base-path`: every sort, page, page-size, export and form link starts with the custom base path. Also a search of the templates finds no `sort=`, `page=` or `size=` text |
| Docs screenshots | `dashboard/normal`, `list/normal`, `list/filters-active`, `detail/normal`, `form/validation-errors`, `form/upload-states`, `error/not-found` |
