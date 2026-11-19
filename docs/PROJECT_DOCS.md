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

**Export properties** (Phase 11, D-107). All optional. `jpa.admin.export-enabled` (default `true`): `false` removes the export button and makes every export address answer 403. `jpa.admin.export-max-rows` (default `50000`): the most rows in one CSV file; it can only lower the cap, because 50,000 is a fixed ceiling, and a value below 1 or above 50000 stops startup with a message that names the property and does not repeat the value. `jpa.admin.export-bom` (default `false`): start each file with a UTF-8 byte order mark, for an Excel that reads it in the wrong character set. See "CSV export" under "Using the console".

**Upload properties** (Phase 9a, D-86). All optional; the storage `default` exists without any of them.

| Property | Default | Meaning |
|---|---|---|
| `jpa.admin.storage.<name>.type` | `local` | The kind of storage. Only `local` (a folder on this server's disk) exists in v0.1; any other type stops startup. S3 is planned for v0.2 |
| `jpa.admin.storage.<name>.directory` | `./uploads` | The folder. A relative path starts at the working directory. Created, and checked for write access, at startup when an upload field uses the storage. Must be outside the web root (not under `src/main/resources`, `src/main/webapp`, `target/classes` or the servlet context's folder), or startup stops |
| `jpa.admin.storage.<name>.max-size` | `5MB` | The largest file of the storage: more than 0 and at most 100MB. A field's `maxSize` may lower it, never raise it |
| `jpa.admin.storage.<name>.public-path` | none | A path such as `/files`. When set, the storage's files are also served to everyone, without a session, at `{public-path}/{key}`, and a file's stored address is the public one. Off by default. Not the console path, not inside it, not containing it, not shared with another storage |
| `jpa.admin.upload.signing-key` | random per start | Signs the upload tickets. At least 32 characters; an unset `${...}` variable stops startup. Unset: a random key is made at every start and a WARN says that a restart invalidates the forms that are open. Set it when you run more than one instance |

`<name>` is a lower-case word of letters, digits and `-` (at most 32 characters). A field uses a storage other than `default` with `@AdminUpload(storage = "name")`. Every wrong value stops startup with one report that names the property and never prints the value. Example:

```yaml
jpa:
  admin:
    storage:
      default:
        directory: /var/lib/shop/uploads
        max-size: 8MB
    upload:
      signing-key: ${UPLOAD_SIGNING_KEY}      # 32 characters or more
spring:
  servlet:
    multipart:
      max-file-size: 8MB                      # the host's own limits, see "File uploads"
      max-request-size: 9MB
```

## B5. Using the console (draft outline)

Sign in, sidebar and groups, dashboard and chart, browsing records, readable values, search and sort, filters, saved views, display options, bulk actions (delete, Phase 7), export, viewing a record (Phase 7), creating and editing, validation, uploads, keyboard shortcuts, themes, what is stored in the browser. Add a screenshot for each section.

### The "Records created" chart (Phase 10, D-101 to D-104)

The dashboard shows how many records of each entity were created on each of the last 7, 30 or 90 days, as stacked bars (one colour per entity), with a legend that gives each entity's total and a closed "Daily counts" table that holds the same numbers as text. The range switch is three plain links (`?range=7`, `?range=30`, `?range=90`); the card needs no script and works with JavaScript off.

**Which entities are in the chart.** An entity is in the chart when it has a creation timestamp field: a field that is visible and not sensitive, of type `LocalDate`, `LocalDateTime`, `Instant`, `OffsetDateTime` or `ZonedDateTime`, and named one of the following (case is ignored, and so are `_` and `-`, so `created_at` and `CREATED-AT` match): `createdAt`, `createdDate`, `createdOn`, `creationDate`, `creationTime`, `createTime`, `createdDateTime`. When an entity has several, the first name in that list wins. A sensitive or hidden field is skipped, never read. `java.util.Date` and `java.sql.Timestamp` fields are not recognised. An entity with no such field is left out, and the card says which ones ("Not in the chart: ..."). When no entity has one, the card is hidden and one sentence under the stat cards says so.

**The range.** Only 7, 30 and 90 are accepted. A missing `range` is 30. Any other value (`45`, `abc`, a repeated parameter) is replaced by 30 and the card says "That range is not available, so the last 30 days are shown". The range is today and the days before it, so 30 means today and the 29 days before.

**What it costs a host.** One query per chart entity, built with the Criteria API, that selects only the creation column for the last 90 days (newest first, at most 50,000 rows) and counts the days in Java. No database function is used, so the same query runs on H2, PostgreSQL and MySQL. The answer is kept for 5 seconds: one dashboard load runs at most one such query per chart entity, a repeat, a change of range and the JSON endpoint inside those 5 seconds run none. An index on the creation column keeps the query cheap on a large table. Each entity is read in its own read-only transaction.

**The row cap.** If an entity has more than 50,000 records in the last 90 days, only the newest 50,000 are counted. The legend marks it "incomplete" and a note names the day up to which the counts are incomplete. A shorter range that lies inside the complete part is not marked.

**Time zone.** Days follow the `Clock` bean of the host application when it has one, otherwise the server's default time zone (set the JVM zone, `-Duser.timezone`, or define a `Clock` bean to change it). A `LocalDate` or `LocalDateTime` value is a calendar value and is counted on the day it holds; an `Instant`, `OffsetDateTime` or `ZonedDateTime` is counted on the day it falls on in that zone. Days are the server's, not the viewer's.

**A table that cannot be read.** The entity is shown as "unavailable" with a plain message, one log line (without a stack trace) is written per 5 seconds at most, and the rest of the dashboard and the other entities are unaffected.

**The JSON.** `GET {path}/api/stats?range=7|30|90` answers the same data as JSON: `range`, `entities` (`name`, `label`, `available`, `total`, `capped`), `days` (`date`, `counts` in the order of the entities) and `notes`. It needs the signed-in session (an anonymous request is sent to the sign-in page), answers GET only (a POST is refused), and holds no entity, no sensitive value and no hidden entity. It is for the console and is not a stable public API in v0.1.

**Limits.** A 7-day view reads the same 90-day window as a 90-day one. Counts are incomplete beyond 50,000 records in 90 days. A creation column that is not the moment of creation (imported rows) shows what it holds. An entity whose URL name is `api` cannot open its record with the id `stats`.

### CSV export (Phase 11, D-105 to D-111)

Every list has an **Export CSV** button in its header. It downloads the records the list is showing, as a UTF-8 CSV file, with the search, the filters and the sort of the page (not its page number or size: the file is every matching record, up to the cap). The button is a plain link, so it works without JavaScript.

**What is in the file**

- The **columns** are exactly the list's visible columns, in the list's order, under the same headers. A sensitive field (a name from `jpa.admin.sensitive-fields` or `@AdminSensitive`) is not in the file at all, not even as a header; nor is a hidden field, a collection, an embedded value or binary data. The value of a sensitive field is never read from the database for an export.
- **Dates and numbers** are written for machines: dates in ISO-8601 (`2026-03-09`, `2026-11-19T10:15:30`, an offset kept as stored, `2026-11-19T10:15:30Z` for an instant), never converted to another zone; numbers plain, with a decimal point, no thousands separators and no exponent (`1234.50`). **Booleans** are `true` and `false`; an **enum** is its constant name (`OUT_OF_STOCK`), not the list's wording; **text** is complete (not cut like the list's cells) and keeps its line breaks. An empty value is an empty cell.
- A **relation** is the related record's label, as the list shows it. A relation with no label (the target has no text field, or is hidden from the console) is exported as the plain id, without the `#` of the list. An **upload field** is exported as the address text that is stored in it, not the file.
- **Quoting** follows RFC 4180: a cell that holds a comma, a double quote, a carriage return or a line feed is wrapped in double quotes, and a double quote inside it is doubled. Rows end with `\r\n`. Control characters other than tab, carriage return and line feed are dropped.
- **Spreadsheet formulas (SEC-10).** A cell whose first character is `=`, `+`, `-`, `@`, a tab or a carriage return gets a single quote in front of it, so Excel, LibreOffice and Google Sheets show the text and do not run it. The rule has no exceptions, so a **negative number is exported as text** (`'-2.50`): a spreadsheet will not add it up until the quote is removed. The header cells follow the same rule.

**How big a file can be.** At most `min(jpa.admin.export-max-rows, 50000)` rows: 50,000 is a fixed ceiling and the property can only lower it. When more records match, the file holds the first rows in the list's order and the console says so in two ways: the response carries `X-Export-Truncated: true` and `X-Export-Row-Limit: <the cap>` (only when the file is cut), and the **list page itself** shows a notice, "Export is limited to the first 50,000 of 120,345 matching records. Narrow the search or the filters to export the rest.", whenever the total is above the cap. The file never has an extra row for a note, so it stays valid for every consumer. The notice is on the list page and not "after the download" because a download never replaces the page (D-110).

**The file name** is `<entity>-<yyyy-MM-dd>.csv` (for example `product-2026-11-19.csv`): the entity's address name and the date from the application's `Clock` (or the server's clock), ASCII only, nothing a visitor typed. The response is `text/csv;charset=UTF-8` with `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff` and `Cache-Control: no-store`. There is no byte order mark unless `jpa.admin.export-bom=true`; turn it on when Excel opens a file in the wrong character set (it breaks some other tools, which then see an invisible first character in the first header).

**Switches.** `jpa.admin.export-enabled=false` removes the button everywhere and makes every export address answer 403; `@AdminResource(exportable = false)` does the same for one entity. Both are enforced in the service, not only by hiding the button. A read-only entity can still be exported. An unknown or hidden entity is the console's 404, the same for both.

**Cost, for the host to know.** The export runs the list's own query in pages of 500 rows, each page in a read-only transaction of its own, so memory is bounded by one page and a slow download does not hold a database connection open. A file costs one count, the one small statement per filterable relation that the list also pays, and one statement per page: at the cap, about 100 statements. Paging with an offset gets slower the further it goes, and a search is a full scan as on the list (index advice is under Limitations). An export is a GET and changes nothing; it needs the signed-in admin like every console route.

**Limits.** The file follows the list's search and filters; it cannot export a different set. A record added or removed while a large file is being written can move a row from one page to the next (rows are not locked). If the database fails after the first rows were sent, the connection is cut, so a half file is never taken for a whole one. Columns hidden by a saved view arrive with Phase 12. The Excel check (open a file with accents, a comma, a quote and a line break in Excel and in LibreOffice) is a manual item.

## B6. Customizing entities (draft outline)

One section per annotation with a short example and a table of options with defaults and status (Supported, Partial, Not yet). Include examples for hiding an entity, marking a field sensitive, setting a group and icon, making an entity read-only, coloured badges, choosing a relation label, and an upload field.

## B7. File uploads (draft; the backend is Phase 9a, the form and the uploader are Phase 9b)

**How it works.** Put `@AdminUpload` on a `String` field. A file goes to the upload endpoint, `POST {path}/uploads`, which checks it and stores it through the storage provider under a random name in `folder/yyyy/MM/`, and answers with the file's address. The form then saves that address in the field (Phase 9b, below). The console shows the address; the file is served by `GET {path}/files/{storage}/{key}`.

**What is validated, in this order, before anything is stored** (UPL-1, UPL-2, UPL-2a, UPL-5):

1. a signed **upload ticket**, issued with the form page, valid for about 10 minutes, bound to the entity, the field and the largest size; an expired, tampered, foreign or malformed ticket is refused with 403. A ticket is not a session: the endpoint also needs the signed-in admin and the CSRF token (the `X-CSRF-TOKEN` header from script, or the hidden `_csrf` field of a plain form);
2. the **size**: the claimed size, and then the bytes that really arrive, against the lowest of the ticket's size, the field's `maxSize` and the storage's `max-size` (never above 100MB);
3. the **real type**, from the file's first bytes: PNG, JPEG, GIF, WebP or PDF. The file name and the `Content-Type` the browser sends are never believed: the declared type may be missing or `application/octet-stream`, and any other value must equal the real type;
4. the type must be in the field's `accept` list;
5. no **hidden markup**: a file that contains `<script`, `<html`, `<?php`, `<iframe`, `<svg` or `<!doctype html` anywhere (a polyglot) is refused;
6. the file is not empty.

SVG and HTML are never accepted in v0.1, even when listed in `accept`: they have no reliable signature and can carry script, and a field that lists a type the console cannot recognise does not start. Adding a type means adding a row to the signature table (`FileSignatures`) with a test.

**`@AdminUpload` options** (all optional):

| Option | Default | Meaning |
|---|---|---|
| `storage` | `"default"` | The storage, configured with `jpa.admin.storage.<name>.*` |
| `folder` | `""` | A sub-folder, such as `avatars` or `docs/invoices`: parts of letters, digits, `-` and `_`, at most 4 parts. Anything else stops startup |
| `accept` | `image/png`, `image/jpeg`, `image/gif`, `image/webp`, `application/pdf` | The accepted MIME types (only those five can be recognised) |
| `maxSize` | the storage's `max-size` | A size such as `512KB` or `2MB`; lowers the limit, never raises it |

**Startup rules** (UPL-8). Startup stops, with one list of the fields (or storages) and the reason, when: a field names a storage that is not configured; a storage has a type other than `local`; a field is not a `String`; a folder, an `accept` entry or a `maxSize` is wrong; or the folder of a storage that a field uses cannot be created or written to, is a file, or lies inside the web root. The defaults alone pass. A host with no upload field touches no disk. The absolute folder of each storage in use is logged once at INFO.

**Serving rules** (UPL-6, UPL-7). The console route needs the signed-in admin. Every answer carries `X-Content-Type-Options: nosniff` and `Content-Security-Policy: default-src 'none'; sandbox`; a success carries the content type detected from the file's first bytes; an image is shown inline and everything else (a PDF, or an HTML file that reached the folder some other way) is a download named by its stored name. Any path with `..`, an encoded dot or slash, a backslash, a `;`, a missing part or an unknown storage is the console's 404. With a `public-path`, the same files are served at that path with the same headers to everyone, and **the host's own security applies to that path** (with Spring Boot's default security the files still need the host's login unless the host permits the path).

**The host's multipart limits.** The console never changes `spring.servlet.multipart.*`. In Spring Boot 4.1 the defaults are `max-file-size` 1MB and `max-request-size` 10MB, below the console's 5MB default, and they apply first. To accept larger files raise both in the host (`max-file-size` and `max-request-size`). A file over the host's limit is answered with a plain message that names the two properties (HTTP 413); a startup WARN says when a field allows more than the host does. `spring.servlet.multipart.enabled=false` switches file uploads off for the whole application, the console's included. One limit: a plain form (no script) that is over the host's limit gets the server's own 413 page, because the limit is hit while the security chain is still reading the form; the script flow gets the console's message.

**The upload form** (Phase 9b, D-95 to D-99). An upload field is a section of the create and edit page with a label, a sentence that says what it takes ("Accepts PNG image, JPEG image, up to 2 MB."), the current file (a picture, or a link for a document), a file input and, with script, "Replace file" and "Remove file" buttons. It sits **after** the record form and **above** Save, because a file needs a form of its own and HTML does not allow a form inside a form; Save and Cancel belong to the record form through the `form` attribute. The record form holds only the hidden address of the file. What the page does:
- *With script* (`uploader.js`): choosing or dropping a file uploads it at once with a progress bar; the page is not reloaded and nothing typed is lost; the address goes into the hidden input and the preview changes; **Save is disabled while a file is on its way**; a finished upload, a replace and a remove count as unsaved changes. The browser checks the type and size only to say so early. The server decides again.
- *Without script* (option A): the section is a plain form with its own ticket and the CSRF token as hidden fields. "Upload file" sends the file, and the page is shown again with the new address in the field and a message. **That is a new page load, so text typed in the record form and not saved is lost**: the section says so, and the advice is to save first. The result waits in the visitor's own session (one entry per field, shown once, cleared at sign-out). The remove checkbox ("Remove the current file when the record is saved") works without script.
- *Saving*: a posted address is stored only if the field's own storage made it (its storage, its folder, a type the field takes, a file that is still there); otherwise that field alone shows "The file is not one this console uploaded. Upload it again." and nothing is saved. A blank value keeps the stored file; the remove marker clears the field; a new address replaces it. **The old file always stays on disk.**
- *Showing*: a list or a page shows a stored picture as a small thumbnail (the original file scaled by CSS, so a list of large pictures downloads them all), with the field's label as its alternative text; a document as a link; a file that is gone as the words "<label>: file not found"; anything that is not one of the console's own addresses, SVG included, as plain text. The detail page also shows the pictures in a strip. A duplicate never copies a file.
- *A ticket* is issued every time the form is shown and is good for ten minutes for that field only. If the form stays open longer, the upload says to open the form again.

**Known limits.** One file per field. Replacing or removing a file keeps the old file on disk in v0.1 (UPL-9): files whose address no record holds any more pile up, and a cleanup is planned (Phase 14). No server-side thumbnails or resizing: an image is the original, scaled by CSS. The check reads the whole file once, so a PDF that holds the plain text of a tag such as `<script` in an uncompressed stream is refused. A host entity whose console address is exactly `uploads` is not supported (the address belongs to the endpoint). Hibernate's own logging is the host's: with `org.hibernate.orm.jdbc.bind` at TRACE every value of an insert or update, the stored address included, is in Hibernate's log. Back up the storage folder with the database. The S3 provider is "not yet" (planned for v0.2); a host can write its own `StorageProvider` bean.

## B8. Security (draft outline)

Sign-in and sessions (the console uses Spring Boot's default session cookie settings; host apps should set `Secure` and `SameSite` themselves; the console keeps its login, CSRF token and saved page under its own names in the shared session; signing out removes only the console's own session data and ends the session only when nothing else is in it, so a host login in the same browser stays; a startup warning under `prod` over plain HTTP, and the reverse-proxy settings that silence it), response headers (including a strict Content-Security-Policy, and why the console sends no HSTS), what is protected, built-in safeguards, the `prod` profile password check (at least 12 characters, not a common password), limits to know (single admin, no audit log, no login rate limiting yet), and a hardening checklist: password from a secret, HTTPS, restrict network access, hide or read-only sensitive entities, private upload storage, disable demo settings in production. Uploads (Phase 9a): a file is trusted for what its first bytes say and for nothing else; it needs the signed-in admin, the CSRF token and a signed ticket; it is stored under a random name that is never the client's, below a folder that is outside the web root and that no link or traversal can leave; it is served with `nosniff`, a sandboxing policy and as a download unless it is an image; a `public-path` is an explicit opt-in that the host's own security governs; set `jpa.admin.upload.signing-key` from a secret; back up the storage folder.

## B9. Annotations reference (draft outline)

Table per annotation: option, type, default, status, description. Implemented in v0.1 (package `io.github.mdmusahub.jpaadmin.annotation`), all optional; `@AdminHidden` and `@AdminSensitive` are read on the field or its getter:

- **`@AdminHidden`** — on an entity class or a field. On a class, the entity is removed from the console completely (no sidebar, no dashboard, not reachable by URL). On a field, the field is never shown, searched, sorted, filtered or exported. It cannot hide an entity's identifier (records are addressed by it); the console logs a warning and keeps the id if it is annotated.
- **`@AdminSensitive`** — on a field. The value is masked everywhere (SEC-1), whatever the field is called. It adds to `jpa.admin.sensitive-fields`.
- **`@AdminResource`** (on an entity class, Phase 7, D-68) — options `readOnly` (default `false`; the console only shows the records: no create, no edit, no delete), `creatable` (default `true`; `false` forbids creating and duplicating only), `updatable` (default `true`; `false` forbids editing only) and `deletable` (default `true`; `false` forbids deleting only). `creatable`, `updatable` and `deletable` are ignored when `readOnly`. Enforced in the service layer, not only by hiding a button (SEC-11): a forged request answers 403 for a visible entity and 404 for a hidden one. Inherited by subclasses. `exportable` (Phase 11, D-106; default `true`; `false` removes the export button and makes the entity's export address answer 403, enforced in the service; a read-only entity can still be exported, because an export only reads). The other options named in the project plan (display and plural names, group, icon, order, default sort, page size) arrive with the phases that use them.

- **`@AdminUpload`** (on a `String` field or its getter, Phase 9a, D-85) — marks the field as the address of an uploaded file. Options: `storage` (default `"default"`), `folder` (default empty), `accept` (default `image/png`, `image/jpeg`, `image/gif`, `image/webp`, `application/pdf`; only those five can be recognised, so SVG, HTML and every other type stop startup), `maxSize` (default the storage's `max-size`, for example `"2MB"`; lowers the limit, never raises it). A field that is not a `String`, a storage that is not configured or a wrong folder, type or size stops startup with the field named. Status: supported (Phase 9a and 9b): the upload input in the forms, the uploader, the image and file cells. See "File uploads".

The other annotations of `docs/PLAN.md` section 6 (`@AdminField`, `@AdminBadge`, `@AdminLookup`) arrive in later phases.

## B10. Endpoints reference (draft outline)

Routes from `docs/PLAN.md` section 9, with parameters, responses and error codes. State that the endpoints serve the console and are not a stable public API in v0.1.

Upload routes (Phase 9a). `POST {path}/uploads`, `multipart/form-data`, parts `file`, `ticket`, `entity`, `field` and, for the plain form, `record` (the id of the record being edited, used only to build the way back). Needs the signed-in admin and the CSRF token. With `Accept: application/json`: status 200 and `{"url":"...","name":"...","size":N,"error":null}`, or the refusal's status and `{"url":null,"name":null,"size":0,"error":"..."}`; statuses: 400 nothing to store or not multipart, 403 bad, expired or missing ticket, 413 too large (the field's, the storage's or the host's limit), 415 wrong content, 500 not stored. Without that header: 303 to the form page with a one-time message, or the console's error page with the refusal's status. `GET {path}/files/{storage}/{key}` serves a stored file to the signed-in admin (404 for anything that is not one); with a `public-path`, `GET {public-path}/{key}` serves it to everyone.

Export route (Phase 11). `GET {path}/{entity}/export.csv` with the list's `q`, `filter.*`, `sort` and `dir` (a `page` or `size` is ignored) returns the CSV file of that list as an attachment (see "CSV export" under "Using the console"). It needs the signed-in admin and answers GET only (a POST is refused with the console's 404, or 403 without the CSRF token). An unknown or hidden entity is the console's 404, identical in both cases; an entity or a console with the export switched off is 403. A file cut at the cap carries `X-Export-Truncated: true` and `X-Export-Row-Limit`. An entity whose identifier is the text `export.csv` cannot open its detail page, as one named `new` cannot.

Chart route (Phase 10). `GET {path}/api/stats?range=7|30|90` returns the data of the dashboard chart as JSON (see "The Records created chart" under "Using the console"). It needs the signed-in admin, answers GET only (a POST is refused with the console's 404, or 403 without the CSRF token), reads only the creation column of the entities that have one, and replaces a `range` that is not 7, 30 or 90 by 30 and says so in `notes`. The body is written by the console itself, so the host's JSON settings do not change it.

## B11. Deployment and troubleshooting (draft outline)

Running behind a reverse proxy, HTTPS, multiple instances (set `jpa.admin.upload.signing-key` so upload tickets validate on every instance), file storage volumes, common errors (a 500 from the console with the log line "did not pass through the console's security chain", most often because of a servlet path prefix, `spring.mvc.servlet.path`, which v0.1 does not support; startup failure on missing properties, including a username or password variable that is not set; a weak password under the `prod` profile; unconfigured storage, 404 for unknown entity, lazy-loading message, blocked uploads) with fixes. Upload problems (Phase 9a): "startup stops and lists upload fields" (the storage is not configured, the type is not `local`, the folder cannot be created or written to or is inside the web root, a type cannot be recognised: read the list, it names each field and why); "413 / larger than this server accepts" (raise `spring.servlet.multipart.max-file-size` and `max-request-size` in the host, Boot's defaults are 1MB and 10MB); "403 / no longer valid" (the form was open more than 10 minutes, the application restarted without a configured `jpa.admin.upload.signing-key`, or the request went to another instance with another key: open the form page again, and set the key when you run several instances); "the file has page or script markup hidden inside it" (export a clean copy); "public files ask for a login" (the host's own security protects the `public-path`: permit the path in the host's configuration).

## B12. Limitations and roadmap (draft)

Current limits: single admin account, a servlet path prefix is not supported in v0.1 (`spring.mvc.servlet.path` must be unset; otherwise the console answers 500 and logs why, D-41), password at most 72 bytes, console templates are cached and not reloaded at run time (D-40), no audit log, no login rate limiting, no inline child rows, one file per upload field, no image resizing, English only, saved views stored per browser, CSV only, servlet applications only.

Upload limits (Phase 9a and 9b, see "File uploads"): a file sent with the plain form (no script) reloads the page and loses text typed and not saved in the record form (the page says so); the thumbnails of a list are the original files scaled by CSS; the list checks that each stored file is still there, one file test per row; replacing or removing a file keeps the old file on disk (UPL-9; a cleanup is planned for Phase 14), the host's multipart limits (Spring Boot 4.1 default: 1MB per file, 10MB per request) apply before the console's and the console never changes them, only the five types PNG, JPEG, GIF, WebP and PDF can be stored (SVG and HTML never), a plain form over the host's limit gets the server's own 413 page, an entity whose console address is `uploads` is not supported, the `public-path` is protected only by the host's own security, and Hibernate's own SQL logging at TRACE would show the stored address.

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
- **Permissions.** `@AdminResource(readOnly = true)` makes an entity show-only; `@AdminResource(creatable = false)`, `(updatable = false)` and `(deletable = false)` forbid creating, editing and deleting only. The services enforce all of them: a forged request answers 403 and never reaches the database. An entity whose identifier is a sensitive field is listed with the identifier masked but has no detail page, link, checkbox or delete.
- **Delete.** `POST {path}/{entity}/{id}/delete` with the CSRF token, after a question that needs no script. The delete runs in one transaction through the persistence provider, so the host's own mapping (cascades, `@PreRemove`) applies. A record that other records still depend on is kept and the page says so in plain words, with no SQL, table name or stack trace. A `GET` to a delete address changes nothing (405).
- **Bulk delete.** `POST {path}/{entity}/bulk-delete` with the ids in the `ids` parameter, **at most 100 per request** (the largest page; a request with more is refused and nothing is deleted). All or nothing: if one record cannot be deleted, none is. Ids that are not ids, or have no record, are skipped and counted ("Deleted 2 records. 1 selected record was skipped ..."); the same id twice counts once. The header checkbox selects the rows of the current page only, and the selection is cleared whenever a page is shown (a new page, search, filter or sort). Selecting every row that matches a filter is not part of v0.1.
- **After a delete** the visitor returns to the same search, filters, sort and page size, on the first page when something was deleted, on the same page when nothing was; a page past the end becomes the last page. The message is shown once.
- **Duplicate.** The route `{path}/{entity}/new?copyFrom={id}` checks its source (unknown, malformed and hidden sources are the console's 404; a read-only entity is 403). A copy never takes the identifier or the version, a sensitive field, a hidden field, a collection, an embedded value or binary data; a relation is copied only when its target is in the console. Since Phase 8 the route shows the create form filled with those values (see the form rules below), and the detail page offers "Duplicate record".

Form rules (Phase 8, D-75 to D-84):

- **Where.** `GET {path}/{entity}/new` (a create form), `GET {path}/{entity}/{id}/edit` (an edit form), `GET {path}/{entity}/new?copyFrom={id}` (a create form filled from another record), `POST {path}/{entity}` (create) and `POST {path}/{entity}/{id}` (update). Every POST needs the console's CSRF token. Opening a form changes nothing. A hidden, unknown or malformed entity, id or `copyFrom` is the console's 404; an entity that may not be created in or changed is 403.
- **Which fields.** Only the visible fields of the entity's own metadata: text, number, yes/no, date, date and time, enum and a to-one relation whose target is in the console. Never the identifier of an existing record, the `@Version`, a hidden field, a collection, an embedded value or binary data; a name in the request that is none of these is never read. An identifier that the persistence layer does not generate (no `@GeneratedValue`) is an input of the create form only. A field that was not sent is empty on create and unchanged on update; an update writes only what changed.
- **Values.** Whole numbers in digits (with an optional minus sign) within the type's range; decimals with one point, no exponent or grouping; yes/no; dates as `yyyy-MM-dd` and times as `yyyy-MM-ddTHH:mm[:ss]`, real dates only (an instant or an offset time is read and shown in UTC); an enum by its constant; a relation by choosing one of the listed records. A text is stored as given (not trimmed); a text of spaces only is empty; control characters are refused; the longest text is the column's length (`@Column(length)`, 255 by default) and never more than 100,000 characters. An empty optional field is stored as no value; an empty required field is refused.
- **Errors.** A field that is wrong gets a plain sentence next to it, linked to its input, and a summary at the top links to each; nothing is saved and the form is shown again (status 422) with what was typed. The sentences say what is wrong and how to fix it; they never repeat the typed text and never name a class, a constraint or a table.
- **Sensitive fields.** A masked input that never has a value. On edit, leave it blank to keep the stored value; type a value to replace it. The console stores exactly what it is given: if the stored value should be hashed, do it in the entity's setter. Nothing typed is shown back, redirected, put in a message or written to the console's log.
- **Relations.** A select of the target's labels, at most **200** records, ordered by label. A target with more records is shown as a notice ("it keeps its current value"), and a save that would change it is refused for that field only. A required relation whose target has no record yet is a notice too.
- **Stale forms.** For an entity with a `@Version`, a save from a form that was opened before another change is refused with a plain message, nothing is saved, and the form stays stale until the record is opened again. An entity without a `@Version` has no such check (the last save wins).
- **Validation.** The console works with or without Hibernate Validator. When the host has it (for example Spring Boot's validation starter), its violations are shown next to the field they name, in the console's own words by kind of constraint (required, size, minimum, maximum, e-mail, pattern, past or future, and a general sentence for any other). Without it, the console checks what it can itself and a database refusal (NOT NULL, unique) is one sentence for the whole form. **A host note:** validation runs in the persistence layer at insert and update, so the entity's own constraints are what count.
- **After a save.** A created record returns to the list's first page; a changed one to its own page; a message says what happened (it names the record's label, never another value). The address to go to is built by the console and is never taken from the form.
- **Leaving.** "Cancel" asks "Discard your changes?" without script; with script the browser also warns when a changed form is left another way, and Cancel goes straight back when nothing changed.

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
- **The export follows the list and nothing else** (Phase 11, D-105 to D-111): the file is the list's search, filters and sort, its visible non-sensitive columns, up to 50,000 rows; a negative number arrives as text because of the formula guard; a relation arrives as its label (or its plain id); a file cut at the cap is said so in two headers and in a notice on the list page; rows are read with an offset, so the last pages of a very large file cost more than the first; a record added while a large file is written can shift a row between pages. Columns hidden by a saved view arrive with Phase 12. Excel and LibreOffice were not opened in the automated tests (a manual item).
- **The page size is fixed at 25 by default** and `jpa.admin.page-size` does not exist yet; the plural, the badge tones and the relation label are rules of the console, not choices of the host. The property and the annotations that replace the rules arrive in Phase 13.
- **An image cell has no picture in the preview** until the uploader exists (Phase 9).
- **Detail and delete limits (Phase 7).** An identifier of a type other than the seven listed (a `char`, a date, a floating-point number) gives no detail page and no delete. A `String` identifier that holds a `/` (or is `.` or `..`) cannot be written into an address, so its row has no link and the record cannot be opened or deleted in the console; an identifier with an encoded slash, a NUL byte or `..` in an address is refused by Spring Security's HTTP firewall with a plain 400, before the console is asked. A composite identifier is still unsupported. Editing and creating records are Phase 8, so the detail page shows no "Edit record" and no "Duplicate record" yet.
- **A delete reads the whole row.** The console deletes through the persistence provider so that the host's mapping applies, and Hibernate loads the record it removes. The console itself never logs a value (its log line holds the entity's address and the kind of failure), but with Hibernate's `org.hibernate.orm.jdbc.extract` logger at TRACE a sensitive column of the record being deleted is in Hibernate's own log. Do not enable that logger in production.
- **The cap of 100 records per bulk delete is not configurable** in v0.1, and one failing record rolls back the whole batch (by design). The messages say how many were deleted; they do not say which record blocked the batch.
- **The bulk bar and the header checkbox need script;** without script the row checkboxes, the question and the submit button still work, and the header checkbox is not shown. The automated real-browser test of these behaviours is a Phase 14 item; Phase 7 relies on tests of the markup and the script's text and on a manual browser pass.
- **Form limits (Phase 8).** An identifier type other than the seven of the detail page, a field of a type with no input (a `Calendar`, a `LocalTime`, a collection, an embedded value, binary data) and a relation to a hidden entity are not part of the form, so a record that needs one of them cannot be created in the console. A relation with more than 200 records cannot be set (a typed-id box is not offered in v0.1). There are no read-only fields, labels or help texts of the host's choosing until `@AdminField` (Phase 13). File and image fields are Phase 9.
- **Typed values reach Hibernate's log if the host asks for it.** With `org.hibernate.orm.jdbc.bind` at TRACE every bound value of an insert or update, a typed password included, is in Hibernate's log; the console's own log lines never hold a value. Leave that logger off in production.
- **The stale check needs a `@Version`.** No sample entity has one; the case is proven in `jpa-admin-core` with a test entity.
- **A date and time is shown to the millisecond** (the most the input holds). An edit that does not touch it leaves the stored value as it is; one that does writes the millisecond value.
- **A detail page for a very wide entity** prints every visible field; there is no field selection or hiding of columns until Phase 12.

Roadmap: v0.2 rate limiting, S3 storage provider and multi-value filters, v0.3 audit log, v0.4 multiple admins with roles, v0.5 inline child rows, v0.6 galleries and thumbnails, v1.0 stable API, `@Embedded` and composite keys. A Spring Boot 3.5 compatibility build can be reconsidered if users ask.

Chart limits (Phase 10, D-104): the chart reads up to 50,000 timestamps per chart entity every 5 seconds at most, whatever the range, so index the creation column of a very large table; counts are incomplete beyond 50,000 records in 90 days (the card says so); `java.util.Date` and `java.sql.Timestamp` creation fields are not recognised; days follow the server's time zone (or the host's `Clock`), not the viewer's; the one-transaction-per-entity isolation is proven on H2, and a PostgreSQL run is a Phase 14 item; the browser pass of the card (both themes, phone width, keyboard, script off) is a manual item.

## B13. Contributing pages (draft outline)

Architecture overview with the module map and request flow, development setup (`mvn clean verify`, running the sample), coding rules summary linking to `docs/RULES.md`, release guide, and how to publish the docs.
