package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.metadata.AdminField.Kind;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.ParcelStatus;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Text to value, per type and per invalid text: no exception, a plain sentence, never the typed text. */
class ValueConverterTest {

    private static AdminField field(Class<?> type, Kind kind, boolean required) {
        List<String> constants = kind == Kind.ENUM
                ? Arrays.stream(type.getEnumConstants()).map(c -> ((Enum<?>) c).name()).toList() : List.of();
        return new AdminField("value", "Value", type, kind, constants, null, false, false, !required, required, false, false);
    }

    private static ValueConverter.Result convert(Class<?> type, Kind kind, String text) {
        return ValueConverter.convert(field(type, kind, false), text, 0);
    }

    // ---- empty and required -------------------------------------------------------------------------------------------

    @Test
    void anEmptyOptionalFieldIsNull() {
        for (String empty : new String[] {"", "   ", "\t"}) {
            assertThat(convert(Integer.class, Kind.NUMBER, empty)).isEqualTo(ValueConverter.Result.of(null));
            assertThat(convert(String.class, Kind.TEXT, empty).value()).isNull();
        }
    }

    @Test
    void anEmptyRequiredFieldIsRefusedWithTheRequiredSentence() {
        ValueConverter.Result result = ValueConverter.convert(field(String.class, Kind.TEXT, true), "  ", 0);
        assertThat(result.failed()).isTrue();
        assertThat(result.error()).isEqualTo(FieldMessages.required());
    }

    // ---- text ---------------------------------------------------------------------------------------------------------

    @Test
    void textIsStoredAsGivenAndNotTrimmed() {
        assertThat(convert(String.class, Kind.TEXT, "  hello  ").value()).isEqualTo("  hello  ");
    }

    @Test
    void textLongerThanTheColumnIsRefusedWithTheLimit() {
        ValueConverter.Result result = ValueConverter.convert(field(String.class, Kind.TEXT, false), "x".repeat(31), 30);
        assertThat(result.error()).isEqualTo(FieldMessages.tooLong(30)).doesNotContain("xxx");
        assertThat(ValueConverter.convert(field(String.class, Kind.TEXT, false), "x".repeat(30), 30).failed()).isFalse();
    }

    @Test
    void thereIsAnAbsoluteCapEvenForAColumnWithNoKnownLimit() {
        String huge = "x".repeat(FieldMessages.MAX_TEXT_LENGTH + 1);
        assertThat(ValueConverter.convert(field(String.class, Kind.TEXT, false), huge, 0).error())
                .isEqualTo(FieldMessages.tooLong(FieldMessages.MAX_TEXT_LENGTH));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\u0000b", "a\u0007b", "a\u001bb", "a\u007fb"})
    void controlCharactersAreRefused(String text) {
        assertThat(convert(String.class, Kind.TEXT, text).error()).isEqualTo(FieldMessages.badCharacters());
    }

    @Test
    void tabsAndLineBreaksAreAllowed() {
        assertThat(convert(String.class, Kind.TEXT, "a\tb\nc\r\nd").failed()).isFalse();
    }

    @Test
    void aCharacterNeedsExactlyOne() {
        assertThat(convert(Character.class, Kind.TEXT, "x").value()).isEqualTo('x');
        assertThat(convert(char.class, Kind.TEXT, "xy").error()).isEqualTo(FieldMessages.singleCharacter());
    }

    @Test
    void aUuidNeedsTheStrictLayout() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        assertThat(convert(UUID.class, Kind.TEXT, id).value()).isEqualTo(UUID.fromString(id));
        assertThat(convert(UUID.class, Kind.TEXT, "1-1-1-1-1").error()).isEqualTo(FieldMessages.uuid());
    }

    // ---- numbers ------------------------------------------------------------------------------------------------------

    @Test
    void wholeNumbersOfEveryType() {
        assertThat(convert(Integer.class, Kind.NUMBER, "42").value()).isEqualTo(42);
        assertThat(convert(int.class, Kind.NUMBER, " -7 ").value()).isEqualTo(-7);
        assertThat(convert(Long.class, Kind.NUMBER, "9223372036854775807").value()).isEqualTo(Long.MAX_VALUE);
        assertThat(convert(Short.class, Kind.NUMBER, "32767").value()).isEqualTo((short) 32767);
        assertThat(convert(Byte.class, Kind.NUMBER, "-128").value()).isEqualTo((byte) -128);
        assertThat(convert(BigInteger.class, Kind.NUMBER, "123456789012345678901234567890").value())
                .isEqualTo(new BigInteger("123456789012345678901234567890"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12abc", "1.5", "1e3", "0x10", "--1", "+1", "1 2", "1,000", "", "NaN", "١٢"})
    void textThatIsNotAWholeNumberIsRefusedWithoutAnException(String text) {
        if (text.isEmpty()) {
            return; // empty is not an error: see the empty tests
        }
        ValueConverter.Result result = convert(Integer.class, Kind.NUMBER, text);
        assertThat(result.failed()).as(text).isTrue();
        assertThat(result.error()).isEqualTo(FieldMessages.wholeNumber());
    }

    @Test
    void aWholeNumberOutsideTheTypesRangeIsRefusedWithTheRange() {
        assertThat(convert(Integer.class, Kind.NUMBER, "2147483648").error())
                .isEqualTo(FieldMessages.outOfRange("-2147483648", "2147483647"));
        assertThat(convert(Short.class, Kind.NUMBER, "40000").failed()).isTrue();
        assertThat(convert(Byte.class, Kind.NUMBER, "128").failed()).isTrue();
        assertThat(convert(Long.class, Kind.NUMBER, "9223372036854775808").failed()).isTrue();
    }

    @Test
    void decimalsNeedDigitsAndOnePoint() {
        assertThat(convert(BigDecimal.class, Kind.NUMBER, "19.99").value()).isEqualTo(new BigDecimal("19.99"));
        assertThat(convert(BigDecimal.class, Kind.NUMBER, "-0.5").value()).isEqualTo(new BigDecimal("-0.5"));
        for (String bad : new String[] {"abc", "1e3", "1,5", "1.", ".5", "1.2.3", "Infinity"}) {
            assertThat(convert(BigDecimal.class, Kind.NUMBER, bad).error()).as(bad).isEqualTo(FieldMessages.number());
        }
    }

    @Test
    void floatingPointNumbersAreNeverInfiniteOrNaN() {
        assertThat(convert(Double.class, Kind.NUMBER, "1.5e3").value()).isEqualTo(1500.0);
        assertThat(convert(Double.class, Kind.NUMBER, "1e999").error()).isEqualTo(FieldMessages.number());
        assertThat(convert(Float.class, Kind.NUMBER, "1e50").error()).isEqualTo(FieldMessages.number());
        assertThat(convert(Double.class, Kind.NUMBER, "NaN").error()).isEqualTo(FieldMessages.number());
        assertThat(convert(Double.class, Kind.NUMBER, "0x1p3").error()).isEqualTo(FieldMessages.number());
        assertThat(convert(Double.class, Kind.NUMBER, "1d").error()).isEqualTo(FieldMessages.number());
    }

    // ---- booleans ----------------------------------------------------------------------------------------------------

    @Test
    void aBooleanIsExactlyTrueOrFalse() {
        assertThat(convert(Boolean.class, Kind.BOOLEAN, "true").value()).isEqualTo(true);
        assertThat(convert(boolean.class, Kind.BOOLEAN, "false").value()).isEqualTo(false);
        for (String bad : new String[] {"yes", "on", "1", "TRUE", "True"}) {
            assertThat(convert(Boolean.class, Kind.BOOLEAN, bad).error()).as(bad).isEqualTo(FieldMessages.yesNo());
        }
    }

    // ---- dates --------------------------------------------------------------------------------------------------------

    @Test
    void aDateMustExist() {
        assertThat(convert(LocalDate.class, Kind.DATE, "2026-11-17").value()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(convert(java.sql.Date.class, Kind.DATE, "2024-02-29").value())
                .isEqualTo(java.sql.Date.valueOf("2024-02-29"));
        for (String impossible : new String[] {"2026-02-30", "2026-13-01", "2025-02-29", "2026-00-10", "2026-11-31"}) {
            assertThat(convert(LocalDate.class, Kind.DATE, impossible).error()).as(impossible).isEqualTo(FieldMessages.date());
        }
        for (String shape : new String[] {"17/11/2026", "2026-1-1", "yesterday", "+2026-11-17", "20261117"}) {
            assertThat(convert(LocalDate.class, Kind.DATE, shape).error()).as(shape).isEqualTo(FieldMessages.date());
        }
    }

    @Test
    void aDateTimeAcceptsTheInputsFormsAndMustExist() {
        LocalDateTime expected = LocalDateTime.of(2026, 11, 17, 10, 30);
        assertThat(convert(LocalDateTime.class, Kind.DATETIME, "2026-11-17T10:30").value()).isEqualTo(expected);
        assertThat(convert(LocalDateTime.class, Kind.DATETIME, "2026-11-17T10:30:15").value())
                .isEqualTo(expected.withSecond(15));
        assertThat(convert(LocalDateTime.class, Kind.DATETIME, "2026-11-17T10:30:15.123").value())
                .isEqualTo(expected.withSecond(15).withNano(123_000_000));
        for (String bad : new String[] {"2026-11-17", "2026-11-17T25:00", "2026-11-17T10:61", "2026-02-30T10:00",
                "2026-11-17 10:30", "2026-11-17T10:30Z", "now"}) {
            assertThat(convert(LocalDateTime.class, Kind.DATETIME, bad).error()).as(bad).isEqualTo(FieldMessages.dateTime());
        }
    }

    @Test
    void instantsAndLegacyDatesAreReadAsUtc() {
        Instant instant = Instant.parse("2026-11-17T10:30:00Z");
        assertThat(convert(Instant.class, Kind.DATETIME, "2026-11-17T10:30").value()).isEqualTo(instant);
        assertThat(convert(OffsetDateTime.class, Kind.DATETIME, "2026-11-17T10:30").value())
                .isEqualTo(OffsetDateTime.of(2026, 11, 17, 10, 30, 0, 0, ZoneOffset.UTC));
        assertThat(convert(ZonedDateTime.class, Kind.DATETIME, "2026-11-17T10:30").value())
                .isEqualTo(ZonedDateTime.of(2026, 11, 17, 10, 30, 0, 0, ZoneOffset.UTC));
        assertThat(convert(java.sql.Timestamp.class, Kind.DATETIME, "2026-11-17T10:30").value())
                .isEqualTo(java.sql.Timestamp.from(instant));
        assertThat(convert(java.util.Date.class, Kind.DATETIME, "2026-11-17T10:30").value())
                .isEqualTo(java.util.Date.from(instant));
    }

    // ---- enums --------------------------------------------------------------------------------------------------------

    @Test
    void anEnumIsOneOfItsConstantsExactly() {
        assertThat(convert(ParcelStatus.class, Kind.ENUM, "IN_TRANSIT").value()).isEqualTo(ParcelStatus.IN_TRANSIT);
        for (String bad : new String[] {"in_transit", "SHIPPED", "In transit", "0", "IN_TRANSIT "}) {
            if (bad.endsWith(" ")) {
                assertThat(convert(ParcelStatus.class, Kind.ENUM, bad).value()).isEqualTo(ParcelStatus.IN_TRANSIT);
            } else {
                assertThat(convert(ParcelStatus.class, Kind.ENUM, bad).error()).as(bad).isEqualTo(FieldMessages.chooseListed());
            }
        }
    }

    // ---- nothing leaks ----------------------------------------------------------------------------------------------

    @Test
    void noAnswerRepeatsTheTypedText() {
        String typed = "needle-typed-value-7731";
        for (Kind kind : new Kind[] {Kind.NUMBER, Kind.BOOLEAN, Kind.DATE, Kind.DATETIME}) {
            Class<?> type = switch (kind) {
                case NUMBER -> Integer.class;
                case BOOLEAN -> Boolean.class;
                case DATE -> LocalDate.class;
                default -> LocalDateTime.class;
            };
            assertThat(String.valueOf(convert(type, kind, typed).error())).as(kind.name()).doesNotContain("needle");
        }
        assertThat(String.valueOf(convert(ParcelStatus.class, Kind.ENUM, typed).error())).doesNotContain("needle");
    }

    @Test
    void relationsAndCollectionsAreNotConvertedHere() {
        assertThat(convert(Object.class, Kind.TO_ONE, "1").error()).isEqualTo(FieldMessages.cannotBeSet());
        assertThat(convert(Object.class, Kind.TO_MANY, "1").error()).isEqualTo(FieldMessages.cannotBeSet());
        assertThat(convert(Object.class, Kind.BINARY, "1").error()).isEqualTo(FieldMessages.cannotBeSet());
    }
}
