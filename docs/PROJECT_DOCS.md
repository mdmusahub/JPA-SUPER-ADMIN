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

Out of scope for v0.1: S3 storage provider (planned for v0.2), multiple admins and roles, audit log, inline child rows, embedded and composite keys, image resizing and galleries, server-side saved views, XLSX export, translations, WebFlux and non-JPA stores.

## A6. User stories and acceptance criteria

### Setup and access

**US-1 Add the dependency.** As a developer, I add one dependency and two properties and the console is available.
- Given only the starter and valid credentials, when the app starts, then `/admin` responds.
- Given a missing username or password, when the app starts, then startup fails and the message names the missing property.
- Given `jpa.admin.enabled=false`, then no console beans or routes exist.

**US-2 Sign in.** As an admin, I sign in with the configured credentials.
- Unauthenticated requests to console pages redirect to the login page.
- Wrong credentials show an error and do not reveal which field was wrong.
- After login I land on the dashboard. After logout I am signed out of the console.
- The host application's own routes and users are unaffected: the console's sign-in and its session data are separate from the host's, whatever the host's own security looks like.
- Signing out clears only what the console keeps in the browser session. A host login, cart or any other state in the same session is not touched, and the session itself ends only when nothing but console state was in it (D-39).

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
- Paging offers 10, 25, 50 and 100 rows per page (25 unless you choose), with a range summary ("Showing 26-50 of 94"), "Page 2 of 4" and first, previous, next and last links.
- A page number that is too high shows the last page; a page, size or sort that makes no sense falls back to the default, so a shared or old link never shows an error.
- Empty tables show a helpful empty state.

**US-6 Search and sort.** As an admin, I find records quickly.
- Search matches non-sensitive text fields, case-insensitively, anywhere in the text. What you type is taken literally: `%`, `_`, quotes and a pasted address such as `http://x` are searched for as typed. Numbers, dates, enums, relations and hidden or sensitive fields are not searched. Text longer than 100 characters is cut.
- Clicking a header sorts ascending, then descending. A relation column sorts by the related record's label (for example products by their category's name), not by its identifier; a relation shown as `#id` and a masked column have no sort. Records with no value in the sorted column come last in both directions, on every database, and rows with equal values keep a fixed order (the identifier), so paging never repeats or skips a row.
- When a search finds nothing, the page names the text columns that were searched (never a sensitive or hidden one), or says that the table has no text column a search can look in. When a search is longer than 100 characters, a notice says it was cut and gives the limit.
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
- Stored files get random names. Images show as thumbnails in tables and on detail pages (the original image scaled by CSS, with no server-side resizing).
- Uploads need a short-lived signed ticket issued with the form page, in addition to the session and CSRF token.
- Files are served only to signed-in admins unless a public path is configured.
- Startup fails if an upload field refers to an unconfigured storage.

### Output

**US-13 Export CSV.** As a QA or support user, I export the current results.
- Export respects search, filters, sort and visible columns, up to the configured row cap (never more than 50,000).
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
| Compatibility | Java 17+, Spring Boot 4.1.x, Jakarta Persistence 3.2, Hibernate ORM 7. Spring Boot 3.x is not supported in v0.1 |
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
- The console path is not the site root, has no trailing slash, and uses only letters, digits, `-` and `_` between single slashes (SEC-14).
- Local storage is the only upload store in v0.1. S3 is planned for v0.2.
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

Requirements: Java 17+, Spring Boot 4.1.x (Spring Boot 3.x is not supported), Spring Data JPA, a servlet web application.

The starter brings the Thymeleaf library that the console renders its own pages with. It does not set Thymeleaf up for your application: your own views, template engine, view resolvers and `spring.thymeleaf.*` settings (or JSP, FreeMarker, Mustache) are neither used nor changed by the console, and if you have no Thymeleaf you do not get one (D-40).

Add the two mandatory properties:

```yaml
jpa:
  admin:
    username: admin
    password: ${ADMIN_PASSWORD}
```

Start the application and open `/admin`. If either property is missing, the app stops and tells you which one.

## B4. Configuration (draft)

Reproduce the property table from `docs/PLAN.md` section 5, with a sentence per property and an example. State clearly which properties are required (only `username` and `password`), that an environment variable which is not set counts as missing (`password: ${ADMIN_PASSWORD}` with no such variable stops startup), the rules for `path` and `primary-color`, and that one startup report lists every wrong property.

**`jpa.admin.excluded-entities`** (list, empty by default). Entity class names the console must never show. An entry matches by simple class name or by fully qualified class name, ignoring case (`AuditEntry`, `com.example.AuditEntry`, `auditentry` all match the same class). A hidden entity is not in the sidebar, not on the dashboard, and its address returns the same 404 as an address that was never an entity. Example: `jpa.admin.excluded-entities[0]=AuditEntry`.

**`jpa.admin.sensitive-fields`** (list, default `password, secret, token, apikey`). Field names whose values are always masked. The match ignores case, `_` and `-`, and is a substring match, so `password` also covers `userPassword` and `api_key` matches `apikey`. Because it is a substring match it masks a little too much on purpose: a field called `tokenizer` contains `token` and is masked. Narrow the list if that is not wanted. `@AdminSensitive` adds to this list and is never removed from it. Example: `jpa.admin.sensitive-fields[0]=password`.

## B5. Using the console (draft outline)

Sign in, sidebar and groups, dashboard and chart, browsing records, readable values, search and sort, filters, saved views, display options, bulk actions (delete, Phase 7), export, viewing a record (Phase 7), creating and editing, validation, uploads, keyboard shortcuts, themes, what is stored in the browser. Add a screenshot for each section.

## B6. Customizing entities (draft outline)

One section per annotation with a short example and a table of options with defaults and status (Supported, Partial, Not yet). Include examples for hiding an entity, marking a field sensitive, setting a group and icon, making an entity read-only, coloured badges, choosing a relation label, and an upload field.

## B7. File uploads (draft outline)

How uploads work, `@AdminUpload` options, local storage configuration, serving and visibility, size and type limits, what is validated, known limits (one file per field, old files are kept on replace, no server-side thumbnails), upload tickets, and the S3 provider as "not yet" (planned for v0.2).

## B8. Security (draft outline)

Sign-in and sessions (the console uses Spring Boot's default session cookie settings; host apps should set `Secure` and `SameSite` themselves; the console keeps its login, CSRF token and saved page under its own names in the shared session; signing out removes only the console's own session data and ends the session only when nothing else is in it, so a host login in the same browser stays; a startup warning under `prod` over plain HTTP, and the reverse-proxy settings that silence it), response headers (including a strict Content-Security-Policy, and why the console sends no HSTS), what is protected, built-in safeguards, the `prod` profile password check (at least 12 characters, not a common password), limits to know (single admin, no audit log, no login rate limiting yet), and a hardening checklist: password from a secret, HTTPS, restrict network access, hide or read-only sensitive entities, private upload storage, disable demo settings in production.

## B9. Annotations reference (draft outline)

Table per annotation: option, type, default, status, description. Implemented in v0.1 (package `io.github.mdmusahub.jpaadmin.annotation`), all optional; `@AdminHidden` and `@AdminSensitive` are read on the field or its getter:

- **`@AdminHidden`** — on an entity class or a field. On a class, the entity is removed from the console completely (no sidebar, no dashboard, not reachable by URL). On a field, the field is never shown, searched, sorted, filtered or exported. It cannot hide an entity's identifier (records are addressed by it); the console logs a warning and keeps the id if it is annotated.
- **`@AdminSensitive`** — on a field. The value is masked everywhere (SEC-1), whatever the field is called. It adds to `jpa.admin.sensitive-fields`.
- **`@AdminResource`** (on an entity class, Phase 7, D-68) — options `readOnly` (default `false`; the console only shows the records, no delete and from Phase 8 no create or edit) and `deletable` (default `true`; `false` forbids deleting only; ignored when `readOnly`). Enforced in the service layer, not only by hiding a button (SEC-11). Inherited by subclasses. The other options named in the project plan (display and plural names, group, icon, order, creatable, updatable, exportable, default sort, page size) arrive with the phases that use them.

The other annotations of `docs/PLAN.md` section 6 (`@AdminField`, `@AdminBadge`, `@AdminLookup`, `@AdminUpload`) arrive in later phases.

## B10. Endpoints reference (draft outline)

Routes from `docs/PLAN.md` section 9, with parameters, responses and error codes. State that the endpoints serve the console and are not a stable public API in v0.1.

## B11. Deployment and troubleshooting (draft outline)

Running behind a reverse proxy, HTTPS, multiple instances (set `jpa.admin.upload.signing-key` so upload tickets validate on every instance), file storage volumes, common errors (a 500 from the console with the log line "did not pass through the console's security chain", most often because of a servlet path prefix, `spring.mvc.servlet.path`, which v0.1 does not support; startup failure on missing properties, including a username or password variable that is not set; a weak password under the `prod` profile; unconfigured storage, 404 for unknown entity, lazy-loading message, blocked uploads) with fixes.

## B12. Limitations and roadmap (draft)

Current limits: single admin account, a servlet path prefix is not supported in v0.1 (`spring.mvc.servlet.path` must be unset; otherwise the console answers 500 and logs why, D-41), password at most 72 bytes, console templates are cached and not reloaded at run time (D-40), no audit log, no login rate limiting, no inline child rows, one file per upload field, no image resizing, English only, saved views stored per browser, CSV only, servlet applications only.

Discovery limits (Phase 3): an entity with a composite primary key (`@EmbeddedId` or `@IdClass`) is not supported and is left out of the console, with a one-line startup warning (entities need a single-column id in v0.1). The console reads one `EntityManagerFactory`: if the host has several and marks none `@Primary`, the console lists no entities and logs a warning rather than guessing which database it would show. Sensitive-field name matching is a case-insensitive substring match, so it can mask a field nobody meant it to (for example `tokenizer`, which contains `token`); narrow `jpa.admin.sensitive-fields` to avoid it.

List limits (Phase 4): a relation column shows the related record's label, chosen as the first text field named `name`, `title`, `label`, `fullName` and similar, else the first text field, else `#id` (`@AdminLookup` will let you choose, later); it is not a link until the detail page exists. A relation column sorts by the related record's label (Phase 5). To-many relations, embedded values and binary data are not shown in the list. A text longer than 120 characters is cut, with the full text on hover. Numbers are shown as stored, dates as `yyyy-MM-dd`, and an instant in UTC. The plural in the heading is a plain English guess (`Category` is `Categories`, `Person` would wrongly be `Persons`) until `@AdminResource`. Each list page runs two queries, a count and a page, and the sidebar adds one count per entity at most every five seconds; on a very large table the count is not free. The page size default is 25 and cannot be configured yet (`jpa.admin.page-size` is planned). "New record", export, bulk delete and filters are not on the list yet.

Sort and search rules (Phase 5, D-60, D-61):

- **What can be sorted.** A visible, non-sensitive column of kind identifier, text, number, boolean, enum, date or date-time, and a to-one relation whose target is in the console and has a label (the same label the cell shows: a text field named `name`, `title`, `label`, `fullName` and similar, else the first text field). A relation shown as `#id`, a masked column, a collection, an embedded value and binary data cannot be sorted. A sort key that is not one of these is ignored and the list is in identifier order; a valid key with a missing or unknown direction is ascending.
- **How a relation is ordered.** By the target's label, not by its identifier, through the join the list already makes: no extra query. A sensitive attribute of the target is never the label, so it can never order the list.
- **Missing values.** In every sort, records with no value in the sorted column (an empty field, or a relation that is not set) come last, in both directions, on every database. After the sorted column, rows are ordered by the identifier ascending, so paging never repeats or skips a row. Databases differ in where they put missing values by default (H2 and MySQL first, PostgreSQL last, in an ascending sort); the console does not follow them. It is tested on H2; PostgreSQL and MySQL are tested in Phase 14.
- **What a search covers.** The visible, non-sensitive text fields of the entity itself (and its identifier if that is text): not numbers, dates, enums, booleans, relations, or hidden or sensitive fields. When a search finds nothing, the page lists the columns it looked in by their header names (at most ten, then "and N more"), or says that the table has no text column a search can look in. A search longer than 100 characters is cut to 100, and a notice says so and gives the limit.
- **Limits.** The order of text, including relation labels, is the database's own (upper and lower case and accents follow its collation); a relation whose target has a label that is empty or missing sorts as missing; a search that finds rows does not list its columns.

Filter rules (Phase 6, D-62, D-63, D-64):

- **Where.** A filter panel sits under the search box on every list page (a "Filters" button that opens it). It is part of the same form as the search, so searching keeps the filters and filtering keeps the search. The state is in the address: `filter.<field>=<value>`, so a filtered list can be bookmarked and the Back button works.
- **What can be filtered.** A visible, non-sensitive field that is not the identifier and is one of: text, number, yes/no, enum, date or date-time, or a to-one relation whose target is in the console and has a label (the same label the cell shows). A sensitive field, a hidden field, a collection, an embedded value, binary data and a relation to a hidden entity have no filter. A filter parameter that names one of them is ignored, in the same words as a name that does not exist, and no value is ever shown.
- **Parameters.** Text, enum, yes/no and relation: `filter.<field>=<value>`. Numbers: `filter.<field>.min` and `.max`. Dates: `filter.<field>.from` and `.to`, written `yyyy-MM-dd`. A blank value means "not set" and says nothing, because an HTML form sends blank controls.
- **Text.** Case-insensitive "contains". `%`, `_` and `!` are taken literally. Control characters become spaces, the ends are trimmed, and text longer than 100 characters is cut (and a notice says so).
- **Enum.** One constant name, exactly as the enum spells it. **Yes/no.** `true` or `false` in any letter case; `any` is the same as leaving it out.
- **Relation.** The dropdown lists the target's labels and sends its identifier. The identifier must be one of the choices listed, otherwise the filter is ignored. The dropdown lists at most **200** records, ordered by label (a record with no label last, shown as `#id`). **A target with more than 200 records gets no relation filter at all**, and the page says so, rather than showing a list that is silently cut short. The choices come from the target's own records and never from a hidden entity or a sensitive field.
- **Number range.** `min` and/or `max`, inclusive: digits with an optional minus sign and decimal point, no exponent and no thousands separator. A whole-number field rounds a fractional bound inwards (a minimum of 2.5 means 3 and up). A bound the field cannot hold is ignored. A minimum above the maximum ignores the whole filter.
- **Date range.** `from` and/or `to`, inclusive, real calendar days from year 1000 to 9998 (so `2026-02-30` is ignored). On a date-time field `from` starts at the first moment of that day and `to` includes the whole of that day. A date-time stored as an instant is cut into days in UTC, as its cell shows it. A `from` after the `to` ignores the whole filter.
- **Ignored values.** Never an error. The list shows without that filter, and a notice at the top names the filter by its label and says why (never repeating what was typed). A repeated parameter uses the first value and says so. A name that matches no filter is counted in one message that names nothing.
- **How many.** At most **10** filters apply at once (a range with two bounds counts as one), taken in column order; the others are ignored and counted in a notice.
- **Combining.** Filters are ANDed with each other, the search, the sort and the paging. Each active filter is a chip with a remove link, there is a "Clear all filters" link, and removing a filter or clearing all keeps the search, the sort and the page size and starts again at the first page.
- **Cost.** The count and the page use the same conditions (all bound parameters). A relation filter adds one small statement per list page for its choices (identifier and label only, 201 rows at most); a list with two filterable relation columns costs four statements, whatever the number of rows. Nothing else grows with the filters, and no join is added.
- **Not yet.** Several values for one field (v0.2), filtering on a relation's own fields, "is empty" and "is not empty" filters, and saved views (Phase 12) are not part of Phase 6.

Record rules (Phase 7, D-65 to D-74):

- **The detail page.** `GET {path}/{entity}/{id}`, reached from the first column of a row or from a relation. One statement; a password-like field is dots and is never read; a hidden field is absent; a collection, an embedded value and binary data show their label and "(not shown)"; a long text keeps its line breaks and, past 300 characters, sits behind a summary. The breadcrumb leads back to the list with the search, filters, sort and page the visitor came from.
- **Which ids work.** The id in the address is turned into the identifier's type: `Long`, `Integer`, `Short`, `Byte`, `BigInteger`, `UUID` or `String`. A number must be written in its plain form (`7`, not `007` or `+7`); a UUID in the usual 8-4-4-4-12 form; a text id is at most 255 characters with no control character, no `/` or `\`, and is not `.` or `..`. Any text that is not an id gives the same console 404 as an id with no record, as does a hidden or unknown entity or an entity whose identifier is a sensitive field.
- **Relation links.** A relation cell links to the related record when the related entity is in the console, and shows `#id` with no link when it is hidden or unsupported. An empty relation is an empty cell.
- **Permissions.** `@AdminResource(readOnly = true)` makes an entity show-only; `@AdminResource(deletable = false)` forbids deleting only. The service enforces both: a forged request answers 403 and never reaches the database. An entity whose identifier is a sensitive field is listed with the identifier masked but has no detail page, link, checkbox or delete.
- **Delete.** `POST {path}/{entity}/{id}/delete` with the CSRF token, after a question that needs no script. The delete runs in one transaction through the persistence provider, so the host's own mapping (cascades, `@PreRemove`) applies. A record that other records still depend on is kept and the page says so in plain words, with no SQL, table name or stack trace. A `GET` to a delete address changes nothing (405).
- **Bulk delete.** `POST {path}/{entity}/bulk-delete` with the ids in the `ids` parameter, **at most 100 per request** (the largest page; a request with more is refused and nothing is deleted). All or nothing: if one record cannot be deleted, none is. Ids that are not ids, or have no record, are skipped and counted ("Deleted 2 records. 1 selected record was skipped ..."); the same id twice counts once. The header checkbox selects the rows of the current page only, and the selection is cleared whenever a page is shown (a new page, search, filter or sort). Selecting every row that matches a filter is not part of v0.1.
- **After a delete** the visitor returns to the same search, filters, sort and page size, on the first page when something was deleted, on the same page when nothing was; a page past the end becomes the last page. The message is shown once.
- **Duplicate.** The route `{path}/{entity}/new?copyFrom={id}` checks its source (unknown, malformed and hidden sources are the console's 404; a read-only entity is 403). A copy never takes the identifier or the version, a sensitive field, a hidden field, a collection, an embedded value or binary data; a relation is copied only when its target is in the console. The create form, and with it the visible "Duplicate" link, arrive in Phase 8.

Known limits and the phase that addresses each (re-scope, D-60). This list adds to the limits above; none of them is removed.

- **A relation column costs one `LEFT JOIN` per relation column** (two statements per page whatever the number of columns, but each relation column adds a join to the page query). An entity with many relation columns on a large table can be slow. A column hidden by a saved view will stop being queried (Phase 12). A cost bound is measured and documented, or the strategy changed, in Phase 14.
- **The search is a full scan.** It is a case-insensitive "contains" (`lower(column) like '%text%'`), which an ordinary index cannot serve, so on a large table it reads the table. `count(*)` is not free either. Measured, with index advice, in Phase 14.
- **Database portability is untested.** All list tests run on H2. `lower(...)` on large-text columns, the `!` escape in `like`, and null ordering may behave differently on PostgreSQL and MySQL. Tested on both in Phase 14. A large-text column that does not support `lower(...)` would make that entity's list answer with the console's 500 page.
- **The phone-width behaviour is proven by a manual browser pass and by tests that read the stylesheet**, not by an automated browser test. A manual responsive pass of every page is part of Phase 13; the automated real-browser test of the list is a Phase 14 item (it needs a browser-driver dependency, which needs approval then).
- **Null ordering is one rule, proven on H2 only.** Records with no value in the sorted column come last in both directions (Phase 5, D-60, D-61). Hibernate writes the clause only where the database's default differs, so the SQL differs by database; PostgreSQL and MySQL are tested in Phase 14.
- **A search says what it covers only when it finds nothing** (Phase 5). A search that finds rows does not list its columns.
- **A relation's label is not searched**, although it can be sorted (Phase 5). Finding records by a relation's label is the relation filter (Phase 6): choose the record from the dropdown. A relation shown as `#id` cannot be sorted. The order of labels is the database's text order (case and accents follow its collation), proven on H2 only until Phase 14.
- **Filters are limited to what is listed under "Filter rules" above** (Phase 6): one value per field (multi-value filters are v0.2), the identifier cannot be filtered, a text filter is a "contains" like the search (a full scan on a large table, see the search limit above), and a relation filter costs one small extra statement per filterable relation on every list page.
- **A relation cell is a link only when its target is in the console** (Phase 7, D-70). A relation to a hidden or unsupported entity shows `#id` with no link.
- **There is no "New record" and no export on the list.** They arrive with Phase 8 and Phase 11.
- **The page size is fixed at 25 by default** and `jpa.admin.page-size` does not exist yet; the plural, the badge tones and the relation label are rules of the console, not choices of the host. The property and the annotations that replace the rules arrive in Phase 13.
- **An image cell has no picture in the preview** until the uploader exists (Phase 9).
- **Detail and delete limits (Phase 7).** An identifier of a type other than the seven listed (a `char`, a date, a floating-point number) gives no detail page and no delete. A `String` identifier that holds a `/` (or is `.` or `..`) cannot be written into an address, so its row has no link and the record cannot be opened or deleted in the console; an identifier with an encoded slash, a NUL byte or `..` in an address is refused by Spring Security's HTTP firewall with a plain 400, before the console is asked. A composite identifier is still unsupported. Editing and creating records are Phase 8, so the detail page shows no "Edit record" and no "Duplicate record" yet.
- **A delete reads the whole row.** The console deletes through the persistence provider so that the host's mapping applies, and Hibernate loads the record it removes. The console itself never logs a value (its log line holds the entity's address and the kind of failure), but with Hibernate's `org.hibernate.orm.jdbc.extract` logger at TRACE a sensitive column of the record being deleted is in Hibernate's own log. Do not enable that logger in production.
- **The cap of 100 records per bulk delete is not configurable** in v0.1, and one failing record rolls back the whole batch (by design). The messages say how many were deleted; they do not say which record blocked the batch.
- **The bulk bar and the header checkbox need script;** without script the row checkboxes, the question and the submit button still work, and the header checkbox is not shown. The automated real-browser test of these behaviours is a Phase 14 item; Phase 7 relies on tests of the markup and the script's text and on a manual browser pass.
- **A detail page for a very wide entity** prints every visible field; there is no field selection or hiding of columns until Phase 12.

Roadmap: v0.2 rate limiting, S3 storage provider and multi-value filters, v0.3 audit log, v0.4 multiple admins with roles, v0.5 inline child rows, v0.6 galleries and thumbnails, v1.0 stable API, `@Embedded` and composite keys. A Spring Boot 3.5 compatibility build can be reconsidered if users ask.

## B13. Contributing pages (draft outline)

Architecture overview with the module map and request flow, development setup (`mvn clean verify`, running the sample), coding rules summary linking to `docs/RULES.md`, release guide, and how to publish the docs.
