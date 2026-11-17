package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Parcel;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What any text filter over an entity must do, whoever implements it (SEC-3): text a visitor puts in the address is data,
 * never part of the query. It is a contract so that a different, deliberately unsafe implementation can be held to the same
 * checks: {@link ListQueryServiceFilterSafetyTest} runs it against the real service, and mutation check (c) of Phase 6 ran
 * it against a test-only copy that built the query by string concatenation, which it rejected.
 *
 * <p>Three parcels: notes "100% sure", "a_b" and "plain". A safe text filter finds the first for {@code %}, the second for
 * {@code _}, and nothing for an injection attempt, whose text must also not appear in any SQL statement.
 */
abstract class FilterSafetyContract {

    /** How many parcels a text filter on the notes finds for {@code text}. */
    protected abstract long matches(String text);

    @BeforeEach
    void seedThreeParcels() {
        ListTestPersistence.empty();
        ListTestPersistence.inTransaction(em -> {
            for (String[] row : new String[][] {{"R-1", "100% sure"}, {"R-2", "a_b"}, {"R-3", "plain"}}) {
                Parcel parcel = new Parcel();
                parcel.setReference(row[0]);
                parcel.setNotes(row[1]);
                em.persist(parcel);
            }
        });
        RecordingStatementInspector.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"x' or '1'='1", "'; drop table parcel; --", "\" or 1=1 --", "1' union select * from depot --",
            "%' or '%'='", "x') or ('1'='1", "\' or 1=1"})
    void anInjectionAttemptFindsNothingAndItsTextIsInNoStatement(String attempt) {
        assertThat(matches(attempt)).isZero();

        assertThat(RecordingStatementInspector.statements()).isNotEmpty().allSatisfy(sql ->
                assertThat(sql.toLowerCase(Locale.ROOT)).doesNotContain(attempt.toLowerCase(Locale.ROOT))
                        .doesNotContain("1'='1").doesNotContain("drop table"));
        assertThat(ListTestPersistence.entityManager().createQuery("select count(p) from Parcel p", Long.class)
                .getSingleResult()).as("nothing was dropped or changed").isEqualTo(3);
    }

    @Test
    void aPercentSignAndAnUnderscoreAreLiteralNotWildcards() {
        assertThat(matches("%")).isEqualTo(1);
        assertThat(matches("100%")).isEqualTo(1);
        assertThat(matches("_")).isEqualTo(1);
        assertThat(matches("a_b")).isEqualTo(1);
        assertThat(matches("p_ain")).as("an underscore is not 'any one character'").isZero();
        assertThat(matches("%%")).isZero();
    }

    @Test
    void aFilterWithoutSpecialCharactersFindsTheRightRows() {
        assertThat(matches("sure")).isEqualTo(1);
        assertThat(matches("PLAIN")).isEqualTo(1);
        assertThat(matches("nothing like it")).isZero();
    }
}
