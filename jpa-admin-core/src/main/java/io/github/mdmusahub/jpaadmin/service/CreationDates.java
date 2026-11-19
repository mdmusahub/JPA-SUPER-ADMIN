package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the creation dates of one entity for the dashboard chart and counts them per day (Phase 10, D-101).
 *
 * <p>One Criteria query per call: it selects <em>only</em> the creation column (never an entity, never another
 * attribute, so a sensitive value cannot come with it, SEC-1), keeps the rows between two bound parameters, orders them
 * newest first and reads at most {@code rowCap + 1} of them. The rows are then put into days in Java, in the zone that
 * is passed in. No database function is involved, so the same query runs on every database the host uses (H2,
 * PostgreSQL, MySQL).
 *
 * <p>This class is separate from {@link StatsService} on purpose: its method is the unit of a read-only transaction
 * (ARCH-5), so every entity is read in a transaction of its own. On a database that aborts a transaction after a failed
 * statement (PostgreSQL), one broken table then cannot make the next entity fail.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it.
 */
public class CreationDates {

    private final EntityManager entityManager;

    /** Not private: Spring makes the transactional proxy by subclassing, which needs a constructor it may call. */
    protected CreationDates(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * The usual reader.
     *
     * @param entityManager the console's transaction-aware entity manager
     */
    public static CreationDates over(EntityManager entityManager) {
        return new CreationDates(Objects.requireNonNull(entityManager, "entityManager"));
    }

    /** A reader that can read nothing: every read fails, so the chart says "unavailable". */
    public static CreationDates unavailable() {
        return new CreationDates(null);
    }

    /**
     * The days of one entity.
     *
     * @param perDay          records created per day (only days with at least one record)
     * @param capped          whether the table held more rows in the window than were read
     * @param incompleteThrough when {@code capped}: the oldest day that was reached; that day and every earlier one may be
     *                        incomplete. {@code null} otherwise
     */
    public record Days(Map<LocalDate, Long> perDay, boolean capped, LocalDate incompleteThrough) {
        public Days {
            perDay = Map.copyOf(perDay);
            if (capped != (incompleteThrough != null)) {
                throw new IllegalArgumentException("incompleteThrough is present exactly when capped");
            }
        }
    }

    /**
     * Counts the records of {@code entity} created from {@code first} to {@code last}, both included, per day of
     * {@code zone}.
     *
     * @param entity an entity from the registry that has a creation field
     * @param first  the first day of the window
     * @param last   the last day of the window
     * @param zone   the zone that decides which day a moment belongs to (a {@code LocalDate} or {@code LocalDateTime}
     *               value is already a calendar value and is not converted)
     * @param rowCap the most rows to read
     * @throws RuntimeException when the table cannot be read; the caller turns that into "unavailable"
     */
    @Transactional(readOnly = true)
    public Days read(AdminEntity entity, LocalDate first, LocalDate last, ZoneId zone, int rowCap) {
        Objects.requireNonNull(entity, "entity");
        if (entityManager == null) {
            throw new IllegalStateException("no entity manager is available");
        }
        AdminField field = entity.creationField()
                .orElseThrow(() -> new IllegalArgumentException("the entity has no creation field"));
        Class<?> type = field.javaType();
        List<?> rows = query(entity, field, bound(type, first, zone), bound(type, last.plusDays(1), zone), rowCap + 1);
        boolean capped = rows.size() > rowCap;
        List<?> kept = capped ? rows.subList(0, rowCap) : rows;
        Map<LocalDate, Long> perDay = new HashMap<>();
        LocalDate oldest = null;
        for (Object value : kept) {
            LocalDate day = day(value, zone);
            perDay.merge(day, 1L, Long::sum);
            oldest = day;
        }
        return new Days(perDay, capped, capped ? oldest : null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<?> query(AdminEntity entity, AdminField field, Comparable from, Comparable until, int limit) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> query = builder.createQuery(Object.class);
        Root<?> root = query.from(entity.type());
        Path<Comparable> created = root.get(field.name());
        Class<Comparable> type = (Class<Comparable>) field.javaType();
        ParameterExpression<Comparable> lower = builder.parameter(type, "from");
        ParameterExpression<Comparable> upper = builder.parameter(type, "until");
        query.select(created).where(builder.greaterThanOrEqualTo(created, lower), builder.lessThan(created, upper))
                .orderBy(builder.desc(created));
        TypedQuery<Object> typed = entityManager.createQuery(query);
        typed.setParameter(lower, from);
        typed.setParameter(upper, until);
        typed.setMaxResults(limit);
        return typed.getResultList();
    }

    /** The start of {@code day} as a value of the field's own type. */
    private static Comparable<?> bound(Class<?> type, LocalDate day, ZoneId zone) {
        if (type == LocalDate.class) {
            return day;
        }
        if (type == LocalDateTime.class) {
            return day.atStartOfDay();
        }
        if (type == Instant.class) {
            return day.atStartOfDay(zone).toInstant();
        }
        if (type == OffsetDateTime.class) {
            return day.atStartOfDay(zone).toOffsetDateTime();
        }
        if (type == ZonedDateTime.class) {
            return day.atStartOfDay(zone);
        }
        throw new IllegalArgumentException("not a creation field type");
    }

    /** The day a stored value belongs to in {@code zone}. */
    static LocalDate day(Object value, ZoneId zone) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(zone).toLocalDate();
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.atZoneSameInstant(zone).toLocalDate();
        }
        if (value instanceof ZonedDateTime zoned) {
            return zoned.withZoneSameInstant(zone).toLocalDate();
        }
        throw new IllegalStateException("not a creation value");
    }
}
