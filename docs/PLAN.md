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
| `jpa.admin.storage.default.type` | `local` | `local` only in v0.1 (`s3` planned for v0.2). Any other type stops startup (D-87) |
| `jpa.admin.storage.default.directory` | `./uploads` | Local storage root. Relative paths start at the working directory. Created and probed at startup when an upload field uses the storage; refused inside the web root (D-87) |
| `jpa.admin.storage.default.max-size` | `5MB` | Largest file of the storage. More than 0 and at most 100MB (the global ceiling, D-86). A field may lower it, never raise it |
| `jpa.admin.storage.default.public-path` | none | If set, files are also served without a session at this path (`/files`). Off by default. Not the console path, not inside it, not containing it (D-86, D-93) |
| `jpa.admin.storage.<name>.*` | none | Another storage, with the same four properties. `<name>` is a lower-case word; a field names it with `@AdminUpload(storage = "name")`. The storage `default` exists without any property |
| `jpa.admin.upload.signing-key` | random per start | Signs upload tickets (see Uploads). At least 32 characters. Unset: a random key per start and a startup WARN that restarts invalidate open forms. Set explicitly when running several instances |

Startup fails fast, with one list of the offending fields and the reason, when any `@AdminUpload` field refers to a storage that is not configured, a storage has a type other than `local`, or the folder of a storage that a field uses cannot be created or written to (D-87). The defaults alone pass. The host's multipart settings (`spring.servlet.multipart.*`, 1MB per file and 10MB per request in Boot 4.1) are the host's: the console never changes them and its limits apply inside them (D-92).

## 6. Annotations (customization API)

All optional. The console works without them.

| Annotation | Target | Purpose |
|---|---|---|
| `@AdminResource` | Entity | Display name, plural name, group, icon, order, read-only, creatable, updatable, deletable, exportable, default sort, page size. **Delivered so far (Phases 7 and 8, D-68, D-76): `readOnly`, `creatable`, `updatable` and `deletable`; the rest arrive with the phases that use them** |
| `@AdminField` | Field | Label, help text, order, show in list, form, detail, read-only, searchable, sortable, filterable |
| `@AdminHidden` | Entity or field | Remove from the console and API |
| `@AdminSensitive` | Field | Mask everywhere |
| `@AdminBadge` | Enum or String field | Value-to-colour mapping |
| `@AdminLookup` | Relation field | Property used as the related record label |
| `@AdminUpload` | String field | Storage name (`storage`), folder (`folder`), allowed MIME types (`accept`, default PNG, JPEG, GIF, WebP and PDF; only types the console can recognise by content), max size (`maxSize`, default the storage's). **Delivered in Phase 9a (D-85)** |

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
| POST | `/admin/uploads` | Upload a file (needs session, CSRF and a valid upload ticket). Returns the stored URL as JSON for `Accept: application/json`; a plain form gets a redirect back to the form page, or an error page (D-90) |
| GET | `/admin/files/{storage}/{key}` | Authenticated file serving for local storage (D-93). With a `public-path`, also `{public-path}/{key}` without a session |
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
| 5 Sort and search quality (re-scoped, D-60; delivered 2026-11-17, D-61) | What Phase 4 left over about sorting and searching, in four small items, with no new dependency, no contract change and no new property. **(1) Relation-aware sort:** a relation column whose target has a label becomes sortable, ordered by that label through the join the list already makes (no extra join, no extra statement); a relation shown as `#id` (no label, or a hidden target) stays unsortable; this lifts the relation clause of D-53, which D-60 supersedes (approved 2026-11-11). **(2) One rule for nulls in a sort**, the same on every database: nulls last in both directions (approved 2026-11-11), set with JPA 3.2's `Nulls`, always followed by the identifier as the final tie-breaker. **(3) A sort proof on real data:** every sortable column of every sample entity, both directions, with and without a search, in both open-in-view modes. **(4) Say what the search covers:** the empty state names the columns that are searched (never a sensitive or hidden one), says so when nothing is searchable, and a search cut at 100 characters tells the visitor. Not in this phase, and why, is in `docs/DECISIONS.md` D-60; in particular `jpa.admin.page-size` is **not** a Phase 5 item, it moved to Phase 13 (approved 2026-11-11) | **(1)** sorting the sample products by category gives the label order in both directions, ties by the product's id, the header carries `aria-sort` and a link that flips the direction, the page still costs two statements, and a relation with no label is not sortable and `sort=` for it is ignored. **(2)** the sample orders (which have a nullable note) and a core test entity list nulls last for ascending and descending, with a stable order inside the nulls; the SQL carries the null rule. **(3)** one parameterised sample-app test covers every sortable column of the five entities in both directions and both open-in-view settings: the rows are in the right order, exactly the sorted header has the matching `aria-sort` and a link to the other direction, all other sortable headers say `none`, and nothing returns an error. **(4)** the empty state and the cut-search notice are asserted in core and in the sample app, and no sensitive or hidden column label appears in any of them |
| 6 Filters (delivered 2026-11-17, D-62 to D-64) | Field filters, chips, clear all. Also the filter link builders that the Phase 4 row promised and Phase 4 did not deliver (`filter.*` parameters, chip removal and clear-all links, built by the same class that builds the other list links), the `Filters` part of `ListView` (empty until now), and relation filters (a relation's label is not searched, D-52, so a relation filter is how an admin finds products by category name) | Each filter type works and combines with search and sort. Filter links are percent-encoded, lie under the base path and keep search, sort and page size, tested with the same awkward values as the Phase 4 links (spaces, slashes, quotes, unicode, a literal `http://x`) |
| 7 Detail and delete (delivered 2026-11-17, D-65 to D-74; duplicate delivered as its route and checks, the form is Phase 8, D-73) | Detail page, delete with confirm, bulk delete, duplicate. Revisit the routes the templates still build themselves (detail, edit, duplicate, delete, bulk delete, logout, New record) and decide whether the backend should build them too (D-29). Relation cells in the list become links to the related record's detail page, which removes the Phase 4 "no link yet" limit (D-54), and a hidden or unsupported target still gets no link | Deletes work, foreign key failures show friendly message. A relation cell links to its target's detail page, a hidden target's cell has no link, and every such link lies under the base path |
| 8 Create and edit (delivered 2026-11-18, D-75 to D-84) | Simple types, enums, dates, relations, validation errors, sensitive behaviour. Also the duplicate form (carried from Phase 7, D-73), `@AdminResource(creatable, updatable)` enforced in the services (SEC-11), the typed read of a record for the form, stale-version refusal, and the unsaved-changes guard (U5) | Full CRUD on every sample entity, invalid input never saves. A required field left empty, text in a number, an impossible date, an unknown enum constant and an unknown relation never save and never throw; a blank secret keeps the stored value and a typed one replaces it and appears nowhere; a posted identifier, version or unknown name is never stored; a stale form is refused with a plain message; every POST needs the CSRF token and a GET changes nothing; a relation with more than 200 records is a notice. Library tests run with `spring.jpa.open-in-view` both true and false (D-29) |
| 9a Uploads: backend (2026-11-18, D-85 onward) | The first six items of `docs/logs/phase-9-scope.md`. `@AdminUpload` (storage, folder, `accept`, `maxSize`) and the properties `jpa.admin.storage.<name>.{type,directory,max-size,public-path}` and `jpa.admin.upload.signing-key`, validated by plain code; the startup check (UPL-8): it fails, with a list of the offending fields and the reason, when a field names a storage that is not configured, when a storage has a type other than `local`, or when the directory of a storage that a field uses cannot be created or written to (or lies inside the web root); defaults alone pass; the absolute directory is logged once; the storage SPI (`store`, `delete`, `open`, `url`) and the local provider (random names in `folder/yyyy/MM/`, root check, no symbolic link, replaceable); `UploadTicketService` (HMAC, about 10 minutes, bound to entity, field and size); `POST {path}/uploads` (session, CSRF by header or hidden field, ticket, size, file signature, declared type, polyglot scan, nothing stored on failure; JSON for script, a redirect or a plain error page for the plain form, "option A"); `GET {path}/files/**` (session only, safe headers, attachment for non-images) and the optional public path. **The multipart rule:** the console never changes the host's multipart settings; the host's limits apply first (Boot 4.1: `spring.servlet.multipart.max-file-size` 1MB and `max-request-size` 10MB by default). Replace and remove keep the old file on disk (UPL-9; a cleanup is a Phase 14 item). No page shows an uploader yet | Dangerous files rejected and nothing stored when any check fails; tickets are refused when expired, tampered, for another entity or field, or signed with another key; files are served only to a signed-in admin (or at the public path when set), with `nosniff` and `attachment` for non-images; traversal in any form is refused; startup fails on an unknown storage, an unknown type or an unusable directory and passes on the defaults; the host's multipart settings and routes are untouched. Library tests run with `spring.jpa.open-in-view` both true and false (D-29) |
| 9b Uploads: forms and UI (delivered 2026-11-18, D-95 to D-100) | The last three items of `docs/logs/phase-9-scope.md`: the upload input in the create, edit and duplicate forms (ticket issued by the form render path, a posted value accepted only if it is a URL the console's storage issued, a duplicate never copies a file), `uploader.js` and the uploader fragment, the no-script form of option A, image and file cells, the detail page's images, previews, replace and remove | Upload, preview, replace, remove work in the sample app. The form waits for uploads before saving. Dangerous files rejected in the page with a plain message |
| 10 Charts | `StatsService`, stats endpoint, chart card with range switch | Chart matches data, hidden when no timestamp fields |
| 11 CSV export | `ExportService`, export button, cap, escaping | Export respects search, filters, sort, columns. Sensitive fields absent. Formula cells escaped |
| 12 Saved views | Save, apply, rename, delete views per entity. A column hidden by a view is not selected or joined by the list query, so hiding relation columns also lowers the cost of a page (the Phase 4 join-per-relation-column limit) | Views restore the exact state and survive reload. A page with hidden relation columns runs fewer joins, shown in the recorded SQL |
| 13 Polish | Styling from prototype, dark mode, toasts, empty states, shortcuts (`/` for the search box), annotations (including the pieces Phase 4 stood in for: the plural and page size of `@AdminResource`, the badge tones of `@AdminBadge`, the relation label of `@AdminLookup`), the `jpa.admin.page-size` property of section 5, moved here from Phase 5 (approved 2026-11-11, D-60; default 25, one of 10, 25, 50, 100), accent colour served as a validated generated stylesheet (SEC-15, UI-13), a manual responsive, keyboard and dark-theme pass of every page including the list (U9), README with screenshots | UI matches prototype, README quick start works in 5 minutes. A bad `jpa.admin.page-size` stops startup with a message that names the property |
| 14 Quality and release | Testcontainers (PostgreSQL, MySQL), security tests, CI, docs site, JitPack then Maven Central. **Cleanup of orphaned upload files (carried from Phase 9, UPL-9):** replacing or removing a file keeps the old file on disk in v0.1, so files whose URL no record holds any more pile up; decide whether to ship a report or a cleanup command (a file is an orphan when no upload field of any entity holds its URL), or to keep the limitation documented. **Moved here from Phases 4 and 5 (D-60), because they need the whole product or a real database:** the list tests (search with `lower(...) like ... escape '!'` on text and large-text columns, sort, null ordering, the identifier tie-breaker, paging) run on PostgreSQL and MySQL; a cost bound for relation columns (one `LEFT JOIN` per relation column, so a page with many relation columns on a large table is measured and the limit documented, or the strategy changed to a batch query beyond a cap); a measurement of the search full scan and of `count(*)` on a large table, with index advice in the docs; an automated real-browser test of the list at phone width (no sideways page scroll, the table scrolls inside its box, visible focus, Back and reload keep the state), which needs a browser-driver dependency and therefore needs approval then (AI-8) | All tests green, docs live, `v0.1.0` tagged. The same `host-check/` project is run against the release candidate installed in the local Maven repository, and passes. The list tests pass on H2, PostgreSQL and MySQL; the relation cost bound and the search and count measurements are recorded in the docs; the automated phone-width test passes |

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
- [x] **The filter links** (`filter.*` parameters, chip removal, the clear-all link) and the data of `ListView.Filters` were **not** delivered in Phase 4 (`ListViewFactory` built an empty `Filters` and no class built a `filter.*` link), so Phase 4 is "delivered, with the filter-links exception", not "complete" (D-60). **Delivered in Phase 6 (2026-11-17, D-62 to D-64)**: `ListViewFactory` fills `Filters` and `ListLinks` builds the links
- [x] Merge to `main` and tag `v0.0.4`: done 2026-11-11 (merge commit `c1e7975`, tag `v0.0.4`)
- Carried out of Phase 4 by the re-scope (D-60), none of it started: [ ] filter link builders and the `Filters` data (Phase 6); [x] relation cells as links (Phase 7, delivered 2026-11-17, D-70); [ ] PostgreSQL and MySQL runs of the list tests, the relation cost bound, the search and count measurements and the automated phone-width test (Phase 14); [ ] hidden columns not queried (Phase 12); [ ] `jpa.admin.page-size`, `@AdminBadge`, `@AdminLookup`, `@AdminResource` plural, the `/` shortcut and the manual responsive pass (Phase 13)

### Phase 5 checklist (re-scoped by D-60; delivered 2026-11-17, D-61)

- [x] Relation-aware sort through the existing join (no extra statement, no extra join); a relation without a label stays unsortable (approved 2026-11-11; reverses the relation clause of D-53, superseded by D-60)
- [x] One null rule in every sort, the same on every database: nulls last in both directions, then the identifier as the final tie-breaker (approved 2026-11-11)
- [x] A sort proof on every sortable column of every sample entity, both directions, with and without a search, with `spring.jpa.open-in-view` both true and false
- [x] The empty state says what the search covers, and a search cut at 100 characters says so
- [x] No new dependency (the browser-driver dependency is deferred to Phase 14), no contract change, no new property (`jpa.admin.page-size` is Phase 13); docs and CHANGELOG updated; D-60 already records the change to D-53, and a new decision is needed for anything that changes D-52
- [x] Merge to `main` and tag `v0.0.5`: done (the tag exists)
- Delivered 2026-11-17 (D-61): relation sort by label through the existing join; nulls last in both directions then the identifier (proven on H2); the sort proof (104 cases per open-in-view mode over the five sample entities, expected order computed in the test, `aria-sort` of every header); the search-scope message and the cut notice; preview fixtures for each; host-check relation sort. Mutation checks (a) to (h) all caught, files restored byte-identically (MD5). No template or stylesheet change was needed. Report: `docs/logs/phase-5-report.md`

### Phase 6 checklist (both tracks, one session: D-64; delivered 2026-11-17)

- [x] Filter types, driven by metadata only (SEC-2): text (case-insensitive contains, bound parameter), enum, boolean, relation (exact target id from a dropdown of the target's labels), number range, date range. A filter applies only to a visible, non-sensitive, non-identifier field; a sensitive, hidden, collection, embedded, binary or hidden-target field has none, and the attempt is ignored in the same words as an unknown name (D-62)
- [x] `filter.<field>[.min|.max|.from|.to]` in the address (UI-10); filters combine (AND) with each other, the search, the sort and the paging; an invalid value (not a number, impossible date, from after to, unknown enum constant, unknown target id) is ignored and reported by filter label and reason, never echoing the typed value; **cap of 10 applied filters**, in column order, enforced in the parser and in the `ListQuery` record
- [x] Relation filter: the dropdown lists the target's labels, **200 at most**; a target with more gets no filter and the page says so (never a silently cut list); never a sensitive attribute, never a hidden entity's row (D-63)
- [x] Chips with remove links and a clear-all link, built by `ListLinks` (percent-encoded, under the base path, keeping search, sort and page size, back to page 0); `ListView.Filters` filled; `Column.filterable` true exactly for a filterable column; no record changed
- [x] Cost: the count and the page share the same bound conditions; one small statement per filterable relation per page for its choices (a list with two such relations costs four statements); no extra join, no entity or collection loaded
- [x] UI task U3 delivered and connected (details-based panel in the search form, chips, `filters.js`, preview fixtures); see `docs/UI_PLAN.md`
- [x] Library tests run with `spring.jpa.open-in-view` both true and false (D-29); `host-check` filters its own entities with no library change
- [x] Mutation checks (a) to (i): see `docs/logs/phase-6-report.md`
- [x] No new dependency, no new property, no annotation, no contract change
- [x] Merge to `main` and tag `v0.0.6`: done (the tag exists)

### Phase 7 checklist (both tracks, one session: D-66; delivered 2026-11-17)

- [x] Id conversion shared by detail, delete, bulk delete and duplicate (`IdConverter`): `Long`, `Integer`, `Short`, `Byte`, `BigInteger`, `UUID`, `String`; strict canonical forms; every text that is not an id is handled exactly like an id with no record (D-67)
- [x] Detail page `GET {path}/{entity}/{id}`: one statement, attributes only, no entity or proxy (open-in-view on and off), sensitive masked and not selected, hidden absent, collections "(not shown)", unknown id, malformed id, hidden entity and sensitive identifier are one console 404 (D-69)
- [x] Relation cells and the identifier cell link to the detail page, exactly when the target is in the console; a hidden target keeps `#id` with no link; no extra statement; the link id equals the filter dropdown id (D-70)
- [x] Delete (`POST {path}/{entity}/{id}/delete`) and bulk delete (`POST {path}/{entity}/bulk-delete`): CSRF, confirmation that needs no script, `@Transactional`, friendly foreign key message with no SQL, SEC-11 in the service, all or nothing, cap of 100, counted skips, redirect to the same list state (page 0 after a deletion), message shown once (D-71)
- [x] `@AdminResource(readOnly, deletable)` and the real permission flags (D-68)
- [x] Row checkboxes, header checkbox for this page only, floating bar, `bulk.js`, selection cleared on every page show; no form inside another (D-72)
- [x] Duplicate: the route and its checks, the copy seed that never holds a sensitive value; the link stays out of real pages until Phase 8 (D-73)
- [x] Contract change, asked first and approved: `ListView.Actions.bulkDeleteHref` and `DetailView.Actions.deleteHref` (D-65)
- [x] UI task U4 delivered and connected (detail page, confirmation, bulk bar, row links, preview fixtures); see `docs/UI_PLAN.md`
- [x] Library tests run with `spring.jpa.open-in-view` both true and false (D-29); `host-check` has detail and delete tests of its own entities with no library change
- [x] Mutation checks (a) to (m): see `docs/logs/phase-7-report.md`
- [x] Backend builds every link that carries state; the templates build none, and a test searches them (D-74)
- [x] Merge to `main` and tag `v0.0.7`: done (the tag exists)

### Phase 8 checklist (both tracks, one session: D-75; delivered 2026-11-18)

- [x] Create (`GET {path}/{entity}/new`, `POST {path}/{entity}`) and edit (`GET {path}/{entity}/{id}/edit`, `POST {path}/{entity}/{id}`) for every sample entity, in one `@Transactional` method each; inputs for text, number, boolean, date, date-time, enum and to-one relation; required markers, help text, errors next to the field linked with `aria-describedby`, a summary, focus on the first error (D-77, D-78, D-84)
- [x] Invalid input never saves and never throws: required empty, text in a number, an impossible date, an unknown enum constant, an unknown relation id, a text too long, a Bean Validation or database refusal; each is a plain sentence per field or for the form, never a constraint name, class name or SQL (D-78, D-82)
- [x] The one editable-field rule (`EditableFields`): a posted id, version, hidden field, collection or unknown name is never read, so never stored; a name not sent is empty on create and unchanged on update; only changes are written (D-77)
- [x] Sensitive fields: masked input with no value, blank keeps the stored value, a typed value replaces it, never in the HTML, redirect, message or console log (D-79)
- [x] Stale forms refused with a plain message (`@Version`), the old version kept in the address (D-80); no sample entity changed
- [x] `@AdminResource(creatable, updatable)` enforced in the services before anything else; 403 for a visible entity, 404 for a hidden one; dashboard permissions, "New record", "Edit record" and "Duplicate record" are real (D-76)
- [x] Duplicate renders the create form from the copy seed (no identifier, version, secret, hidden field or collection; a relation to a hidden target is empty); `DeleteMessages#duplicateComesLater` deleted (D-73, D-83)
- [x] A relation with more than 200 records is a notice and a change to it is refused for that field only (D-81); without Hibernate Validator the console still saves and maps database refusals (D-82)
- [x] CSRF on every POST; a GET changes nothing; the redirect is built by `ListLinks` and never taken from a field (D-83)
- [x] UI task U5 delivered and connected (form, field fragment, guard script, preview fixtures); see `docs/UI_PLAN.md`
- [x] Library tests run with `spring.jpa.open-in-view` both true and false (D-29); `host-check` (no validator) creates and edits its own entities with no library change
- [x] Mutation checks (a) to (k): see `docs/logs/phase-8-report.md`
- [x] One new dependency (`jakarta.validation-api`, provided, D-75), no new property, no contract change
- [ ] Merge to `main` and tag `v0.0.8`: the maintainer's step (GIT-2: the phase is connected, the real pages work in the sample app)

### Phase 9a checklist (backend; both tracks, one session: D-85; built 2026-11-18, not yet merged or tagged)

- [x] Public annotation `@AdminUpload` (`storage`, `folder`, `accept`, `maxSize`, defaults documented) on a `String` field or its getter, read once by the scanner into `AdminField.upload`; properties `jpa.admin.storage.<name>.{type,directory,max-size,public-path}` and `jpa.admin.upload.signing-key`, plain validation naming the property and never printing a value (D-85, D-86)
- [x] Startup check (UPL-8): one report listing every offending field and the reason; fails on an unconfigured storage, a type other than `local`, a field that is not a `String`, an unrecognisable `accept` type, an unusable folder or size, and a storage folder that cannot be created or written to or lies inside the web root; defaults alone pass; no disk touched for a host without upload fields; the absolute folder logged once (D-87)
- [x] Storage SPI and local provider (UPL-3, UPL-4, UPL-10): random names under `folder/yyyy/MM/`, root check, symbolic links and junctions refused, `CREATE_NEW`, a failed copy leaves nothing, replaceable with `@ConditionalOnMissingBean` (D-88)
- [x] `UploadTicketService` (UPL-2a): HMAC-SHA256, 10 minutes, bound to entity, field and size; expired, tampered, wrong entity, wrong field, wrong key and malformed are refused; not a session; never logged (D-89)
- [x] `POST {path}/uploads` (UPL-1, UPL-2, UPL-5): session, CSRF by header or hidden field, ticket, size, signature, declared type, markup scan; nothing stored on any failure; JSON, redirect or error page (option A) (D-90, D-91)
- [x] `GET {path}/files/**` and the optional public path (UPL-6, UPL-7): detected type, `nosniff`, sandbox policy, `attachment` for non-images, one 404 for every traversal form (D-93)
- [x] The host's multipart settings and routes untouched; a file over the host's limit reported clearly (D-92)
- [x] Sample app: one nullable upload field (`Category.image`), seed rows unchanged, a test storage under `target`; sign in, upload, view and serve tested over real HTTP with `spring.jpa.open-in-view` on and off
- [x] Mutation checks (a) to (j): see `docs/logs/phase-9a-report.md`
- [x] No new dependency, no contract change, no new template, stylesheet or script
- [x] Phase 9b (forms and UI) is done: see the checklist below. `v0.0.9` is the maintainer's step (GIT-2: both halves are connected, the real pages work in the sample app)

### Phase 9b checklist (both tracks, one session: D-95; built 2026-11-18)

- [x] `StorageProvider.parse(url)` and `owns(url)` (default: owns nothing), implemented by the local provider for the exact addresses it makes and for files that are stored; the field also demands its own storage, folder and types (D-97)
- [x] A form renders an `UPLOAD` field with a fresh ticket bound to that entity and field; no other field carries one; the ticket is never logged; an upload field with no usable target is not a form field at all (D-96, D-97)
- [x] A posted upload address is stored only when the field's own storage made it; otherwise that field alone is refused with a plain sentence; blank keeps; the remove marker clears; a new address replaces; no file is ever deleted; only editable names (and `<field>-remove` of an upload field) are read (D-97)
- [x] A copy of a record never carries a file; the edit form reads the stored address as the field's value (a value that is not the console's own address is not shown) (D-97)
- [x] No-script path, option A: a separate section per upload field posts a plain multipart form with the hidden CSRF field and the ticket; the redirect comes back to the form page; the address, name and size wait in a console-only session attribute, are shown once as the field's value after the storage confirms them, and are saved only with the record; the page warns that unsaved text is lost (D-95)
- [x] `uploader.js` (states, advisory checks, progress, header token, preview, replace and remove with `aria-label`, Save disabled while uploading) and the `form-guard.js` change (D-98)
- [x] Image and file cells and the detail strip: thumbnails under the base path with the field label as alternative text, a missing file as words, a foreign value or an SVG as escaped text (D-99)
- [x] Preview: upload fixtures, the stub endpoint with the six states of `docs/FIXTURES.md` section 8 (stores nothing), `upload-cells` for the detail page, an alternative-text rule in `HtmlRules`
- [x] Library tests run with `spring.jpa.open-in-view` both true and false (D-29); `host-check` unaffected
- [x] Mutation checks (a) to (i): see `docs/logs/phase-9b-report.md`
- [x] No new dependency, no new property, no contract change (a cleaner `FormView.Field.upload` record is proposed for later, D-96)
- [ ] Not done: the browser pass for `uploader.js` and `form-guard.js` (manual item and Phase 14 item); the orphan-file cleanup (Phase 14)
- [x] Phase 9 is connected: the packaged sample app was run on 2026-11-18 (edit page with the upload field and ticket, a real PNG sent with the plain form, address shown once, record saved, thumbnail in the list and the detail page, the file served with `nosniff` and an image type, a foreign address refused with 422)
- [ ] Merge to `main` and tag `v0.0.9`: the maintainer's step (GIT-2)

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
