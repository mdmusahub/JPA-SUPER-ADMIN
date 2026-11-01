package io.github.mdmusahub.jpaadmin.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** SEC-7: shorter than 12 characters, or a common password, is weak. */
class PasswordPolicyTest {

    @Test
    void elevenCharactersAreTooShortAndTwelveAreEnough() {
        assertThat(PasswordPolicy.weakness("x7Gq-4mZp2v")).hasValue("it is shorter than 12 characters");
        assertThat(PasswordPolicy.weakness("x7Gq-4mZp2vK")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "admin", "password", "Admin123", "changeme"})
    void shortPasswordsAreWeak(String password) {
        assertThat(PasswordPolicy.weakness(password)).hasValue("it is shorter than 12 characters");
    }

    @ParameterizedTest
    @ValueSource(strings = {"correcthorsebatterystaple", "CorrectHorseBatteryStaple", "PASSWORDPASSWORD",
            "Administrator1", "123456789012", "QWERTYUIOP123", "passwordpassword"})
    void longButCommonPasswordsAreWeakIgnoringCase(String password) {
        assertThat(PasswordPolicy.weakness(password)).hasValue("it is on the list of common passwords");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tr0ub4dor&3-horse", "x7Gq-4mZp2vK", "this is a long passphrase", "pa$$w{0}rd-long-enough"})
    void longUncommonPasswordsAreAccepted(String password) {
        assertThat(PasswordPolicy.weakness(password)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short-1", "CorrectHorseBatteryStaple"})
    void theReasonNeverContainsThePassword(String password) {
        assertThat(PasswordPolicy.weakness(password)).hasValueSatisfying(reason -> assertThat(reason).doesNotContain(password));
    }
}
