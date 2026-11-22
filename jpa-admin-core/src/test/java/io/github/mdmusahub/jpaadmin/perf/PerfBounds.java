package io.github.mdmusahub.jpaadmin.perf;

import java.util.Map;

/**
 * The upper bound, in milliseconds, of each measurement on each engine (Phase 14a, D-131). A measured median above its bound flags
 * a regression.
 *
 * <p><b>How the bounds were set.</b> Not from a guess: from the first measured run (2026-11-22, the machine in
 * {@code docs/logs/phase-14a-performance.md}). Each bound is <b>five times the median of that first run, rounded up to a multiple of
 * 10 ms, and never below 50 ms</b>: a bound is there to catch a change that makes a query several times slower, not to judge
 * speed, and a median of a few milliseconds would otherwise flag a busy machine. A measurement that has no entry has no bound yet
 * ({@link Long#MAX_VALUE}) and says so in the report; that is the state of a new measurement before its first run.
 *
 * <p>These are not tests of speed in the build: the measurements run only with {@code mvn -B -pl jpa-admin-core -Pperf test}.
 */
final class PerfBounds {

    private static final Map<String, Long> BOUNDS = Map.ofEntries(
            Map.entry("PostgreSQL | chart: read the creation column, 90 days (under the 50,000 cap)", 160L),
            Map.entry("PostgreSQL | chart: read the creation column, 400 days (200,000 rows, cut at the 50,000 cap)", 180L),
            Map.entry("PostgreSQL | chart alternative: group by day in the database, 90 days", 90L),
            Map.entry("PostgreSQL | chart alternative: group by day in the database, 400 days", 140L),
            Map.entry("PostgreSQL | export: 50,000 rows (a count and 100 pages of 500)", 10990L),
            Map.entry("PostgreSQL | export: the first page of 500 rows (offset 0)", 50L),
            Map.entry("PostgreSQL | export: the last page of 500 rows (offset 49,500)", 170L),
            Map.entry("PostgreSQL | list, first page, no search", 110L),
            Map.entry("PostgreSQL | list, middle page, no search", 370L),
            Map.entry("PostgreSQL | list, last page, no search", 590L),
            Map.entry("PostgreSQL | list, first page, search 'zebra' (rare)", 870L),
            Map.entry("PostgreSQL | list, middle page, search 'zebra' (rare)", 1590L),
            Map.entry("PostgreSQL | list, last page, search 'zebra' (rare)", 2140L),
            Map.entry("PostgreSQL | list, first page, search 'item' (every row)", 770L),
            Map.entry("PostgreSQL | list, middle page, search 'item' (every row)", 1950L),
            Map.entry("PostgreSQL | list, last page, search 'item' (every row)", 3100L),
            Map.entry("PostgreSQL | relations: 0 relation columns shown, first page", 110L),
            Map.entry("PostgreSQL | relations: 0 relation columns shown, middle page", 180L),
            Map.entry("PostgreSQL | relations: 1 relation column shown, first page", 120L),
            Map.entry("PostgreSQL | relations: 1 relation column shown, middle page", 220L),
            Map.entry("PostgreSQL | relations: 2 relation columns shown, first page", 110L),
            Map.entry("PostgreSQL | relations: 2 relation columns shown, middle page", 290L),
            Map.entry("PostgreSQL | relations: 3 relation columns shown, first page", 100L),
            Map.entry("PostgreSQL | relations: 3 relation columns shown, middle page", 360L),
            Map.entry("PostgreSQL | search: count where lower(title) like '%zebra%' (rare)", 170L),
            Map.entry("PostgreSQL | search: count where lower(title) like '%item%' (every row)", 150L),
            Map.entry("PostgreSQL | count(*) of the whole table", 60L),
            Map.entry("PostgreSQL | with trigram indexes: count where lower(title) like '%zebra%' (rare)", 50L),
            Map.entry("PostgreSQL | with trigram indexes: list, first page, search 'zebra' (rare)", 90L),
            Map.entry("PostgreSQL | with trigram indexes: list, first page, search 'item' (every row)", 780L),
            Map.entry("H2 | chart: read the creation column, 90 days (under the 50,000 cap)", 290L),
            Map.entry("H2 | chart: read the creation column, 400 days (200,000 rows, cut at the 50,000 cap)", 400L),
            Map.entry("H2 | chart alternative: group by day in the database, 90 days", 110L),
            Map.entry("H2 | chart alternative: group by day in the database, 400 days", 180L),
            Map.entry("H2 | export: 50,000 rows (a count and 100 pages of 500)", 9980L),
            Map.entry("H2 | export: the first page of 500 rows (offset 0)", 50L),
            Map.entry("H2 | export: the last page of 500 rows (offset 49,500)", 170L),
            Map.entry("H2 | list, first page, no search", 50L),
            Map.entry("H2 | list, middle page, no search", 330L),
            Map.entry("H2 | list, last page, no search", 690L),
            Map.entry("H2 | list, first page, search 'zebra' (rare)", 440L),
            Map.entry("H2 | list, middle page, search 'zebra' (rare)", 640L),
            Map.entry("H2 | list, last page, search 'zebra' (rare)", 1540L),
            Map.entry("H2 | list, first page, search 'item' (every row)", 580L),
            Map.entry("H2 | list, middle page, search 'item' (every row)", 1120L),
            Map.entry("H2 | list, last page, search 'item' (every row)", 1820L),
            Map.entry("H2 | relations: 0 relation columns shown, first page", 50L),
            Map.entry("H2 | relations: 0 relation columns shown, middle page", 50L),
            Map.entry("H2 | relations: 1 relation column shown, first page", 50L),
            Map.entry("H2 | relations: 1 relation column shown, middle page", 210L),
            Map.entry("H2 | relations: 2 relation columns shown, first page", 50L),
            Map.entry("H2 | relations: 2 relation columns shown, middle page", 420L),
            Map.entry("H2 | relations: 3 relation columns shown, first page", 50L),
            Map.entry("H2 | relations: 3 relation columns shown, middle page", 460L),
            Map.entry("H2 | list, first page, sorted by title (no index)", 850L),
            Map.entry("H2 | list, last page, sorted by title (no index)", 870L),
            Map.entry("H2 | list, first page, sorted by a relation's label (owner)", 810L),
            Map.entry("H2 | search: count where lower(title) like '%zebra%' (rare)", 160L),
            Map.entry("H2 | search: count where lower(title) like '%item%' (every row)", 150L),
            Map.entry("H2 | count(*) of the whole table", 50L),
            Map.entry("PostgreSQL | list, first page, sorted by title (no index)", 390L),
            Map.entry("PostgreSQL | list, last page, sorted by title (no index)", 1090L),
            Map.entry("PostgreSQL | list, first page, sorted by a relation's label (owner)", 370L)
    );

    private PerfBounds() {
    }

    /** The bound for {@code measurement} on {@code engine}, or {@link Long#MAX_VALUE} when it has none yet. */
    static long of(String engine, String measurement) {
        return BOUNDS.getOrDefault(engine + " | " + measurement, Long.MAX_VALUE);
    }
}
