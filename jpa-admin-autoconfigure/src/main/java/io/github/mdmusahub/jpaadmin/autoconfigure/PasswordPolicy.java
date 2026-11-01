package io.github.mdmusahub.jpaadmin.autoconfigure;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * What counts as a weak admin password (SEC-7): shorter than {@value #MIN_LENGTH} characters, or on a short built-in
 * list of well-known passwords (compared ignoring case). The list is small on purpose; it catches the passwords
 * people really try first. It is a safety net, not a substitute for a long random secret.
 *
 * <p>Results never contain the password.
 */
final class PasswordPolicy {

    static final int MIN_LENGTH = 12;

    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password12", "password123", "password1234", "password12345", "passw0rd",
            "p@ssw0rd", "p@ssword", "pa$$word", "passwordpassword", "admin", "admin1", "admin12", "admin123",
            "admin1234", "admin12345", "admin123456", "administrator", "administrator1", "adminadmin",
            "adminpassword", "admin@123", "admin@1234", "root", "rootroot", "toor", "changeme", "changeit",
            "changemenow", "changeme123", "letmein", "letmein123", "welcome", "welcome1", "welcome123", "secret",
            "secret123", "default", "guest", "test", "test1234", "demo", "qwerty", "qwerty123", "qwertyuiop",
            "qwertyuiop123", "asdfghjkl", "zxcvbnm", "1q2w3e4r", "1qaz2wsx", "zaq12wsx", "abc123", "abcd1234",
            "iloveyou", "monkey", "dragon", "sunshine", "princess", "football", "baseball", "master", "login",
            "trustno1", "superman", "123456", "1234567", "12345678", "123456789", "1234567890", "123456789012",
            "111111", "000000", "123123", "123321", "654321", "jpaadmin", "jpa-admin", "springboot", "spring",
            "correcthorsebatterystaple");

    private PasswordPolicy() {
    }

    /**
     * Why {@code password} is weak, as text that can be shown to the operator, or empty when it is acceptable.
     * The text never contains the password.
     */
    static Optional<String> weakness(String password) {
        if (password.length() < MIN_LENGTH) {
            return Optional.of("it is shorter than " + MIN_LENGTH + " characters");
        }
        if (COMMON.contains(password.toLowerCase(Locale.ROOT))) {
            return Optional.of("it is on the list of common passwords");
        }
        return Optional.empty();
    }
}
