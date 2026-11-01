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
| `jpa.admin.password` | none | Required, BCrypt-hashed at startup (Phase 2). Missing, blank, or an unset `${...}` variable stops startup. Under `prod`, under 12 characters or a common password also stops startup (section 8). Never logged or printed |
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
| 3 Discovery | Scanner, registry, layout, sidebar, dashboard stat cards, and an entity-name check with no new dependency: a CI step or test that greps the sources and resources of the library modules for the demo entity names (`Category`, `Product`, `Customer`, `Order`, `Tag`) and fails if any is found (ARCH-12). The check matches whole words, case-sensitive. Every allow-list entry has a file path and a written reason, and the check also fails when an allow-list entry no longer matches anything, so stale entries cannot hide | Every entity listed with right count, hidden ones absent. A second host project (folder `host-check/`, outside the reactor build) with different entities (`Book`, `Author`, `Publisher`) that adds only the starter dependency and the two required properties shows all three entities in the sidebar with correct counts. No library change is needed for it to work. Library tests run with `spring.jpa.open-in-view` both true and false (D-29) |
| 4 List | Query service, formatter, list page, paging, masking, link builders for sort, page, page-size and filter links | Right columns and values, password always masked. Link builders percent-encode every query value; tests cover spaces, slashes, quotes, unicode and a literal `http://x` search (D-29). Library tests run with `spring.jpa.open-in-view` both true and false (D-29) |
| 5 Search and sort | `q`, `sort`, `dir` validated against metadata | Correct results, shareable URLs, search never reads sensitive fields |
| 6 Filters | Field filters, chips, clear all | Each filter type works and combines with search and sort |
| 7 Detail and delete | Detail page, delete with confirm, bulk delete, duplicate. Revisit the routes the templates still build themselves (detail, edit, duplicate, delete, bulk delete, logout, New record) and decide whether the backend should build them too (D-29) | Deletes work, foreign key failures show friendly message |
| 8 Create and edit | Simple types, enums, dates, relations, validation errors, sensitive behaviour | Full CRUD on every sample entity, invalid input never saves |
| 9 Uploads | `@AdminUpload`, storage SPI, local provider, upload endpoint, uploader UI, startup check | Upload, preview, replace, remove work. Dangerous files rejected. Startup fails on missing storage |
| 10 Charts | `StatsService`, stats endpoint, chart card with range switch | Chart matches data, hidden when no timestamp fields |
| 11 CSV export | `ExportService`, export button, cap, escaping | Export respects search, filters, sort, columns. Sensitive fields absent. Formula cells escaped |
| 12 Saved views | Save, apply, rename, delete views per entity | Views restore the exact state and survive reload |
| 13 Polish | Styling from prototype, dark mode, toasts, empty states, shortcuts, annotations, accent colour served as a validated generated stylesheet (SEC-15, UI-13), README with screenshots | UI matches prototype, README quick start works in 5 minutes |
| 14 Quality and release | Testcontainers (PostgreSQL, MySQL), security tests, CI, docs site, JitPack then Maven Central | All tests green, docs live, `v0.1.0` tagged. The same `host-check/` project is run against the release candidate installed in the local Maven repository, and passes |

### Phase 1 checklist (backend work finished 2026-11-01)

- [x] Properties `enabled`, `path`, `username`, `password`, `title`, `primary-color`; `username` and `password` required, `path` and `primary-color` pattern-checked, `prod` password rule (D-7), all checked at startup with a message that names the property (D-30)
- [x] Auto-configuration and imports file; backs off outside a servlet web app, without JPA or Spring MVC, and with `jpa.admin.enabled=false` (no beans, no routes, no credentials asked for)
- [x] Starter alone makes `/admin` respond (plain-text placeholder, replaced in Phase 2)
- [x] Missing username fails startup clearly (checked on a real start of the sample app)
- [x] Asset handler for `{path}/assets/**` from `classpath:/jpa-admin/assets/` only. Runtime tests: `/admin/assets/**` serves the asset; `/jpa-admin/assets/**`, `/assets/**`, `/css/**` and a custom path's old path return 404
- [x] Every link built from the base path passes the contract's link rule and lies under it (D-29)
- [ ] Merge to `main` and tag `v0.0.1`: the maintainer's step. D-31 records that Phase 1 is tagged although UI task U0 (which runs alongside Phases 1 and 2) connects after Phase 2 (`docs/UI_PLAN.md` section 7), because the Phase 1 "done when" is backend-only and the placeholder page is its UI

### Parallel tracks

From the end of Phase 0, UI work (tasks U0 to U9) and backend work (phases 1 to 14) can run as two parallel tracks in one directory, in separate module folders, meeting at the view-model records. `docs/UI_PLAN.md` defines the contract, path ownership, the `jpa-admin-ui-preview` app, the mapping of UI tasks to backend phases and the sync points. The phase table above stays the source of truth for backend scope and acceptance criteria.

- There is one branch per phase shared by both tracks (no per-track branches). The phase is merged into `main` and tagged only when it is connected, meaning the real controller fills the records and the real page works in the sample app.
- Phases 4 and 11 are verified at the backend level (MockMvc and view-model tests) in their own phase, and the browser check happens when the UI is connected (Phases 5 and 12).
- View-model records are marked draft until the phase that fills them starts. Changes to a draft record need approval but no `DECISIONS.md` entry. After that phase starts, every change needs both.

## 12. Testing strategy

| Layer | What to test |
|---|---|
| Unit | Scanner, converter, formatter, parameter validation, CSV escaping, upload type checks, chart series |
| Slice | MockMvc for controllers, security rules, CSRF |
| Integration | CRUD on every sample entity with H2, then PostgreSQL and MySQL (Testcontainers) |
| Security | Unauthenticated access blocked, no sensitive value in any response or export, unknown sort field rejected, path traversal and disguised file uploads rejected |
| Compatibility | Sample app on Spring Boot 4.1.x (latest patch) with Java 17 and the newest supported Java LTS. A 3.5 compatibility build is not planned for v0.1 |
| Manual | Browser pass on desktop and mobile, light and dark |

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
| 2 | 4 to 7 |
| 3 | 8 |
| 4 | 9 and 10 |
| 5 | 11 to 13 |
| 6 | 14 |

## 18. Decisions

All earlier open decisions are resolved. See `docs/DECISIONS.md` for date and reason.

1. `groupId` is `io.github.mdmusahub`. Starter artifactId is `jpa-super-admin-spring-boot-starter`. Parent artifactId is `jpa-super-admin`.
2. v0.1 targets Spring Boot 4.1.x with Java 17 minimum, pinned to **4.1.2** (D-16, D-19). Spring Boot 3.x is not supported in v0.1.
3. S3 provider is in v0.2.
4. Uploads are private by default (authenticated only).
5. Default console path stays `/admin`.
6. Production password check: fail startup under `prod` for passwords under 12 characters or common ones, otherwise warn.
