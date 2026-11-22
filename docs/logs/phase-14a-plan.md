# Phase 14a plan: database portability, measurements and the open gaps

Branch `phase/14a-tests-and-perf` (from `main`, CI green). One session on both tracks (the recorded exception to AI-11, as Phases 2 to 14). Written 2026-11-22, **before any code**. Nothing is committed, tagged or published, and Phase 14b (publishing, the docs site, the tag) is not started. Every file and command is logged in `docs/logs/phase-14a-change-log.md`. Decisions start at **D-128** (the last one is D-127).

**Status: waiting for your approval of the dependencies in section 1. Nothing has been added to any `pom.xml` yet.** After you approve, I proceed through the steps in section 4 without waiting, and pause only for the four conditions in your brief.

## 0. What I found while reading (facts the plan rests on)

| Fact | Where it comes from |
|---|---|
| Docker Desktop is up: client and server 29.4.0, 12 CPUs, 8 GB for the VM. The local Docker has `hello-world` and `pgvector/pgvector:pg16` only, so both database images will be pulled once. | `docker version`, `docker info`, `docker images` |
| This machine: Windows 11, Temurin 17.0.20.1, Maven 3.10.0, Asia/Calcutta time zone. | `java -version`, `mvn -v` |
| Only `sample-app` has a surefire configuration (`reuseForks=false`, `redirectTestOutputToFile=true`). The other modules use the defaults. | the poms |
| `@Version` **already exists** in two core test entities, `Widget` and `Folio` (Phase 8, D-80), and the stale refusal is tested in core at service and controller level. What is missing is a **sample-app** test, because no sample entity has a `@Version`. | `grep -rln "@Version"` |
| The chart query reads only the creation column for a 90 day window, newest first, at most `rowCap + 1` (50,001) rows, and buckets them in Java (no database function). | `CreationDates` |
| The search is `lower(col) like ? escape '!'` over every visible, non-sensitive `String` attribute, OR-ed. Sorting uses `Nulls.LAST` both ways, then the identifier. A relation column is a `LEFT JOIN`. Export pages are 500 rows, one read-only transaction each. | `ListQueryService` |
| The existing browser driver looks for Chrome at `/usr/bin/google-chrome` among other places and starts it with `--headless=new`, no `--no-sandbox`. A missing browser is a skip with the reason, never a pass. | `HeadlessChrome.find()`, `ConsoleBrowser.SKIP_REASON` |
| The CI runner (`ubuntu-latest`, image 20261116.376.1) already has **Google Chrome 157.0.8103.78**, Docker server 28.0.4, Java 17.0.21 and 21.0.13, Maven 3.10.1. Chromium documents an AppArmor profile for Chrome installed at `/opt/google/chrome/chrome` (the packaged path). | `Ubuntu2404-Readme.md` on `actions/runner-images`, Chromium `apparmor-userns-restrictions.md` |
| Last local full run (Phase 13): core 31 s, autoconfigure 45 s, preview 1 min 47 s, sample app 3 min 18 s. CI has a 20 minute timeout and a Java 17 and 21 matrix. | Phase 13 report, `ci.yml` |

## 1. Dependencies to approve (nothing added until you say yes)

All **test scope**. Versions are the ones the Spring Boot 4.1.2 BOM pins (no version is written in our poms), checked in `~/.m2/.../spring-boot-dependencies-4.1.2.pom` and `testcontainers-bom-2.0.5.pom`, resolved in a throwaway project outside the repository, and every jar was opened with `javap` (CODE-8).

| Dependency | Version | Where verified | Released | Why it is needed |
|---|---|---|---|---|
| `org.testcontainers:testcontainers-postgresql` | 2.0.5 (BOM) | Boot BOM (`testcontainers.version` 2.0.5), `testcontainers-bom-2.0.5.pom`, `javap` of the jar: `org.testcontainers.postgresql.PostgreSQLContainer(String)`, `getJdbcUrl()`, `getUsername()`, `getPassword()`, `withUsername`, `withPassword`, `withDatabaseName` | 2026-04-20 | Starts a real PostgreSQL for the engine tests and the measurements |
| `org.testcontainers:testcontainers-mysql` | 2.0.5 (BOM) | same, `org.testcontainers.mysql.MySQLContainer(String)` | 2026-04-20 | Starts a real MySQL |
| `org.postgresql:postgresql` | 42.7.14 (BOM, `postgresql.version`) | Boot BOM; Maven Central listing | 2026-10-07 | The JDBC driver the test connects with |
| `com.mysql:mysql-connector-j` | 9.7.0 (BOM, `mysql.version`) | Boot BOM; Maven Central listing | 2026-04-22 | The JDBC driver the test connects with |

**Where they go:** `jpa-admin-core` (the engine scenarios and the measurements) **and** `sample-app` (one small full-stack check of the demo application on each engine, section 3 item 1b). The preview, autoconfigure, ui and starter modules get nothing. Nothing reaches `host-check` or any published jar (`test` scope, and the Enforcer rule on the preview module is untouched).

**Transitive jars** (test scope, from `mvn dependency:tree`): `testcontainers` 2.0.5, `testcontainers-jdbc` 2.0.5, `testcontainers-database-commons` 2.0.5, `docker-java-api` 3.7.1, `docker-java-transport` 3.7.1, `docker-java-transport-zerodep` 3.7.1, `jna` 5.18.1, `commons-compress` 1.28.0, `commons-codec` 1.21.0, `commons-io` 2.20.0, `commons-lang3` 3.20.0, `duct-tape` 1.0.8, `org.jetbrains:annotations` 17.0.0, `jackson-annotations` 2.21, `slf4j-api` 2.0.20, `checker-qual` 3.55.1. All were published at least 14 days ago (the youngest is 2026-04-03). No `junit:junit` (JUnit 4) comes in.

**Not proposed, on purpose:**

- `org.testcontainers:testcontainers-junit-jupiter` (2.0.5, same BOM, same date; you listed it). Its `@Testcontainers(disabledWithoutDocker = true)` skips **without a reason and without a way to fail on CI**, which is exactly mutation (d). I start and stop the container in `@BeforeAll` / `@AfterAll` of one shared base class, so the rule "no Docker: skip with the reason on a laptop, **fail** on CI" is mine and is tested. **Say "add it anyway" if you want the extension too**; it would be one more line per module.
- `spring-boot-testcontainers` (`@ServiceConnection`): not needed, the base class passes the URL itself.

**Docker images** (tags, not Maven dependencies, pinned by their push date on Docker Hub): `postgres:17.11` (pushed 2026-11-06, 16 days) and `mysql:8.4.11` (pushed 2026-10-19, 34 days). Newer tags (`17.12`, `8.4.13`) are younger than 14 days and are not used. Both can be overridden with `-Djpa.admin.test.postgres-image=...` / `-Djpa.admin.test.mysql-image=...` to try another version. The support table in `PROJECT_DOCS.md` will say exactly which versions ran.

**Credentials:** user name and password of each container are random per run (generated in the test, `withUsername` / `withPassword`), never written to a file, a log or a message. No secret is ever in the repository.

## 2. Decisions I take unless you say otherwise (each is one line to veto)

| # | Decision | Why |
|---|---|---|
| a | **Docker missing: skip with the reason on a laptop, fail on CI.** `JPA_ADMIN_REQUIRE_ENGINES=true` (set in `ci.yml`) turns "no Docker" into a failure. Locally the skip text names the engine, the reason and the exact command to run elsewhere. The browser tests get the same rule with `JPA_ADMIN_REQUIRE_BROWSER=true`. | Your brief: do not skip silently; a skip prints its reason. The Phase 14 scope file recommended the same ("require it on the release job, skip with a reason locally"). |
| b | **The stale-form sample-app test uses a test-only entity in `sample-app/src/test`**, in a package outside the demo's entity scan (`...jpaadmin.versiontest`), registered only by that one test's context. | A core test entity cannot be used from `sample-app` without a `test-jar`, and that would put core's `persistence.xml` (four persistence units) on the sample app's classpath, where Spring's persistence unit manager reads `classpath*:META-INF/persistence.xml`. The core entities `Widget` and `Folio` stay as they are, and `Folio` is also used by the engine scenarios, so the stale refusal runs on all three engines. No sample entity gets a `@Version`. |
| c | **The orphan report is an internal class in core main** (`storage/OrphanUploadReport`, "internal, not stable API" like its neighbours), with no bean, no route, no property and **no method that deletes**. Tested in core and through the sample app. | A report that only exists in test sources cannot be the "documented command" and cannot be reused when you decide about cleanup. No public API changes. Veto = I move it to test sources. |
| d | **CI:** the two `JPA_ADMIN_REQUIRE_*` variables, one added step that prints test, skip and failure counts per module and **every skip reason** (read from the surefire XML, `if: always()`), and `timeout-minutes` raised from 20 to 30 **only if** the measured build needs it (I will show the number). No browser-download action; the runner's own Chrome is used. | Items 4 and 1. A step with `grep` is not a dependency. |
| e | **No `--no-sandbox`** is added to the Chrome driver. I cannot run a Linux runner from here, so the first CI run is the proof; if Chrome fails to start there, the build fails loudly (rule a) and I report it. | Chromium's documentation says the packaged Chrome has an AppArmor profile; weakening the sandbox on a guess is not mine to do. |
| f | **Engine behaviour differences are asserted, not hidden.** Where an engine differs (case mapping of non-ASCII letters, text collation, `lower()` on a large-text column, date and time precision, `BigDecimal` scale, boolean storage), the test says what each engine does and the difference goes into `docs/PROJECT_DOCS.md`. If a difference is a **defect in the product** (a list that answers 500 on one engine), I stop and ask, as you asked, and do not change behaviour on my own. | Your brief, scope item 1. |

## 3. What gets built (the nine scope items)

### 1. Database portability

- **1a. Core, package `io.github.mdmusahub.jpaadmin.engines` (test sources).** `EngineGate` (the pure decision: run, skip with a reason, or fail), `EngineDatabase` (H2, PostgreSQL, MySQL: start once per test class, random credentials, `select version()` for the report), `EnginePersistence` (the `engine-test` persistence unit with the URL set at run time; neutral entities from `listmodel` plus one new `@Lob String` entity, so the large-text question is really asked), `AbstractEngineScenarios` (the shared scenarios), `H2EngineTest`, `PostgreSqlEngineTest`, `MySqlEngineTest` (each only names its database), and an extension that puts **the engine's name and version in front of every failure message**.
  Shared scenarios (each is a `@Test`): list pages and the last-page clamp; default page size 50 once; search (case, `%` `_` `!` literal, non-ASCII, `length=4000`, `length=20000` and `@Lob` text columns, no match, search plus filter); sort on text, number, boolean, date, date-time, enum and relation label, ascending and descending, **nulls last both ways**, the **identifier tie-breaker** (no row twice and none missing across pages when many rows tie); every filter kind and two filters together; export (more than one page of 500, equal to the list, the cap, formula cells); detail; create and edit (values round-trip); **stale version refused with the plain message** on `Folio`; required and unique violations answered in the console's words with no SQL; delete, delete of a record in use (foreign key) and bulk delete rolling back as a whole; the chart query (`CreationDates`: days, the cap flag, a zone other than UTC); hidden columns drop their joins (the recorded SQL).
- **1b. Sample app, one full-stack check per engine.** The real demo application started against the container (`@DynamicPropertySource`), with the demo data, then: the dashboard (with the chart), every entity's list, a search, a sort on every sortable column, the CSV export, one detail page, and create, edit and delete of a record through the real POST routes with CSRF. Same gate, same failure rule. If this turns out to cost more than it finds, I say so and keep only 1a.
- **Negative tests:** `EngineGateTest` (no Docker plus CI must fail; no Docker plus a laptop must skip **with a non-empty reason that names the engine and the command**; Docker present must run); `EngineFailureNamesEngineTest`.

### 2. Performance measurements (test-only profile, never in a normal build)

- **Where:** `jpa-admin-core/src/test/java/.../perf`, every class `@Tag("perf")` **and** `@EnabledIfSystemProperty(named = "jpa.admin.perf", matches = "true")`. Core's default surefire configuration gets `<excludedGroups>perf</excludedGroups>`; the Maven profile `perf` (no `<activation>`) switches to `<groups>perf</groups>` and sets the property. Command: `mvn -B -pl jpa-admin-core -Pperf test`.
- **Data:** `PerfItem` (200,000 rows: title, a longer text, code, amount, status, `createdAt` spread over 400 days, three to-one relations) and three small target tables (`PerfOwner`, `PerfGroup`, `PerfPlace`, 2,000 rows each, with a `name` label). Loaded with JDBC batches, so loading is not what is measured. On H2 (in memory) and PostgreSQL (container). MySQL is not measured (you listed H2 and PostgreSQL).
- **Measured** (2 warm-ups, 7 timed runs, the median, minimum and maximum are recorded): first, middle and last list page, with and without a search; the search (`lower(title) like '%text%'`) on one text column; the same list with **one, two and three relation columns shown** (the other relation columns hidden with `hide`, D-113); the chart read on the creation column **under** the cap (a 90 day window, about 45,000 rows) and **over** it (400 days, 200,000 rows, so the cap of 50,000 cuts), plus a `GROUP BY` day query as a comparison for item 3; `count(*)`; the export of 50,000 rows (101 statements, 100 pages of 500 and the count); and, on PostgreSQL only, the same search after a `pg_trgm` index, for the index advice.
- **Bounds:** each measurement has an explicit upper bound that flags a regression: **5 times the median of the first measured run, rounded up to a round number**, set after that run and said so in the file. They are not pass or fail tests of speed in the build (the profile never runs there).
- **Record:** `docs/logs/phase-14a-performance.md` with the machine (Windows 11, 12 logical CPUs, Temurin 17.0.20.1, Docker Desktop VM 8 GB, so the PostgreSQL numbers include the Docker Desktop VM and are not a production server), the JDK, the engine versions and the table.
- **Guard test:** `PerfProfileIsOffByDefaultTest` reads the pom and fails if the `perf` profile is active by default, if `excludedGroups` no longer names `perf`, or if a perf class lacks the tag or the property gate; and it asserts that the property is not set in this JVM.

### 3. Chart cap

From the numbers above I write the options with their cost (keep the Java bucketing; move the count to a `GROUP BY` for `LocalDate` and `LocalDateTime` columns only; lower or raise the cap; keep and document) in `docs/logs/phase-14a-performance.md`, with a recommendation. **No chart behaviour changes without your approval.**

### 4. Browser tests in CI

Check the workflow; make a missing Chrome a **failure on CI** (`JPA_ADMIN_REQUIRE_BROWSER=true`) and a **skip with a printed reason** elsewhere, through one helper that every browser test class uses (today each class reaches `HeadlessChrome.find()` its own way); print the counts and skip reasons in the CI log; use the runner's own Chrome (157.0.8103.78 in the current image); record how many browser tests ran and skipped locally (30 ran in Phase 13) and what CI will show. I can only run Windows locally, so **the first real Linux run happens on GitHub after you push**; the report will say so and will not claim a CI pass.

### 5. Stale version

New sample-app test `StaleFormPagesTest` with a test-only entity (decision b): open the edit form, change the record behind its back (a second transaction), post the stale form, expect 422, the plain sentence, no stack trace, nothing saved, and a second try with the same stale form refused too (D-80). Plus the engine scenario on `Folio` (item 1).

### 6. Orphan upload report (read only)

`OrphanUploadReport` (decision c): walks the local storage folder without following links, turns each file into the address the provider would have written for it, reads the addresses held by every upload field of every entity (one Criteria query per field, `select distinct`, names from the metadata, SEC-3), and lists the files no record holds, with sizes. It counts but does not list files that are not in the console's key shape (a probe file, a file placed by hand). Tests: orphans listed and referenced files not listed; **every file still exists afterwards**; a record that holds a file twice; an empty storage; a storage folder that does not exist; a link inside the folder is not followed. The documented command is `mvn -pl jpa-admin-core test -Dtest=OrphanUploadReportTest` (and the same through the sample app). Deleting is your Phase 14 decision and no code path here can do it.

### 7 and 8. Options, no implementation

`docs/logs/phase-14a-options.md`: (7) the relation picker for a target over 200 records (searchable picker, id box, lazy list) with the cost of each (new route, tests, script, no-script behaviour, SQL, accessibility); (8) the input border contrast measured from the tokens in both themes and cross-checked in the real browser (light 1.52, dark 1.67 in Phase 13; I recompute), the options (leave; darken `--line-strong` for controls only; a thicker or double border; a different surface) with the colour each needs to reach 3 to 1 and **which controls on which pages change**. No colour is changed.

### 9. Trace output

`reuseForks=false` and `redirectTestOutputToFile=true` are in `sample-app/pom.xml`. I keep them, add `BuildSettingsGuardTest` (reads the pom, fails if either is missing; mutation e), and record evidence: the full CI-like run's console log is searched for row values and bound parameters (none expected), and the same values are shown to be in `target/surefire-reports/*-output.txt` (so the redirect works). The other modules do not redirect; the evidence covers their output too, and if any prints row values I stop and ask.

## 4. Steps (a module build after each)

0. This plan and the log (done). **Stop for the dependencies.**
1. Poms: the four test dependencies in core and sample-app; `mvn -B -pl jpa-admin-core,sample-app -am -DskipTests verify` to prove nothing else changed.
2. `EngineGate`, `EngineDatabase`, the extension and their negative tests (no container needed).
3. The `engine-test` persistence unit, the entities, `AbstractEngineScenarios`, `H2EngineTest` first (fast), then PostgreSQL, then MySQL. Differences found are written down as they appear.
4. The sample-app engine check (1b).
5. Stale form sample test (5).
6. Orphan report and tests (6).
7. Browser helper and CI (4).
8. Perf profile, guard test, first run, bounds, `phase-14a-performance.md` (2, 3).
9. Options file (7, 8). Trace evidence (9).
10. Docs: `PLAN.md` (Phase 14 row, only what passed), `DECISIONS.md` from D-128, `CHANGELOG.md` under `[Unreleased]`, `PROJECT_DOCS.md` (database support table, measured limits, engine differences, replace the three "untested on PostgreSQL and MySQL" notes).
11. Mutation checks (a) to (e), one at a time, MD5 compared:

| | Break | The test that must fail |
|---|---|---|
| (a) | `RecordWriteService` lets a stale version save | `StaleFormPagesTest`, the `Folio` scenario on each engine, the existing core stale tests |
| (b) | `OrphanUploadReport` deletes the orphan it finds | `OrphanUploadReportTest` (the file must still exist) |
| (c) | the `perf` profile gets `<activeByDefault>true</activeByDefault>` (and, second run, `excludedGroups` removed) | `PerfProfileIsOffByDefaultTest` |
| (d) | `EngineGate` skips silently (no reason) even where Docker is missing on CI, and a second form: the base class always aborts | `EngineGateTest` |
| (e) | `redirectTestOutputToFile` removed from `sample-app/pom.xml` | `BuildSettingsGuardTest` |

12. Final checks: `mvn -B clean verify`, `mvn -B -DskipTests install`, `mvn -B -f host-check/pom.xml verify`; every process I start is stopped by PID; ports 8080, 8081 and 8090 are free at the end. The report goes to `docs/logs/phase-14a-report.md`.

## 5. Files expected to change

New: the plan, the log, the performance file, the options file, the report (docs/logs); core test classes (`engines/*`, `perf/*`, `OrphanUploadReportTest`, the new entities, the `engine-test` unit in `persistence.xml`); core main `storage/OrphanUploadReport`; sample-app tests (`StaleFormPagesTest`, the engine check, `BuildSettingsGuardTest`, the browser helper); one `ci.yml` edit. Edited: the root pom is **not** touched (versions come from the BOM); `jpa-admin-core/pom.xml`, `sample-app/pom.xml`, the browser test classes that call the driver, `ci.yml`, `PLAN.md`, `DECISIONS.md`, `CHANGELOG.md`, `PROJECT_DOCS.md`. No template, CSS, script, view-model record, public annotation or property changes.
