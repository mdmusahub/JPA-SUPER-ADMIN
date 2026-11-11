# JPA Super Admin: Project Plan

Status: approved scope for v0.1. Read this file, then `CLAUDE.md` and `docs/RULES.md`, before writing code.
Product requirements live in `docs/PROJECT_DOCS.md` (BRD and user documentation outline).

---

## 1. Vision

Django developers get an admin panel for free. Spring Boot developers do not. JPA Super Admin is one open-source starter dependency that gives any Spring Boot + JPA application a complete admin console, with no extra code.

**Promise:** add one dependency, set `username` and `password`, start the app, open `/admin`, log in, manage every entity.

**Stack:** Java 17 minimum, Spring Boot 4.1.x (latest stable patch; Spring Boot 3.x is not supported in v0.1), Spring Security, Spring Data JPA, Thymeleaf (server-rendered), small vanilla JavaScript, Maven multi-module.

## 2. Scope

### In scope for v0.1

| Area | Included |
|---|---|
| Setup | Single starter dependency, auto-configuration, mandatory `username` and `password` validated at startup |
| Security | Dedicated filter chain for the console path, BCrypt, form login, logout, CSRF, sensitive field masking |
| Discovery | All `@Entity` classes found through the JPA metamodel, grouped sidebar with record counts |
| Browse | Table with formatted values, paging, search, sorting |
| Filters | Per-field filters (text, enum, boolean, relation, number range, date range) with removable chips |
| Saved views | Name and restore a combination of search, filters, sort, page size and visible columns (stored per browser) |
| CRUD | Detail, create, edit, delete, bulk delete, duplicate |
| Fields | Text, numbers, booleans, dates, enums, many-to-one relations, validation errors next to fields |
| File and image uploads | `@AdminUpload` fields, drag and drop, type and size checks, progress, preview, local storage provider, storage SPI |
| Charts | Dashboard stat cards and a "Records created" chart (7, 30 and 90 days) from a creation timestamp field |
| CSV export | Current search, filters, sort and visible columns, up to 50,000 rows, spreadsheet formula escaping, sensitive fields excluded |
| UI | Light and dark theme, responsive layout, keyboard shortcuts for search and new record |
| Quality | Sample app, tests, CI, README, docs website |

### Out of scope for v0.1

- S3 storage provider (moved to v0.2; the storage SPI ships in v0.1)
- Multiple admin users and per-entity roles
- Audit log and change history
- Inline child rows for one-to-many collections
- `@Embedded` editing and composite primary keys
- Image resizing and thumbnails, multi-file galleries
- Server-side saved views, XLSX export
- WebFlux, non-JPA stores, translations

## 3. User journey

1. Add the starter dependency.
2. Add required properties:

```yaml
jpa:
  admin:
    username: admin
    password: ${ADMIN_PASSWORD}
```

3. Start the app. A missing property stops startup with a message that says what to add.
4. Open `/admin`, sign in.
5. See the dashboard (stat cards and chart) and the entity sidebar.
6. Open an entity, search, filter, sort, save a view, export CSV.
7. Create, edit, duplicate and delete records, upload files to upload fields.
8. Password-like fields show as masked dots everywhere.

## 4. Modules

```
jpa-super-admin/             parent pom: groupId io.github.mdmusahub, artifactId jpa-super-admin
  pom.xml
  jpa-admin-core/            metadata, services, controllers, view-model records (no templates)
  jpa-admin-autoconfigure/   properties, security chain, auto-configuration, asset resource handler
  jpa-admin-ui/              Thymeleaf templates, CSS, JavaScript (resource jar, no Java logic)
  jpa-admin-ui-preview/      unpublished preview app with fixtures (never a dependency of the starter)
  jpa-admin-starter/         empty pom, published as jpa-super-admin-spring-boot-starter
  sample-app/                demo shop, integration tests
  docs/                      PLAN.md, RULES.md, PROJECT_DOCS.md, MkDocs site
  CLAUDE.md
```

Maven `groupId`: `io.github.mdmusahub`. Package root: `io.github.mdmusahub.jpaadmin`. The only artifact users add is `jpa-super-admin-spring-boot-starter`, which depends on `jpa-admin-autoconfigure`, `jpa-admin-core` and `jpa-admin-ui`. Those modules are published; `jpa-admin-ui-preview` and `sample-app` are not.

## 5. Configuration reference

| Property | Default | Notes |
|---|---|---|
| `jpa.admin.enabled` | `true` | Master switch |
| `jpa.admin.path` | `/admin` | Starts with `/`, then letters, digits, `-` and `_` in each segment, segments separated by single slashes, at most 100 characters. Cannot be `/`, no trailing slash (SEC-14). A bad value stops startup |
| `jpa.admin.username` | none | Required. Missing, blank, or an unset `${...}` variable stops startup with a message naming the property |
| `jpa.admin.password` | none | Required, BCrypt-hashed at startup (Phase 2), so at most 72 bytes (BCrypt's limit; longer stops startup). Missing, blank, or an unset `${...}` variable stops startup. Under `prod`, under 12 characters or a common password also stops startup (section 8). Never logged or printed |
| `jpa.admin.title` | `JPA Admin` | Header and login title |
| `jpa.admin.primary-color` | `#0d9f6e` | Validated as `#rrggbb` only. A bad value stops startup |
| `jpa.admin.page-size` | `25` | Default rows per page |
| `jpa.admin.export-enabled` | `true` | Global CSV export switch |
| `jpa.admin.export-max-rows` | `50000` | May lower the cap, never raise it. 50,000 is a fixed internal ceiling |
| `jpa.admin.sensitive-fields` | `password, secret, token, apikey` | Names always masked. Match is case-insensitive, ignores `_` and `-`, and is a substring match (`userPassword`, `api_key` match). `@AdminSensitive` adds to this |
| `jpa.admin.excluded-entities` | empty | Entity names to hide |
| `jpa.admin.storage.default.type` | `local` | `local` only in v0.1 (`s3` planned for v0.2) |
| `jpa.admin.storage.default.directory` | `./uploads` | Local storage root, outside the web root |
| `jpa.admin.storage.default.max-size` | `5MB` | Per-file default |
| `jpa.admin.storage.default.public-path` | none | If set, files are served publicly at this path. Off by default |
| `jpa.admin.upload.signing-key` | random per start | Signs upload tickets (see Uploads). Set explicitly when running several instances |

Startup fails fast when any `@AdminUpload` field refers to a storage that is not configured, and lists the offending fields.

## 6. Annotations (customization API)

All optional. The console works without them.

| Annotation | Target | Purpose |
|---|---|---|
| `@AdminResource` | Entity | Display name, plural name, group, icon, order, read-only, creatable, updatable, deletable, exportable, default sort, page size |
| `@AdminField` | Field | Label, help text, order, show in list, form, detail, read-only, searchable, sortable, filterable |
| `@AdminHidden` | Entity or field | Remove from the console and API |
| `@AdminSensitive` | Field | Mask everywhere |
| `@AdminBadge` | Enum or String field | Value-to-colour mapping |
| `@AdminLookup` | Relation field | Property used as the related record label |
| `@AdminUpload` | String field | Storage name, folder, allowed MIME types, max size |

## 7. Architecture

| Component | Responsibility |
|---|---|
| `JpaAdminProperties` | Validated configuration |
| `JpaAdminAutoConfiguration` | Registers beans only for servlet web apps with JPA |
| `JpaAdminSecurityConfig` | Dedicated chain, in-memory BCrypt admin |
| `EntityScanner` | Reads the JPA metamodel once, builds metadata |
| `AdminEntity`, `AdminField` | Immutable metadata records |
| `AdminEntityRegistry` | Lookup by URL name |
| `DynamicQueryService` | List and count queries (Criteria API) with search, filters, sort, paging |
| `DynamicCrudService` | Create, update, delete, duplicate, bulk delete in transactions |
| `ValueConverter`, `ValueFormatter` | Form string to type, and value to display text with masking |
| `ExportService` | Streams CSV from the same query pipeline, escapes formula cells |
| `StatsService` | Counts, largest table, records-created series |
| `StorageProvider` (SPI) | `store`, `delete`, `open`, `url`. Implementation in v0.1: local (S3 in v0.2) |
| `UploadController` | Ticket-checked uploads, safe file serving |
| `UploadTicketService` | Issues and validates signed upload tickets |
| `AdminController` | Dashboard, list, detail, form, save, delete, export |
| `GlobalExceptionHandler` | Friendly validation and database error messages |

### List request flow

Browser asks for `/admin/product?q=&filter.status=Published&sort=price&dir=desc&page=0`. Security checks session and CSRF. The controller resolves the entity (404 if unknown). `DynamicQueryService` validates every parameter against metadata, runs the query and a count query. `ValueFormatter` builds display values and masks sensitive fields. Thymeleaf renders the page. CSV export reuses the same validated query.

### Saved views

A view is a JSON object: `{ name, q, filters, sort, dir, pageSize, hiddenColumns }`. Stored in the browser (`localStorage`) per entity. Applying a view rebuilds the query string, so views are also shareable as URLs.

### Dashboard chart

`StatsService` finds the creation timestamp field of each entity (`createdAt`, `createdDate`, `createdOn`, `creationDate`, `creationTime`, `createTime`, `createdDateTime`) and returns daily counts per entity for 7, 30 or 90 days. The page renders bars with inline SVG or CSS and vanilla JavaScript. No external chart library. The card is hidden when no entity has such a field.

### Uploads

Drag and drop or click. The client checks type and size, shows a preview and progress, and the form waits for uploads before saving. The server re-checks type (content sniffing, not only the header) and size, assigns a random file name under `folder/yyyy/MM/`, stores through `StorageProvider`, and saves the resulting URL string in the entity field.

**Upload ticket.** When the server renders a form page, it issues a signed token for each upload field. The token lasts about 10 minutes, is bound to the entity and field, and carries the maximum size and an expiry. It is signed with `jpa.admin.upload.signing-key`. `POST /admin/uploads` validates the ticket in addition to the session and CSRF token, and rejects expired, tampered or mismatched tickets.

**Serving.** In v0.1, `GET /admin/files/**` uses session authentication only. Thumbnails are the original image scaled by CSS. There is no server-side resizing.

## 8. Security design

- Dedicated `SecurityFilterChain` matched to the console path with the highest precedence. It never changes the host application's own rules.
- One in-memory admin, BCrypt hash. CSRF on for all state-changing requests. Session cookie settings are left to Spring Boot defaults. The console does not change global session cookie settings, because they would also affect the host app. The docs tell host apps to set `Secure` and `SameSite` themselves. Under the `prod` profile, a startup warning is logged when the console runs over plain HTTP.
- Public: login page and static assets. Everything else requires the admin role.
- Sensitive fields are masked in all responses, excluded from search, filter, sort and export.
- Sort, search and filter parameters accept only names found in metadata.
- CSV cells starting with `=`, `+`, `-` or `@` are prefixed so spreadsheets do not run them.
- Uploads: allow-list of MIME types, content sniffing, size limit, random names, SVG blocked unless explicitly allowed, path traversal impossible by construction, `X-Content-Type-Options: nosniff`, `Content-Disposition: attachment` for non-images, files served only to authenticated admins unless `public-path` is set.
- Rich text or HTML values from the database are escaped on display.
- Sensitive field names are matched case-insensitively, ignoring `_` and `-`, as substrings of the configured names, plus `@AdminSensitive`.
- Production guard: under the `prod` profile, startup fails if the password is shorter than 12 characters or is on a list of common passwords. Under other profiles a weak password logs a warning.
- Uploads also require a valid signed upload ticket (see Uploads).
- Login rate limiting is planned for v0.2. Document HTTPS, VPN or IP allow-list for internet-facing apps.

## 9. Routes

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin` | Dashboard |
| GET/POST | `/admin/login`, POST `/admin/logout` | Auth |
| GET | `/admin/{entity}` | List (`q`, `filter.*`, `sort`, `dir`, `page`, `size`) |
| GET | `/admin/{entity}/export.csv` | CSV export of the current query |
| GET | `/admin/{entity}/new` | Create form (`?copyFrom={id}` for duplicate) |
| POST | `/admin/{entity}` | Create |
| GET | `/admin/{entity}/{id}` | Detail |
| GET/POST | `/admin/{entity}/{id}/edit`, `/admin/{entity}/{id}` | Edit and update |
| POST | `/admin/{entity}/{id}/delete`, `/admin/{entity}/bulk-delete` | Delete |
| POST | `/admin/uploads` | Upload a file (needs session, CSRF and a valid upload ticket), returns the stored URL |
| GET | `/admin/files/**` | Authenticated file serving for local storage |
| GET | `/admin/api/stats?range=30` | Chart data (JSON) |
| GET | `/admin/assets/**` | CSS, JS, images from the jar |

## 10. UI

Use the HTML prototype at `docs/prototype/admin-console.html` as the visual reference (reference only, not shipped in the jar): grouped sidebar with counts, breadcrumbs, stat cards, bar chart, sortable table with badges, floating bulk bar, detail grid, form grid, light and dark themes.

Templates live in the `jpa-admin-ui` module under `templates/jpa-admin/` with one shared layout fragment. Quality floor: responsive, visible focus, labelled controls, reduced motion respected, meaningful empty states.

## 11. Build phases

Every phase ends with a running app, passing `mvn clean verify`, updated docs and a git tag. Do not begin a phase while the previous demo is broken. Ask for an implementation plan before coding.

| Phase | Build | Done when |
|---|---|---|
| 0 Setup | Repo, `.gitattributes` (LF for text files), `.gitignore`, `CHANGELOG.md`, parent POM, modules (core, autoconfigure, ui, ui-preview, starter, sample-app), sample app with entities and seed data, CI skeleton, then the **contract freeze** (empty view-model records, the fixture list document, and a masking unit test; see `docs/UI_PLAN.md` section 8). No fixture code in Phase 0 | Build passes, sample app starts, contract committed |
| 1 Auto-config | Properties validation, auto-configuration, imports file, placeholder `/admin`, asset resource handler for `{path}/assets/**` (from `classpath:/jpa-admin/assets/`) | Starter alone makes `/admin` respond. Missing username fails startup clearly. A runtime test proves `/admin/assets/**` serves the assets and `/jpa-admin/assets/**` (and other paths outside the console path) returns 404. From this phase on, controller tests assert that every link a controller builds is under the base path (D-29) |
| 2 Login | Security chain, login page, logout, public assets. Security response headers on console pages (SEC-13): `X-Content-Type-Options: nosniff`, frame protection, a restrictive referrer policy. A startup warning when the console runs over plain HTTP under the `prod` profile (SEC-8), with global session cookie settings left unchanged (SEC-8) | Redirect to login, bad login rejected, good login works, host routes untouched. A test shows the SEC-13 headers on console responses, and a test shows the SEC-8 warning under `prod` over plain HTTP and none under other profiles |
| 3 Discovery | Scanner, registry, layout, sidebar, dashboard stat cards, and an entity-name check with no new dependency: a CI step or test that greps the sources and resources of the library modules for the demo entity names (`Category`, `Product`, `Customer`, `Order`, `Tag`) and fails if any is found (ARCH-12). The check matches whole words, case-sensitive. Every allow-list entry has a file path and a written reason, and the check also fails when an allow-list entry no longer matches anything, so stale entries cannot hide (rule approved in D-42, which lists the three entries known from Phase 2: Spring's `@Order` annotation, word `Order`, in one main file and two test files) | Every entity listed with right count, hidden ones absent. A second host project (folder `host-check/`, outside the reactor build) with different entities (`Book`, `Author`, `Publisher`) that adds only the starter dependency and the two required properties shows all three entities in the sidebar with correct counts. No library change is needed for it to work. Library tests run with `spring.jpa.open-in-view` both true and false (D-29) |
| 4 List (delivered, with the filter-links exception, D-60) | Query service, formatter, list page, paging, masking, link builders for sort, page and page-size links. Search (`q`) and sort (`sort`, `dir`) were delivered in this phase as well (D-50), because the list page's links carry them; Phase 5 was re-scoped for that reason (D-60). **Not delivered from this row:** the filter links (`filter.*`, chip removal, clear all), which move to Phase 6 (D-60) | Right columns and values, password always masked. Link builders percent-encode every query value; tests cover spaces, slashes, quotes, unicode and a literal `http://x` search (D-29). Library tests run with `spring.jpa.open-in-view` both true and false (D-29) |
| 5 Sort and search quality (re-scoped, D-60) | What Phase 4 left over about sorting and searching, in four small items, with no new dependency, no contract change and no new property. **(1) Relation-aware sort:** a relation column whose target has a label becomes sortable, ordered by that label through the join the list already makes (no extra join, no extra statement); a relation shown as `#id` (no label, or a hidden target) stays unsortable; this lifts the relation clause of D-53, which D-60 supersedes (approved 2026-11-11). **(2) One rule for nulls in a sort**, the same on every database: nulls last in both directions (approved 2026-11-11), set with JPA 3.2's `Nulls`, always followed by the identifier as the final tie-breaker. **(3) A sort proof on real data:** every sortable column of every sample entity, both directions, with and without a search, in both open-in-view modes. **(4) Say what the search covers:** the empty state names the columns that are searched (never a sensitive or hidden one), says so when nothing is searchable, and a search cut at 100 characters tells the visitor. Not in this phase, and why, is in `docs/DECISIONS.md` D-60; in particular `jpa.admin.page-size` is **not** a Phase 5 item, it moved to Phase 13 (approved 2026-11-11) | **(1)** sorting the sample products by category gives the label order in both directions, ties by the product's id, the header carries `aria-sort` and a link that flips the direction, the page still costs two statements, and a relation with no label is not sortable and `sort=` for it is ignored. **(2)** the sample orders (which have a nullable note) and a core test entity list nulls last for ascending and descending, with a stable order inside the nulls; the SQL carries the null rule. **(3)** one parameterised sample-app test covers every sortable column of the five entities in both directions and both open-in-view settings: the rows are in the right order, exactly the sorted header has the matching `aria-sort` and a link to the other direction, all other sortable headers say `none`, and nothing returns an error. **(4)** the empty state and the cut-search notice are asserted in core and in the sample app, and no sensitive or hidden column label appears in any of them |
| 6 Filters | Field filters, chips, clear all. Also the filter link builders that the Phase 4 row promised and Phase 4 did not deliver (`filter.*` parameters, chip removal and clear-all links, built by the same class that builds the other list links), the `Filters` part of `ListView` (empty until now), and relation filters (a relation's label is not searched, D-52, so a relation filter is how an admin finds products by category name) | Each filter type works and combines with search and sort. Filter links are percent-encoded, lie under the base path and keep search, sort and page size, tested with the same awkward values as the Phase 4 links (spaces, slashes, quotes, unicode, a literal `http://x`) |
| 7 Detail and delete | Detail page, delete with confirm, bulk delete, duplicate. Revisit the routes the templates still build themselves (detail, edit, duplicate, delete, bulk delete, logout, New record) and decide whether the backend should build them too (D-29). Relation cells in the list become links to the related record's detail page, which removes the Phase 4 "no link yet" limit (D-54), and a hidden or unsupported target still gets no link | Deletes work, foreign key failures show friendly message. A relation cell links to its target's detail page, a hidden target's cell has no link, and every such link lies under the base path |
| 8 Create and edit | Simple types, enums, dates, relations, validation errors, sensitive behaviour | Full CRUD on every sample entity, invalid input never saves |
| 9 Uploads | `@AdminUpload`, storage SPI, local provider, upload endpoint, uploader UI, startup check | Upload, preview, replace, remove work. Dangerous files rejected. Startup fails on missing storage |
| 10 Charts | `StatsService`, stats endpoint, chart card with range switch | Chart matches data, hidden when no timestamp fields |
| 11 CSV export | `ExportService`, export button, cap, escaping | Export respects search, filters, sort, columns. Sensitive fields absent. Formula cells escaped |
| 12 Saved views | Save, apply, rename, delete views per entity. A column hidden by a view is not selected or joined by the list query, so hiding relation columns also lowers the cost of a page (the Phase 4 join-per-relation-column limit) | Views restore the exact state and survive reload. A page with hidden relation columns runs fewer joins, shown in the recorded SQL |
| 13 Polish | Styling from prototype, dark mode, toasts, empty states, shortcuts (`/` for the search box), annotations (including the pieces Phase 4 stood in for: the plural and page size of `@AdminResource`, the badge tones of `@AdminBadge`, the relation label of `@AdminLookup`), the `jpa.admin.page-size` property of section 5, moved here from Phase 5 (approved 2026-11-11, D-60; default 25, one of 10, 25, 50, 100), accent colour served as a validated generated stylesheet (SEC-15, UI-13), a manual responsive, keyboard and dark-theme pass of every page including the list (U9), README with screenshots | UI matches prototype, README quick start works in 5 minutes. A bad `jpa.admin.page-size` stops startup with a message that names the property |
| 14 Quality and release | Testcontainers (PostgreSQL, MySQL), security tests, CI, docs site, JitPack then Maven Central. **Moved here from Phases 4 and 5 (D-60), because they need the whole product or a real database:** the list tests (search with `lower(...) like ... escape '!'` on text and large-text columns, sort, null ordering, the identifier tie-breaker, paging) run on PostgreSQL and MySQL; a cost bound for relation columns (one `LEFT JOIN` per relation column, so a page with many relation columns on a large table is measured and the limit documented, or the strategy changed to a batch query beyond a cap); a measurement of the search full scan and of `count(*)` on a large table, with index advice in the docs; an automated real-browser test of the list at phone width (no sideways page scroll, the table scrolls inside its box, visible focus, Back and reload keep the state), which needs a browser-driver dependency and therefore needs approval then (AI-8) | All tests green, docs live, `v0.1.0` tagged. The same `host-check/` project is run against the release candidate installed in the local Maven repository, and passes. The list tests pass on H2, PostgreSQL and MySQL; the relation cost bound and the search and count measurements are recorded in the docs; the automated phone-width test passes |

### Phase 1 checklist (backend work finished 2026-11-01)

- [x] Properties `enabled`, `path`, `username`, `password`, `title`, `primary-color`; `username` and `password` required, `path` and `primary-color` pattern-checked, `prod` password rule (D-7), all checked at startup with a message that names the property (D-30)
- [x] Auto-configuration and imports file; backs off outside a servlet web app, without JPA or Spring MVC, and with `jpa.admin.enabled=false` (no beans, no routes, no credentials asked for)
- [x] Starter alone makes `/admin` respond (plain-text placeholder, replaced in Phase 2)
- [x] Missing username fails startup clearly (checked on a real start of the sample app)
- [x] Asset handler for `{path}/assets/**` from `classpath:/jpa-admin/assets/` only. Runtime tests: `/admin/assets/**` serves the asset; `/jpa-admin/assets/**`, `/assets/**`, `/css/**` and a custom path's old path return 404
- [x] Every link built from the base path passes the contract's link rule and lies under it (D-29)
- [ ] Merge to `main` and tag `v0.0.1`: the maintainer's step. D-31 records that Phase 1 is tagged although UI task U0 (which runs alongside Phases 1 and 2) connects after Phase 2 (`docs/UI_PLAN.md` section 7), because the Phase 1 "done when" is backend-only and the placeholder page is its UI

### Phase 2 checklist (both tracks, one session: D-33; finished 2026-11-01, follow-up 2026-11-02)

- [x] One dedicated chain for `{path}/**` only, highest precedence, local one-user authentication (BCrypt, no parent manager), no Spring Security extension point exposed as a bean (D-35)
- [x] Host untouched, proven by running the same requests with the console off and on: a host without Spring Security (no newly protected route, no generated-password line, not even the HTTP firewall on host routes), a host with its own chain, a host relying on Boot's default chain, a host with a high-precedence catch-all chain
- [x] Form login, POST-only logout, CSRF everywhere in the chain, session fixation protection, generic failure for wrong username and wrong password, no username or password echoed, logged or placed in a URL, no open redirect
- [x] SEC-13 headers on console responses only (`nosniff`, frame protection, `no-referrer`, a strict CSP, no HSTS), no-store on pages (D-36)
- [x] SEC-8: startup warning under `prod` over plain HTTP; no cookie or session setting changed
- [x] `theme.css` from the validated colour (SEC-15); login, dashboard (welcome state) and error pages (403, 404, 405, 500) under the console path (D-37)
- [x] UI task U0 delivered and connected (templates, four stylesheets, `theme.js`, `nav.js`, preview fixtures) (D-38)
- [x] Mutation checks (a) to (j) from the brief: see `docs/logs/phase-2-report.md`
- [x] Follow-up: signing out clears only the console's own session data and ends the session only if nothing else is in it, so a host login in the same browser stays (D-39)
- [x] Follow-up: the console renders with its own private Thymeleaf engine and view resolver, limited to its templates; a host's `spring.thymeleaf.*`, own Thymeleaf, other view resolvers or other view technology are neither used nor changed, proven with four kinds of host (D-40)
- [x] Follow-up: mutation checks (k) to (x), see the report
- [x] Merge to `main` and tag `v0.0.2`: done (the tag exists; CHANGELOG `[0.0.2]` dated 2026-11-10)

### Phase 3 checklist (both tracks, one session: D-33 extended; finished 2026-11-10)

- [x] Public annotations `@AdminHidden` (entity or field) and `@AdminSensitive` (field), in `io.github.mdmusahub.jpaadmin.annotation`, with Javadoc; the package documented as public API (ARCH-9). No other annotation added (D-43)
- [x] Properties `jpa.admin.excluded-entities` and `jpa.admin.sensitive-fields` (default `password, secret, token, apikey`), validated by the same plain code, naming the property and never printing a value (SEC-6); documented (DOC-2)
- [x] `EntityScanner` reads the JPA metamodel once at startup into immutable `AdminEntity`/`AdminField` records (ARCH-1); URL name is the lower-cased simple name, a collision moves both to the hyphenated fully qualified name (D-45); hidden and excluded entities are absent from the registry's lookups, so they are unreachable by URL and identical to a name that never existed; composite-id entities are unsupported, logged once at WARN, never an exception; one `EntityManagerFactory` (the primary one), else a WARN and no entities, never a failed host startup (D-48); no entity name hard-coded (ARCH-12)
- [x] Sensitive detection: `@AdminSensitive`, or a name match against `sensitive-fields` that ignores case, `_` and `-` as a substring (`userPassword`, `api_key`, `API-KEY` match; `passport` does not); the possible false positives documented
- [x] `CountService`: one Criteria count per entity, `@Transactional(readOnly = true)` (ARCH-5), a 5-second TTL with an injectable `Clock` (D-46); the sidebar causes at most one count per entity per window and none on a repeat; a failing count is unavailable, logged without a stack trace, and never breaks the page
- [x] Auto-configuration registers the scanner, registry and count service only with an `EntityManagerFactory` (ARCH-7), beans prefixed and replaceable where they do not enforce a rule; ordered after Boot 4's `HibernateJpaAutoConfiguration`
- [x] Dashboard with real stats, groups and counts; `{path}/{entity}` 200 with a "not available yet" notice for a known entity and the console 404 for an unknown or hidden one, identical in both cases; the path looked up only through the registry and never echoed (SEC-2, D-47)
- [x] UI task U1: sidebar with the "Administration" subtitle, a client-side search, collapsible groups, icons and counts (thousands separators, "unavailable"), active item, a user chip with the POST sign-out; dashboard stat cards and group cards; empty states for no entities and an unavailable count; works without JavaScript and in both themes; preview fixtures added
- [x] Contract change (approved, D-44): `PageChrome.user` (nullable) and the counts `Long` (`null` = unavailable); every call site and fixture updated on both sides
- [x] ARCH-12 name check with no new dependency (D-42): whole-word, case-sensitive, an allow-list with path, line and reason per entry, stale entries fail; 42 tests including made-up input
- [x] A second host project `host-check/` (outside the reactor) with `Book`, `Author`, `Publisher` adds only the starter and the two properties and shows all three in the sidebar with the right counts; a CI step installs the reactor and runs it. Phase 3 "done when" and the Phase 14 "same host-check project" wording are met by this project
- [x] Library tests run with `spring.jpa.open-in-view` both true and false (D-29): the sample app keeps the pre-existing `OpenInViewDisabledTest`/`OpenInViewEnabledTest`, and the console reads only inside its own read-only transactions
- [x] Mutation checks (a) to (k): all caught, each file restored byte-identically (MD5), see `docs/logs/phase-3-report.md`
- [ ] Merge to `main` and tag `v0.0.3`: the maintainer's step (GIT-2: the phase is connected, the real pages work in the sample app)

### Phase 4 checklist (both tracks, one session: D-50; delivered 2026-11-11, with the filter-links exception, D-60)

- [x] `ListQueryService` (Criteria API only, no new dependency): a count and a page in one read-only transaction (ARCH-5); a page costs two statements whatever its size or number of relation columns (ARCH-6); only attributes are selected, never entities, collections, embedded values or binary data (ARCH-3, ARCH-4); a sensitive attribute is not selected at all (SEC-1)
- [x] Page sizes 10, 25, 50, 100, default 25; bad size falls back to 25; page below 0 or not a number is page 0; a page past the end, or too big for an `int`, is the last page; no request makes the page fail (D-51, D-56)
- [x] Search: case-insensitive "contains" over visible, non-sensitive text attributes only, bound parameter, `%` `_` and `!` literal, 100-character limit, `http://x`, quotes and unicode work (D-52)
- [x] Sort: only sortable visible non-sensitive columns, both directions, an unknown key or direction is ignored, the identifier is always the last sort key so paging is stable (D-53)
- [x] Relation cells show the target's label, by one join, no extra query; a hidden target shows `#id`; no link until Phase 7 (D-54)
- [x] Links for sort, first/previous/next/last and page size are built by one class that percent-encodes every value; tests cover spaces, slashes, quotes, unicode, `&`, `#`, `%`, `+` and a literal `http://x`; every link lies under the base path (D-29)
- [x] `EntityController` fills only `ListView` (no record changed); unknown and hidden entities give the identical 404; the "not available yet" notice is deleted from code, template, fixtures and tests (D-57)
- [x] UI task U2: list header with count, GET search form that works without JavaScript, table in its own scroll box, sortable headers with `aria-sort`, a renderer for every cell type, pager with page-size links, empty states, phone width and both themes; no JavaScript was needed
- [x] Preview: 18 list fixtures built by the real `ListViewFactory`
- [x] Library tests run with `spring.jpa.open-in-view` both true and false (D-29), now with an explicit page test for `true` as well (the Phase 3 follow-up)
- [x] Host-check: `Book`, `Author` and `Publisher` list with relation labels, a masked token column, search, sort and paging; no library change
- [x] Mutation checks (a) to (k): all caught, each file restored byte-identically (MD5), see `docs/logs/phase-4-report.md`
- [ ] **NOT delivered: the filter links** (`filter.*` parameters, chip removal, the clear-all link) and the data of `ListView.Filters`. `ListViewFactory` still builds an empty `Filters` and no class builds a `filter.*` link. This is a **Phase 6 item** (D-60); the Phase 4 row promised it, and Phase 4 is therefore "delivered, with the filter-links exception", not "complete"
- [x] Merge to `main` and tag `v0.0.4`: done 2026-11-11 (merge commit `c1e7975`, tag `v0.0.4`)
- Carried out of Phase 4 by the re-scope (D-60), none of it started: [ ] filter link builders and the `Filters` data (Phase 6); [ ] relation cells as links (Phase 7); [ ] PostgreSQL and MySQL runs of the list tests, the relation cost bound, the search and count measurements and the automated phone-width test (Phase 14); [ ] hidden columns not queried (Phase 12); [ ] `jpa.admin.page-size`, `@AdminBadge`, `@AdminLookup`, `@AdminResource` plural, the `/` shortcut and the manual responsive pass (Phase 13)

### Phase 5 checklist (re-scoped by D-60; not started)

- [ ] Relation-aware sort through the existing join (no extra statement, no extra join); a relation without a label stays unsortable (approved 2026-11-11; reverses the relation clause of D-53, superseded by D-60)
- [ ] One null rule in every sort, the same on every database: nulls last in both directions, then the identifier as the final tie-breaker (approved 2026-11-11)
- [ ] A sort proof on every sortable column of every sample entity, both directions, with and without a search, with `spring.jpa.open-in-view` both true and false
- [ ] The empty state says what the search covers, and a search cut at 100 characters says so
- [ ] No new dependency (the browser-driver dependency is deferred to Phase 14), no contract change, no new property (`jpa.admin.page-size` is Phase 13); docs and CHANGELOG updated; D-60 already records the change to D-53, and a new decision is needed for anything that changes D-52

### Parallel tracks

From the end of Phase 0, UI work (tasks U0 to U9) and backend work (phases 1 to 14) can run as two parallel tracks in one directory, in separate module folders, meeting at the view-model records. `docs/UI_PLAN.md` defines the contract, path ownership, the `jpa-admin-ui-preview` app, the mapping of UI tasks to backend phases and the sync points. The phase table above stays the source of truth for backend scope and acceptance criteria.

- There is one branch per phase shared by both tracks (no per-track branches). The phase is merged into `main` and tagged only when it is connected, meaning the real controller fills the records and the real page works in the sample app. Phase 1 is a recorded exception (D-31).
- Phases 4 and 11 are verified at the backend level (MockMvc and view-model tests) in their own phase. Phase 4 also had its browser pass inside the phase, because the one-session build connected the UI at once (D-50, D-60); Phase 11's browser check happens when the UI is connected (Phase 12). Automated real-browser tests are a Phase 14 item.
- View-model records are marked draft until the phase that fills them starts. Changes to a draft record need approval but no `DECISIONS.md` entry. After that phase starts, every change needs both.

## 12. Testing strategy

| Layer | What to test |
|---|---|
| Unit | Scanner, converter, formatter, parameter validation, CSV escaping, upload type checks, chart series |
| Slice | MockMvc for controllers, security rules, CSRF |
| Integration | CRUD on every sample entity with H2, then PostgreSQL and MySQL (Testcontainers) |
| Security | Unauthenticated access blocked, no sensitive value in any response or export, unknown sort field rejected, path traversal and disguised file uploads rejected |
| Compatibility | Sample app on Spring Boot 4.1.x (latest patch) with Java 17 and the newest supported Java LTS. A 3.5 compatibility build is not planned for v0.1 |
| Manual | Browser pass on desktop and mobile, light and dark (Phase 13, with a real-browser automated test of the list added in Phase 14) |

Add a regression test for every bug.

## 13. Documentation website

MkDocs Material, deployed to GitHub Pages by CI. Pages: Home, Installation, Configuration, Using the console, Customizing entities, File uploads, Security, Annotations, REST endpoints, Deployment, Troubleshooting, Limitations and roadmap, Architecture, Development, Release guide. Draft content outline is in `docs/PROJECT_DOCS.md`.

## 14. Release checklist

Name checked on Maven Central and GitHub. Apache 2.0 licence. README, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY policy. Issue and PR templates. Semantic versioning and CHANGELOG. Sonatype namespace, GPG signing, source and javadoc jars. Demo GIF and screenshots.

## 15. Risks

| Risk | Mitigation |
|---|---|
| Exposed admin panel is a high-value target | Secure defaults, HTTPS guidance, rate limiting in v0.2, Security page |
| Uploads open an attack surface | Strict validation, random names, no execution, authenticated serving, tests |
| Lazy loading and entity cycles | Read-only transactions, map to display values, never render collections |
| Larger v0.1 scope | Strict phase order, each phase shippable, S3 already moved to v0.2 |
| Large CSV exports hurt memory | Stream rows, hard cap, paged fetching |
| Composite keys and embeddables | Unsupported in v0.1, clear message |
| Name collision | Verify the name early |
| Spring Boot version drift | Track 4.1.x patches. Boot 4.1 OSS support runs to 2027-07-31, so plan a move to the next minor before then. Re-check APIs against the official docs (CODE-8) |

## 16. Roadmap after v0.1

v0.2 login rate limiting, S3 storage provider, multi-value filters. v0.3 audit log. v0.4 multiple admins and enforced per-entity permissions. v0.5 inline child rows and quick-create from relation pickers. v0.6 galleries and image thumbnails. v1.0 stable API, `@Embedded`, composite keys, translations.

## 17. Timeline (solo developer with Claude Code)

| Week | Phases |
|---|---|
| 1 | 0 to 3 |
| 2 | 4 (done) to 7. Phase 5 is now a short phase of four small items |
| 3 | 8 |
| 4 | 9 and 10 |
| 5 | 11 to 13 |
| 6 | 14, which also carries the portability, cost and real-browser items moved out of Phases 4 and 5 |

## 18. Decisions

All earlier open decisions are resolved. See `docs/DECISIONS.md` for date and reason.

1. `groupId` is `io.github.mdmusahub`. Starter artifactId is `jpa-super-admin-spring-boot-starter`. Parent artifactId is `jpa-super-admin`.
2. v0.1 targets Spring Boot 4.1.x with Java 17 minimum, pinned to **4.1.2** (D-16, D-19). Spring Boot 3.x is not supported in v0.1.
3. S3 provider is in v0.2.
4. Uploads are private by default (authenticated only).
5. Default console path stays `/admin`.
6. Production password check: fail startup under `prod` for passwords under 12 characters or common ones, otherwise warn.
