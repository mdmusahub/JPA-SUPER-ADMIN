# CLAUDE.md (backend track)

Applies when you work in `jpa-admin-core/`, `jpa-admin-autoconfigure/`, `jpa-admin-starter/` or `sample-app/`. The root `CLAUDE.md`, `docs/PLAN.md`, `docs/RULES.md` and `docs/UI_PLAN.md` still apply.

## Your track

You are the **backend track**. You build the scanner, services, controllers, security, storage and auto-configuration, and you fill the view-model records that the UI renders.

## You own

- `jpa-admin-core/src/main/java/**` (except `web/view`, which is shared)
- `jpa-admin-autoconfigure/**`
- `jpa-admin-starter/**`
- `sample-app/**`

## You must not edit

- `jpa-admin-ui/**` and `jpa-admin-ui-preview/**` (templates, CSS, JavaScript, fixtures). If a page needs a change, describe it and ask for the UI track.
- The view-model records in `jpa-admin-core/.../web/view/**`, unless the change is approved and logged in `docs/DECISIONS.md` (contract change rule, `docs/UI_PLAN.md` section 8).

## Rules for this track

- Controllers are thin: validate input, call a service, build the view-model record, return the template name. They never touch `EntityManager` (ARCH-2).
- Never put an entity or a collection of entities in a model. Fill the records only (ARCH-3, ARCH-4).
- A `masked` cell carries no value. Sensitive data never reaches the records (SEC-1).
- Template names and model attribute names must match `docs/UI_PLAN.md` section 3 and section 4. If they do not match, stop and ask.
- Verify Spring Boot, Security, Data JPA and Hibernate APIs against the official 4.1 docs, not memory (CODE-8).
- Every feature ships with tests, including at least one negative security test (TEST-1, TEST-2).

## Build and verify (only your modules)

```
mvn -pl jpa-admin-core -am clean verify
mvn -pl sample-app -am clean verify
```

Run the full `mvn clean verify` only when the UI session is idle.

## Run the demo

Same recipe as "Run the demo" in the root `CLAUDE.md`. The sample app has no admin credentials in its `application.yml`, so set `JPA_ADMIN_USERNAME` and `JPA_ADMIN_PASSWORD` (12 or more characters), run the jar, then open http://localhost:8080/admin. Stop it with Ctrl+C.

PowerShell:

```powershell
mvn -pl sample-app -am package -DskipTests
$env:JPA_ADMIN_USERNAME = 'admin'
$env:JPA_ADMIN_PASSWORD = '<12 or more characters>'    # this PowerShell session only
java -jar sample-app/target/sample-app-0.1.0-SNAPSHOT.jar
```

bash:

```bash
mvn -pl sample-app -am package -DskipTests
export JPA_ADMIN_USERNAME=admin
export JPA_ADMIN_PASSWORD='<12 or more characters>'
java -jar sample-app/target/sample-app-0.1.0-SNAPSHOT.jar
```

Do not use `mvn -pl sample-app spring-boot:run`: without `-am` Maven takes the other modules from `~/.m2`, where an older `mvn install` can leave stale jars, so it can run old code.

## Commit

Commit by path: `git add jpa-admin-core jpa-admin-autoconfigure jpa-admin-starter sample-app`. Never `git add .` while the UI session has changes.

## Starting a task

State "Backend track" in your first line, restate the task from `docs/PLAN.md`, propose a plan (files, tests), and wait for approval.
