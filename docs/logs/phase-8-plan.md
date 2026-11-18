# Phase 8 plan: create, edit, duplicate form, validation display, creatable/updatable

Branch `phase/8-create-edit` (from `main`, tag `v0.0.7`). One session on both tracks (the recorded exception to AI-11, as Phases 2 to 7).
Written 2026-11-18 before any code. Source: `docs/PLAN.md` Phase 8 row, `docs/UI_PLAN.md` U5, `docs/logs/phase-8-scope.md`,
`docs/FIXTURES.md` section 7, `docs/RULES.md`, `docs/DECISIONS.md` (next free number: **D-75**). Not committed. Phase 9 not started.

## Verified before planning (CODE-8)

* Jars on the classpath: `jakarta.persistence-api` 3.2.0, `jakarta.validation-api` 3.1.1, Hibernate ORM 7.4.13.Final, Hibernate Validator 9.1.5.Final
  (in `~/.m2`; the sample app brings it through `spring-boot-starter-validation`, D-19), Spring Framework 7.0.x, Spring Boot 4.1.2.
* Hibernate's persist-time validation (`org.hibernate.boot.beanvalidation.BeanValidationEventListener`, checked with `javap -c`) throws
  `jakarta.validation.ConstraintViolationException` (a plain `RuntimeException`, **not** a `PersistenceException`) from its pre-insert and
  pre-update listeners. The service therefore walks the cause chain of anything thrown by `persist`/`flush` and does not rely on the wrapper type.
* **No sample entity has `@Version`.** Decision: **no sample entity is changed**. The optimistic-lock case (service and page level) is tested in
  `jpa-admin-core` with a neutral test entity that has a `@Version` field; the sample app does not exercise it (recorded in the report).
* Packages used: `jakarta.persistence` (`EntityManager`, `PersistenceException`, `OptimisticLockException`, `Tuple`, `Criteria*`, `PersistenceUnitUtil`,
  `GeneratedValue`, `Column`, `Lob`), `jakarta.validation` (`ConstraintViolationException`, `ConstraintViolation`, constraint annotation types by
  name), `org.springframework.transaction.annotation.Transactional`, `org.springframework.web.*` (already used), `org.slf4j`.

## Contract

No frozen record changes. Conventions (to record as decisions):
* the stale-version token travels in the form's `actionHref` query (`?version=N`), built by `ListLinks`;
* a `SELECT` field with **no options** is a notice (its `help` text says why: more than 200 choices, or no records to choose from) and renders no control;
  every other select starts with a blank option ("None" or "Choose...");
* `FormView.formErrors` holds whole-form messages; the per-field errors are summarised by the template from `Field.error`.

## Steps (each ends with the module build; logged in `phase-8-change-log.md`)

0. Housekeeping (done): tag exists, ports free, greps, scope file present.
1. **Metadata and flags** (core): `@AdminResource(creatable, updatable)`; `AdminEntity.Access` gets them (`canCreate`, `canUpdate`, `canDelete`);
   `AdminEntity.canCreate()`, `canUpdate()`; `EditableFields` (the one editable-field rule); `jakarta.validation-api` provided dependency.
2. **Converter and messages** (core): `FormText` (value to form text), `ValueConverter` (text to each type, typed errors), `FieldMessages`,
   `ConstraintMessages` (isolated; loaded only when the validation API is present), `SaveMessages`.
3. **Typed read** (core): `FormQueryService` (`@Transactional(readOnly)`): form record (one statement, no sensitive column, no collection), create
   defaults, relation choices (shared `RelationChoices`, 200 bound), copy seed text through `FormText`.
4. **Write services** (core): `RecordWriteService` (`@Transactional`): create, update; SEC-11 first; mass-assignment rule; blank-keeps-secret;
   stale version; persistence/validation violations mapped to field errors; test double `TransactionalRecordWriteService`.
5. **Controller and view model** (core + autoconfigure): `FormViewFactory`, `FormController` (new, copy, create, edit, update), `ListLinks.afterSave`,
   duplicate route now renders the form, `canDuplicate`/`canEdit`/`canCreate`/dashboard permissions real, auto-configuration beans.
6. **UI U5** (ui): `form.html`, `fragments/field-input.html`, `form-guard.js`, CSS, links on list and detail.
7. **Preview**: `FormFixtures` (nine states of FIXTURES.md section 7) and the `form` page.
8. **Tests**: core (converter, messages, flags, typed read, write rules, mass assignment, stale version, 200 rule, controller pages), preview
   (HTML rules, fixture content), sample app (every entity created and edited, open-in-view on and off, CSRF, GET changes nothing, 404 sameness,
   secrets, log), host-check.
9. **Mutation checks** (a) to (k).
10. **Docs**: PLAN, UI_PLAN, DECISIONS, CHANGELOG, PROJECT_DOCS, FIXTURES; `phase-9-scope.md`.
11. **Final checks**: `mvn -B clean verify`, install, host-check, live run with `curl.exe`, ports, ARCH-12 grep.

## Design in short

* **Editable field rule (one place, `EditableFields`)**: visible, not the version, not a collection/embedded/binary/other kind, and not the identifier
  (exception: a *not generated* identifier is a create-only input, recorded). A to-one relation is editable only when its target is in the console and
  has a single identifier. A posted name that is not in this list is never read.
* **Posted values** arrive through a `Function<String,String>` (no copying of the request map). A missing name: *unchanged* on update, *empty* on create.
* **Update** loads the entity once (`find`), compares each posted text with the current value's form text and assigns **only changes** (so a
  datetime shown to the millisecond never loses precision, and a record edited without changes is not dirtied). Values are set through the
  host's setter when there is one, else the field.
* **Sensitive**: masked input, never a value; blank on update keeps; typed replaces; blank on create is a required error when the field is required.
* **Failures**: all mapped to plain sentences; the service throws `InvalidRecordException` (rolls back); the controller re-renders the form with the
  typed values (sensitive ones dropped) and the errors; nothing from the exception text reaches the page.
* **200 rule**: the form's relation choices are the filter's choices (at most 200). Over the bound the field is a notice; a posted value for it is
  refused for that field only; an unposted one keeps the stored value.
