# Phase 14a: performance measurements

Measured 2026-11-22 on the machine below, on **H2** (in memory) and **PostgreSQL 17.11** (a container). MySQL was not measured (the brief asked for H2 and PostgreSQL). These are **measurements, not speed tests**: nothing here fails a normal build. Each measurement has an upper bound that flags a regression in the profile run; **the bounds were set from the first measured run** (see "How the bounds were set").

## 1. Machine and versions

| | |
|---|---|
| Machine | Windows 11 Pro (10.0.26200), 12 logical CPUs, a laptop (not a server) |
| JDK | Eclipse Adoptium (Temurin) 17.0.20.1, `-Xmx3g` for the measurement JVM |
| Maven, Surefire | Maven 3.10.0, Surefire 3.5.6 |
| H2 | 2.4.240, in memory, **with `OPTIMIZE_REUSE_RESULTS=FALSE`** (see section 3) |
| PostgreSQL | 17.11 (Debian 17.11-1.pgdg13+2), image `postgres:17.11`, in Docker Desktop 29.4.0 (a VM with 8 GB), default configuration, so **the PostgreSQL numbers include the Docker Desktop VM and the published port; they are not a production server** |
| Hibernate / driver | Hibernate ORM 7.4.13.Final, PostgreSQL JDBC 42.7.14 |
| Time zone of the JVM | Asia/Kolkata |

Run it: `mvn -B -pl jpa-admin-core -Pperf test` (needs Docker for PostgreSQL; without it the PostgreSQL measurements are skipped with the reason). Nothing runs without `-Pperf`: the profile has no activation, the default build excludes the folder, and each class carries its own switch (`PerfProfileIsOffByDefaultTest` checks all three, and the build fails if one is lost).

## 2. The data

`PerfItem`: **200,000 rows**, three text columns (`title` 120, `details` 400 characters, `code`), a decimal, an enum, a creation date and time spread evenly over **400 days**, and three to-one relations (`owner`, `team`, `place`) to three tables of 2,000 rows each (labelled by `name`). The word `item` is in every title (so a search for it matches all 200,000 rows) and the word `zebra` is in exactly 200 of them (one in a thousand). 44,751 rows were created in the last 90 days. No index except the primary keys (the foreign keys are not indexed either, PostgreSQL does not do it for you). Loaded with JDBC batches of 5,000 rows in about 4 s (H2) and 7 s (PostgreSQL), then the planner statistics were refreshed.

A list page is the console's own: a `count` and one page of 25 rows with every relation shown as one `LEFT JOIN`, **plus one small statement per filterable relation** (up to 200 choices each, here 3), exactly as the application runs it.

## 3. Method, and two things that would have made the numbers wrong

- Each measurement: **2 warm-up runs, then 7 timed runs; the median is the number**. The tables show two separate runs of the whole profile (run 1 and run 2) so that the spread is visible. On this laptop the same query moves by up to 2 to 3 times between runs when it takes a few milliseconds, and by 10 to 30 percent when it takes more than 50. Read the tables for orders of magnitude, not for the second digit.
- **H2 remembers.** H2 hands out the result of the last identical query again when the tables did not change (`OPTIMIZE_REUSE_RESULTS`, on by default). The first H2 numbers of this phase measured that cache (a sort of 200,000 rows "took" 0.7 ms) and were thrown away; every H2 number below is with the setting off. PostgreSQL has no result cache; its numbers are **warm** (the table is in the page cache after the warm-up), which is the normal state of a console that is in use. A first request after a restart would be slower.
- Times include the Java side: building the criteria query, running it, reading the rows and turning them into display text.

### How the bounds were set

Each bound is **5 times the median of the first measured run, rounded up to a multiple of 10 ms, never below 50 ms** (`PerfBounds`). The H2 bounds come from the first run after the cache was switched off. The three sort measurements of PostgreSQL were added after the first PostgreSQL run and took their bounds from their own first run. A bound is there to catch a change that makes a query several times slower, not to judge speed.

## 4. Results (median milliseconds)

### The list page (25 rows), default order, no search

| Measurement | H2 run 1 | H2 run 2 | H2 bound | PostgreSQL run 1 | PostgreSQL run 2 | PostgreSQL bound |
|---|---:|---:|---:|---:|---:|---:|
| list, first page, no search | 3.0 | 3.3 | 50 | 21.1 | 28.5 | 110 |
| list, middle page, no search | 65.8 | 71.8 | 330 | 73.1 | 80.2 | 370 |
| list, last page, no search | 136.5 | 139.1 | 690 | 118.0 | 125.9 | 590 |

### The list page with a search word

| Measurement | H2 run 1 | H2 run 2 | H2 bound | PostgreSQL run 1 | PostgreSQL run 2 | PostgreSQL bound |
|---|---:|---:|---:|---:|---:|---:|
| list, first page, search 'zebra' (rare) | 87.7 | 90.2 | 440 | 173.8 | 169.9 | 870 |
| list, middle page, search 'zebra' (rare) | 126.4 | 170.0 | 640 | 316.4 | 322.3 | 1590 |
| list, last page, search 'zebra' (rare) | 306.6 | 207.0 | 1540 | 427.6 | 442.1 | 2140 |
| list, first page, search 'item' (every row) | 114.9 | 86.2 | 580 | 153.2 | 256.6 | 770 |
| list, middle page, search 'item' (every row) | 223.4 | 427.4 | 1120 | 388.3 | 427.5 | 1950 |
| list, last page, search 'item' (every row) | 362.1 | 366.9 | 1820 | 619.1 | 612.6 | 3100 |

### The list page sorted by a column that has no index

| Measurement | H2 run 1 | H2 run 2 | H2 bound | PostgreSQL run 1 | PostgreSQL run 2 | PostgreSQL bound |
|---|---:|---:|---:|---:|---:|---:|
| list, first page, sorted by title (no index) | 168.6 | 178.6 | 850 | 76.5 | 207.4 | 390 |
| list, last page, sorted by title (no index) | 172.6 | 214.5 | 870 | 217.6 | 239.7 | 1090 |
| list, first page, sorted by a relation's label (owner) | 161.9 | 169.3 | 810 | 73.2 | 74.4 | 370 |

### The search itself on one text column, and count(*)

| Measurement | H2 run 1 | H2 run 2 | H2 bound | PostgreSQL run 1 | PostgreSQL run 2 | PostgreSQL bound |
|---|---:|---:|---:|---:|---:|---:|
| search: count where lower(title) like '%zebra%' (rare) | 30.9 | 27.5 | 160 | 32.1 | 110.0 | 170 |
| search: count where lower(title) like '%item%' (every row) | 28.1 | 32.2 | 150 | 28.8 | 70.0 | 150 |
| count(*) of the whole table | 0.0 | 0.1 | 50 | 10.6 | 14.9 | 60 |

### The relation joins: a page with none, one, two and three relation columns shown

| Measurement | H2 run 1 | H2 run 2 | H2 bound | PostgreSQL run 1 | PostgreSQL run 2 | PostgreSQL bound |
|---|---:|---:|---:|---:|---:|---:|
| relations: 0 relation columns shown, first page | 7.5 | 1.3 | 50 | 20.1 | 19.1 | 110 |
| relations: 0 relation columns shown, middle page | 6.5 | 6.8 | 50 | 34.2 | 35.3 | 180 |
| relations: 1 relation column shown, first page | 1.7 | 1.3 | 50 | 22.4 | 26.2 | 120 |
| relations: 1 relation column shown, middle page | 41.8 | 25.2 | 210 | 43.5 | 43.0 | 220 |
| relations: 2 relation columns shown, first page | 2.6 | 8.3 | 50 | 20.5 | 19.3 | 110 |
| relations: 2 relation columns shown, middle page | 83.9 | 73.4 | 420 | 57.8 | 55.8 | 290 |
| relations: 3 relation columns shown, first page | 1.5 | 2.3 | 50 | 19.8 | 19.4 | 100 |
| relations: 3 relation columns shown, middle page | 90.5 | 112.0 | 460 | 71.0 | 78.9 | 360 |

### The chart query on the creation column

| Measurement | H2 run 1 | H2 run 2 | H2 bound | PostgreSQL run 1 | PostgreSQL run 2 | PostgreSQL bound |
|---|---:|---:|---:|---:|---:|---:|
| chart: read the creation column, 90 days (under the 50,000 cap) | 58.0 | 22.0 | 290 | 31.3 | 32.8 | 160 |
| chart: read the creation column, 400 days (200,000 rows, cut at the 50,000 cap) | 78.7 | 37.0 | 400 | 35.5 | 37.3 | 180 |
| chart alternative: group by day in the database, 90 days | 20.5 | 14.1 | 110 | 16.8 | 17.8 | 90 |
| chart alternative: group by day in the database, 400 days | 34.1 | 36.4 | 180 | 26.4 | 27.2 | 140 |

### The export

| Measurement | H2 run 1 | H2 run 2 | H2 bound | PostgreSQL run 1 | PostgreSQL run 2 | PostgreSQL bound |
|---|---:|---:|---:|---:|---:|---:|
| export: 50,000 rows (a count and 100 pages of 500) | 1995.9 | 2224.5 | 9980 | 2197.5 | 2220.0 | 10990 |
| export: the first page of 500 rows (offset 0) | 0.8 | 0.7 | 50 | 6.3 | 9.1 | 50 |
| export: the last page of 500 rows (offset 49,500) | 32.3 | 35.9 | 170 | 32.3 | 74.4 | 170 |

### PostgreSQL only: the same searches with three trigram indexes

| Measurement | H2 run 1 | H2 run 2 | H2 bound | PostgreSQL run 1 | PostgreSQL run 2 | PostgreSQL bound |
|---|---:|---:|---:|---:|---:|---:|
| with trigram indexes: count where lower(title) like '%zebra%' (rare) | - | - | - | 1.6 | 7.1 | 50 |
| with trigram indexes: list, first page, search 'zebra' (rare) | - | - | - | 16.4 | 63.2 | 90 |
| with trigram indexes: list, first page, search 'item' (every row) | - | - | - | 154.5 | 168.2 | 780 |


## 5. What the numbers say

**A page is cheap at the start and costs more the deeper it is.** With the default order (by identifier) the first page takes 3 ms on H2 and 20 to 28 ms on PostgreSQL (where 3 filter-choice statements and the Docker round trips are most of it), the middle page (offset 100,000) about 70 ms on both, the last page (offset 199,975) 120 to 140 ms. The database reads and joins every row it skips (`offset`), so the cost grows with the offset and with the number of relation columns, not with the page size.

**The relation join cost (one `LEFT JOIN` per relation column).** On the first page the joins are invisible: 20 ms with none, 20 ms with three on PostgreSQL; 1 to 3 ms on H2. On a middle page they add up: PostgreSQL 34 ms with none, 43 with one, 58 with two, **71 with three (about +12 ms per relation column)**; H2 6.5 ms with none, 25 to 42 with one, 73 to 84 with two, 90 to 112 with three. So **at 200,000 rows a page with three relation columns costs 20 ms at the start and under 150 ms at the very end; hiding the relation columns of a view lowers the cost of deep pages by half or more**. This is why the Phase 12 `hide` parameter matters for large tables. Nothing here asks for a batch query instead of the join at this size. Beyond it the cost is proportional (for example, roughly five times the rows would be five times the deep-page cost: not measured, an estimate from these numbers).

**The search is a full scan.** `lower(column) like '%text%'` cannot use an ordinary index. On one column the count takes 25 to 32 ms on both engines (110 ms in a noisy PostgreSQL run); the console's search is an OR over every visible text column (here three, one of them 400 characters), so a list with a search costs: PostgreSQL 150 to 620 ms, H2 80 to 430 ms, depending on how many rows match and how deep the page is (a search that finds only a few rows pays the full scan for the count and scans a long way for a deep page). `count(*)` of the whole table is free on H2 (it keeps the number) and 10 to 15 ms on PostgreSQL at 200,000 rows (a scan; it grows with the table).

**Index advice (PostgreSQL).** A trigram index per searched column makes a rare search about **10 times faster** and the count about 20 times: with `pg_trgm` and `create index ... using gin (lower(column) gin_trgm_ops)` on `title`, `details` and `code`, the rare search list page went from 174 ms to 16 ms and the count from 32 ms to 1.6 ms (in the noisier second run 63 ms and 7 ms, still about 3 times and 15 times better). **It does not help a search word that is in every row** (153 ms to 154 ms), and **one index is not enough**: the console ORs all text columns, so a column without an index forces the scan again. Build time was 8 to 10 seconds for three indexes on 200,000 rows. MySQL has no equivalent of a trigram index for `like '%text%'` (a full text index answers a different question); a MySQL host keeps the scan. The Phase 14 docs will say so.

**Sorting by a column with no index sorts the whole table.** 160 to 210 ms on H2 and 75 to 210 ms on PostgreSQL for a first page at 200,000 rows (sorting by a relation's label costs the same, the label comes from the join already there). An index on the sort column makes it as cheap as the default order; the console never creates indexes.

**Export of 50,000 rows** (the cap): about **2.2 s** on both (a count and 100 pages of 500, each page read in its own transaction and written as CSV). The database part is small and grows with the offset: the first page of 500 takes 1 to 9 ms and the last (offset 49,500) 32 to 74 ms, so the Java side (reading, formatting, escaping, writing) is most of it. A file of 50,000 rows is therefore a couple of seconds, not a problem, and one request holds no connection between pages.

## 6. The chart and the 50,000-row cap: do we need a database function?

The chart reads **only the creation column** of the rows of the last 90 days, newest first, **at most 50,001 of them**, and counts them per day in Java (`CreationDates`, D-101). The cap is said on the page whenever it cuts.

| | H2 | PostgreSQL |
|---|---:|---:|
| 90 days, 44,751 rows (under the cap) | 22 to 58 ms | 31 to 33 ms |
| 400 days, 200,000 rows in the window, **cut at the cap** (50,000 rows read, the oldest day reached is 2026-08-14, so 6 months are incomplete and the page says so) | 37 to 79 ms | 35 to 37 ms |
| The alternative: `group by` day in the database, 90 days (about 90 rows come back) | 14 to 21 ms | 17 to 18 ms |
| The alternative, 400 days | 34 to 36 ms | 26 to 27 ms |

**Options, with the numbers:**

| Option | What it costs and gains | Verdict |
|---|---|---|
| **A. Keep it** (read up to 50,001 values, count in Java) | 22 to 80 ms (H2) and 31 to 37 ms (PostgreSQL) per entity with a creation field; the dashboard caches each answer for 5 seconds, so a cold dashboard with five such entities is about 150 to 200 ms of chart work on PostgreSQL. Memory: 50,001 date values (a few megabytes) for the moment of the read. Runs on every database, in the zone the chart is read in. The only price is the "incomplete" note for a table with more than 50,000 rows in 90 days | **Recommended for v0.1** |
| **B. A database function** (`group by cast(createdAt as date)`) | Saves about 15 ms of 35 on PostgreSQL (and 10 to 40 on H2), and removes the cap and its note. But it is **correct only for `LocalDate` and `LocalDateTime`** columns; for `Instant`, `OffsetDateTime` and `ZonedDateTime` the day depends on the zone the chart is read in and the SQL for "this instant in that zone" is different on every database, so those types would keep the Java count anyway: two code paths, three databases to test, MySQL's version not measured | Not for v0.1; reconsider only if a host reports a table so large that the cap is hit and the note matters |
| **C. Raise the cap** (for example to 200,000) | Cost grows in proportion: about 140 ms on PostgreSQL per entity at 200,000, and the chart would still be incomplete beyond it | No |
| **D. Lower the cap** (for example to 10,000) | About 7 ms on PostgreSQL, but a table with more than 10,000 rows in 90 days would be reported incomplete | No |

**Recommendation: no database function is needed for the cap.** The chart read is under 40 ms per entity on a real database at the cap and is cached for 5 seconds. **No chart behaviour was changed in this phase and none is changed without your approval.** If you want option B for the two date types that it is exact for, it is a separate, small, approved change with its own tests.

## 7. What was not measured

MySQL; a cold cache (the first request after a restart); concurrent users; a network between the application and the database (the Docker Desktop port is local); tables beyond 200,000 rows (any number above that is an estimate by proportion); the effect of an index on the sort column or on the foreign keys; a `jpa.admin.page-size` other than 25 (the size changes the number of rows joined after the offset, not the offset).

## 8. Files

`jpa-admin-core/src/test/java/.../perf/` (`AbstractPerfMeasurement`, `H2PerfTest`, `PostgreSqlPerfTest`, `PerfBounds`, `PerfSwitch`), `.../testsupport/perfmodel/` (the five classes), the `perf-test` unit in `persistence.xml`, the profile `perf` and the default exclusion in `jpa-admin-core/pom.xml`, `.../buildguard/PerfProfileIsOffByDefaultTest`. The raw reports of each run are written to `jpa-admin-core/target/perf-report-<engine>.md` and `.csv`.
