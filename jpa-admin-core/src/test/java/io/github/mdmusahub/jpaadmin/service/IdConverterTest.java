package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The id text from an address or a form becomes the identifier's Java type, or nothing. One group of tests for each id
 * type, and one list of hostile texts that must give the same answer, nothing, for every type (SEC-2: the caller then
 * answers exactly what it answers for an id that has no record).
 */
class IdConverterTest {

    private static AdminEntity entityWithId(Class<?> idType) {
        AdminField id = new AdminField("id", "ID", idType, AdminField.Kind.NUMBER, List.of(), null, true, false, false,
                false, false, false);
        return new AdminEntity(Object.class, "Thing", "thing", "Thing", "Entities", "table", "id", null, List.of(id));
    }

    private static Optional<Object> convert(Class<?> idType, String text) {
        return IdConverter.convert(entityWithId(idType), text);
    }

    // ---- Long -------------------------------------------------------------------------------------------------------

    @Test
    void aLongIdIsConvertedToALong() {
        assertThat(convert(Long.class, "42")).contains(42L);
        assertThat(convert(long.class, "42")).as("a primitive id").contains(42L);
        assertThat(convert(Long.class, "0")).contains(0L);
        assertThat(convert(Long.class, "-7")).contains(-7L);
        assertThat(convert(Long.class, "9223372036854775807")).contains(Long.MAX_VALUE);
        assertThat(convert(Long.class, "-9223372036854775808")).contains(Long.MIN_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9223372036854775808", "-9223372036854775809", "99999999999999999999",
            "123456789012345678901234567890"})
    void aLongOutOfRangeIsNothing(String text) {
        assertThat(convert(Long.class, text)).isEmpty();
    }

    // ---- Integer, Short, Byte ---------------------------------------------------------------------------------------

    @Test
    void anIntegerIdIsConvertedToAnInteger() {
        assertThat(convert(Integer.class, "42")).contains(42);
        assertThat(convert(int.class, "-1")).contains(-1);
        assertThat(convert(Integer.class, "2147483647")).contains(Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483648", "-2147483649", "4294967296"})
    void anIntegerOutOfRangeIsNothing(String text) {
        assertThat(convert(Integer.class, text)).isEmpty();
    }

    @Test
    void aShortAndAByteIdAreConvertedToTheirOwnType() {
        assertThat(convert(Short.class, "300")).contains((short) 300);
        assertThat(convert(Byte.class, "100")).contains((byte) 100);
        assertThat(convert(Short.class, "32768")).isEmpty();
        assertThat(convert(Byte.class, "128")).isEmpty();
    }

    @Test
    void aBigIntegerIdIsConvertedToABigInteger() {
        assertThat(convert(BigInteger.class, "123456789012345678901234567890"))
                .contains(new BigInteger("123456789012345678901234567890"));
        assertThat(convert(BigInteger.class, "1".repeat(41))).as("more than 40 digits").isEmpty();
    }

    // ---- UUID -------------------------------------------------------------------------------------------------------

    @Test
    void aUuidIdIsConvertedToAUuid() {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        assertThat(convert(UUID.class, "123e4567-e89b-12d3-a456-426614174000")).contains(id);
        assertThat(convert(UUID.class, "123E4567-E89B-12D3-A456-426614174000")).as("upper case").contains(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1-1-1-1-1", "123e4567e89b12d3a456426614174000", "123e4567-e89b-12d3-a456-42661417400",
            "123e4567-e89b-12d3-a456-4266141740000", "g23e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-42661417400z", " 123e4567-e89b-12d3-a456-426614174000",
            "{123e4567-e89b-12d3-a456-426614174000}", "not-a-uuid"})
    void aMalformedUuidIsNothing(String text) {
        assertThat(convert(UUID.class, text)).isEmpty();
    }

    // ---- String -----------------------------------------------------------------------------------------------------

    @Test
    void aTextIdIsKeptAsText() {
        assertThat(convert(String.class, "SKU-1234")).contains("SKU-1234");
        assertThat(convert(String.class, "with space")).contains("with space");
        assertThat(convert(String.class, "café")).contains("café");
        assertThat(convert(String.class, "1 or 1=1")).as("it is a bound parameter, so it is only an id that is not found")
                .contains("1 or 1=1");
        assertThat(convert(String.class, "a".repeat(IdConverter.MAX_TEXT_LENGTH))).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {".", "..", "line\nbreak", "tab\there", "nul\u0000byte", "bell\u0007", "a/b", "../x", "..\\x"})
    void aTextIdThatIsDotsOrHasAControlCharacterIsNothing(String text) {
        assertThat(convert(String.class, text)).isEmpty();
    }

    @Test
    void anOverlongTextIdIsNothing() {
        assertThat(convert(String.class, "a".repeat(IdConverter.MAX_TEXT_LENGTH + 1))).isEmpty();
    }

    // ---- hostile text, every type -----------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1 or 1=1", "1; drop table x", "../x", "..\\x", "%2e%2e", "1/2", "-", "--1", "+1", "1+1",
            "1.0", "1e3", "0x10", "007", "-0", " 1", "1 ", "\t1", "1\n", "١٢", "<script>", "null", "NaN", "'", "\""})
    void hostileTextIsNothingForEveryNumericType(String text) {
        for (Class<?> type : List.of(Long.class, Integer.class, Short.class, Byte.class, BigInteger.class)) {
            assertThat(convert(type, text)).as(type.getSimpleName() + " " + text.replace("\n", "\\n")).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1 or 1=1", "../x", "<script>", "%2e%2e", "0", "1"})
    void hostileTextIsNothingForAUuid(String text) {
        assertThat(convert(UUID.class, text)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void anEmptyOrMissingTextIsNothingForEveryType(String text) {
        for (Class<?> type : List.of(Long.class, Integer.class, Short.class, Byte.class, BigInteger.class, UUID.class,
                String.class)) {
            assertThat(convert(type, text)).as(type.getSimpleName()).isEmpty();
        }
    }

    @Test
    void anOverlongNumberIsNothing() {
        assertThat(convert(Long.class, "1".repeat(20))).isEmpty();
        assertThat(convert(Long.class, "1".repeat(5000))).isEmpty();
    }

    @Test
    void anIdTypeTheConsoleDoesNotKnowIsNothing() {
        assertThat(convert(Character.class, "a")).isEmpty();
        assertThat(convert(Double.class, "1")).isEmpty();
        assertThat(convert(java.time.LocalDate.class, "2026-01-01")).isEmpty();
    }

    @Test
    void anEntityWithoutAnIdFieldGivesNothing() {
        AdminEntity broken = new AdminEntity(Object.class, "Thing", "thing", "Thing", "Entities", "table", null,
                "no identifier", List.of());

        assertThat(IdConverter.convert(broken, "1")).isEmpty();
    }

    @Test
    void theParameterTypeOfAPrimitiveIdIsItsWrapper() {
        assertThat(IdConverter.parameterType(entityWithId(long.class))).isEqualTo(Long.class);
        assertThat(IdConverter.parameterType(entityWithId(int.class))).isEqualTo(Integer.class);
        assertThat(IdConverter.parameterType(entityWithId(UUID.class))).isEqualTo(UUID.class);
    }
}
