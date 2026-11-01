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

The starter depends on `jpa-admin-ui`, so templates and assets reach the host application automatically. Thymeleaf finds templates on the classpath, and the backend registers a resource handler that maps `{path}/assets/**` to `classpath:/jpa-admin/assets/`. That location is deliberately not one of Spring Boot's default static locations (`/static`, `/public`, `/resources`, `/META-INF/resources`), so the assets are served only through the console's own handler and never at a second URL in the host application.

## 3. The contract (view models)

Defined once, at the contract freeze (section 8). All fields are display-ready strings, numbers or lists. No entities, no collections of entities, no sensitive values.

| Record | Used by | Main fields |
|---|---|---|
| `PageChrome` | Every page | `title`, `basePath`, `theme`, `nav` (groups with entity name, label, icon, count), `activeEntity`, `breadcrumbs`, `flash` messages, `csrf` (parameter name and token) |
| `LoginView` | Login | `title`, `error`, `logoutNotice` |
| `DashboardView` | Dashboard | `stats` (total records, entities, groups, largest table), `groups` (entities with count and permissions), `chartAvailable` |
| `ChartSeries` (JSON) | Chart endpoint | `range`, `days` (date, count per entity), `entities` |
| `ListView` | List | `entity` (label, plural, icon), `columns` (key, label, type, sortable, align, visible, filterable), `rows` (id, cells), `search`, `sort`, `dir`, `filters` (definitions plus active chips), `paging` (page, size, total, sizes), `actions` (can create, export, bulk delete), `empty` (reason, hint) |
| `Cell` | List, detail | `type` (text, number, boolean, enum, relation, date, image, file, masked, empty), `display`, `tone`, `href`, `title` (hover text) |
| `DetailView` | Detail | `title`, `entityLabel`, `id`, `fields` (label, cell), `images`, `actions` (edit, duplicate, delete) |
| `FormView` | Create, edit | `mode`, `fields` (key, label, input type, value, required, help text, options, error), `formErrors`, `uploadTicket` per upload field |
| `UploadResult` (JSON) | Upload endpoint | `url`, `name`, `size`, `error` |
| `ErrorView` | Error pages | `status`, `heading`, `hint` |

Rule: a cell of type `masked` carries no value at all, only the display dots. A `FormView` field for a sensitive property has input type `masked` and no value; a blank submit on edit keeps the stored value. Templates must not receive sensitive data in any form (SEC-1).

Draft status: until the phase that fills a record starts, the record is a draft. A draft change needs approval but no `DECISIONS.md` entry. After that phase starts, every change needs both (section 8). Records are public but internal, not stable API (ARCH-9). Model attribute names (for example `chrome` for `PageChrome` and `view` for the page record) are fixed at the contract freeze, so templates and controllers agree.

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

- [ ] Runtime test in `sample-app`: with the console at its default path, `/admin/assets/...` serves an asset, and `/jpa-admin/assets/...` returns 404 (also with a custom `jpa.admin.path`, the old path returns 404 and the new one serves the asset). Assets are never served outside `{path}/assets/**`.
