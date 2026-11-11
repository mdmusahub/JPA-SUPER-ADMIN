# Phase 3 plan: discovery (both tracks, one session)

Branch `phase/3-discovery`, from tag `v0.0.2`. One session on both tracks, the same recorded exception to AI-11 as
Phase 2 (D-33), extended to Phase 3. Nothing is committed and Phase 4 is not started.

Every file and command is logged in `docs/logs/phase-3-change-log.md`.

## 0. Environment note (found before writing any code)

Maven is **not** on the PATH of this machine and there is no local Maven repository (`%USERPROFILE%\.m2` does not
exist). The only Maven here is the one bundled with IntelliJ IDEA,
`C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\plugins\maven-plugin\lib\maven3\bin` (Apache Maven 3.9.16,
Java 17.0.20.1 Temurin). Maven Central is reachable. Every build in this phase therefore prepends that folder to
`PATH` and the first build downloads the whole dependency tree. A baseline `mvn -B clean verify` is run before any
change, so the report can give test counts before and after.

## 1. What Phase 3 has to deliver (`docs/PLAN.md` phase table, `docs/UI_PLAN.md` task U1)

Scanner, registry, counts, sidebar and dashboard cards, the temporary entity page, the ARCH-12 name check, the
second host project `host-check/`, and the U1 UI (sidebar with search, groups, counts, user chip; dashboard stat
cards and group cards; empty states).

## 2. Open questions (asked before coding; they are the only blocking ones)

1. **`PageChrome` cannot carry the signed-in user.** The brief asks for a user chip at the bottom of the sidebar
   showing the signed-in username with Sign out. `PageChrome` has no user field and no "signed in" flag (D-38 says
   so explicitly). Proposal: add one optional field `user` (nullable, `null` when nobody is signed in).
2. **A count cannot be "unavailable".** `PageChrome.NavItem.count` and `DashboardView.EntityCard.count` are
   `long` and the constructors reject a negative value, so a failed count has no representation. Proposal: make
   both `Long`, where `null` means "unavailable".
3. **Two compile-time-only dependencies outside the pre-approved list** are needed to wire the scanner and the
   count service: `jakarta.persistence:jakarta.persistence-api` and `org.springframework:spring-orm` in
   `jpa-admin-autoconfigure` (both `provided`, versions from the Boot BOM). Without the first, no bean method in
   that module can name `EntityManagerFactory`; without the second, the count service cannot get a
   transaction-aware `EntityManager` (`SharedEntityManagerCreator`) and ARCH-5's `@Transactional(readOnly = true)`
   would be decoration only.
4. **`docs/prototype/admin-console.html` is not in the repository** (not in the working tree and not in any
   commit on any branch), although the brief names it as the visual reference. Proposal: build U1 from
   `docs/UI_PLAN.md` section 5 plus the four elements the brief names (sidebar groups with counts, stat cards,
   user chip, brand subtitle), as task U0 did, and leave the alignment pass for after the file is committed.

Everything below assumes the recommended answer; the change log records what was actually decided.

## 3. Steps

### Step 0: housekeeping
`CHANGELOG.md`: `[Unreleased]` becomes `[0.0.2] - 2026-11-10` (tag `v0.0.2` exists) and a fresh `[Unreleased]` is
added. Report what D-42 lists today.

### Step 1: annotations (`jpa-admin-core`, package `io.github.mdmusahub.jpaadmin.annotation`)
`@AdminHidden` (type and field, also a getter) and `@AdminSensitive` (field, also a getter), both
`@Retention(RUNTIME)`, `@Documented`, with Javadoc. `package-info.java` says this package is the public API.
`docs/RULES.md` ARCH-9 gets the sentence that this package is public, documented API.

### Step 2: properties (`jpa-admin-autoconfigure`)
`jpa.admin.excluded-entities` (list, empty by default) and `jpa.admin.sensitive-fields` (list, default
`password, secret, token, apikey`). Validated by the same plain code as the other properties: a blank or null entry
is rejected with a message that names the property and gives an example, and never prints a value that could be a
secret. Documented in `docs/PROJECT_DOCS.md` (DOC-2).

### Step 3: metadata and scanner (`jpa-admin-core`, new package `metadata`)
- `AdminEntity` (record): java type, url name, label, group (`Entities`), icon (`table`), id attribute,
  `unsupportedReason` (null when supported, so `supported()` is derived), fields.
- `AdminField` (record): name, label, java type, `Kind` (TEXT, NUMBER, BOOLEAN, ENUM, DATE, DATETIME, TO_ONE,
  TO_MANY, EMBEDDED, BINARY, OTHER), enum values, target entity, id, version, nullable, required, sensitive,
  hidden.
- `EntityScanner`: reads the `jakarta.persistence.metamodel.Metamodel` once at startup (ARCH-1) and builds the
  registry. URL name = lower-case simple name, every character outside `a-z0-9` becomes `-`; on a collision both
  entities get the lower-cased fully qualified name with `.` replaced by `-`; a name that still collides, or that
  is empty, makes both entities unsupported. Hidden (annotation or `excluded-entities`) entities and fields are
  kept out of the registry's lookups. Unsupported entities (`@EmbeddedId`, `@IdClass`, no addressable name) are
  logged once at WARN with names and reasons and are never reachable.
- `AdminEntityRegistry`: immutable, `find(urlName)` and `all()` in a stable order (group, label, class name).
- `SensitiveNames`: case-insensitive substring match, ignoring `_` and `-`, plus `@AdminSensitive`.

### Step 4: counts (`jpa-admin-core`, package `service`)
`CountService`: one Criteria `count` per entity, `@Transactional(readOnly = true)` (ARCH-5), cached for a 5-second
TTL (internal constant, injectable `Clock`). A failure gives an unavailable count, is logged without a stack
trace, and never breaks the page.

### Step 5: auto-configuration (`jpa-admin-autoconfigure`)
Scanner, registry and count service only when exactly one `EntityManagerFactory` is available
(`ObjectProvider.getIfUnique()`; several with no primary logs one WARN and registers no entities, and never fails
the host's startup). `@AutoConfiguration` gains the Boot 4 JPA auto-configuration in `afterName`
(`org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration`, checked against the 4.1.2 jar,
not memory). Beans are prefixed `jpaAdmin` and replaceable with `@ConditionalOnMissingBean` where they do not
enforce a rule.

### Step 6: controllers (`jpa-admin-core`)
- `ConsoleNavigation` (core): builds the sidebar groups and the dashboard groups from registry plus counts.
- `ConsoleChromeFactory`: fills `nav` and `activeEntity`, and takes flash messages.
- `ConsoleController.dashboard`: real `DashboardView` (total records, entities, groups, largest table,
  `chartAvailable` false).
- `EntityController`: `GET {path}/{entity}` resolves the name **only** through the registry (SEC-2). Known: 200,
  the entity active in the sidebar and the notice "This table view is not available yet.". Unknown or hidden: the
  console's own 404 page, identical in both cases. The path parameter is never echoed.

### Step 7: UI task U1 (`jpa-admin-ui`, `jpa-admin-ui-preview`)
Sidebar: brand with the subtitle "Administration", a client-side nav search (enhancement only), groups that
collapse (state in `localStorage` inside try/catch), entity items with icon and count (thousands separators,
"unavailable" when a count failed), active item, user chip with the signed-in name and the POST sign-out with CSRF.
Dashboard: stat cards, one card per group with its entities and counts, empty states for "no entities" (naming
`@AdminHidden` and `excluded-entities`) and for an empty database. New temporary template for the entity page.
Preview: the U1 fixtures of `docs/FIXTURES.md` plus the states the brief lists (long and unicode names, counts of
0 and very large counts, an unavailable count, an empty sidebar, the entity page).

### Step 8: ARCH-12 name check
`NameCheck` (a test-scope helper in `jpa-admin-core`, like `HtmlRules`) walks `jpa-admin-core`,
`jpa-admin-autoconfigure`, `jpa-admin-ui` and `jpa-admin-starter` (main and test, sources and resources) and fails
on a whole-word, case-sensitive hit for `Category`, `Product`, `Customer`, `Order` or `Tag` unless an allow-list
entry (one file, `docs/name-check-allow-list.txt`, each entry with path, line text and a written reason) matches.
An entry that matches nothing fails the check, and so does an entry without a reason. `NameCheckTest` runs the
real check and feeds the helper synthetic input, so a weakened check fails its own tests.

### Step 9: `host-check/`
Outside the reactor (not in the root `modules`), own pom with `spring-boot-starter-parent` 4.1.2, entities `Book`,
`Author` and `Publisher` (a to-one relation and a `secretToken` field), H2, seeded rows, the two required
properties in test resources, port 8090 when run by hand. Its test starts the app, signs in and asserts the
sidebar lists exactly those three entities with the right counts and nothing from the demo shop. README with the
exact PowerShell commands. CI runs `mvn -B -DskipTests install` for the reactor and then
`mvn -B -f host-check/pom.xml verify`.

### Step 10 to 12: tests, mutation checks, docs
As listed in the brief: scanner, registry, counts, pages, open-in-view true and false, HTML rules, preview
fixtures, host-check; the eleven mutation checks (a) to (k), each restored byte-identically (MD5); then
`docs/PLAN.md`, `docs/UI_PLAN.md`, `docs/DECISIONS.md`, `CHANGELOG.md`, `docs/PROJECT_DOCS.md` and
`docs/FIXTURES.md`.

## 4. Verification

Module builds after every step, `mvn -B clean verify` at the end, then the manual checks of the brief with
`curl.exe` against the real jar (sign-in, the five demo entities with counts, `/admin/product` 200,
`/admin/nothing` 404, and the same with `JPA_ADMIN_EXCLUDED_ENTITIES=Tag`), `host-check` by hand on port 8090,
processes stopped by PID and ports 8080, 8081 and 8090 free.
