# Phase 8 scope: create and edit forms, duplicate form fill, validation display

Written at the end of Phase 7 (2026-11-18). **Nothing in this file is implemented.** It lists what `docs/PLAN.md` gives Phase 8
(row 8: "Simple types, enums, dates, relations, validation errors, sensitive behaviour"; done when "Full CRUD on every sample entity,
invalid input never saves"), what Phase 7 hands over (the duplicate form fill, the deferred flags), what each item must be tested
with, and what Phase 7 leaves in place and what it does not. Phase 8 starts with its own plan and approval (AI-2).

Sources: `docs/PLAN.md` Phase 8 row, sections 6, 7 and 9; `docs/UI_PLAN.md` U5; `docs/FIXTURES.md` section 7; D-65 to D-74;
`docs/logs/phase-7-report.md`. **File uploads are Phase 9** (`@AdminUpload`, the storage SPI, the upload endpoint, tickets) and are
not Phase 8 items; a `FormView.Field` of type `UPLOAD` is not rendered yet.

## Items

### 1. Create form and save: `GET {path}/{entity}/new`, `POST {path}/{entity}`

*Plan:* template `jpa-admin/form` filled with `FormView` (frozen, `Mode.CREATE`); a service that converts the posted strings to the
field types (the plan's `ValueConverter`) and persists in a `@Transactional` method; a console 404 for a hidden or unknown entity;
403 for a read-only entity.

*Expected tests:* every sample entity can be created; a required field left empty never saves and shows its error next to the field;
a text that is not a number, an impossible date or an unknown enum constant never saves (error, no exception, no SQL in the page); the
posted field names are checked against the metadata (a hidden, sensitive-by-name-but-not-annotated or unknown field name is ignored
and not stored); mass assignment of the identifier, the version or a read-only field is impossible; CSRF required (403); a GET changes
nothing; a failed save keeps what the visitor typed (except a sensitive field); the redirect after a save is built by `ListLinks`
(never taken from a field) and the new record's page shows a "created" message once.

*What Phase 7 leaves in place:* the route `GET {path}/{entity}/new` exists for `copyFrom` only (`RecordController#copy`) and answers
404 for a missing `copyFrom`; **Phase 8 gives it a second job** (a plain "New record" with no `copyFrom`) and replaces the redirect-with-notice
of a valid source with the filled form. `IdConverter`, the `BulkDeleteIds`-style cap pattern, `ConsoleFlash`, `ConsoleRedirect`,
`DeleteMessages` (its texts are the model for `SaveMessages`), `ListLinks` (add `afterSave`), `Relation` (the id and label of a
target), the filter-choice query (`ListQueryService#choices`, 200 at most) for the relation dropdown, and `AdminEntity.Access`
(`readOnly`). *Not in place:* `FormView` has never been filled; there is no `form.html`; no `DynamicCrudService`/`ValueConverter`;
`@AdminResource` has no `creatable` or `updatable` (add them, with the same names SEC-11 uses); `POST {path}/{entity}` is unmapped.

### 2. Edit form and update: `GET {path}/{entity}/{id}/edit`, `POST {path}/{entity}/{id}`

*Plan:* `FormView` in `Mode.EDIT` with `recordId`, prefilled; a sensitive field is a `MASKED` input with **no value**, and a blank
submit keeps the stored value (SEC-1); an update is `@Transactional` and rolls back on validation failure (ARCH-5); optimistic
locking (`@Version`) failure gets a friendly message.

*Expected tests:* every sample entity can be edited; a sensitive field is never in the form's HTML, never in a log, and a blank value
keeps the stored one (a typed value replaces it, and is hashed or stored as the host's code decides: the console stores what it is
given); the id in the address and an id in the body cannot differ; an unknown id, a text that is not an id and a hidden entity are the
same console 404 (reuse the Phase 7 sameness test); a stale version is refused with a message, not a stack trace; no change of the
identifier; the "Edit record" link appears on the detail page (`DetailView.Actions.canEdit` becomes real, the template already prints it).

*What Phase 7 leaves in place:* `DetailView.Actions.canEdit` and the template's Edit link (a Thymeleaf path variable, so the id is
encoded), `DetailQueryService` (the read of one record; Phase 8 adds a typed read for the form: values as text, one statement, no
sensitive column selected), the detail page as the place to return to. *Not in place:* the typed read, the converter, the update service.

### 3. The duplicate form fill (carried from Phase 7, D-73)

*Plan:* `GET {path}/{entity}/new?copyFrom={id}` renders a `Mode.CREATE` form pre-filled from `CopySeed`; `recordId` is `null`; the
form posts as a new record.

*Expected tests:* a copy starts with the copyable values and nothing else (no identifier, no version, no sensitive value, no hidden
field, no collection); a relation whose target is hidden is empty in the copy; **an upload field's stored file is not copied** (this
test needs Phase 9's `@AdminUpload` and is written there; Phase 8 leaves the rule in `DetailQueryService#copyable`); the unknown, hidden
and malformed `copyFrom` tests of Phase 7 stay and still pass; `canDuplicate` becomes true on the real detail page, and the template's link
(`/new?copyFrom=`) lands on the form.

*What Phase 7 leaves in place:* the route and all its checks, `CopySeed` and its tests (`DetailQueryServiceTest`), `ConsoleRecordPagesTest`
(duplicate section: the redirect and the notice assertions change to "renders the form"), `DeleteMessages#duplicateComesLater` (delete it
with the redirect). *Not in place:* the form, the controller change, flipping `canDuplicate`.

### 4. Field inputs, validation errors next to fields, the unsaved-changes guard (U5)

*Plan:* inputs for text, number, boolean, date, date-time, enum (select) and relation (select of the target's labels, at most 200, the
rule of D-63); required markers; help text; `FormView.formErrors` summary; error text linked to its input (`aria-describedby`); focus on
the first error; an unsaved-changes guard in a small script; Bean Validation messages from the host's annotations (the sample entities
already use them, D-19) shown in plain words, never the raw constraint or class name.

*Expected tests:* a preview fixture per `FIXTURES.md` section 7 state (`create-empty`, `edit-prefilled`, `long-identity`,
`validation-errors`, `form-errors`, `select-options`, `masked-input`, `long-form`, `duplicate`); HTML rules (labels, nothing inline, no
form inside another, escaped values, the value of a masked input is empty); both themes and phone width are manual items; the sample
app proves each sample entity with open-in-view on and off; a log test shows no typed sensitive value in any console log line.

*What Phase 7 leaves in place:* `HtmlRules` (labels, inline, nested forms), the confirmation pattern (`details` with a plain form, for a
"discard changes" question), `.btn-danger`, `.js-only`/`.no-js-only`, the CSRF hidden field pattern, `ConsoleFlash` for "saved" messages,
`FormView.Option` (already used by the filter definitions), the preview's chrome-from-record pattern (`RecordFixtures#detailChrome`).
*Not in place:* `form.html`, `fragments/field-input.html`, the validation display, the guard script, the `FormView` fixtures.

### 5. Server-side flags: creatable, updatable (SEC-11)

*Plan:* `@AdminResource(creatable, updatable)`; the service refuses a create on an entity that is not creatable and an update on one that is
not updatable, even when the page hid the button; `readOnly` already implies both.

*Expected tests:* the same pair as Phase 7's SEC-11 tests (the service refuses, the database is never asked, 403 for a visible entity, 404 for
a hidden one); the dashboard's `Permissions.create` and `update` and `ListView.Actions.canCreate` become real; "New record" appears on
the list only when allowed; a mutation check that removes the service check.

*What Phase 7 leaves in place:* `AdminEntity.Access`, `canDelete()`, `DeleteService#requireDeletable`, `ActionRefusedException`,
`RecordNotFoundException` and the 403/404 mapping in `RecordController`. *Not in place:* the two options, the create/update services.

## Check against what Phase 7 leaves in place

| Item | Ready from Phase 7 | Missing, to plan in Phase 8 |
|---|---|---|
| Create | id conversion, flash, redirect check, link writer, relation choices, `Access` | `form` template and `FormView` filling, `ValueConverter`, create service, `POST {path}/{entity}`, `creatable` |
| Edit | detail page and Edit link in the template, one-record read | typed read for the form, update service with blank-keeps-secret, `updatable`, version handling |
| Duplicate form | route, checks, `CopySeed`, tests | the form, flipping `canDuplicate`, replacing the redirect and its notice |
| Inputs and errors | HTML rules, confirmation pattern, preview chrome pattern | `field-input` fragment, error display, guard script, fixtures |
| Flags | `Access`, service-side refusal pattern | `creatable` and `updatable` options and their checks |

Cross-cutting facts to carry into Phase 8: a **delete reads the whole row** (D-71), so a save that echoes a record must read only the
non-sensitive attributes (use the detail read, not the entity); the **ids of a form** go through `IdConverter` (D-67); **every link that
carries state is built by `ListLinks`** and a test searches the templates (D-74): the form's `actionHref` and `cancelHref` are already
records fields, so the same rule applies; the **cap pattern** (a limit in the record and in the parser) applies to any list a form posts.
