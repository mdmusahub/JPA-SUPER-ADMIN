# PROJECT_DOCS.md

Part A is the Business Requirements Document (BRD). Part B is the outline and draft content of the user-facing documentation that will be published on the docs website. Claude Code should treat Part A as the "why and what", `docs/PLAN.md` as the "how and when", and Part B as the text to keep in sync with the code.

---

# Part A: Business Requirements Document (BRD)

## A1. Document control

| Item | Value |
|---|---|
| Project | JPA Super Admin (working name, to be verified) |
| Type | Open-source library (Spring Boot starter) |
| Version of this document | 1.0 |
| Target release | v0.1.0 |
| Owner | Project maintainer |
| Licence | Apache 2.0 |

## A2. Problem statement

Spring Boot developers repeatedly build the same internal screens to view and edit database records: lists, forms, search, delete. Django ships this as a built-in admin. Spring Boot has nothing equivalent that is self-contained, secure by default and quick to adopt. Existing options either monitor the application (not data), require custom front-end work, or lack basics such as secure defaults and masking of sensitive fields.

## A3. Business objectives

| ID | Objective | Measure |
|---|---|---|
| BO-1 | Let any Spring Boot + JPA app get a working admin console with one dependency | A new user reaches a working console in 5 minutes or less |
| BO-2 | Be safe by default | No sensitive field value appears in any response or export. Security tests pass in CI |
| BO-3 | Cover day-to-day data work without code | CRUD, search, filter, sort, export and uploads work for the sample entities |
| BO-4 | Build an open-source community | Public repo, docs site, issue templates, first external contributor within 3 months of release |
| BO-5 | Publish a stable, documented API | Properties and annotations documented, semantic versioning followed |

## A4. Stakeholders and personas

| Persona | Needs |
|---|---|
| **Backend developer (primary)** | Inspect and fix data during development with zero setup |
| **QA engineer** | Look up records, filter and export data to verify behaviour |
| **Support or operations person** | Find a customer record and correct simple mistakes safely |
| **Technical lead** | Confidence that the tool is secure and will not break the host app |
| **Open-source contributor** | Clear architecture, tests, and contribution guide |

## A5. Scope summary

In scope for v0.1: auto-configuration, mandatory admin credentials, separate login, entity discovery, list with search, sort, paging and filters, saved views, detail, create, edit, delete, bulk delete, duplicate, file and image uploads, dashboard stats and chart, CSV export, sensitive field masking, light and dark theme, sample app, tests, docs.

Optional in v0.1: S3 storage provider.

Out of scope for v0.1: multiple admins and roles, audit log, inline child rows, embedded and composite keys, image resizing and galleries, server-side saved views, XLSX export, translations, WebFlux and non-JPA stores.

## A6. User stories and acceptance criteria

### Setup and access

**US-1 Add the dependency.** As a developer, I add one dependency and two properties and the console is available.
- Given only the starter and valid credentials, when the app starts, then `/admin` responds.
- Given a missing username or password, when the app starts, then startup fails and the message names the missing property.
- Given `jpa.admin.enabled=false`, then no console beans or routes exist.

**US-2 Sign in.** As an admin, I sign in with the configured credentials.
- Unauthenticated requests to console pages redirect to the login page.
- Wrong credentials show an error and do not reveal which field was wrong.
- After login I land on the dashboard. After logout my session is invalid.
- The host application's own routes and users are unaffected.

### Navigation

**US-3 See all entities.** As an admin, I see every entity in the sidebar.
- All `@Entity` classes appear, grouped, each with its record count.
- Entities marked hidden or excluded by property do not appear.
- Unknown entity URLs return a not-found page.

**US-4 Dashboard.** As an admin, I get an overview.
- Stat cards show total records, number of entities, groups and the largest table.
- A "Records created" chart shows daily counts for 7, 30 or 90 days using each entity's creation timestamp field.
- The chart is hidden when no entity has such a field.

### Browse

**US-5 View records.** As an admin, I browse an entity as a table.
- Columns show readable values: dates, booleans, enum badges, relation labels, numbers.
- Sensitive fields show as masked dots.
- Paging offers 10, 25, 50 and 100 rows per page, with a range summary.
- Empty tables show a helpful empty state.

**US-6 Search and sort.** As an admin, I find records quickly.
- Search matches non-sensitive text fields, case-insensitively.
- Clicking a header sorts ascending, then descending.
- State is in the URL and survives refresh and Back.

**US-7 Filter.** As an admin, I narrow results by field.
- Filter controls match field types (text, enum, boolean, relation, number range, date range).
- Active filters appear as chips that can be removed one by one or cleared together.
- Filters combine with search and sort.

**US-8 Saved views.** As an admin, I save a useful combination of search, filters, sort, page size and visible columns.
- I can name, apply, rename and delete views per entity.
- Applying a view restores the exact state.
- Views persist across reloads in the same browser. The app works if storage is unavailable.

### Change data

**US-9 View a record.** I open a record and see all fields formatted, with Edit, Duplicate and Delete actions.

**US-10 Create and edit.** As an admin, I create and edit records.
- Inputs suit each type. Relations are chosen from a dropdown.
- Required fields are marked. Validation errors appear next to the field and nothing is saved.
- Sensitive fields use a masked input. Leaving it blank on edit keeps the current value.
- Duplicate opens a pre-filled create form without the id and secrets.
- Unsaved changes trigger a warning when leaving the page.

**US-11 Delete.** As an admin, I delete one or many records.
- Delete always asks for confirmation.
- Bulk delete works on the selected rows.
- If a foreign key blocks deletion, a friendly message explains why.

**US-12 Upload files and images.** As an admin, I attach a file or image to a record.
- Upload fields support drag and drop or click to browse, show a preview and progress, and block saving until the upload finishes.
- Type and size are checked in the browser and again on the server by content.
- Disallowed or disguised files are rejected with a clear message.
- Stored files get random names. Images show as thumbnails in tables and on detail pages.
- Files are served only to signed-in admins unless a public path is configured.
- Startup fails if an upload field refers to an unconfigured storage.

### Output

**US-13 Export CSV.** As a QA or support user, I export the current results.
- Export respects search, filters, sort and visible columns, up to the configured row cap.
- Sensitive fields are never included.
- Cells that could run as spreadsheet formulas are neutralised.
- Export can be turned off globally or per entity.

### Experience

**US-14 Theme and shortcuts.** I can switch between light, dark and system theme. Pressing `/` focuses search and `N` starts a new record in a table.

**US-15 Customize.** As a developer, I tune the console with annotations (`@AdminResource`, `@AdminField`, `@AdminHidden`, `@AdminSensitive`, `@AdminBadge`, `@AdminLookup`, `@AdminUpload`) and properties, and every option is documented.

## A7. Functional requirements (traceability)

| ID | Requirement | Stories |
|---|---|---|
| FR-1 | Auto-configuration through the starter | US-1 |
| FR-2 | Mandatory validated username and password | US-1 |
| FR-3 | Configurable console path | US-1 |
| FR-4 | Dedicated login chain, logout, CSRF | US-2 |
| FR-5 | Entity discovery via JPA metamodel | US-3 |
| FR-6 | Hide entities and fields | US-3, US-15 |
| FR-7 | Dashboard stats and records-created chart | US-4 |
| FR-8 | Formatted table with paging | US-5 |
| FR-9 | Search and sort | US-6 |
| FR-10 | Field filters with chips | US-7 |
| FR-11 | Saved views per entity | US-8 |
| FR-12 | Detail page | US-9 |
| FR-13 | Create, edit, duplicate with validation | US-10 |
| FR-14 | Delete and bulk delete with confirmation | US-11 |
| FR-15 | File and image uploads with storage SPI | US-12 |
| FR-16 | CSV export | US-13 |
| FR-17 | Sensitive field masking everywhere | US-5, US-10, US-13 |
| FR-18 | Light and dark theme, shortcuts | US-14 |
| FR-19 | Annotations and properties | US-15 |

## A8. Non-functional requirements

| Area | Requirement |
|---|---|
| Security | Secure defaults, CSRF, masked secrets, validated inputs, safe uploads, no change to host security |
| Usability | Working console in 5 minutes. Clear errors. Responsive and keyboard friendly |
| Compatibility | Java 17+, Spring Boot 3.x (3.2+ recommended), Jakarta, Hibernate 6. Boot 4 verified later |
| Databases | H2, PostgreSQL, MySQL tested |
| Performance | Metadata read once. Lists always paged. Export streamed with a row cap. No N+1 on relation labels |
| Footprint | No Node build for users. No external network calls from the UI |
| Maintainability | Small public API, immutable metadata, tests for every feature, CI on every push |
| Accessibility | Labelled controls, visible focus, sufficient contrast, reduced motion respected |
| Documentation | Every property and annotation documented. Honest limitations page |

## A9. Assumptions and constraints

- The host application is a servlet-based Spring Boot app using JPA with Hibernate.
- Entities use single-column ids (`Long`, `Integer`, `UUID`, `String`).
- A single admin account is acceptable for v0.1.
- The console path is not the site root.
- Local storage is the default upload store. S3 is optional.
- Development is done by a small team with Claude Code, following `docs/PLAN.md`.

## A10. Risks

See `docs/PLAN.md` section 15. Main items: exposed admin panel, upload attack surface, lazy-loading errors, scope size, name collision.

## A11. Success metrics

| Metric | Target for first 3 months |
|---|---|
| Time to first working console | 5 minutes or less (measured on the sample project) |
| Automated tests | All green on H2, PostgreSQL and MySQL |
| Security regressions | Zero sensitive values in responses or exports |
| Adoption | Published on Maven Central, docs site live, first external issue or pull request |

## A12. Glossary

| Term | Meaning |
|---|---|
| Console | The admin web UI served by the starter |
| Entity | A class annotated with `@Entity` |
| Metadata | Description of entities and fields built at startup |
| Sensitive field | A field whose value must never be shown (password, secret, token) |
| Saved view | A named combination of search, filters, sort, page size and columns |
| Storage provider | A component that stores and serves uploaded files |
| Starter | The single dependency users add |

---

# Part B: User documentation (outline and draft content)

These pages become the MkDocs site. Keep them accurate with the code. Anything accepted but not implemented must be marked "not yet".

## B1. Site map

```
Home
Get started: Installation, Run the sample, Configuration
Guides: Using the console, Customizing entities, File uploads, Security
Reference: Annotations, Endpoints
Operations: Deployment, Troubleshooting, Limitations and roadmap
Contributing: Architecture, Development, Release guide
```

## B2. Home (draft)

**JPA Super Admin** gives your Spring Boot application a complete admin console. Add one dependency, set an admin username and password, and manage all your data in the browser.

Highlights: every entity found automatically, search, filters and saved views, create and edit with validation, file and image uploads, CSV export, dashboard chart, light and dark theme, sensitive fields always masked.

## B3. Installation (draft)

```xml
<dependency>
  <groupId>io.github.mdmusahub</groupId>
  <artifactId>jpa-super-admin-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

Requirements: Java 17+, Spring Boot 3.x, Spring Data JPA, a servlet web application.

Add the two mandatory properties:

```yaml
jpa:
  admin:
    username: admin
    password: ${ADMIN_PASSWORD}
```

Start the application and open `/admin`. If either property is missing, the app stops and tells you which one.

## B4. Configuration (draft)

Reproduce the property table from `docs/PLAN.md` section 5, with a sentence per property and an example. State clearly which properties are required (only `username` and `password`).

## B5. Using the console (draft outline)

Sign in, sidebar and groups, dashboard and chart, browsing records, readable values, search and sort, filters, saved views, display options, bulk actions, export, viewing a record, creating and editing, validation, uploads, keyboard shortcuts, themes, what is stored in the browser. Add a screenshot for each section.

## B6. Customizing entities (draft outline)

One section per annotation with a short example and a table of options with defaults and status (Supported, Partial, Not yet). Include examples for hiding an entity, marking a field sensitive, setting a group and icon, making an entity read-only, coloured badges, choosing a relation label, and an upload field.

## B7. File uploads (draft outline)

How uploads work, `@AdminUpload` options, local storage configuration, serving and visibility, size and type limits, what is validated, known limits (one file per field, old files are kept on replace, no thumbnails), and the optional S3 provider.

## B8. Security (draft outline)

Sign-in and sessions, what is protected, built-in safeguards, limits to know (single admin, no audit log, no login rate limiting yet), and a hardening checklist: password from a secret, HTTPS, restrict network access, hide or read-only sensitive entities, private upload storage, disable demo settings in production.

## B9. Annotations reference (draft outline)

Table per annotation: option, type, default, status, description.

## B10. Endpoints reference (draft outline)

Routes from `docs/PLAN.md` section 9, with parameters, responses and error codes. State that the endpoints serve the console and are not a stable public API in v0.1.

## B11. Deployment and troubleshooting (draft outline)

Running behind a reverse proxy, HTTPS, multiple instances (set the upload signing key), file storage volumes, common errors (startup failure on missing properties, unconfigured storage, 404 for unknown entity, lazy-loading message, blocked uploads) with fixes.

## B12. Limitations and roadmap (draft)

Current limits: single admin account, no audit log, no login rate limiting, no inline child rows, no embedded or composite keys, one file per upload field, no image resizing, English only, saved views stored per browser, CSV only, servlet applications only.

Roadmap: v0.2 rate limiting and multi-value filters, v0.3 audit log, v0.4 multiple admins with roles, v0.5 inline child rows, v0.6 galleries and thumbnails, v1.0 stable API and Spring Boot 4.

## B13. Contributing pages (draft outline)

Architecture overview with the module map and request flow, development setup (`mvn clean verify`, running the sample), coding rules summary linking to `docs/RULES.md`, release guide, and how to publish the docs.
