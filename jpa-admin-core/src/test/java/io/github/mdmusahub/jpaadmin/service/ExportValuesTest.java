package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.web.view.Cell;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/** How a value becomes the text of a CSV cell (Phase 11, D-105, D-109): plain numbers, ISO dates, nulls, relations, text. */
class ExportValuesTest {

    private enum Level { OUT_OF_STOCK, LOW }

    private static String text(Cell.Type type, Object value) {
        return ExportValues.text(type, value);
    }

    // ---- numbers -----------------------------------------------------------------------------------------------------------

    @Test
    void wholeNumbersAreWrittenWithNoGrouping() {
        assertThat(text(Cell.Type.NUMBER, 1234567)).isEqualTo("1234567");
        assertThat(text(Cell.Type.NUMBER, 1234567890123L)).isEqualTo("1234567890123");
        assertThat(text(Cell.Type.NUMBER, (short) -7)).isEqualTo("-7");
        assertThat(text(Cell.Type.NUMBER, new BigInteger("123456789012345678901234567890")))
                .isEqualTo("123456789012345678901234567890");
    }

    @Test
    void decimalsUseAPointAndNeverAnExponent() {
        assertThat(text(Cell.Type.NUMBER, new BigDecimal("1234.50"))).isEqualTo("1234.50");
        assertThat(text(Cell.Type.NUMBER, new BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(text(Cell.Type.NUMBER, new BigDecimal("0.00000012"))).isEqualTo("0.00000012");
        assertThat(text(Cell.Type.NUMBER, 12345678.9d)).isEqualTo("12345678.9");
        assertThat(text(Cell.Type.NUMBER, 1.0E10d)).isEqualTo("10000000000");
        assertThat(text(Cell.Type.NUMBER, 0.1f)).isEqualTo("0.1");
    }

    @Test
    void notANumberAndInfinityAreWrittenAsWords() {
        assertThat(text(Cell.Type.NUMBER, Double.NaN)).isEqualTo("NaN");
        assertThat(text(Cell.Type.NUMBER, Double.POSITIVE_INFINITY)).isEqualTo("Infinity");
        assertThat(text(Cell.Type.NUMBER, Float.NEGATIVE_INFINITY)).isEqualTo("-Infinity");
    }

    // ---- dates -----------------------------------------------------------------------------------------------------------------

    @Test
    void aLocalDateIsIsoDate() {
        assertThat(text(Cell.Type.DATE, LocalDate.of(2026, 3, 9))).isEqualTo("2026-03-09");
        assertThat(text(Cell.Type.DATE, java.sql.Date.valueOf("2026-03-09"))).isEqualTo("2026-03-09");
    }

    @Test
    void aLocalDateTimeIsIsoWithoutAZone() {
        assertThat(text(Cell.Type.DATE, LocalDateTime.of(2026, 11, 19, 10, 15, 30))).isEqualTo("2026-11-19T10:15:30");
        assertThat(text(Cell.Type.DATE, LocalDateTime.of(2026, 11, 19, 10, 15, 30, 123_000_000)))
                .isEqualTo("2026-11-19T10:15:30.123");
    }

    @Test
    void anOffsetAndAZonedDateTimeKeepTheirOwnOffsetAndAreNotConverted() {
        OffsetDateTime offset = OffsetDateTime.of(2026, 11, 19, 10, 15, 30, 0, ZoneOffset.ofHoursMinutes(5, 30));
        ZonedDateTime zoned = ZonedDateTime.of(2026, 7, 1, 8, 0, 0, 0, ZoneId.of("Europe/Paris"));

        assertThat(text(Cell.Type.DATE, offset)).isEqualTo("2026-11-19T10:15:30+05:30");
        assertThat(text(Cell.Type.DATE, zoned)).isEqualTo("2026-07-01T08:00:00+02:00");
    }

    @Test
    void anInstantEndsInZ() {
        assertThat(text(Cell.Type.DATE, Instant.parse("2026-11-19T10:15:30Z"))).isEqualTo("2026-11-19T10:15:30Z");
        assertThat(text(Cell.Type.DATE, Timestamp.from(Instant.parse("2026-11-19T10:15:30Z"))))
                .isEqualTo("2026-11-19T10:15:30Z");
        assertThat(text(Cell.Type.DATE, new Date(0))).isEqualTo("1970-01-01T00:00:00Z");
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        calendar.setTimeInMillis(0);
        assertThat(text(Cell.Type.DATE, calendar)).isEqualTo("1970-01-01T00:00:00Z");
    }

    // ---- other kinds ---------------------------------------------------------------------------------------------------------

    @Test
    void booleansAreTrueAndFalse() {
        assertThat(text(Cell.Type.BOOLEAN, true)).isEqualTo("true");
        assertThat(text(Cell.Type.BOOLEAN, false)).isEqualTo("false");
    }

    @Test
    void anEnumIsItsConstantNameNotTheListsHumanisedText() {
        assertThat(text(Cell.Type.ENUM, Level.OUT_OF_STOCK)).isEqualTo("OUT_OF_STOCK");
        assertThat(text(Cell.Type.ENUM, "LOW")).isEqualTo("LOW");
    }

    @Test
    void textIsCompleteAndKeepsItsLineBreaks() {
        String long300 = "x".repeat(300) + "\nsecond line";

        assertThat(text(Cell.Type.TEXT, long300)).isEqualTo(long300);
    }

    @Test
    void nullIsNullForEveryKind() {
        for (Cell.Type type : new Cell.Type[] {Cell.Type.TEXT, Cell.Type.NUMBER, Cell.Type.BOOLEAN, Cell.Type.ENUM, Cell.Type.DATE}) {
            assertThat(text(type, null)).as(type.name()).isNull();
        }
    }

    @Test
    void anUnknownObjectIsItsToStringButACollectionMapOrArrayIsEmpty() {
        assertThat(text(Cell.Type.TEXT, java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000")))
                .isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        assertThat(text(Cell.Type.TEXT, java.time.LocalTime.of(10, 15))).isEqualTo("10:15");
        assertThat(text(Cell.Type.TEXT, List.of("secret"))).isNull();
        assertThat(text(Cell.Type.TEXT, Map.of("k", "secret"))).isNull();
        assertThat(text(Cell.Type.TEXT, new byte[] {1, 2, 3})).isNull();
    }

    // ---- relations -------------------------------------------------------------------------------------------------------------

    @Test
    void aRelationIsTheLabelWhenThereIsOne() {
        assertThat(ExportValues.relation("North depot", 7L)).isEqualTo("North depot");
    }

    @Test
    void aRelationWithNoLabelIsThePlainIdWithoutTheHash() {
        assertThat(ExportValues.relation(null, 7L)).isEqualTo("7");
        assertThat(ExportValues.relation("   ", 7L)).isEqualTo("7");
        assertThat(ExportValues.relation(null, "abc")).isEqualTo("abc");
    }

    @Test
    void aRelationThatIsNotSetIsNullEvenWhenALabelWouldExist() {
        assertThat(ExportValues.relation(null, null)).isNull();
        assertThat(ExportValues.relation("orphan label", null)).isNull();
    }
}
