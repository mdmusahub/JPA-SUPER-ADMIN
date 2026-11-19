# Phase 10 plan: dashboard chart ("Records created")

Branch `phase/10-chart`, from `main` (tag `v0.0.9`). Both tracks in one session (the recorded exception to AI-11 as in Phases 2 to 9). Nothing is committed. Phase 11 is not started.

Sources read first: `CLAUDE.md` (root, core, ui), `docs/PLAN.md` (Phase 10 row, section 7 "Dashboard chart"), `docs/RULES.md`, `docs/UI_PLAN.md` (U7), `docs/FIXTURES.md`, `docs/DECISIONS.md`, `docs/PROJECT_DOCS.md`, the Phase 3 and 9b reports, and the frozen records `DashboardView`, `ChartSeries`, `Permissions`, the stat cards.

## 0. The one question that needed the maintainer (answered)

The frozen `ChartSeries.entities` is a `List<String>` (no URL name, no availability) and `DashboardView` holds only `chartAvailable`. The page needs the series, the range links, the fallback note, the cap note, the entities left out and per-entity totals. The maintainer approved **option A: change both records additively**, as one decision (D-101), both sides in the same change. Shape:

```
ChartSeries(int range, List<Entity> entities, List<Day> days, List<String> notes)
  Entity(String name, String label, boolean available, long total, boolean capped)
  Day(String date, List<Long> counts)                         unchanged; 0 for an unavailable entity
DashboardView(Stats stats, List<Group> groups, boolean chartAvailable, Chart chart)
  Chart(ChartSeries series, List<RangeLink> ranges, List<String> leftOut, long peak, int width, List<String> axis, List<Bar> bars)
  RangeLink(int days, boolean current, String href)           Hrefs rule, built by the backend
  Bar(String date, String label, String summary, long total, int x, List<Segment> segments)
  Segment(int entity, int y, int height)                      integer geometry in a 0..1000 box, drawn bottom-up
  chart is null exactly when chartAvailable is false
```

Added beyond the shape shown at the question, and recorded in D-101: `Chart.peak`, `Chart.width`, `Chart.axis` and `Chart.bars` (the template cannot sum, so the backend computes the stacked geometry once, in a pure, tested class). Nothing else in any record changes.

## 1. Detection (metadata only, ARCH-1, ARCH-12)

`AdminEntity.creationField()` returns the first acceptable field in this name order: `createdAt`, `createdDate`, `createdOn`, `creationDate`, `creationTime`, `createTime`, `createdDateTime`. A name matches case-insensitively, ignoring `_` and `-`. Acceptable: a visible, non-sensitive, non-hidden attribute of type `LocalDate`, `LocalDateTime`, `Instant`, `OffsetDateTime` or `ZonedDateTime`, on a supported entity. The rule lives in the package-private `CreationFields` (metadata package, next to `Names`); no entity name anywhere.

## 2. Counting (bounded, portable)

Approach: **one Criteria query per chart entity** that selects only the creation column for the 90-day window ending today, newest first, with `setMaxResults(cap + 1)`; the rows are bucketed by day in Java. No database function, no `group by` on a date part, no extra dependency.

- Allowed ranges 7, 30, 90. Anything else (`45`, `abc`, empty, repeated) falls back to 30 and a note says so; an absent parameter is the default 30 with no note. The note never repeats the typed value.
- The query always covers the largest window (90 days) and the ranges slice the cached buckets. So one dashboard load costs at most one chart query per entity, and a switch between 7, 30 and 90 inside the cache window costs none.
- Row cap: **50,000 rows per entity per read** (the export ceiling's number). When the window holds more, only the newest 50,000 are counted, the entity is marked `capped`, and a note names the day up to which counts are incomplete. Never silent. (The cap is a protection of memory and time; it is not removable without a database function, and the maintainer's brief allows it with a stated note.)
- Time: the injected `Clock` (no new property). The day boundaries and the bucketing use `clock.getZone()`. Without a host `Clock` bean the zone is the JVM's default zone (`Clock.systemDefaultZone()`), which is documented. `LocalDate` and `LocalDateTime` values are wall-clock values and are bucketed as they are; `Instant`, `OffsetDateTime` and `ZonedDateTime` are converted to the clock's zone first.
- Each entity is read in **its own read-only transaction** (a separate proxied reader, `CreationDates`), so on a database that aborts a transaction after an error (PostgreSQL) one failing entity cannot make the next one fail.

## 3. Cache

`StatsService` keeps one answer per entity for 5 seconds (same constant and pattern as `CountService`, injected `Clock`, a clock that jumps back invalidates). A failure is cached for the window too, so a broken table is queried and logged at most once per window.

## 4. Failure

A failing entity: `available=false`, zero counts, a note "The creation dates of X could not be read.", one WARN line without a stack trace (the cause's `toString`, like `CountService`), the page and the other entities unaffected.

## 5. Endpoint

`GET {path}/api/stats?range=7|30|90`, session required (the console's chain: 302 to the sign-in page when anonymous), GET only (a POST is 403 without the CSRF token and 405 with it). The body is `ChartSeries`, written by hand (like the upload answer) so a host's Jackson settings cannot change its shape. The `{path}/api` prefix is unused by any route today; a literal mapping beats `{entity}/{id}` and a test proves it.

## 6. Dashboard and UI (U7)

`DashboardCharts` (web) builds `DashboardView.Chart` from `StatsService`: range links (`{path}?range=N`), the left-out labels, the stacked geometry (`ChartPlot`, pure). `ConsoleController.dashboard` reads `range`. The template adds a chart card (range switch as plain links with `aria-current`, an inline SVG drawn from the bars, a legend with thousands-separated totals, a closed `<details>` with the daily table as the text alternative, the notes, the left-out sentence, a zero state, an unavailable state). Colour: eight CSS classes derived from `--ac` with `color-mix`, reused beyond eight. No script is needed, so none is added (D-102). When `chartAvailable` is false the card is hidden and one muted sentence under the stat cards says why.

## 7. Preview

Dashboard fixtures built by the real `ChartPlot`: `chart-normal` (the existing `normal` carries a chart), `chart-range-7`, `chart-range-90`, `chart-one-unavailable`, `chart-all-zero`, `chart-large-counts`, `chart-long-labels`, `chart-range-fallback`, `chart-capped`, `chart-many-entities`; the existing `no-chart` is the "no timestamp field" state.

## 8. Tests

Detection (core, metadata). Counting, ranges, fallback, cap, time zones, query count, cache, failure (core, own persistence unit `statsmodel`). Endpoint JSON, dashboard, session, GET only, no hidden entity or sensitive value, route precedence (core MockMvc-free controller tests and the sample app over the real chain). Open-in-view on and off (sample app). Template rules and fixtures (preview). Seed-based daily buckets in the sample app with a fixed `Clock`.

## 9. Mutation checks (one at a time, MD5 restored)

(a) ignore the list order, (b) include a sensitive timestamp, (c) accept any range, (d) remove the cache, (e) let one failing entity break the page, (f) bucket in UTC instead of the Clock's zone, (g) show the card when no entity has a timestamp, (h) inline style or script in the chart template, (i) return a hidden entity in the JSON.

## 10. Docs and checks

DECISIONS (D-101 onward), CHANGELOG `[Unreleased]`, PROJECT_DOCS (chart rules, detection list, range fallback, query cost, cap and note, time zone, limitations), PLAN and UI_PLAN ticks, FIXTURES. Final: `mvn -B clean verify`, `mvn -B -DskipTests install`, `mvn -B -f host-check/pom.xml verify`, then the packaged sample app with `curl.exe`.

## 11. APIs checked against the jars (CODE-8)

Jakarta Persistence 3.2 (`jakarta.persistence.criteria.CriteriaBuilder`, `CriteriaQuery`, `Path`, `ParameterExpression`, `TypedQuery#setMaxResults`), Hibernate ORM 7.4 (`org.hibernate.stat.Statistics` for statement counts in tests), Spring Framework 7 (`org.springframework.transaction.annotation.Transactional`, `org.springframework.web.bind.annotation.GetMapping`/`RequestParam`), Spring Boot 4.1 (`org.springframework.boot.test.system.OutputCaptureExtension`, `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`). Exact checks are listed in the report.
