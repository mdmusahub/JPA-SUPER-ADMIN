# Phase 10 report: dashboard chart ("Records created")

Branch `phase/10-chart`, from `main` (tag `v0.0.9`). One session on both tracks. Nothing is committed. Phase 11 is not started (its scope is in `docs/logs/phase-11-scope.md`). Plan: `docs/logs/phase-10-plan.md`. Log of every file and command: `docs/logs/phase-10-change-log.md` (52 entries).

## 1. Files

`git diff --stat`: 24 tracked files changed, 703 insertions, 58 deletions. New files are listed separately. Nothing was deleted.

**Modified (24)**

| Track | Files |
|---|---|
| Backend, main | `jpa-admin-core/.../metadata/AdminEntity.java` (`creationField()`), `web/ConsoleController.java` (`range` parameter, charts), `web/ConsoleNavigation.java` (`dashboard(Chart)`), `web/UploadAnswers.java` (`string` package-private), `web/view/ChartSeries.java`, `web/view/DashboardView.java` (contract change D-101), `jpa-admin-autoconfigure/.../JpaAdminAutoConfiguration.java` (four beans) |
| Backend, tests | `ConsolePagesTest`, `Samples`, `ViewHrefRuleTest`, `ViewInvariantsTest`, `ViewNullRejectionTest` (core), `META-INF/persistence.xml` (unit `stats-test`), `sample-app/.../ConsoleAtDefaultPathTest.java` |
| UI | `jpa-admin-ui/.../templates/jpa-admin/dashboard.html`, `assets/css/pages.css`, `jpa-admin-ui-preview/.../PageFixtures.java`, `PreviewPagesTest.java` |
| Docs | `CHANGELOG.md`, `docs/DECISIONS.md` (D-101 to D-104), `docs/FIXTURES.md`, `docs/PLAN.md`, `docs/PROJECT_DOCS.md`, `docs/UI_PLAN.md` |

**Created (35 files)**

- Core main: `metadata/CreationFields.java`, `service/CreationDates.java`, `service/StatsService.java`, `web/ChartJson.java`, `web/ChartPlot.java`, `web/DashboardCharts.java`, `web/StatsController.java`.
- Core tests: `CreationFieldsTest`, `StatsServiceTest`, `ChartPlotTest`, `DashboardChartsTest`, `StatsControllerTest`, `web/view/ChartRecordsTest`, `testsupport/StatsTestPersistence`, `testsupport/statsmodel/StatsEntities`.
- Autoconfigure test: `ChartAutoConfigurationTest`.
- UI: `templates/jpa-admin/fragments/chart.html`; preview `ChartFixtures.java`, test `PreviewChartPagesTest.java`.
- Sample app tests: `FixedClockConfiguration`, `AbstractChartPagesTest`, `ChartPagesOpenInViewDisabledTest`, `ChartPagesOpenInViewEnabledTest`, `ChartFailureTest`, `ChartWithoutCreationFieldsTest`, `ChartPagesLogTest`.
- Host-check test: `ConsoleHasNoChartForThisHostTest`.
- Docs: `docs/logs/phase-10-plan.md`, `phase-10-change-log.md`, `phase-10-report.md`, `phase-11-scope.md`.

## 2. Tests and builds

| Module | Before | After | Change |
|---|---|---|---|
| jpa-admin-core | 3190 (1 skipped) | 3404 (1 skipped) | +214 |
| jpa-admin-autoconfigure | 498 | 505 | +7 |
| jpa-admin-ui-preview | 1882 | 2087 | +205 |
| sample-app | 819 | 881 | +62 |
| **Reactor total** | **6389** | **6877** | **+488** |
| host-check (outside the reactor) | 21 | 23 | +2 |

- `mvn -B clean verify`: BUILD SUCCESS, 6:46 min.
- `mvn -B -DskipTests install`: BUILD SUCCESS.
- `mvn -B -f host-check/pom.xml verify`: BUILD SUCCESS, 23 tests.
- Packaged run (PowerShell `Start-Process`, `curl.exe` with a cookie jar): the card shows Category, Customer, Order and Product, Tag is named as left out and absent from the chart; `range=45` gives range 30 with the note, on the page and in the JSON; anonymous `GET` of the stats is a 302 to the sign-in page; a POST without a token is 403; no password in the JSON. Ports 8080, 8081, 8090 were free before and after; the process was stopped by PID.

**About "Order 30 across the window".** The totals from the real run were 30 days: Category 6, Customer 12, **Order 29**, Product 25; 90 days: **Order 30** (the 90 daily counts add up to 30); 7 days: 2, 3, 6, 6. The run was at 00:25 local time. The demo seed puts each row 1 to 10 hours before "now", so at that hour every row falls on the previous calendar day, and the oldest order lands on day 30, outside the 30-day window. This is the seed meeting the clock, not a counting error. The exact proof is the fixed-clock sample tests (noon UTC): 7, 30 and 90 day totals 2/3/7/7, 6/12/30/25 and 6/12/30/25, orders one per day on each of the 30 days, every daily bucket of categories and customers on its seed day, in both open-in-view modes.

## 3. Mutation checks (one at a time; each file restored byte-identical, MD5 checked)

| | Break | Result |
|---|---|---|
| (a) | `CreationFields`: declaration order instead of the list order | CAUGHT, 1 failure (`CreationFieldsTest` list-order test) |
| (b) | `CreationFields`: sensitive field allowed | CAUGHT, 18 failures in 4 classes |
| (c) | `StatsService.parseRange`: any integer accepted | CAUGHT, 15 failures in 3 classes (`45` ends in the record's own check, a 500 page, which is a second line of defence only) |
| (d) | `StatsService.answer`: cache lookup disabled | CAUGHT, 4 failures |
| (e) | `StatsService.read`: rethrow instead of "unavailable" | CAUGHT, 5 failures |
| (f) | `CreationDates`: bucket in `ZoneOffset.UTC` instead of the Clock's zone | CAUGHT, 3 failures (Kolkata, today in the zone, offset and zoned values) |
| (g) | `DashboardCharts.chart`: the "has a chart" guard removed | CAUGHT, 2 failures in core. The end-to-end tests of the same rule (sample app `ChartWithoutCreationFieldsTest`, host-check `ConsoleHasNoChartForThisHostTest`) were not re-run against this mutation |
| (h) | `chart.html`: `style="..."` on the svg | CAUGHT, 3 failures (`TemplateRulesTest`, 2 in `PreviewChartPagesTest`) |
| (i) | `EntityScanner`: `@AdminHidden` no longer excludes an entity, so the hidden entity reaches the chart and the JSON | CAUGHT, 13 failures (service 11, controller 2). The scanner's own tests were not part of this run |

Baseline MD5s: CreationFields 58662cf2, StatsService 5cab64b1, CreationDates 410f924c, DashboardCharts c7a1c434, EntityScanner c0d8f8cd, chart.html d9ee76a9; all six identical after the checks, and `git status` shows `EntityScanner.java` unchanged.

## 4. Decisions and deviations (full text in `docs/DECISIONS.md` D-101 to D-104)

- **Contract change (D-101), asked first, option A approved.** The frozen records could not carry what the chart needs, so both changed additively. **Deviation to be aware of:** `Chart` also has `peak`, `width`, `axis` and `bars` (and a derived `anyAvailable()`), which the question did not list, because a template cannot sum and the stacked geometry is computed once by a pure, tested class. They are inside the approved change in spirit; say so if you want them reviewed on their own.
- **Where the entity "name and label" come from.** Your brief said the JSON carries "entities with label and name"; the frozen record had only display names. That is what the question was about.
- **Counting approach (D-102) and its query cost.** One Criteria query per chart entity, only the creation column, bound parameters, newest first, `cap + 1` rows, bucketed in Java. **Always the 90-day window; 7 and 30 are cut from it**, so one load costs at most one query per chart entity and a range switch costs none. Measured over the real application with Hibernate statistics: endpoint 4 statements (Category, Customer, Order, Product; not Tag, not the hidden entity), repeats and other ranges 0, a cold dashboard 5 counts + 4 chart statements, the next one 0.
- **Row cap: 50,000 per entity per read**, an internal constant. Beyond it the oldest days are incomplete and the card says which ("up to and including <day>"), with an "incomplete" flag; a shorter range inside the complete part is not marked. Removing the cap needs a `group by` on a date part (a database function), so I kept the cap with the note, as your brief allows. I did not stop to ask because the brief allows the cap when it is stated.
- **Time zone rule:** the injected `Clock` decides today and the zone of `Instant`, `OffsetDateTime` and `ZonedDateTime`; `LocalDate` and `LocalDateTime` are calendar values and not converted. No host `Clock` bean: `Clock.systemDefaultZone()`, so **the server's default zone** (documented). Existing beans fall back to `systemUTC`; the chart deliberately does not.
- **Each entity in its own read-only transaction** (a separate proxied reader). Proven on H2, which does not abort a transaction after an error; PostgreSQL is a Phase 14 run.
- **Failure:** unavailable, zero counts, a note, one WARN line per window with no stack trace, cached for the window.
- **POST to the stats path is the console's 404 (403 without a token), not 405.** The existing update route `POST {entity}/{id}` claims `api/stats` first and finds no entity `api`. Nothing changes, nothing is revealed; a 405 would need a mapping that exists only to say 405. PUT, PATCH, DELETE are 405. **Limit:** an entity whose URL name is `api` cannot open its record with id `stats`.
- **No script (D-103).** The card is drawn by the server; `chart.js` of the UI plan is not written. The endpoint serves other clients.
- **When there is no chart:** the card is hidden (the acceptance criterion and mutation (g)); I also print one muted sentence under the stat cards that says why. Your brief said "the empty state says so"; this is how I read it. Tell me if you want the sentence removed.
- **Detection:** `java.util.Date` and `java.sql.Timestamp` are not accepted (your brief lists five `java.time` types).
- **Tests changed to follow the contract, not weakened:** `ViewHrefRuleTest` gained `RangeLink.href` (and its count 3 to 4); `ConsolePagesTest` finds the mapped method with its new parameter; `ConsoleAtDefaultPathTest` now accepts `/admin?...` as under the base path, which is what `Hrefs.isUnder` already says; `/administrator` is still rejected.
- **What the build found:** a property test (300 random series) found overlapping segments; the name check caught my own use of `Order` and `Product` in a test (renamed); a wrong assumption about a column name in a log test; a `sed` of mine turned a `LoginView` line wrong and a range delete removed a FIXTURES heading (both repaired and logged).
- **What a host should know:** the extra query per chart entity (at most once per 5 seconds, up to 50,000 timestamps), an index on the creation column on a big table, the timestamp rule and name list, the cap note, the zone rule, and that days are the server's, not the viewer's.
- **CODE-8, honestly.** I verified APIs by compiling and running against the pinned 4.1.2 jars and by `javap` for Jackson 3 (`tools.jackson.databind.json.JsonMapper`, `JsonNode.asString()`, `longValue()`, `propertyNames()`); I did not read the online reference pages in this session. Packages used: `jakarta.persistence.criteria` (`CriteriaBuilder`, `CriteriaQuery`, `ParameterExpression`, `Path`, `Root`), `jakarta.persistence.TypedQuery` (`setParameter(Parameter, T)`, `setMaxResults`), `org.hibernate.SessionFactory` and `org.hibernate.stat.Statistics#getPrepareStatementCount`, `org.springframework.transaction.annotation.Transactional`, `org.springframework.orm.jpa.SharedEntityManagerCreator`, `org.springframework.web.bind.annotation.{GetMapping, RequestParam}`, `org.springframework.boot.test.system.{CapturedOutput, OutputCaptureExtension}`, `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, `org.springframework.boot.persistence.autoconfigure.EntityScan`, `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration`.

## 5. Not done, manual checks, contract and Phase 11

**Not done**
- The browser pass of the card: both themes, phone width, keyboard only, script off. No browser driver (AI-8); the CSS has a phone breakpoint and visible focus, but I did not look at it in a browser. Manual item and Phase 14 item.
- A PostgreSQL and MySQL run of the chart query and of the one-transaction-per-entity isolation (Phase 14).
- Mutations (g) and (i) were run against the core tests only.

**Manual checks in a browser** (`mvn -pl jpa-admin-ui-preview -am spring-boot:run`, then `/admin-preview/dashboard?state=normal` and the `chart-*` states): the eight tones in light and dark (are they told apart, is the text readable on the swatches), the range switch with the keyboard (Tab, visible focus, `aria-current`), phone width 390px (the switch fills the width, the legend wraps, the daily table scrolls in its own box, no sideways page scroll), JavaScript off, the "Daily counts" `details`, a screen reader on the SVG (title, description) and the table.

**Contract change proposed:** none beyond D-101 (done, approved).

**Phase 11 scope:** `docs/logs/phase-11-scope.md` (not implemented).

## 6. Suggested commits (PowerShell-safe, by path, never `git add .`)

```powershell
# 1. contract, detection, statistics and endpoint (backend)
git add jpa-admin-core/src/main jpa-admin-autoconfigure/src/main
git commit -m "feat(core): records-created statistics, creation-field detection and GET /api/stats

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 2. backend tests
git add jpa-admin-core/src/test jpa-admin-autoconfigure/src/test sample-app/src/test host-check/src/test
git commit -m "test: chart detection, buckets, cache, failure, endpoint, real-application proof

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 3. UI and preview (U7)
git add jpa-admin-ui jpa-admin-ui-preview
git commit -m "feat(ui): dashboard chart card with range switch and preview fixtures

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"

# 4. docs and logs
git add CHANGELOG.md docs/DECISIONS.md docs/FIXTURES.md docs/PLAN.md docs/PROJECT_DOCS.md docs/UI_PLAN.md docs/logs/phase-10-plan.md docs/logs/phase-10-change-log.md docs/logs/phase-10-report.md docs/logs/phase-11-scope.md
git commit -m "docs: Phase 10 decisions D-101 to D-104, plan ticks, changelog and logs

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

Commit 1 compiles only together with commit 2's test changes (the existing tests call the changed records) and commit 3 only together with commit 1 (the preview fixtures need the new records), so if you want every commit to build, make it one commit or order them 1, 2, 3 in a row. The merge to `main` and the tag `v0.0.10` are yours (GIT-2).
