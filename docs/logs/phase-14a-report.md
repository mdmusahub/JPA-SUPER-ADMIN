# Phase 14a report: database portability, measurements and the open gaps

Branch `phase/14a-tests-and-perf`, from `main`. One session on both tracks (the recorded exception to AI-11, as Phases 2 to 14, D-128). **Nothing is committed, tagged or published; Phase 14b is not started.** Plan: `docs/logs/phase-14a-plan.md`. Every file and command: `docs/logs/phase-14a-change-log.md` (78 entries). Decisions: **D-128 to D-134**. Numbers: `docs/logs/phase-14a-performance.md`. Options for items 7 and 8: `docs/logs/phase-14a-options.md`.

**Result in one paragraph.** The same 69 list, search, sort, filter, export, detail, create, edit, stale-form, delete and chart scenarios, and a start of the whole demo application, run on **H2 2.4.240, PostgreSQL 17.11 and MySQL 8.4.11** and all pass; the runs found four real differences between the engines and one limit that holds on all three (an entity with a `@Lob String` cannot be searched), none of which I changed. A profile that never runs in a normal build measured 200,000 rows on H2 and PostgreSQL; the chart cap needs no database function. The browser tests and the engine tests now fail on CI when Docker or Chrome is missing and are skipped with their reason on a laptop; the full build, run exactly as CI will run it (both require variables set), has **30 browser tests and 225 engine tests, none skipped**. The full `mvn -B clean verify`, `mvn -B -DskipTests install` and `mvn -B -f host-check/pom.xml verify` are green. The one thing I could not do is run the workflow on a Linux runner: **that first run on GitHub is still open.**

## What you must decide (the short list)

1. **`@Lob String` search (a finding, D-134).** On all three databases a search on an entity with a `@Lob String` attribute fails (Hibernate refuses `lower(...)` on a CLOB before the database is asked). The list without a search works. Options: leave it documented (what is written now); leave `@Lob` attributes out of the search and say so in the empty state (a small change in `ListQueryService`, a behaviour change I did not make); or cast the column. **Recommendation: leave `@Lob` attributes out of the search.**
2. **Chart cap (item 3).** Recommendation: **no database function**; keep the Java count and the 50,000 cap (31 to 37 ms per entity at the cap on PostgreSQL, cached 5 seconds). No chart behaviour changed.
3. **Relation picker for a target over 200 records (item 7):** id box for 0.1, searchable picker for 0.2; needs approval of a `FormView` contract change. Note that a **required** relation to such a target cannot be set, so such a record cannot be created in the console today.
4. **Input border contrast (item 8):** 1.52 to 1 light and 1.67 to 1 dark, confirmed in a real browser; recommendation a `--control-line` token (light #8b8f94, dark #666c75, both 3 to 1 or more). No colour changed.
5. **Orphan upload files:** the report exists and is read-only; whether to add a cleanup is yours (Phase 14).
6. **CI timeout:** I set `timeout-minutes` to 45 (the build takes 19 minutes on a 12-core laptop); correct it after the first real run.
7. **`--no-sandbox`:** not added (Chromium documents an AppArmor profile for the runner's Chrome). If Chrome cannot start on the runner the build fails loudly; then it comes to you.

## 1. Files

`git status --short`:

```
 M .github/workflows/ci.yml
 M CHANGELOG.md
 M docs/DECISIONS.md
 M docs/PLAN.md
 M docs/PROJECT_DOCS.md
 M jpa-admin-core/pom.xml
 M jpa-admin-core/src/test/resources/META-INF/persistence.xml
 M sample-app/pom.xml
 M sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/ConsoleBrowser.java
 M sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/ListLayoutBrowserTest.java
 M sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/SavedViewsBrowserTest.java
?? .github/scripts/
?? docs/logs/phase-14a-change-log.md
?? docs/logs/phase-14a-options.md
?? docs/logs/phase-14a-performance.md
?? docs/logs/phase-14a-plan.md
?? docs/logs/phase-14a-report.md
?? jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/storage/OrphanUploadReport.java
?? jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/buildguard/
?? jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/engines/
?? jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/perf/
?? jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/storage/OrphanUploadReportTest.java
?? jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/testsupport/enginemodel/
?? jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/testsupport/perfmodel/
?? rules.txt
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/AbstractSampleEngineTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/AbstractStaleFormPagesTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/BrowserPolicy.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/BrowserPolicyTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/BuildSettingsGuardTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/H2SampleEngineTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/MySqlSampleEngineTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/OrphanUploadReportPagesTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/PostgreSqlSampleEngineTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/SampleEngineDatabase.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/SampleEngineGate.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/SampleEngineGateTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/SampleEngineSkipLedger.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/StaleFormPagesOpenInViewDisabledTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/sample/StaleFormPagesOpenInViewEnabledTest.java
?? sample-app/src/test/java/io/github/mdmusahub/jpaadmin/versiontest/
```

`git diff --stat` (tracked files; new files are not counted):

```
 .github/workflows/ci.yml                           | 17 ++++-
 CHANGELOG.md                                       |  6 ++
 docs/DECISIONS.md                                  |  7 ++
 docs/PLAN.md                                       | 21 ++++-
 docs/PROJECT_DOCS.md                               | 46 +++++++++--
 jpa-admin-core/pom.xml                             | 89 ++++++++++++++++++++++
 .../src/test/resources/META-INF/persistence.xml    | 51 +++++++++++++
 sample-app/pom.xml                                 | 25 ++++++
 .../mdmusahub/jpaadmin/sample/ConsoleBrowser.java  | 14 ++--
 .../jpaadmin/sample/ListLayoutBrowserTest.java     |  5 +-
 .../jpaadmin/sample/SavedViewsBrowserTest.java     |  5 +-
 11 files changed, 259 insertions(+), 27 deletions(-)
```

`rules.txt` (one line, a text-replace rule) was untracked before this phase and is **not mine**; it is left alone and should not go into any commit below.

| Area | Created | Edited |
|---|---|---|
| Build | `.github/scripts/report-tests.sh` | `jpa-admin-core/pom.xml` (4 test dependencies, default surefire exclusion and redirect, the profile `perf`), `sample-app/pom.xml` (4 test dependencies), `.github/workflows/ci.yml` |
| Core main | `storage/OrphanUploadReport` | |
| Core tests | `engines/` (15 classes), `perf/` (5), `buildguard/` (2), `storage/OrphanUploadReportTest`, `testsupport/enginemodel/` (`Essay`, `Tome`), `testsupport/perfmodel/` (5) | `META-INF/persistence.xml` (units `engine-test` and `perf-test`) |
| Sample app tests | `AbstractSampleEngineTest`, `SampleEngineGate`, `SampleEngineDatabase`, `SampleEngineSkipLedger`, three engine classes, `SampleEngineGateTest`, `AbstractStaleFormPagesTest` and two subclasses, `versiontest/Tally`, `OrphanUploadReportPagesTest`, `BrowserPolicy`, `BrowserPolicyTest`, `BuildSettingsGuardTest` | `ConsoleBrowser`, `ListLayoutBrowserTest`, `SavedViewsBrowserTest` (they ask `BrowserPolicy`; nothing weakened) |
| Docs | `docs/logs/phase-14a-plan.md`, `-change-log.md`, `-performance.md`, `-options.md`, `-report.md` | `docs/PROJECT_DOCS.md`, `docs/DECISIONS.md` (D-128 to D-134), `docs/PLAN.md`, `CHANGELOG.md` |
| Deleted | nothing (two throwaway probe tests were created and removed inside the phase, entries 28 and 56) | |

No template, CSS, script, view-model record, public annotation, property or route changed. **No dependency other than the four approved ones.**

## 2. Tests

| Module | Before (Phase 13) | After | Change |
|---|---|---|---|
| jpa-admin-core | 4033 (1 skipped) | **4280** (2 skipped) | +247 |
| jpa-admin-autoconfigure | 581 | **581** | 0 |
| jpa-admin-ui-preview | 2788 | **2788** | 0 |
| sample-app | 1039 (0 skipped) | **1093** (0 skipped) | +54 |
| reactor total | 8441 | **8742** | +301 |
| host-check (outside the reactor) | 37 | **37** | 0 |

The full `mvn -B clean verify` was run **with `JPA_ADMIN_REQUIRE_ENGINES=true` and `JPA_ADMIN_REQUIRE_BROWSER=true`, as CI will run it**, so a missing Docker or Chrome would have failed it. Results of the last run, after the final edit:

```
[INFO] Tests run: 4280, Failures: 0, Errors: 0, Skipped: 2
[INFO] Tests run: 581, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 2788, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 1093, Failures: 0, Errors: 0, Skipped: 0
[INFO] Reactor Summary for JPA Super Admin 0.1.0-SNAPSHOT:
[INFO] JPA Super Admin .................................... SUCCESS [  0.401 s]
[INFO] JPA Admin Core ..................................... SUCCESS [01:10 min]
[INFO] JPA Admin UI ....................................... SUCCESS [  0.171 s]
[INFO] JPA Admin Autoconfigure ............................ SUCCESS [ 47.482 s]
[INFO] JPA Admin UI Preview ............................... SUCCESS [01:50 min]
[INFO] JPA Super Admin Spring Boot Starter ................ SUCCESS [  0.009 s]
[INFO] JPA Admin Sample App ............................... SUCCESS [15:26 min]
[INFO] BUILD SUCCESS
[INFO] Total time:  19:15 min
```

- **`mvn -B -DskipTests install`: BUILD SUCCESS (6.6 s). `mvn -B -f host-check/pom.xml verify`: BUILD SUCCESS, 37 tests, 0 failed (22.7 s).**
- **Ran, skipped, CI-only.** 30 real-browser tests ran, 0 skipped (Chrome 157.0.8103.52 on this machine, through the existing JDK-only driver); 225 engine tests ran on H2, PostgreSQL and MySQL, 0 skipped. The only skips are the two symbolic-link tests in `jpa-admin-core` (`LocalStorageProviderTest` and my `OrphanUploadReportTest`): this Windows account cannot create symbolic links, and the Linux runner will run both (my report class also has a directory-junction variant that did run here). **What I could not run: the workflow on a Linux runner, so no claim is made about CI passing.** What CI will show, from the same script: `bash .github/scripts/report-tests.sh`.
- **The perf profile is outside the normal build** (not counted above): `mvn -B -pl jpa-admin-core -Pperf test` ran 8 measurements per engine, 16 in all, under their bounds.
- **ARCH-12 name check and host isolation** (`NameCheckTest`, `HostIsolationTest`) are part of the green run; **`host-check` is green** (37 tests, no library change).
- **Running the engine tests alone:** `mvn -B -pl jpa-admin-core test -Dtest='H2EngineTest,PostgreSqlEngineTest,MySqlEngineTest'` and `mvn -B -pl sample-app -am test -Dtest='*SampleEngineTest' -Dsurefire.failIfNoSpecifiedTests=false`. Without Docker: skipped with the reason and the exact command; with `JPA_ADMIN_REQUIRE_ENGINES=true`: a failure.
- **Build time.** The full verify takes about 19 minutes here: the sample app is 15 of them because each of its 67 test classes starts its own JVM and Spring context (`reuseForks=false`, added after the Phase 13 report; its 3:18 was measured before). My additions cost about 3 minutes of that (the three engine classes and their containers, the stale-form and orphan tests). Parallel forks would shorten it; I did not change that.

### Evidence for item 9 (trace output)

- `sample-app/pom.xml` has `reuseForks=false` and `redirectTestOutputToFile=true`, and `BuildSettingsGuardTest` fails the build if either goes (mutation e).
- **I searched the full console log of a CI-style run for row values and bound parameters.** The first run **found something**: 76 TRACE and DEBUG lines from `jpa-admin-core`, 6 of them bound or extracted values (a made-up `Dossier` with a random file address and the title `T`), written by `UploadFormTest.aTicketIsNeverLoggedWhileFormsAreRenderedAndSaved`, which sets the log level to TRACE on purpose. Core had no output redirect. **Fixed at the cause:** `redirectTestOutputToFile=true` in core's surefire configuration, with `TestOutputGoesToFilesTest` guarding it (mutation e3: removed, 1 failure, restored byte for byte).
- The last run's log (3,331 lines): **0 TRACE or DEBUG lines and 0 bound or extracted values.** None of the demo data (`Paperback novel`, `Ava Stone`, `@example.com`, `SKU-0`, `Gift wrap please`, `Demo category`), the test secrets (`hunter2`, `BADGE-SECRET`, `DEPOT-KEY-SECRET`, `memo-secret`, `typed-secret`, `old-secret-value`) or the console's test password appears. In `sample-app/target/surefire-reports/*-output.txt` the demo values are present (4 files each), so the redirect works.
- Container credentials are made up per run (`withUsername`, `withPassword`) and appear nowhere; Testcontainers logs the JDBC URL (host, random port, database name), never a credential.

## 3. Mutation checks (each: break, see a test fail, restore, MD5 identical, green again; details with MD5s in change-log entries 60 to 64 and 68)

| | Break | What failed | Restore |
|---|---|---|---|
| (a) | `RecordWriteService`: a stale version saves (`if (false)`) | core **6** (`aStaleFormIsRefusedWithAPlainMessage...` and `aFormWithNoVersion...IsStaleToo`, each in `RecordWriteServiceTest`, `H2EngineTest`, `PostgreSqlEngineTest`); demo app **5** (`StaleFormPagesOpenInViewDisabledTest`) | md5 `ac45045e...` identical; green |
| (b) | `OrphanUploadReport` deletes each orphan it finds | core 1 (`everyFileIsStillThereWithTheBytesItHadAfterTheReport`), demo app 1 (`theReportDeletesNothingEveryFileIsStillThereWithItsBytes`) | md5 `f7029d5f...` identical; green |
| (c) | the profile `perf` gets `activeByDefault`; second form: the default build stops excluding `**/perf/**` | `PerfProfileIsOffByDefaultTest` 2 failures, then 1 | md5 `fd625568...` identical both times; green |
| (d) | an engine skipped silently: (d1) a missing Docker skipped where CI requires it; (d2) every core engine scenario aborted with Docker present; (d3) the same in the demo-app check | `EngineGateTest` 2; (d2) `H2EngineTest` and `PostgreSqlEngineTest`: 69 skipped **and the class fails** with "[engine] 69 scenario(s) were skipped although the engine started and Docker is available"; (d3) 6 skipped and the class fails | md5 `2650416d...`, `1222d233...`, `734ce858...` identical; green |
| (e) | `redirectTestOutputToFile` removed from `sample-app/pom.xml` (and, extra, from `ci.yml`'s browser variable and from core's pom) | `BuildSettingsGuardTest` 1 (`expected "true" but was null`); 1 for the CI variable; `TestOutputGoesToFilesTest` 1 | md5 `f128e556...`, `186a91e7...`, `546e04ac...` identical; green |

Mutation (d) led to a **new guard that was not in the plan**: the gate decides what a missing Docker means, but a skip from anywhere else (a stray `assumeTrue(false)`) with Docker running would have left every scenario skipped and the build green. `EngineSkipLedger` closes that, and (d2) proves it.

## 4. Performance and the chart cap

Machine: Windows 11 Pro, 12 logical CPUs, Temurin 17.0.20.1, Docker Desktop 29.4.0 (8 GB VM); H2 2.4.240 and PostgreSQL 17.11 (container). 200,000 rows, page size 25, median of 7 runs. **Run 1 / run 2** shown where useful; the full tables, the method and the two traps (H2 result reuse, a warm PostgreSQL) are in `docs/logs/phase-14a-performance.md`. Bounds: 5 times the median of the first run, rounded up to 10 ms, at least 50 ms, **set from the first measured run**.

| Measurement | H2 ms | PostgreSQL ms |
|---|---:|---:|
| List first / middle / last page, no search | 3 / 66 to 72 / 137 to 139 | 21 to 28 / 73 to 80 / 118 to 126 |
| List first page, search 'zebra' (200 matches) | 88 to 90 | 170 to 174 |
| List first page, search 'item' (every row) | 86 to 115 | 153 to 257 |
| List last page, search 'item' | 362 to 367 | 613 to 619 |
| First page sorted by an unindexed column | 169 to 179 | 76 to 207 |
| Relation columns shown 0 / 1 / 2 / 3, **middle page** | 6.5 / 25 to 42 / 73 to 84 / 90 to 112 | 34 / 43 / 56 to 58 / 71 to 79 |
| Relation columns shown 0 / 1 / 2 / 3, first page | 1 to 8 | 19 to 26 (no visible difference) |
| `count where lower(title) like '%zebra%'` | 25 to 31 | 27 to 110 |
| `count(*)` of the whole table | 0 | 11 to 15 |
| Chart read, 90 days (44,751 rows, under the cap) | 22 to 58 | 31 to 33 |
| Chart read at the cap (50,000 of 200,000 rows) | 37 to 79 | 35 to 37 |
| Chart alternative: `group by` day (90 / 400 days) | 14 to 21 / 34 to 36 | 17 to 18 / 26 to 27 |
| Export of 50,000 rows (a count and 100 pages) | 2.0 to 2.2 s | 2.2 s |
| PostgreSQL with 3 trigram indexes: rare search page / count | | 16 to 63 / 1.6 to 7 (was 170 / 28 to 110) |

**What it means.** A page costs little at the start and more the deeper it is, because the database joins the rows it skips; the join cost per relation column is about 12 ms at offset 100,000 on PostgreSQL and invisible on a first page. At this size no batch query is needed, and hiding relation columns (Phase 12) helps deep pages. The search is a scan (150 to 620 ms on PostgreSQL with three searched columns); a `pg_trgm` GIN index **per searched column** makes a rare search about 10 times faster and does not help a word that is in every row; MySQL has no equivalent. Sorting by an unindexed column sorts everything (75 to 210 ms). The export of 50,000 rows takes about 2.2 s.

**The chart cap: no database function is needed.** The read is 31 to 37 ms per entity at the cap on PostgreSQL and is cached for 5 seconds. A `group by` saves about 15 ms, is exact only for `LocalDate` and `LocalDateTime`, and would need a second code path per database; raising the cap grows the cost in proportion; lowering it makes more tables "incomplete". Options with numbers: `docs/logs/phase-14a-performance.md`, section 6. **Nothing changed.**

## 5. Dependencies (approved; test scope; `jpa-admin-core` and `sample-app` only)

| Dependency | Version (Boot 4.1.2 BOM) | Verified | Released |
|---|---|---|---|
| `org.testcontainers:testcontainers-postgresql` | 2.0.5 | `spring-boot-dependencies-4.1.2.pom` (`testcontainers.version`), `testcontainers-bom-2.0.5.pom`, `javap` of the jar (`org.testcontainers.postgresql.PostgreSQLContainer`), Maven Central listing | 2026-04-20 |
| `org.testcontainers:testcontainers-mysql` | 2.0.5 | same (`org.testcontainers.mysql.MySQLContainer`) | 2026-04-20 |
| `org.postgresql:postgresql` | 42.7.14 | BOM (`postgresql.version`), Maven Central listing | 2026-10-07 |
| `com.mysql:mysql-connector-j` | 9.7.0 | BOM (`mysql.version`), Maven Central listing | 2026-04-22 |

Transitive (test scope, all more than 14 days old): `testcontainers`, `-jdbc`, `-database-commons` 2.0.5; `docker-java-api`, `-transport`, `-transport-zerodep` 3.7.1; `jna` 5.18.1; `commons-compress` 1.28.0; `commons-codec` 1.21.0; `commons-io` 2.20.0; `commons-lang3` 3.20.0; `duct-tape` 1.0.8; `org.jetbrains:annotations` 17.0.0; `jackson-annotations` 2.21; `slf4j-api` 2.0.20; `checker-qual` 3.55.1. The demo jar carries none of them. **Not added, as agreed: `testcontainers-junit-jupiter`.** Images: `postgres:17.11` (pushed 2026-11-06) and `mysql:8.4.11` (2026-10-19), pinned by Docker Hub push date. One API correction from checking the jar: `@EntityScan` is `org.springframework.boot.persistence.autoconfigure.EntityScan` in Boot 4.1.2, not the Boot 3 package I first wrote from memory (CODE-8).

## 6. Findings, deviations and honest limits

- **Findings (D-134), nothing changed:** `@Lob String` search fails on every database; a sort by it is by creation order on PostgreSQL; a moment with an offset comes back with the offset on H2 and in UTC on PostgreSQL and MySQL; text order follows the collation; the official PostgreSQL image refuses the JVM zone alias `Asia/Calcutta` (an environment trap, documented). All in `docs/PROJECT_DOCS.md` ("Database support").
- **Things the work found in my own first versions, all fixed with a test:** a skip taken in `@BeforeAll` was invisible ("0 tests run"); the gate could not see a skip from anywhere else (the ledger); my first H2 measurements measured H2's result cache and were thrown away; my first inline report step in `ci.yml` broke on quoting and became a script; core's test output reached the CI console (item 9).
- **Deviations from the plan, each flagged in it:** the stale-form test uses a test-only entity in `sample-app/src/test` (a core test jar would put core's persistence units on the demo's classpath); the demo-app gate is a copy of core's for the same reason; the skip ledger and `redirectTestOutputToFile` in core were not in the plan.
- **Not done, honestly:** the workflow was not run on Linux; MySQL was not measured (H2 and PostgreSQL were asked for); `ListQueryService` and the product are untouched; the orphan report has no entry point for a host (a documented limit: the class is internal); the manual passes of Phase 13 are still the maintainer's; nothing of 14b (publishing, the docs site, the tag, the security-test pass) was started.
- **Rules:** none could not be satisfied. SEC-1 to SEC-3 hold in the new code (the orphan report reads names from the metadata and binds nothing from text; the tests print no secret). ARCH-12 (`NameCheckTest`) is green.

## 7. Suggested commits (PowerShell-safe, by path, never `git add .`, no trailer; `rules.txt` stays out)

```powershell
# 1. build: test dependencies, the profile that is never in a normal build, CI
git add jpa-admin-core/pom.xml sample-app/pom.xml .github/workflows/ci.yml .github/scripts/report-tests.sh
git commit -m "build: Testcontainers test dependencies, the perf profile, and CI that fails on a missing Docker or browser (Phase 14a, D-128 to D-131)"

# 2. core main: the read-only orphan upload report
git add jpa-admin-core/src/main
git commit -m "feat: read-only report of upload files that no record holds (Phase 14a, UPL-9, D-132)"

# 3. core tests: the engine scenarios on H2, PostgreSQL and MySQL, the measurements, the guards
git add jpa-admin-core/src/test
git commit -m "test: run the list, write and chart scenarios on H2, PostgreSQL and MySQL; perf measurements; build guards (Phase 14a)"

# 4. sample app tests: engine checks, stale form, orphan report, one browser policy, build settings guard
git add sample-app/src/test
git commit -m "test: demo app on PostgreSQL and MySQL, stale form through the real app, one browser policy for CI (Phase 14a)"

# 5. docs
git add CHANGELOG.md docs
git commit -m "docs: Phase 14a decisions D-128 to D-134, database support table, measurements, options and report"
```

Then the maintainer's steps: review, push, **watch the first run on the Linux runner** (the browser and engine counts and the skip reasons are printed by the step "Report what ran and what was skipped"), decide the items above, merge, tag `v0.1.0-rc.14a` if you want one (GIT-2).
