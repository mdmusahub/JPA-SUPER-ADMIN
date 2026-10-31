# CLAUDE.md

Instructions for Claude Code. Read this file first in every session.

## What this project is

**JPA Super Admin**: an open-source Spring Boot starter that gives any Spring Boot + JPA app a Django-Admin-style console. One dependency, two required properties (`jpa.admin.username`, `jpa.admin.password`), open `/admin`, log in, manage every entity. Entities are discovered automatically.

## Read these before coding

1. `docs/PLAN.md`: scope, architecture, routes, phases, acceptance criteria. This is the source of truth for what to build.
2. `docs/RULES.md`: engineering, security, UI, testing and workflow rules. Rule IDs (for example `SEC-3`) are referenced in reviews.
3. `docs/PROJECT_DOCS.md`: business requirements (BRD), user stories and the user-documentation outline.

If a request conflicts with these files, stop and ask. Do not silently change the plan.

## Stack

Java 17, Spring Boot 3.x, Spring Security, Spring Data JPA (Criteria API), Thymeleaf, small vanilla JavaScript, Maven multi-module, JUnit 5, Testcontainers, MkDocs Material for docs.

No Node, npm, React or external chart libraries. The UI ships inside the jar.

## v0.1 scope in one paragraph

Auto-configuration, mandatory username and password, separate login, entity discovery and sidebar, list with search, sort, paging and filters, saved views (browser-stored), detail, create, edit, delete, bulk delete, duplicate, file and image uploads (local storage plus storage SPI), dashboard stats and a records-created chart, CSV export, sensitive field masking, light and dark theme. Out of scope: multiple admins and roles, audit log, inline child rows, embedded and composite keys, image resizing, translations, WebFlux, non-JPA stores. Optional: S3 provider.

## Repository layout

```
jpa-admin-core/            metadata, services, controllers, templates, static files
jpa-admin-autoconfigure/   properties, security chain, auto-configuration
jpa-admin-starter/         empty pom (the only dependency users add)
sample-app/                demo app and integration tests
docs/                      PLAN.md, RULES.md, PROJECT_DOCS.md, MkDocs site
```

Package root: `io.github.<owner>.jpaadmin`. Templates go in `templates/jpa-admin/`. Static files are served from `/admin/assets/**`.

## Commands

```
mvn clean verify                          # build and run all tests (must pass before you finish)
mvn -pl sample-app spring-boot:run        # run the demo at http://localhost:8080/admin
mvn -pl jpa-admin-core test               # core tests only
mkdocs serve                              # preview docs
```

## How to work

1. Work on **one phase at a time**, in the order in `docs/PLAN.md`. Never start a later phase early.
2. Before writing code, state a short implementation plan (files to add or change, tests to write) and wait for approval.
3. Make small steps. After each step run `mvn clean verify`.
4. Write tests with every feature, in the same step.
5. At the end of a phase: run the sample app, list what to check manually in the browser, update docs and `CHANGELOG.md`, and suggest a commit message and tag.
6. Record non-obvious design choices in `docs/DECISIONS.md` (date, decision, reason).

## Non-negotiables (details in `docs/RULES.md`)

- Sensitive fields never appear in any response, export, log or error message.
- Sort, search and filter parameters are untrusted. Accept only names present in the metadata.
- Never render raw entities or collections. Map to display values.
- The console's security chain must never alter the host application's own security rules.
- Uploads are validated by content, size and type, stored under random names, and never executed.
- Every state-changing request needs CSRF protection.
- Keep the public API (annotations, properties) small and documented.

## Definition of done for any task

- Acceptance criteria in `docs/PLAN.md` for the phase are met.
- `mvn clean verify` passes.
- New behaviour has tests, including a negative or security test where relevant.
- Docs and CHANGELOG updated.
- No features from the out-of-scope list were added.

## When unsure

Ask a short, specific question. Offer two options with a recommendation. Do not guess on security, public API names or database behaviour.
