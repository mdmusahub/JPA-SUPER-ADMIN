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
| `ListView` | List | `entity` (label, plural, icon), `columns` (key, label, type, sortable, sort link, sort state, align, visible, filterable), `rows` (id, cells), `search`, `sort`, `dir`, `filters` (definitions plus active chips, each with its link), `paging` (page, size, total, first, previous, next and last links, sizes each with a link), `actions` (can create, export, bulk delete, export link), `empty` (reason, hint) |
| `Cell` | List, detail | `type` (text, number, boolean, enum, relation, date, image, file, masked, empty), `display`, `tone`, `href`, `title` (hover text) |
| `DetailView` | Detail | `title`, `entityLabel`, `id`, `fields` (label, cell), `images`, `actions` (edit, duplicate, delete) |
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
- The backend builds the links and the template only prints them (D-28, see "Links" below). The template still builds the few fixed entity routes from `chrome.basePath`, `chrome.activeEntity` and the row or record id: the dashboard link, "New record", the links from a row or record to its detail, edit, duplicate and delete routes, bulk delete and logout. Every form posts the CSRF token from `chrome.csrf`. These routes stay template-built for now and are revisited in Phase 7 (D-29).
- A column's `sortState` matches `ListView.sort` and `dir`: the column whose key is `sort` is `ASCENDING` or `DESCENDING`, every other column is `NONE`. `sortHref` is present exactly when the column is sortable. Template: `aria-sort` and the arrow come from `sortState`, the header links to `sortHref`.
- Paging links: `firstHref` and `prevHref` are present exactly when `page > 0`; `nextHref` and `lastHref` exactly when there is a later page (`page + 1 < totalPages()`). Exactly the `SizeOption` whose size equals `size` has `current` true.
- `Actions.exportHref` is present exactly when `canExport` is true. It carries the current search, filters, sort and visible columns.
- `FormView.recordId` is present exactly in `EDIT` mode. The duplicate flow is a `CREATE` form that is pre-filled, so it has no `recordId`.
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
| `ListView.Actions` | `exportHref` (present exactly when export is offered) |
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
| U2 | List table, cell renderer, search box, sortable headers, pager, empty state | 4 List, 5 Search and sort | Phase 5 |
| U3 | Filter panel, chips, clear all | 6 Filters | Phase 6 |
| U4 | Detail page, confirm dialog, bulk bar, duplicate and delete flows | 7 Detail and delete | Phase 7 |
| U5 | Form page, all field inputs, validation display, unsaved-changes guard | 8 Create and edit | Phase 8 |
| U6 | Uploader component, previews, progress, errors, image cells | 9 Uploads | Phase 9 |
| U7 | Dashboard chart with range switch | 10 Charts | Phase 10 |
| U8 | Export button states, saved views menu | 11 Export, 12 Saved views | Phase 12 |
| U9 | Accessibility, responsive and dark theme passes, keyboard shortcuts, docs screenshots | 13 Polish | Phase 13 |

### U0 status (2026-11-01, Phase 2)

Delivered and connected in the sample app: `tokens.css`, `base.css`, `components.css`, `pages.css`; `theme.js` (blocking, in `<head>`) and `nav.js`; `layout.html`, `login.html`, `dashboard.html` (welcome state only), `error.html`, and the fragments `head`, `sidebar`, `topbar`, `breadcrumbs`, `toast`, `icons`, `theme-toggle`; preview fixtures for login, the minimal dashboard, the error pages and nine chrome variants. The look follows section 5 because `docs/prototype/admin-console.html` was not in the repository when U0 was built. The maintainer is adding that file themselves (2026-11-02) and will ask for a UI alignment pass after their commit; that pass is not part of U0 and nothing was changed for it.
Since 2026-11-02 (D-40) the real console renders these templates with its own private Thymeleaf engine, which finds them under `classpath:/templates/jpa-admin/` and reads nothing of the host's Thymeleaf setup or `spring.thymeleaf.*` settings. Template names must stay lower-case words joined by hyphens (`jpa-admin/entity-list`, not `jpa-admin/EntityList`), or the console refuses them. The preview app is unchanged: it keeps Boot's Thymeleaf with `spring.thymeleaf.cache=false`, so a template change shows after a rebuild.
Not part of U0 and still to do: stat cards, entity cards and the chart (U1, U7), tables and everything after, the accent colour and theme polish pass and screenshots for the docs (U9), toasts that disappear by themselves (U9), a full focus trap in the drawer (U9), and the `long-sidebar` chrome variant (U1).

### U1 status (2026-11-10, Phase 3)

Delivered and connected in the sample app. The sidebar now has the brand subtitle "Administration", a nav search box that filters the entity list in the browser (an enhancement: hidden without JavaScript, and the list is fully usable without it), entity groups that collapse and expand as `<details>` elements (closed state remembered in one `localStorage` key, in try/catch, UI-11), entity items with an icon and a count (thousands separators, and "unavailable" when the console could not read the count), the active item highlighted, and a user chip at the bottom with the signed-in name and the sign-out (the POST with the CSRF token moved here from the top bar, so there is one sign-out on the page). The dashboard has four stat cards and one card per group listing its entities with counts and links, an empty-database state and a "no entities" welcome state that names `@AdminHidden` and `jpa.admin.excluded-entities`. A temporary `entity-notice.html` is the entity page until U2 builds the table.

Contract change (D-44): `PageChrome` gained `user` (nullable; `null` on the sign-in and error pages and for an anonymous visitor), and `PageChrome.NavItem.count` and `DashboardView.EntityCard.count` became `Long` (`null` = the count could not be read). The `nav` of `PageChrome` now holds the discovered entities; before Phase 3 it was always empty.

New preview fixtures: chrome variants `long-sidebar` (8 groups, 60 entities, counts from 0 to 1,000,000, one unavailable, a very long name and names in five scripts), `no-user` and `entity-active`; dashboard `normal`, `no-chart`, `empty-database`, `count-unavailable`, `no-entities`, `many-groups`, `read-only-entities`; the new `entity` page with the state `not-available`.

A real Thymeleaf-precedence bug was found and fixed by the preview test: `th:replace` runs before `th:if`, so an `entity-icon(name)` fragment with both on one element rendered every icon; they are now on separate elements. Still to do (U9): the `read-only-entities` dashboard fixture does not yet hide actions, because Phase 3 offers no per-entity action at all (every `Permissions` is false); real permissions arrive with Phase 7.

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
