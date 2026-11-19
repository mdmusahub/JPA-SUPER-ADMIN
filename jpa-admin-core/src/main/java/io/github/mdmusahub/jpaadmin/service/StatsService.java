package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.web.view.ChartSeries;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The "Records created" chart: how many records of each entity were created on each of the last 7, 30 or 90 days
 * (Phase 10, D-101, D-102).
 *
 * <p><b>Which entities.</b> Those whose metadata has a creation field ({@link AdminEntity#creationField()}); the others are
 * left out and {@link #leftOut()} names them, so the page can say so. No entity name is known here (ARCH-12).
 *
 * <p><b>Cost.</b> One query per chart entity, through {@link CreationDates}, that reads only the creation column for the
 * largest window (90 days) and at most {@link #ROW_CAP} rows of it. The answer is kept for {@value #TTL_SECONDS} seconds
 * (the same short window and the same injectable clock as {@link CountService}) and the three ranges are cut from it, so
 * one dashboard load costs at most one query per chart entity, and a repeat inside the window, a change of range and the
 * JSON endpoint after the page cost none. A table with more than {@link #ROW_CAP} rows in the window is read from its
 * newest end: the entity is marked capped and a note names the day up to which the counts are incomplete. The chart never
 * truncates silently.
 *
 * <p><b>Time.</b> The injected {@link Clock} says what today is and in which zone a moment belongs to a day. Without a
 * host {@code Clock} bean the auto-configuration passes the system clock, so the zone is the server's default zone.
 *
 * <p><b>Failure.</b> A table that cannot be read is an unavailable entity: zero counts, a note, one log line without a
 * stack trace per window. It never stops the other entities and never breaks the page.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it and the pages use it.
 */
public final class StatsService {

    /** How long a read is reused. An internal constant, deliberately not a property (as for {@link CountService}). */
    public static final int TTL_SECONDS = CountService.TTL_SECONDS;

    /** The most rows read per entity. A fixed internal ceiling, deliberately not a property. */
    public static final int ROW_CAP = 50_000;

    /** The range shown when none, or an unknown one, is asked for. */
    public static final int DEFAULT_RANGE = 30;

    private static final int WINDOW_DAYS = 90;
    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);
    private static final Logger log = LoggerFactory.getLogger(StatsService.class);

    private final CreationDates dates;
    private final AdminEntityRegistry registry;
    private final Clock clock;
    private final int rowCap;
    private final Map<String, Answer> cache = new ConcurrentHashMap<>();

    private StatsService(CreationDates dates, AdminEntityRegistry registry, Clock clock, int rowCap) {
        this.dates = Objects.requireNonNull(dates, "dates");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (rowCap < 1) {
            throw new IllegalArgumentException("rowCap must be at least 1");
        }
        this.rowCap = rowCap;
    }

    /**
     * The usual service.
     *
     * @param dates    the reader; every entity is read in a transaction of its own
     * @param registry the entities the console manages
     * @param clock    says what today is and in which zone; tests hand in their own (TEST-6)
     */
    public static StatsService over(CreationDates dates, AdminEntityRegistry registry, Clock clock) {
        return new StatsService(dates, registry, clock, ROW_CAP);
    }

    /** As {@link #over(CreationDates, AdminEntityRegistry, Clock)} with a smaller cap, so a test can reach it. */
    public static StatsService over(CreationDates dates, AdminEntityRegistry registry, Clock clock, int rowCap) {
        return new StatsService(dates, registry, clock, rowCap);
    }

    /** A service for a console with no entity manager: every chart entity is unavailable. */
    public static StatsService unavailable(AdminEntityRegistry registry, Clock clock) {
        return new StatsService(CreationDates.unavailable(), registry, clock, ROW_CAP);
    }

    /** Whether at least one entity has a creation field, so the chart has something to show. */
    public boolean hasChart() {
        return registry.all().stream().anyMatch(entity -> entity.creationField().isPresent());
    }

    /** The display names of the entities that have no creation field and so are not in the chart. */
    public List<String> leftOut() {
        return registry.all().stream().filter(entity -> entity.creationField().isEmpty()).map(AdminEntity::label)
                .toList();
    }

    /** Forgets every cached read. For tests and for a later "refresh" action. */
    public void clear() {
        cache.clear();
    }

    /**
     * The series for a range.
     *
     * @param requestedRange the {@code range} parameter as it came, {@code null} when absent; untrusted. Exactly
     *                       {@code 7}, {@code 30} or {@code 90} are taken; anything else is replaced by
     *                       {@value #DEFAULT_RANGE} and a note says so (the typed text is never repeated)
     */
    public ChartSeries series(String requestedRange) {
        int range = DEFAULT_RANGE;
        List<String> notes = new ArrayList<>();
        if (requestedRange != null) {
            Integer parsed = parseRange(requestedRange);
            if (parsed == null) {
                notes.add("That range is not available, so the last " + DEFAULT_RANGE + " days are shown. Choose 7, 30 or "
                        + "90 days.");
            } else {
                range = parsed;
            }
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate first = today.minusDays(range - 1L);
        List<AdminEntity> chartEntities = registry.all().stream().filter(entity -> entity.creationField().isPresent())
                .toList();
        List<ChartSeries.Entity> entities = new ArrayList<>();
        List<List<Long>> columns = new ArrayList<>();
        for (AdminEntity entity : chartEntities) {
            Answer answer = answer(entity, today);
            List<Long> column = new ArrayList<>();
            long total = 0;
            if (answer.days() != null) {
                for (int i = 0; i < range; i++) {
                    long count = answer.days().perDay().getOrDefault(first.plusDays(i), 0L);
                    column.add(count);
                    total += count;
                }
            } else {
                for (int i = 0; i < range; i++) {
                    column.add(0L);
                }
                notes.add("The creation dates of " + entity.label() + " could not be read, so it is not in the chart.");
            }
            boolean capped = answer.days() != null && answer.days().capped()
                    && !answer.days().incompleteThrough().isBefore(first);
            if (capped) {
                notes.add("The counts for " + entity.label() + " up to and including " + answer.days().incompleteThrough()
                        + " are incomplete: it has more than " + String.format(Locale.ROOT, "%,d", rowCap)
                        + " records in the last " + WINDOW_DAYS + " days, and only the newest "
                        + String.format(Locale.ROOT, "%,d", rowCap) + " are counted.");
            }
            entities.add(new ChartSeries.Entity(entity.urlName(), entity.label(), answer.days() != null, total, capped));
            columns.add(column);
        }
        List<ChartSeries.Day> days = new ArrayList<>();
        for (int i = 0; i < range; i++) {
            List<Long> counts = new ArrayList<>();
            for (List<Long> column : columns) {
                counts.add(column.get(i));
            }
            days.add(new ChartSeries.Day(first.plusDays(i).toString(), counts));
        }
        return new ChartSeries(range, entities, days, notes);
    }

    private static Integer parseRange(String text) {
        for (int range : ChartSeries.RANGES) {
            if (text.equals(Integer.toString(range))) {
                return range;
            }
        }
        return null;
    }

    private Answer answer(AdminEntity entity, LocalDate today) {
        long now = clock.millis();
        Answer cached = cache.get(entity.urlName());
        if (cached != null && !cached.isStale(now, today, TTL.toMillis())) {
            return cached;
        }
        Answer fresh = read(entity, today, now);
        cache.put(entity.urlName(), fresh);
        return fresh;
    }

    private Answer read(AdminEntity entity, LocalDate today, long now) {
        ZoneId zone = clock.getZone();
        try {
            CreationDates.Days days = dates.read(entity, today.minusDays(WINDOW_DAYS - 1L), today, zone, rowCap);
            return new Answer(days, now, today);
        } catch (RuntimeException failure) {
            // No stack trace: this is logged again every few seconds while the problem lasts, and the message can name
            // statements. It never reaches the browser (SEC-12).
            log.warn("The JPA Admin console could not read the creation dates of {}: {}. The chart shows the entity as "
                    + "unavailable.", entity.className(), failure.toString());
            return new Answer(null, now, today);
        }
    }

    /** One cached read: the days, or {@code null} when it failed; when it was read and which day it was then. */
    private record Answer(CreationDates.Days days, long readAt, LocalDate readOn) {

        boolean isStale(long now, LocalDate today, long milliseconds) {
            // A clock that jumps backwards looks fresh for at most one window; a new day always asks again.
            return now - readAt >= milliseconds || now < readAt || !readOn.equals(today);
        }
    }
}
