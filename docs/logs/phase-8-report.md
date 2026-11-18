# Phase 8 report: create, edit, duplicate form, validation display, creatable and updatable

Branch `phase/8-create-edit` (from `main`, tag `v0.0.7`). One session on both tracks (the recorded exception to AI-11, D-75). Nothing is
committed. Plan: `phase-8-plan.md`. Every file and command: `phase-8-change-log.md` (76 entries). Decisions: D-75 to D-84. Phase 9 is not
started; `phase-9-scope.md` lists its items.

APIs verified against the pinned jars and the library source (CODE-8), not from memory: `jakarta.persistence` 3.2.0 (`EntityManager`,
`PersistenceException`, `OptimisticLockException`, `PersistenceUnitUtil`, `Tuple`, the Criteria types, `@Column`, `@GeneratedValue`, `@Lob`),
`jakarta.validation` 3.1.1 (`ConstraintViolationException`, `ConstraintViolation`, `Path.Node`, `ConstraintDescriptor`), Hibernate ORM
7.4.13 (`BeanValidationEventListener`, read with `javap -c`: it throws `jakarta.validation.ConstraintViolationException`, a plain
`RuntimeException`, from its pre-insert and pre-update listeners, so the service walks the cause chain), Hibernate Validator 9.1.5 (the
sample app, through Boot's validation starter), Spring Framework 7.0.x (`@Transactional`, `SharedEntityManagerCreator`, the web
annotations) and Spring Boot 4.1.2. **No sample entity has `@Version`**; none was added (see section 4).

## 1. Files

`git status --short`: 70 paths, none deleted: **32 modified** (`git diff --stat`: 511 insertions, 174 deletions, before this report and
the last log entries were written) and **38 new**.

**New, backend** (`jpa-admin-core`): `service/`: `EditableFields`, `FieldMessages`, `FormPage`, `FormQueryService`, `FormText`,
`InvalidRecordException`, `Members`, `RecordWriteService`, `RelationChoices`, `ValueConverter`, `ConstraintMessages`; `web/`:
`FormController`, `FormViewFactory`, `SaveMessages`.
**Modified, backend:** `AdminResource` (`creatable`, `updatable`), `AdminEntity` (`Access`, `canCreate`, `canUpdate`), `EntityScanner`,
`ListQueryService` (the choices query moved to `RelationChoices`), `DetailQueryService` (the copy seed text through `FormText`),
`ListLinks`, `RecordController` (the `copy` route moved to `FormController`; `canEdit` and `canDuplicate` real), `EntityController`,
`ConsoleNavigation`, `DeleteMessages` (`duplicateComesLater` deleted), `JpaAdminAutoConfiguration` (three beans), `jpa-admin-core/pom.xml`
(`jakarta.validation-api`, provided).
**New and modified, UI:** `form.html`, `fragments/field-input.html`, `form-guard.js`; `pages.css` (appended section), `layout.html` (one
script tag); preview `FormFixtures`, `PageFixtures`, `PreviewController`, `RecordFixtures` and `ChromeFixtures` (the `duplicate-later`
variants removed).
**Tests (new):** core `ValueConverterTest`, `FormTextTest`, `ConstraintMessagesTest`, `AbstractFormServiceTest`, `RecordWriteServiceTest`,
`FormQueryServiceTest`, `ConsoleFormPagesTest`, `TransactionalRecordWriteService` and three neutral test entities (`Folio` with a
`@Version`, `Frozen`, `Sealed`); preview `PreviewFormPagesTest`; sample `AbstractFormPagesTest` with its two open-in-view subclasses,
`FormPagesLogTest`; host-check `ConsoleFormsOfThisHostTest`. **Tests (changed because the product changed, nothing weakened):**
`ConsoleEntityPagesTest`, `ConsoleListPageTest`, `ConsoleRecordPagesTest` (its duplicate section now asserts the rendered form),
`AbstractRecordPagesTest` (one assertion), `PreviewPagesTest`, `PreviewRecordPagesTest` (the `duplicate-later` fixtures), the test
`persistence.xml` and `ListTestPersistence.empty()` (the new entities).
**Docs:** `DECISIONS` (D-75 to D-84), `PLAN`, `UI_PLAN`, `FIXTURES`, `PROJECT_DOCS`, `CHANGELOG`, and in `docs/logs`: plan, change log,
this report, `phase-9-scope`.

## 2. Tests

| Module | Before | After |
|---|---|---|
| jpa-admin-core | 2495 | 2625 |
| jpa-admin-autoconfigure | 449 | 449 |
| jpa-admin-ui-preview | 1530 | 1714 |
| sample-app | 679 | 743 |
| host-check (outside the reactor) | 16 | 21 |
| **Reactor total** | **5153** | **5531** |

`mvn -B clean verify`: BUILD SUCCESS. `mvn -B -DskipTests install`: BUILD SUCCESS. `mvn -B -f host-check/pom.xml verify`: BUILD SUCCESS,
21 tests (the second host has no validation library at all). The live run passed (entry 74 of the change log). Ports 8080, 8081 and 8090
are free, no java process of mine is left (the sample app was stopped by PID 21020, the preview by its PID). The ARCH-12 grep finds only the
three allow-listed Spring `@Order` lines.

## 3. Mutation checks

Each: save the file, break one thing, run the named tests, see a failure, restore from the saved copy, compare MD5, run the same tests
green, log. All eleven were caught.

| | Break | Caught by | Result |
|---|---|---|---|
| a | the blank-keeps-secret rule removed (blank overwrites) | `RecordWriteServiceTest`, `ConsoleFormPagesTest` | 3 failures |
| b | the version and the "generated id is not an input" checks removed, the id editable on update | `FormQueryServiceTest`, `RecordWriteServiceTest`, `ConsoleFormPagesTest` | 17 failures and errors |
| c | `visibleFields()` replaced by `fields()` (a hidden field is editable) | the same three | 6 failures |
| d | the `canCreate` check skipped in the create service | `RecordWriteServiceTest`, `ConsoleFormPagesTest` | 3 failures |
| e | the `canUpdate` check skipped in the update service | the same two | 3 failures |
| f | a secret is a plain input and its column is selected | `FormQueryServiceTest`, `RecordWriteServiceTest`, `ConsoleFormPagesTest` | 9 failures |
| g | the failed-save form puts a typed secret back | `FormQueryServiceTest`, `ConsoleFormPagesTest` | 3 errors (see note) |
| h | the stale-version refusal removed | `RecordWriteServiceTest`, `ConsoleFormPagesTest` | 4 failures |
| i | the redirect taken from a `next` form field | `ConsoleFormPagesTest` | 1 failure (see note) |
| j | `GET edit` calls the update service | `ConsoleFormPagesTest` | 1 failure |
| k | the identifier and the sensitive check removed from the copy rule | `DetailQueryServiceTest` | 3 failures |

Honest notes: **(g)** fails by an exception, not by a quiet leak, because the frozen `FormView.Field` constructor already refuses a
masked field with a value: a second line of defence. **(i)** was first caught only because `ConsoleRedirect` refuses an external address;
the test posted nothing in-site, so I strengthened it (three targets, an in-site address first) and re-ran: it now fails as an assertion
(`expected "/admin/folio" but was "/admin/folio/1"`). **(h)**: the lost-race test still passes with the check removed, because the
database's own version check answers the same way (a second line of defence, also tested).

## 4. Decisions, deviations and what a host should know

**The stop-and-decide defaults (all recorded):**

* **A relation over 200 records (D-81):** a notice, no list; a save that would change it is refused for that field only, with a message;
  the stored value is kept otherwise. The notice is a `SELECT` field with no options and a help text (a convention of the frozen record,
  not a change to it). **Please confirm.** Alternative not built: a typed-id box.
* **Hibernate Validator absent (D-82):** the console works without it. It checks what it can itself and lets the persistence layer have the
  last word; with a validator, violations are mapped to the field named by the first element of the property path, in the console's own
  sentence for the kind of constraint (never the host's message, the class or the value); a database refusal without a validator is one
  sentence for the form. It mapped cleanly, so I did not stop. Proven by the second host (no validator) and the sample app (with one).
* **`@Version` (D-80):** no sample entity has one and **none was added** (the brief allowed it only if the test needed it). The stale
  case is tested in `jpa-admin-core` with a neutral test entity, at service level and through the controller. It is therefore not
  exercised through the sample app or the browser. Entities without a `@Version` have no stale check (last write wins).
* **creatable and updatable (D-76):** default `true`, `readOnly` implies neither; 403 for a visible entity, 404 for a hidden one; checked
  first in the service and in the form service.

**Other decisions and deviations, with reasons (please look at these):**

* **No contract change.** Three conventions of the frozen `FormView` (D-75): the stale-version token is in the form's `actionHref` query
  (`jpa-admin-version`); a `SELECT` with no options is a notice; `formErrors` leads with a count line when fields have errors. The
  cleaner way to carry the version would be a hidden input type in `InputType`: a contract change **proposed for later**, not needed.
* **A refused save answers status 422** (not in the brief): the page is the form again with the typed values and the sentences. A browser
  shows it like any page.
* **After a create the visitor goes to the list's first page** (the brief said `ListLinks.afterSave`); after an update to the record's
  page. The brief's final check asks for "redirects to the list" for a product create; both are built by `ListLinks`, never from a field.
* **Only changes are written on update (D-77)**, compared with the stored value's form text. Reason: the database keeps microseconds and
  the date-time input only milliseconds, so rewriting every field would silently truncate a value the visitor never touched, and would
  move the version of an untouched record. A found-by-test consequence: an edit that touches a date-time writes the millisecond value.
* **An identifier that is not generated is an input of the create form only (D-77)**: not in the brief, but without it no entity with an
  assigned identifier could be created. A sensitive identifier is not an input.
* **Absent means empty on create and unchanged on update (D-77).** A text of spaces only is empty (stored as no value); text is not
  trimmed; CR LF from a text area is stored as LF; the longest text is the column's length (`@Column(length)`, 255 by default) and never
  more than 100,000 characters (my choice).
* **A number is a text input** (the browser's number input would wipe a mistake instead of letting the server explain it).
* **The brief mentioned "read-only fields"**: the metadata has no per-field read-only flag until `@AdminField` (Phase 13). The editable rule
  excludes the identifier, the version, hidden fields, collections, embedded and binary fields, and types with no input.
* **A duplicate's flags are checked before its source** (a forbidden entity is 403 whatever `copyFrom` says).
* **Thymeleaf's Spring integration prints a second identical hidden `_csrf` input** right after any `<form th:action>` (the delete forms
  already did). Harmless; noted.
* **Sign-in answers 302** (Spring Security's default), not 303; nothing changed there.

**What a host application should know:**

* **New write paths:** `POST {path}/{entity}` and `POST {path}/{entity}/{id}`, both `@Transactional`, both behind the console's chain and CSRF.
  Values are written through the entity's own setter when it has one (so a setter that hashes a password still runs: the console stores
  exactly what it is given), else the field; the entity needs a no-argument constructor to be created.
* **Extra reads:** a form costs one statement for the record plus one small statement per relation for its choices (200 rows at most); the
  create form reads nothing from the database for its defaults (it reads a fresh instance); an update loads the entity (and so its whole row).
* **Validation:** the entity's own constraints are what count and they run in the persistence layer. No validator is needed.
* **Logging:** with Hibernate's bind logger (`org.hibernate.orm.jdbc.bind`) at TRACE, every typed value of an insert or update, a password
  included, is in **Hibernate's** log. The console's own log lines hold the entity's address and exception class names only (tested).
* **New provided dependency:** `jakarta.validation:jakarta.validation-api` in `jpa-admin-core` (nothing added to a host).
* **New public API:** `@AdminResource(creatable, updatable)` (the names RULES SEC-11 and PLAN section 6 already use, so not a rename).

## 5. Not done, to check by hand, proposals, Phase 9

* **Manual browser pass (not done, no browser driver):** in light and dark, at 390 px and with the keyboard only: `create-empty`,
  `edit-prefilled`, `validation-errors`, `select-options`, `relation-notice`, `long-form`, `duplicate` (preview URLs
  `/admin-preview/form?state=...`); focus moves to the summary and the first error; the Cancel question opens and closes with the
  keyboard; with script off the form posts and the question still works; the browser's warning appears when a changed form is left by
  the sidebar, and not after Save or "Yes, discard changes"; nothing scrolls sideways; a required `*` is visible in both themes.
  Then the real pages in the sample app.
* **Not done:** an automated real-browser test of `form-guard.js` (Phase 14: it is checked by tests of its text and of the markup, not
  executed); a stale-form test through the sample app (no `@Version` there); a typed-id box for a relation over 200 records; labels,
  help texts and read-only fields of the host's choosing (`@AdminField`, Phase 13); uploads (Phase 9).
* **Contract change proposed for later:** a hidden input type (or a field on `FormView`) for the version, to replace the query parameter
  convention. Nothing else.
* **Phase 9 scope** (uploads) is in `docs/logs/phase-9-scope.md`: `@AdminUpload`, the storage SPI and local provider, upload tickets, the
  endpoint, file serving, the startup check, the upload input in the forms (including the rule that an upload field is never copied, which
  Phase 8 left in `DetailQueryService#copyable`), image and file cells, and UI task U6.

## 6. Suggested commits (by path, PowerShell-safe; never `git add .`)

```powershell
# 1 backend: flags, services, controller, wiring, the provided dependency
git add jpa-admin-core/pom.xml jpa-admin-core/src/main jpa-admin-autoconfigure/src/main
git commit -m "Add create, edit and duplicate forms, creatable/updatable and stale-form handling (Phase 8 backend)"

# 2 UI: template, field fragment, guard script, styles
git add jpa-admin-ui
git commit -m "Add the form page, field inputs and unsaved-changes guard (Phase 8, task U5)"

# 3 preview: fixtures and their tests
git add jpa-admin-ui-preview
git commit -m "Add form preview fixtures and tests"

# 4 tests: core, sample app, second host
git add jpa-admin-core/src/test sample-app host-check
git commit -m "Test forms: converter, typed read, write rules, controller, every sample entity, second host"

# 5 docs
git add docs CHANGELOG.md
git commit -m "Document Phase 8 (D-75 to D-84), U5 and the Phase 9 scope"
```

Each commit message ends with the attribution line the session was given (`Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`).
The first two commits do not build alone (the controller needs the templates, and the tests need both); if you want every commit to build,
squash 1 to 4.
