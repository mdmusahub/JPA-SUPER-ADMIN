package io.github.mdmusahub.jpaadmin.testsupport.statsmodel;

import io.github.mdmusahub.jpaadmin.annotation.AdminHidden;
import io.github.mdmusahub.jpaadmin.annotation.AdminSensitive;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * Neutral entities for the chart tests (Phase 10): one for each accepted type of creation field, one with none, and the
 * ones the rule must rule out (a sensitive creation field, a hidden one, a hidden entity). None is a demo entity
 * (ARCH-12). Plain public fields: these are test fixtures.
 */
public final class StatsEntities {

    private StatsEntities() {
    }

    /** A {@code LocalDateTime} called {@code createdAt}. */
    @Entity(name = "Journal")
    public static class Journal {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;
        public String title;
        public LocalDateTime createdAt;
    }

    /** An {@code Instant} called {@code createdOn}: the type whose day depends on the zone. */
    @Entity(name = "Beacon")
    public static class Beacon {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;
        public Instant createdOn;
    }

    /** A {@code LocalDate} called {@code creationDate}. */
    @Entity(name = "Voucher")
    public static class Voucher {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;
        public LocalDate creationDate;
    }

    /** An {@code OffsetDateTime} called {@code createTime}. */
    @Entity(name = "Stub")
    public static class Stub {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;
        public OffsetDateTime createTime;
    }

    /** A {@code ZonedDateTime} called {@code createdDateTime}. */
    @Entity(name = "Relic")
    public static class Relic {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;
        public ZonedDateTime createdDateTime;
    }

    /** No creation field at all: left out of the chart. */
    @Entity(name = "Plain")
    public static class Plain {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;
        public String name;
        public LocalDateTime updatedAt;
    }

    /** Its only creation-like field is sensitive, so the entity is left out. */
    @Entity(name = "Masked")
    public static class Masked {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;
        @AdminSensitive
        public LocalDateTime createdAt;
    }

    /** Its first-choice name is sensitive, so the next acceptable name ({@code creationDate}) is used. */
    @Entity(name = "Fallback")
    public static class Fallback {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;
        @AdminSensitive
        public LocalDateTime createdAt;
        public LocalDate creationDate;
    }

    /** Its only creation-like field is hidden, so the entity is left out. */
    @Entity(name = "Veiled")
    public static class Veiled {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;
        @AdminHidden
        public LocalDateTime createdAt;
    }

    /** A hidden entity with a perfect creation field: it must never reach the chart or the JSON. */
    @Entity(name = "Shadow")
    @AdminHidden
    public static class Shadow {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;
        public LocalDateTime createdAt;
    }
}
