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
 * What any search over an entity's text must do, whoever implements it (SEC-3): text a visitor types is data, never
 * part of the query. It is a contract so that a different (or a deliberately unsafe) implementation can be held to
 * exactly the same checks: {@link ListQueryServiceSearchSafetyTest} runs it against the real service, and the mutation
 * check (k) of Phase 4 ran it against a copy that built the query by string concatenation, which it rejected.
 *
 * <p>Three parcels: one whose notes say "100% sure", one with "a_b", one plain. A search that is safe finds the first
 * for {@code %}, the second for {@code _}, and nothing for an injection attempt, whose text must also not appear in any
 * SQL statement.
 */
abstract class SearchSafetyContract {

    /** How many parcels a search for {@code text} finds. */
    protected abstract long matches(String text);

    @BeforeEach
    void seedThreeParcels() {
        ListTestPersistence.empty();
        ListTestPersistence.inTransaction(em -> {
            for (String[] row : new String[][] {{"PKG-1", "100% sure"}, {"PKG-2", "a_b"}, {"PKG-3", "plain"}}) {
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
            "%' or '%'='", "x') or ('1'='1", "\\' or 1=1"})
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
        assertThat(matches("pkg-_")).as("an underscore is not 'any one character'").isZero();
        assertThat(matches("%%")).isZero();
    }

    @Test
    void aSearchWithoutSpecialCharactersFindsTheRightRows() {
        assertThat(matches("pkg")).isEqualTo(3);
        assertThat(matches("PKG-2")).isEqualTo(1);
        assertThat(matches("plain")).isEqualTo(1);
        assertThat(matches("nothing like it")).isZero();
    }
}
