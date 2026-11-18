# UI_PLAN.md

How the console UI is designed and built **in parallel with the backend, in one project directory, in separate module folders**. Read with `docs/PLAN.md` (phases), `docs/RULES.md` (UI-1 to UI-12, SEC, UPL) and `docs/prototype/admin-console.html` (visual reference).

---

## 1. Principle: contract first, then two folders

The UI and backend meet at one boundary: **view-model records** (Java records in `io.github.mdmusahub.jpaadmin.web.view`, inside `jpa-admin-core`). Templates only read these records. Controllers only fill them.

- **UI folder** (`jpa-admin-ui`, plus `jpa-admin-ui-preview`) holds Thymeleaf templates, CSS, JavaScript and fixture data. It never calls services, repositories or `EntityManager`.
- **Backend folders** (`jpa-admin-core`, `jpa-admin-autoconfigure`) hold scanner, services, controllers and security, producing the same records from real data.
- When both are done for a feature, they connect with no rework, because the shape was agreed first.

## 2. Project layout and ownership

```
JPA Super Admin/                      one git repo, one branch per phase
  CLAUDE.md                           shared rules (read by every session)
  pom.xml                             parent
  jpa-admin-core/                     BACKEND  metadata, services, controllers, view-model records
    CLAUDE.md                         backend-only instructions
  jpa-admin-autoconfigure/            BACKEND  properties, security chain, auto-configuration, resource handlers
  jpa-admin-ui/                       UI       templates + CSS + JS only (resource jar, no Java logic)
    CLAUDE.md                         UI-only instructions
    src/main/resources/templates/jpa-admin/**
    src/main/resources/jpa-admin/assets/**
  jpa-admin-ui-preview/               UI       small Spring Boot app that renders the real templates with fixtures
  jpa-admin-starter/                  BACKEND  empty pom: depends on autoconfigure, core, ui
  sample-app/                         BACKEND  demo app with entities and tests
  docs/
```

| Area | Owner | Path |
|---|---|---|
| Java logic, controllers, services, security, auto-configuration | Backend | `jpa-admin-core/src/main/java/**` (except `web/view`), `jpa-admin-autoconfigure/**`, `jpa-admin-starter/**`, `sample-app/**` |
| View-model records (the contract) | Both. Changes need approval and a `DECISIONS.md` entry | `jpa-admin-core/.../web/view/**` |
| Templates, CSS, JavaScript | UI | `jpa-admin-ui/**` |
| Fixtures and preview controller | UI | `jpa-admin-ui-preview/**` (never published, never part of the starter) |
| Shared docs and parent pom | Whoever is working, one session at a time | `docs/**`, `pom.xml`, `CLAUDE.md` |

The starter depends on `jpa-admin-ui`, so templates and assets reach the host application automatically. Thymeleaf finds templates on the classpath (the console uses its own private engine for them, D-40, so the host's Thymeleaf or other view technology is neither used nor changed), and the backend registers a resource handler that maps `{path}/assets/**` to `classpath:/jpa-admin/assets/`. That location is deliberately not one of Spring Boot's default static locations (`/static`, `/public`, `/resources`, `/META-INF/resources`), so the assets are served only through the console's own handler and never at a second URL in the host application. That handler never serves hidden files or folders (any path segment that starts with a dot), so a stray `.gitkeep` or `.DS_Store` in the asset folder is never published (D-32); the `.gitkeep` that used to sit in that folder has been removed.

## 3. The contract (view models)

Defined once, at the contract freeze (section 8). All fields are display-ready strings, numbers or lists. No entities, no collections of entities, no sensitive values.

| Record | Used by | Main fields |
|---|---|---|
| `PageChrome` | Every page | `title`, `basePath`, `theme`, `nav` (groups with entity name, label, icon, count; a `null` count means "unavailable"), `activeEntity`, `breadcrumbs`, `flash` messages, `user` (signed-in name, `null` when nobody is), `csrf` (parameter name and token) |
| `LoginView` | Login | `title`, `error`, `logoutNotice` |
| `DashboardView` | Dashboard | `stats` (total records, entities, groups, largest table), `groups` (entities with count and permissions), `chartAvailable` |
| `ChartSeries` (JSON) | Chart endpoint | `range`, `days` (date, count per entity), `entities` |
| `ListView` | List | `entity` (label, plural, icon), `columns` (key, label, type, sortable, sort link, sort state, align, visible, filterable), `rows` (id, cells), `search`, `sort`, `dir`, `filters` (definitions plus active chips, each with its link), `paging` (page, size, total, first, previous, next and last links, sizes each with a link), `actions` (can create, export, bulk delete, export link, bulk-delete link), `empty` (reason, hint) |
| `Cell` | List, detail | `type` (text, number, boolean, enum, relation, date, image, file, masked, empty), `display`, `tone`, `href`, `title` (hover text) |
| `DetailView` | Detail | `title`, `entityLabel`, `id`, `fields` (label, cell), `images`, `actions` (can edit, can duplicate, can delete, delete link) |
| `FormView` | Create, edit | `mode`, `title`, `entityLabel`, `recordId` (`null` on create), `actionHref`, `cancelHref`, `fields` (key, label, input type, value, required, help text, options, error), `formErrors`, `uploadTicket` per upload field |
| `UploadResult` (JSON) | Upload endpoint | `url`, `name`, `size`, `error` |
| `ErrorView` | Error pages | `status`, `heading`, `hint` |

Rule: a cell of type `masked` carries no value at all, only the display dots. A `FormView` field for a sensitive property has input type `masked` and no value; a blank submit on edit keeps the stored value. Templates must not receive sensitive data in any form (SEC-1).

Draft status: until the phase that fills a record starts, the record is a draft. A draft change needs approval but no `DECISIONS.md` entry. After that phase starts, every change needs both (section 8). Records are public but internal, not stable API (ARCH-9). Model attribute names (for example `chrome` for `PageChrome` and `view` for the page record) are fixed at the contract freeze, so templates and controllers agree.

### Frozen shape (contract freeze, Phase 0)

The records live in `jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/view/`. Every record and enum is public, and its Javadoc says it is draft, internal, not stable API and holds no sensitive values (ARCH-9 exception). A test enforces this. The one exception to "records only" is `Hrefs.java`: a package-private, non-record helper that holds the link rule shared by the records (D-28, D-29). It is not API, and the same test fails if any other non-record file appears in the package.

| Record | Nested types |
|---|---|
| `PageChrome` | `Theme`, `NavGroup`, `NavItem`, `Breadcrumb`, `FlashMessage` (`Level`), `Csrf` (parameter name, header name, token) |
| `LoginView` | none |
| `DashboardView` | `Stats`, `LargestTable`, `Group`, `EntityCard`, `Permissions` (create, update, delete, export) |
| `ChartSeries` | `Day` (ISO date, counts in entity order). `range` is 7, 30 or 90 |
| `ListView` | `EntityInfo`, `Column` (`Align`, `SortState`), `Row`, `Filters` (`Definition` with `Kind`, `Chip`), `Paging` (`SizeOption`), `Actions`, `Empty` (`Reason`) |
| `Cell` | `Type`, `Tone`, constant `MASK` |
| `DetailView` | `Field`, `Image`, `Actions` |
| `FormView` | `Mode`, `Field` (`InputType`), `Option` (also used by filter definitions) |
| `UploadResult` | none. `success(...)` or `failure(...)` |
| `ErrorView` | none |

Conventions the records enforce or assume:

- Required values are never `null`; the constructor throws. Optional values are `null` when absent (for example `Cell.href`, `Breadcrumb.href`, `FormView.Field.help`), so templates test `x != null`. Text that is a form input value (`ListView.search`) is empty, not `null`.
- Lists are never `null`, are copied with `List.copyOf` and cannot be modified.
- A row has exactly one cell per column, hidden columns included. `ListView.Empty.Reason` is `NONE` exactly when there are rows. `sort` and `dir` are both set or both `null`; `dir` is `asc` or `desc`.
- A `MASKED` cell holds only `Cell.MASK`, with no tone, href or title. A `MASKED` form field has no value. An `UPLOAD` field has a ticket and no other field type has one.
- `Cell` conventions: an `IMAGE` cell has the image URL in `href` and the file name in `display`; a `FILE` cell has the download URL in `href`; a `RELATION` cell links to the related record in `href`.
- The backend builds the links and the template only prints them (D-28, see "Links" below). Since Phase 7 (D-74) the backend builds every link that carries state: the way back to the list, the row link and the relation link to a detail page, the delete address and the bulk-delete address. The template still builds the few fixed routes that carry none, from `chrome.basePath` and `chrome.activeEntity` (the dashboard link, "New record", logout, and "Edit" and "Duplicate" with the id as a Thymeleaf path variable or link parameter, which the engine percent-encodes). Every form posts the CSRF token from `chrome.csrf`. A test searches every template for a hand-built query string.
- A column's `sortState` matches `ListView.sort` and `dir`: the column whose key is `sort` is `ASCENDING` or `DESCENDING`, every other column is `NONE`. `sortHref` is present exactly when the column is sortable. Template: `aria-sort` and the arrow come from `sortState`, the header links to `sortHref`.
- Paging links: `firstHref` and `prevHref` are present exactly when `page > 0`; `nextHref` and `lastHref` exactly when there is a later page (`page + 1 < totalPages()`). Exactly the `SizeOption` whose size equals `size` has `current` true.
- `Actions.exportHref` is present exactly when `canExport` is true. It carries the current search, filters, sort and visible columns.
- `Actions.bulkDeleteHref` is present exactly when `canBulkDelete` is true, and `DetailView.Actions.deleteHref` exactly when `canDelete` is true (D-65). Each is the delete route plus the list state in its query; the controller re-reads that state with the list parser, so the redirect after a delete returns to the same list and is never taken from a request.
- A `RELATION` cell links to the related record when its column names a target in the console (a hidden target has no link, D-54, D-70). The identifier cell of a row links to the record. A `TEXT` or `NUMBER` cell with an `href` is such a link.
- `FormView.recordId` is present exactly in `EDIT` mode. The duplicate flow is a `CREATE` form that is pre-filled, so it has no `recordId`.
- Phase 8 conventions of `FormView`, with no change to the record (D-75): the edit form's `actionHref` carries the version the form was opened with (`?jpa-admin-version=N`, only for an entity with a `@Version`); a `SELECT` field with no `options` is a notice (its `help` says why) and prints no control, and every other select starts with a blank option ("None" or "Choose..."); `formErrors` holds the whole-form sentences, led by one line that counts the fields with errors; a `MASKED` field has no value in any mode and its input has no `value` attribute; an `UPLOAD` field is not rendered until Phase 9.
- The login page also receives a `PageChrome`, with an empty sidebar and `user` `null`, for the base path, theme and CSRF token.
- A record count (`PageChrome.NavItem.count`, `DashboardView.EntityCard.count`) is a `Long`: a number, or `null` when the console could not read it, in which case the page prints "unavailable" and the entity is left out of `DashboardView.Stats.totalRecords` and `largestTable` (D-44). `PageChrome.user` is the signed-in username or `null`; it holds no credential (SEC-1).

### Template names and model attributes

| Page | Template (view name) | Model attributes |
|---|---|---|
| Login | `jpa-admin/login` | `chrome` (`PageChrome`), `view` (`LoginView`) |
| Dashboard | `jpa-admin/dashboard` | `chrome`, `view` (`DashboardView`) |
| List | `jpa-admin/list` | `chrome`, `view` (`ListView`) |
| Detail | `jpa-admin/detail` | `chrome`, `view` (`DetailView`) |
| Create and edit | `jpa-admin/form` | `chrome`, `view` (`FormView`) |
| Error | `jpa-admin/error` | `chrome`, `view` (`ErrorView`) |
| Chart data | none (JSON body) | `ChartSeries` |
| Upload result | none (JSON body) | `UploadResult` |

The names are constants in the records (`PageChrome.ATTRIBUTE`, `PageChrome.VIEW_ATTRIBUTE`, and `TEMPLATE` on each page record), so controllers and the preview app use the same strings as the tests.

### Links (D-28)

The backend builds every link the page prints; templates only print them. Every link in every record follows one rule, enforced by the record constructors (the package-private `Hrefs` helper) and tested for each place a link appears:

- It is a relative path that starts with a single `/`.
- It has no scheme, so `javascript:`, `data:`, `https:` and similar are rejected. It has no `//` in its path, so protocol-relative URLs are rejected. The query and fragment may contain `//` and `:`, because they are data there.
- It has no backslash, no whitespace and no control character. Browsers strip tabs and line breaks from URLs, so `/<tab>/host` would become `//host`.
- Links under `PageChrome` (breadcrumbs) must lie under `basePath`. `basePath` itself follows the same rule, because templates build the fixed entity routes from it. The other records do not carry `basePath`, so controller tests from Phase 1 on must assert that every link a controller builds is under the base path (D-29).
- The code that builds a link must percent-encode every parameter value (search text, filter values, sort keys); Phase 4 tests cover spaces, slashes, quotes, unicode and a literal `http://x` search (D-29). A value that is not encoded and contains a backslash, a space or a control character makes the record constructor throw; the error names the field and never repeats the link.

| Record | Links |
|---|---|
| `PageChrome` | `basePath`, `Breadcrumb.href` (optional) |
| `Cell` | `href` (optional) |
| `ListView.Column` | `sortHref` (present exactly when sortable) |
| `ListView.Filters` | `clearAllHref` (optional), `Chip.removeHref` |
| `ListView.Paging` | `firstHref`, `prevHref`, `nextHref`, `lastHref` (optional, see conventions), `SizeOption.href` |
| `ListView.Actions` | `exportHref` (present exactly when export is offered), `bulkDeleteHref` (present exactly when bulk delete is offered) |
| `DetailView.Actions` | `deleteHref` (present exactly when delete is offered) |
| `FormView` | `actionHref`, `cancelHref` |
| `DetailView.Image` | `url` |
| `UploadResult` | `url` (success only) |

The four gaps found at the freeze (sort links, page links and page sizes, export link, form identity) were closed by D-28 on 2026-11-01. There are no open contract questions.

## 4. Template and asset structure (inside `jpa-admin-ui`)

```
src/main/resources/templates/jpa-admin/
  layout.html                 shell: sidebar, top bar, theme toggle, flash, content slot
  login.html  dashboard.html  list.html  detail.html  form.html  error.html
  fragments/
    sidebar.html  topbar.html  breadcrumbs.html
    table.html  pager.html  filter-panel.html  filter-chips.html  bulk-bar.html
    cell.html                 renders any Cell by type
    field-input.html          renders any form input by type
    uploader.html  confirm-dialog.html  stat-cards.html  chart.html  toast.html

src/main/resources/jpa-admin/assets/
  css/  tokens.css (colours, spacing, type, both themes)  base.css  components.css  pages.css
  js/   theme.js  nav.js  table.js  filters.js  views.js (saved views)  uploader.js
        chart.js  dialogs.js  shortcuts.js  csrf.js
  img/  logo and an inline SVG icon sprite
```

View names and model attributes are fixed in section 3 (`jpa-admin/login`, `jpa-admin/dashboard`, `jpa-admin/list`, `jpa-admin/detail`, `jpa-admin/form`, `jpa-admin/error`; attributes `chrome` and `view`).

Rules: no CDN or remote fonts (UI-2), no build step (CODE-6), plain JavaScript modules per feature, links and forms work first and JavaScript only enhances them.

## 5. Design system (taken from the prototype)

**Tokens** (CSS variables in `tokens.css`, with a dark set): background, panel, ink, muted text, line, accent (default `#0d9f6e`, configurable and validated as `#rrggbb`), accent tint, hover, red, amber and grey tones with tints for badges. System UI font stack, base 14px, small text 12px. 8px control radius, 12px card radius, 1px lines, one soft shadow only for floating bars and dialogs. 4px spacing scale. Page padding 24px (14px on phones).

**Layout:** 248px sidebar (drawer under 900px), sticky top bar with breadcrumbs and theme toggle, content area up to about 1240px.

**Components** (each gets a preview state): stat card, bar chart with range switch, sidebar group and item with count, search input, filter button, filter chips, sortable table, row checkbox, enum badge, relation link, masked value, pager, floating bulk bar, detail grid, form grid, switch, select, date input, uploader (idle, dragging, uploading, done, error), confirm dialog, toast, empty state, error state.

**Quality floor:** keyboard reachable, visible focus, labelled icon buttons, contrast in both themes, reduced motion respected, theme applied before first paint by a small blocking `theme.js` in `<head>` (no inline scripts, handlers or styles, UI-13). The accent colour is served as a validated generated stylesheet (SEC-15), detailed in Phase 13.

**Copy:** sentence case, active verbs ("Save changes", "Delete record"), the same action name in button, dialog and toast, errors say what went wrong and how to fix it, empty states say what to do next.

## 6. Preview app (UI works without the backend)

`jpa-admin-ui-preview` is a tiny Spring Boot app that depends on `jpa-admin-core` (for the records) and `jpa-admin-ui` (for templates and assets). It has no database and no login. A `PreviewController` renders every real template with fixture view models.

- Fixtures cover: normal data, empty table, long text, many columns, every cell type, validation errors, upload states, flash messages, a very long entity list.
- URL pattern: `/admin-preview/{page}?state={fixtureName}`.
- It is not published and is not a dependency of the starter.

Run it: `mvn -pl jpa-admin-ui-preview -am spring-boot:run`

## 7. How UI work maps to the backend phases

UI tasks use the prefix **U**. A UI task is "done" when it works in the preview with all relevant fixtures. A backend phase is "done" as in `PLAN.md`. A feature is **connected** when the real controller fills the record and the real page works in the sample app.

| UI task | Builds | Runs alongside backend phase | Connected after |
|---|---|---|---|
| U0 | Tokens, base styles, layout, top bar, theme toggle, login page, error pages | 1 Auto-config, 2 Login | Phase 2 |
| U1 | Sidebar with groups and counts, dashboard cards (no chart yet) | 3 Discovery | Phase 3 |
| U2 | List table, cell renderer, search box, sortable headers, pager, empty state | 4 List (search and sort were built with the list, D-50); Phase 5 (D-60) refined it with no template or stylesheet change | Phase 4 (connected: the real list page works in the sample app) |
| U3 | Filter panel, chips, clear all (the filter links and the `Filters` data come with it from the backend, moved out of Phase 4, D-60). **Delivered 2026-11-17, see "U3 status" below** | 6 Filters | Phase 6 |
| U4 | Detail page, confirm dialog, bulk bar, duplicate and delete flows, row checkboxes in the list, and relation cells as links (D-54). **Delivered 2026-11-17, see "U4 status" below** | 7 Detail and delete | Phase 7 |
| U5 | Form page, all field inputs, validation display, unsaved-changes guard. **Delivered 2026-11-18, see "U5 status" below** | 8 Create and edit | Phase 8 |
| U6 | Uploader component, previews, progress, errors, image cells | 9 Uploads | Phase 9 |
| U7 | Dashboard chart with range switch | 10 Charts | Phase 10 |
| U8 | Export button states, saved views menu, column visibility | 11 Export, 12 Saved views | Phase 12 |
| U9 | Accessibility, responsive and dark theme passes (including the manual phone-width pass of the list), keyboard shortcuts (`/` for the search box), sticky table header, docs screenshots | 13 Polish | Phase 13 |

Phase 5 (re-scoped, D-60) has no UI task of its own: its four items change data the list templates already read (a relation header gains a sort link and `aria-sort`, the empty-state hint and a notice carry new text), so `list.html`, `table.html` and the stylesheets are expected to stay as they are. If one of them has to change, the change goes in with a note in this section. **Result (2026-11-17): none of them changed**; see "U2 refinements status" below. An automated real-browser test of the list belongs to Phase 14.

### U0 status (2026-11-01, Phase 2)

Delivered and connected in the sample app: `tokens.css`, `base.css`, `components.css`, `pages.css`; `theme.js` (blocking, in `<head>`) and `nav.js`; `layout.html`, `login.html`, `dashboard.html` (welcome state only), `error.html`, and the fragments `head`, `sidebar`, `topbar`, `breadcrumbs`, `toast`, `icons`, `theme-toggle`; preview fixtures for login, the minimal dashboard, the error pages and nine chrome variants. The look follows section 5 because `docs/prototype/admin-console.html` was not in the repository when U0 was built. The maintainer is adding that file themselves (2026-11-02) and will ask for a UI alignment pass after their commit; that pass is not part of U0 and nothing was changed for it.
Since 2026-11-02 (D-40) the real console renders these templates with its own private Thymeleaf engine, which finds them under `classpath:/templates/jpa-admin/` and reads nothing of the host's Thymeleaf setup or `spring.thymeleaf.*` settings. Template names must stay lower-case words joined by hyphens (`jpa-admin/entity-list`, not `jpa-admin/EntityList`), or the console refuses them. The preview app is unchanged: it keeps Boot's Thymeleaf with `spring.thymeleaf.cache=false`, so a template change shows after a rebuild.
Not part of U0 and still to do: stat cards, entity cards and the chart (U1, U7), tables and everything after, the accent colour and theme polish pass and screenshots for the docs (U9), toasts that disappear by themselves (U9), a full focus trap in the drawer (U9), and the `long-sidebar` chrome variant (U1).

### U1 status (2026-11-10, Phase 3)

Delivered and connected in the sample app. The sidebar now has the brand subtitle "Administration", a nav search box that filters the entity list in the browser (an enhancement: hidden without JavaScript, and the list is fully usable without it), entity groups that collapse and expand as `<details>` elements (closed state remembered in one `localStorage` key, in try/catch, UI-11), entity items with an icon and a count (thousands separators, and "unavailable" when the console could not read the count), the active item highlighted, and a user chip at the bottom with the signed-in name and the sign-out (the POST with the CSRF token moved here from the top bar, so there is one sign-out on the page). The dashboard has four stat cards and one card per group listing its entities with counts and links, an empty-database state and a "no entities" welcome state that names `@AdminHidden` and `jpa.admin.excluded-entities`. A temporary `entity-notice.html` is the entity page until U2 builds the table.

Contract change (D-44): `PageChrome` gained `user` (nullable; `null` on the sign-in and error pages and for an anonymous visitor), and `PageChrome.NavItem.count` and `DashboardView.EntityCard.count` became `Long` (`null` = the count could not be read). The `nav` of `PageChrome` now holds the discovered entities; before Phase 3 it was always empty.

New preview fixtures: chrome variants `long-sidebar` (8 groups, 60 entities, counts from 0 to 1,000,000, one unavailable, a very long name and names in five scripts), `no-user` and `entity-active`; dashboard `normal`, `no-chart`, `empty-database`, `count-unavailable`, `no-entities`, `many-groups`, `read-only-entities`; the new `entity` page with the state `not-available`.

A real Thymeleaf-precedence bug was found and fixed by the preview test: `th:replace` runs before `th:if`, so an `entity-icon(name)` fragment with both on one element rendered every icon; they are now on separate elements. Still to do (U9): the `read-only-entities` dashboard fixture does not yet hide actions, because Phase 3 offers no per-entity action at all (every `Permissions` is false); real permissions arrive with Phase 7.

### U2 status (2026-11-11, Phase 4)

Delivered and connected in the sample app: `list.html` and the fragments `table`, `cell` and `pager`; styles in `pages.css`; the pager icons. The page is a header (icon, plural name, record count, and the actions the record offers), a GET search form with a label, hidden fields for the current sort and size and a "Clear search" link, the table, the pager and the empty state. Search is a plain form and sort and paging are plain links, so the page works without JavaScript and the whole state is in the address (UI-10); **no JavaScript file was added** (`table.js` of section 4 is not needed yet). The table sits in a scroll box of its own (focusable, with a name), so a wide table scrolls inside the box and never the page; the first column stays in view; a sortable header is a link with an arrow drawn from `aria-sort`; a column that cannot be sorted (a masked column, and, since Phase 5, only a relation shown without a label) is plain text. The cell fragment renders every `Cell.Type`: text (cut text shows its full text as hover text), number (right aligned through the column), boolean (check or cross with the word), enum badge by tone, relation (a link when the record has an `href`, otherwise text), date, image, file, masked (dots, with "Hidden value" for a screen reader) and empty (a dash, with "No value"). Every value is printed with `th:text`; a test renders `<script>` as text. The pager shows the range ("Showing 26-50 of 94"), the page-size links (current marked with `aria-current`), "Page 2 of 4" and first/previous/next/last as icon links with `aria-label`; a control with no link is dimmed and hidden from screen readers. No template builds a sort, page, size, `dir` or `q` link (a test searches the markup). "New record" and the export button are printed only when `view.actions` says so; the real backend offers neither yet, so nothing is disabled.

No record changed (no contract change). New preview fixtures: the `list` page with `normal`, `middle-page`, `last-page`, `single-page`, `page-size-10`, `page-size-50`, `sorted-asc`, `sorted-desc`, `search-active`, `empty-no-records`, `empty-no-matches`, `read-only`, `masked-column` (with the chrome variant `member-active`), `relation-cells`, `long-text`, `unicode`, `many-columns` and `all-cell-types`. The chrome variant `entity-active` and the page `entity` were removed with the notice. Not yet in the preview (their tasks): the filter fixtures (U3), `export-enabled`, `export-disabled` and `saved-views` (U8). The image cell has no picture to show in the preview, because no image asset exists before the uploader (U6): it shows its alt text.

Still to do: keyboard shortcut `/` for the search box and a full-width sticky header on long tables (U9); column visibility and saved views (U8, Phase 12); row checkboxes and the bulk bar (U4, Phase 7). A browser pass in light and dark, at phone width and with the keyboard, is a manual step listed in `docs/logs/phase-4-report.md`.

### U2 refinements status (2026-11-17, Phase 5)

Delivered and connected in the sample app. **No template, stylesheet or JavaScript file changed**, as expected in section 7: everything the refinements need is data the templates already read. A relation header with a label is now a sortable header, so `table.html` prints `aria-sort="none"` and a sort link for it, and `ascending` or `descending` when it is the sorted one, exactly as for any other column. The search-scope sentence arrives in `view.empty.hint`, which `list.html` prints with `th:text` (escaped); the cut notice arrives as an INFO message in `chrome.flash`, which the toast fragment prints as a `role="status"` message above the content, readable without JavaScript, in both themes, with no inline script or style. A mutation that removed `aria-sort` from relation headers was caught by the sample-app sort proof (mutation e).

New preview fixtures, built by the real `ListViewFactory`: `relation-sort-asc`, `relation-sort-desc`, `nulls-last`, `nulls-last-desc` (the ten articles with no author are the last ten rows both ways), `empty-search-scope`, `empty-search-nothing-searchable`, `empty-search-escaped` (column labels that look like markup are shown as text), and `search-cut` with the new chrome variant `search-cut`. In the preview's article fixtures the Author column is sortable now; the `long-text` fixture keeps an unsortable relation (a relation shown without a label). `docs/FIXTURES.md` section 5 lists them. Still manual (Phase 13, U9): phone width, keyboard order and dark theme of the two new messages on the real pages.

### U3 status (2026-11-17, Phase 6)

Delivered and connected in the sample app (D-62 to D-64). **No record changed** (no contract change): the frozen `Filters`, `Definition`, `Option` and `Chip` carried everything, a range uses `from` and `to` for its two bounds (the kind says whether the names are `min`/`max` or `from`/`to`), and the notices use `chrome.flash`.

The search box and the filter panel are **one GET form** (`data-filter-form`), so a search keeps the filters and a filter keeps the search, and the whole state is in the address (UI-10). The panel (`fragments/filter-panel.html`) is a `details` element under the search row: closed by default, opened by the "Filters" button (which shows the number of active filters), no script needed. It holds one labelled control for each definition: a text input, a select with "Any" first for enum, yes/no and relation, a fieldset with a "Min" and a "Max" number input, a fieldset with a "From" and a "To" date input (every input has its own `id` and `label for`, with the field name added for screen readers), then "Apply filters" and, when something is active, "Clear all filters". The active filters are chips (`fragments/filter-chips.html`) between the form and the table: each has a remove link (an icon with an `aria-label` such as "Remove filter Status: Published") and there is a "Clear all filters" link; both links come from the backend, which keeps the search, the sort and the page size and drops the page. "Clear search" says "Clear search and filters" when filters are active, and the empty state offers the matching way back. `Column.filterable` is true exactly for a filterable column. Ignored or cut filters are shown by the existing notice area (WARNING as an alert, INFO as a status).

`filters.js` (about 30 lines, loaded with `defer`) only leaves empty controls out of the address when the form is submitted and switches them back on when the page is shown again; the page works without it, because the server ignores blank values silently. Styles are in `pages.css` (section "list page: filters"): tokens only, so both themes follow; the controls wrap in a grid and stack at 599px and below; chips wrap; a remove target is 28px with a visible focus ring; no animation. No inline script, style or handler, no `th:utext`.

New preview fixtures (list states, built by the real factory, see `docs/FIXTURES.md` section 5): `filters-open`, `filters-active`, `relation-many-options`, `filter-text`, `filter-enum`, `filter-boolean`, `filter-relation`, `filter-number-range`, `filter-number-min-only`, `filter-date-range`, `filter-combined`, `filter-middle-page`, `filter-ignored` (with the chrome variant `filters-ignored`), `filter-no-matches`, `filter-no-matches-with-search`, `filter-relation-dropdown`, `filter-chips-escaped`, `filter-sensitive-absent`, `filter-hidden-entity-absent`. A manual browser pass (light and dark, phone width, keyboard only) is listed in `docs/logs/phase-6-report.md`; the automated real-browser test stays a Phase 14 item.

### U4 status (2026-11-17, Phase 7)

Delivered and connected in the sample app (D-65 to D-74). **One contract change, asked first and approved (D-65):** `ListView.Actions.bulkDeleteHref` and `DetailView.Actions.deleteHref`, each present exactly when its flag is true. Every other record is unchanged.

**The detail page** (`detail.html`, `GET {path}/{entity}/{id}`) is a heading (the record's label, with "Entity #id" above it), the actions, a card with a labelled grid of every visible field, an image strip when the record has images, and a "Delete this record" card. Every value goes through the cell fragment (`th:text`, escaped): a masked value is dots with "Hidden value" for a screen reader, a missing value is a dash with "No value", a collection, an embedded value or binary data shows "(not shown)", a relation is a link when its cell has an `href`. A long text keeps its line breaks and wraps long words; past 300 characters it sits behind a `details` element whose summary is the start of it. "Edit record" and "Duplicate record" are printed only when `view.actions` says so (the real console says neither until Phase 8, D-73); the delete control is printed when `canDelete`. The way back to the list is the breadcrumb, which keeps the list state the page was opened with.

**Deleting needs no script** (UI-5): "Delete record" is a `details` element whose panel holds the question and a plain POST form (the CSRF token, `view.actions.deleteHref`) with "Yes, delete record". The same pattern is the bulk bar's "Delete selected". After the POST the console redirects (303) to the list, with a message above the content (`chrome.flash`, role `status` for success and `alert` for a refusal), or back to the record when the database refused.

**Row checkboxes and the bulk bar** (`fragments/table.html`, `fragments/bulk-bar.html`, `list.html`, `bulk.js`): when `Actions.canBulkDelete` is true the table has a first column of checkboxes (name `ids`, the value is the row id, each with a visually hidden label such as "Select Article 12", escaped) and a header checkbox that selects only this page (it says so in its label and is shown only with script). The table and the bar are inside a POST form of their own with the CSRF token; the search and filter form stays a separate GET form that closes before it; no form is inside another. Without script the bar is a row under the table and the confirmation is the `details` panel; with script (`bulk.js`, about 70 lines, loaded with `defer`, stores nothing) the bar is fixed at the bottom of the screen while something is selected and shows the count, Escape closes the question, and the selection is cleared whenever the page is shown (Back and the browser's cache included). The first column of a row (the identifier) and every set relation cell are links, built by the backend.

Styles are in `components.css` (the danger button, script-only and no-script-only helpers, the confirmation) and `pages.css` (checkbox column, bar, detail grid): tokens only, so both themes follow; the grid and the bar stack at 599px and below; no animation; no inline script, style or handler, no `th:utext`.

New preview fixtures (built by the real factories, see `docs/FIXTURES.md` sections 5 and 6): the page `detail` with 15 states and 12 new `list` states, and 9 new chrome variants for the pages that follow a delete. A manual browser pass (light and dark, phone width, keyboard only, script off) is listed in `docs/logs/phase-7-report.md`; the automated real-browser test stays a Phase 14 item.

### U5 status (2026-11-18, Phase 8)

Delivered and connected in the sample app (D-75 to D-84). **No record changed** (no contract change): the frozen `FormView` carried everything; the conventions are listed in section 3.

**The form** (`form.html`, `fragments/field-input.html`) is one template for create and edit: a heading (the title, with "Entity #id" above it on edit), a plain POST form with the CSRF token, a two-column grid of fields (one column under 600px; a text area spans both), and the actions. It posts to `view.actionHref` and prints `view.cancelHref`; it builds no link. Each field has a label that points at its input, a visible `*` and the words "(required)" for a screen reader when required, the help text and the error, both linked with `aria-describedby`, and `aria-invalid` on an input with an error. The inputs: text, text area, number (a text input with a numeric keypad, so the server's sentence can explain a mistake), a checkbox followed by a hidden `false`, date, date-time (`step="1"`), a select (blank choice first), and a masked input that is a password input with no `value` attribute. A select with no options is a notice, not a control. After a refused save the page is shown again (status 422) with what was typed (not a secret), a sentence next to each field that was wrong, and a summary at the top (role alert, `autofocus`) with a link to each of them.

**Leaving the form.** "Cancel" is a `details` element whose panel asks "Discard your changes?" and holds the link ("Yes, discard changes"), so it needs no script (UI-5). `form-guard.js` (about 70 lines, loaded with `defer`, stores nothing) adds the browser's warning when a changed form is left another way, a direct Cancel when nothing changed, and focus on the first error or the summary. The "New record" button of the list and the "Edit record" and "Duplicate record" buttons of the detail page were already in the templates; the backend now offers them.

Styles are in `pages.css` (section "create and edit form"): tokens only, so both themes follow; no animation; no inline script, style or handler, no `th:utext`.

New preview fixtures (built by the real `FormViewFactory` and the real `FieldMessages`, see `docs/FIXTURES.md` section 7): the page `form` with 12 states. A manual browser pass (light and dark, phone width, keyboard only, script off) is listed in `docs/logs/phase-8-report.md`; the automated real-browser test stays a Phase 14 item.

## 8. Sync points

1. **Contract freeze, right after Phase 0.** Together, define the records in section 3 (no logic) and the fixture list. Commit. Both sides start from this commit.
2. **After each connected feature,** run the real page in the sample app, compare it with the preview, and fix the side that is wrong.
3. **Contract change.** The side that needs a new field proposes it, the other side agrees, both update the record and fixtures, and the change is logged in `DECISIONS.md`. Never add a field on one side only.
4. **Shared files** (`pom.xml`, `docs/**`, root `CLAUDE.md`) change only when no other session is mid-task.

## 9. Working in one directory with two Claude Code sessions

You can run two Claude Code chats in the same VS Code folder, one per track. Each is told its track in the first message and works only inside that track's folders (section 2). The nested `CLAUDE.md` files in `jpa-admin-core/` and `jpa-admin-ui/` repeat the boundaries.

Safety rules for a shared working tree:

- Each session edits only its own folders. Shared files need the other session to be idle.
- Build only your own module: `mvn -pl jpa-admin-ui-preview -am ...` for UI, `mvn -pl jpa-admin-core -am ...` for backend. Run the full `mvn clean verify` only when the other session is idle, so the two do not delete each other's `target` folders.
- Commit by path: `git add jpa-admin-ui` for UI, `git add jpa-admin-core jpa-admin-autoconfigure sample-app` for backend. Never `git add .` while both sessions have changes.
- Use one phase branch for both tracks, and merge into `main` when the phase is connected.
- If the two sessions collide, stop one and finish the other. A single session that alternates tasks (UI task, then its backend task) is a valid fallback.

Per-task flow: read the task, propose a plan (files, fixtures, states), wait for approval, build, run the module build, check manually in light and dark and at a narrow width, commit by path.

## 10. UI acceptance checklist (per page)

- [ ] Renders correctly for every fixture state, including empty and error
- [ ] Matches the prototype (layout, spacing, badges, floating bars)
- [ ] Works in light and dark themes without flashing on load
- [ ] Works at phone width (drawer sidebar, scrollable table) and desktop
- [ ] Keyboard only: reachable, visible focus, Escape closes dialogs
- [ ] Icon buttons and inputs have labels, errors are linked to fields
- [ ] No external requests (CDN, fonts, images) in the network panel
- [ ] No sensitive value appears in HTML, attributes, or JavaScript data
- [ ] All user-visible text is escaped (no `th:utext` on stored data)
- [ ] State (search, filters, sort, page) is in the URL
- [ ] Works when `localStorage` is unavailable
- [ ] Screenshot taken for the docs site

Once, when the console's asset handler first exists (Phase 1 or task U0), and again if the path or handler changes:

- [x] Runtime test in `sample-app`: with the console at its default path, `/admin/assets/...` serves an asset, and `/jpa-admin/assets/...` returns 404 (also with a custom `jpa.admin.path`, the old path returns 404 and the new one serves the asset). Assets are never served outside `{path}/assets/**`. Done in Phase 1 by `ConsoleAtDefaultPathTest`, `ConsoleAtCustomPathTest`, `ConsoleAtNestedPathTest` (MockMvc) and `ConsoleStartupTest` (real HTTP). The only asset so far is the placeholder `css/tokens.css` that the backend track added in Phase 1; U0 replaces it. Re-run these tests whenever the path or the handler changes. `DotfilesAreNeverServedTest` and `NoDotfilesResourceResolverTest` cover the hidden-file rule.
