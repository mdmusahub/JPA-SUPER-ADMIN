package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The text a form input holds for a stored value, and that the converter reads it back. */
class FormTextTest {

    @Test
    void nullStaysNull() {
        assertThat(FormText.of(null)).isNull();
    }

    @Test
    void numbersHaveNoGroupingOrExponent() {
        assertThat(FormText.of(new BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(FormText.of(new BigDecimal("19.90"))).isEqualTo("19.90");
        assertThat(FormText.of(1234567)).isEqualTo("1234567");
        assertThat(FormText.of(true)).isEqualTo("true");
    }

    @Test
    void enumsUseTheirConstantName() {
        assertThat(FormText.of(ParcelStatus.IN_TRANSIT)).isEqualTo("IN_TRANSIT");
    }

    @Test
    void datesAreIso() {
        assertThat(FormText.of(LocalDate.of(2026, 11, 17))).isEqualTo("2026-11-17");
        assertThat(FormText.of(java.sql.Date.valueOf("2026-11-17"))).isEqualTo("2026-11-17");
    }

    @Test
    void dateTimesStopAtTheMillisecondBecauseThatIsWhatTheInputHolds() {
        assertThat(FormText.of(LocalDateTime.of(2026, 11, 17, 10, 30))).isEqualTo("2026-11-17T10:30");
        assertThat(FormText.of(LocalDateTime.of(2026, 11, 17, 10, 30, 15, 123_456_789)))
                .isEqualTo("2026-11-17T10:30:15.123");
    }

    @Test
    void instantsAndOffsetTimesAreShownInUtc() {
        assertThat(FormText.of(Instant.parse("2026-11-17T10:30:00Z"))).isEqualTo("2026-11-17T10:30");
        assertThat(FormText.of(OffsetDateTime.of(2026, 11, 17, 12, 30, 0, 0, ZoneOffset.ofHours(2))))
                .isEqualTo("2026-11-17T10:30");
        assertThat(FormText.of(ZonedDateTime.of(2026, 11, 17, 12, 30, 0, 0, ZoneOffset.ofHours(2))))
                .isEqualTo("2026-11-17T10:30");
        assertThat(FormText.of(java.sql.Timestamp.from(Instant.parse("2026-11-17T10:30:00Z")))).isEqualTo("2026-11-17T10:30");
        assertThat(FormText.of(java.util.Date.from(Instant.parse("2026-11-17T10:30:00Z")))).isEqualTo("2026-11-17T10:30");
    }

    @Test
    void whatTheFormShowsIsWhatTheConverterReadsBack() {
        LocalDateTime time = LocalDateTime.of(2026, 11, 17, 10, 30, 15, 500_000_000);
        assertThat(FormText.of(time)).isEqualTo("2026-11-17T10:30:15.500");
        UUID id = UUID.randomUUID();
        assertThat(FormText.of(id)).isEqualTo(id.toString());
    }
}
