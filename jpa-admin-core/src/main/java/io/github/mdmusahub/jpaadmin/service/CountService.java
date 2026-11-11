package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts the records of an entity for the sidebar and the dashboard.
 *
 * <p>One cheap {@code select count(...)} per entity, built with the Criteria API and never with string
 * concatenation (SEC-3), inside a read-only transaction (ARCH-5). The sidebar is on every page, so the answers are
 * cached for {@value #TTL_SECONDS} seconds: inside that window a repeated page costs no query at all, and after it
 * each entity costs one query again. The window is short on purpose, because a count that is half a minute old
 * would look wrong right after someone added a record.
 *
 * <p>A count that fails (a missing table, a database that is down, no rights) does not break the page: that entity
 * reports an unavailable count and the failure is logged once per window, with the cause's message and without a
 * stack trace, which would otherwise fill the host's log on every page.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it and the pages use it.
 */
public class CountService {

    /** How long a count is reused. An internal constant, deliberately not a property. */
    public static final int TTL_SECONDS = 5;

    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);
    private static final Logger log = LoggerFactory.getLogger(CountService.class);

    private final EntityManager entityManager;
    private final Clock clock;
    private final Map<String, Answer> cache = new ConcurrentHashMap<>();

    /**
     * Not public, because {@link #over} and {@link #unavailable} say which of the two states is meant. Not private
     * either: Spring makes the transactional proxy by subclassing this class, and it can only do that when it finds
     * a constructor it may call.
     */
    protected CountService(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The usual service.
     *
     * @param entityManager the console's transaction-aware entity manager
     * @param clock         the clock the cache measures its window with; tests hand in their own (TEST-6)
     */
    public static CountService over(EntityManager entityManager, Clock clock) {
        return new CountService(Objects.requireNonNull(entityManager, "entityManager"), clock);
    }

    /**
     * A service that can count nothing, because the console has no entity manager factory to ask (the host has
     * several and marks none as primary). Every count is unavailable. The console then lists no entities either,
     * so nothing asks this service for a number.
     */
    public static CountService unavailable(Clock clock) {
        return new CountService(null, clock);
    }

    /**
     * The number of records of {@code entity}, or empty when it could not be read.
     *
     * @param entity an entity from the registry
     */
    @Transactional(readOnly = true)
    public OptionalLong count(AdminEntity entity) {
        Objects.requireNonNull(entity, "entity");
        long now = clock.millis();
        Answer answer = cache.get(entity.urlName());
        if (answer != null && !answer.isOlderThan(now, TTL.toMillis())) {
            return answer.value();
        }
        Answer fresh = ask(entity, now);
        cache.put(entity.urlName(), fresh);
        return fresh.value();
    }

    /**
     * The counts of several entities, in the order they were given. One map lookup or one query per entity, never
     * more (the sidebar and the dashboard of one page share this).
     *
     * @param entities entities from the registry
     */
    @Transactional(readOnly = true)
    public Map<String, OptionalLong> countAll(Iterable<AdminEntity> entities) {
        Map<String, OptionalLong> counts = new LinkedHashMap<>();
        for (AdminEntity entity : entities) {
            counts.put(entity.urlName(), count(entity));
        }
        return counts;
    }

    /** Forgets every cached count. For tests and for a later "refresh" action. */
    public void clear() {
        cache.clear();
    }

    private Answer ask(AdminEntity entity, long now) {
        if (entityManager == null) {
            return new Answer(OptionalLong.empty(), now);
        }
        try {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = builder.createQuery(Long.class);
            query.select(builder.count(query.from(entity.type())));
            Long result = entityManager.createQuery(query).getSingleResult();
            return new Answer(result == null ? OptionalLong.empty() : OptionalLong.of(result), now);
        } catch (RuntimeException failure) {
            // No stack trace: this is logged again every few seconds while the problem lasts. The cause's own
            // message is enough to find it, and it never reaches the browser (SEC-12).
            log.warn("The JPA Admin console could not count the records of {}: {}. The console shows the count as "
                    + "unavailable.", entity.className(), failure.toString());
            log.debug("The count of {} failed.", entity.className(), failure);
            return new Answer(OptionalLong.empty(), now);
        }
    }

    /** One cached answer: the count (or nothing) and when it was read. */
    private record Answer(OptionalLong value, long readAt) {

        boolean isOlderThan(long now, long milliseconds) {
            // A clock that jumps backwards makes the answer look fresh for at most one window, never forever.
            return now - readAt >= milliseconds || now < readAt;
        }
    }
}
