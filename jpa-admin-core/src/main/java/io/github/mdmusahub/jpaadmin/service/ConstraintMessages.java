package io.github.mdmusahub.jpaadmin.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a Bean Validation failure into one plain sentence per property. Hibernate Validator is the host's choice, not a
 * dependency of the console: the console works without it, and when the host has it (Spring Boot's validation starter) the
 * persistence layer validates the entity at insert and update and throws {@code jakarta.validation.ConstraintViolationException}
 * (checked in Hibernate ORM 7.4: it is a plain {@code RuntimeException}, not a {@code PersistenceException}). This class finds
 * that exception anywhere in a cause chain and maps each violation.
 *
 * <p>The sentence depends on the <em>kind</em> of constraint ({@code @NotBlank}, {@code @Size}, {@code @Min}, ...) and on
 * its own numbers (a limit), never on the host's message text, the validated value, the class or the property path beyond
 * the first name (SEC-1, SEC-12). A constraint the console has no sentence for gets a general one.
 *
 * <p>The jakarta.validation types are only touched inside {@link Api}, which is loaded only when the API is on the class path,
 * so a host without it never meets a {@code NoClassDefFoundError}.
 *
 * <p>Package-private: it is a rule, not API.
 */
final class ConstraintMessages {

    /** One violation: the first name of the property path and the sentence. */
    record Violation(String property, String message) {
    }

    private static final boolean AVAILABLE = classPresent("jakarta.validation.ConstraintViolationException");

    private ConstraintMessages() {
    }

    /** Whether the validation API is on the class path. */
    static boolean available() {
        return AVAILABLE;
    }

    /**
     * The violations carried by {@code failure} or by anything in its cause chain, or an empty list when there are none (or
     * the API is absent).
     */
    static List<Violation> violationsOf(Throwable failure) {
        if (!AVAILABLE) {
            return List.of();
        }
        List<Violation> found = new ArrayList<>();
        int depth = 0;
        for (Throwable current = failure; current != null && depth < 20; current = current.getCause(), depth++) {
            found.addAll(Api.violations(current));
        }
        return found;
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name, false, ConstraintMessages.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    /** The sentence for a constraint of this kind with these attributes. Public to the package for the tests. */
    static String sentence(String kind, Map<String, Object> attributes) {
        return switch (kind) {
            case "NotNull", "NotBlank", "NotEmpty" -> FieldMessages.required();
            case "Size", "Length" -> size(attributes);
            case "Min", "DecimalMin" -> "Enter a number that is " + (inclusive(attributes) ? "at least " : "greater than ")
                    + attributes.get("value") + ".";
            case "Max", "DecimalMax" -> "Enter a number that is " + (inclusive(attributes) ? "at most " : "less than ")
                    + attributes.get("value") + ".";
            case "Positive" -> "Enter a number greater than zero.";
            case "PositiveOrZero" -> "Enter zero or a number greater than zero.";
            case "Negative" -> "Enter a number less than zero.";
            case "NegativeOrZero" -> "Enter zero or a number less than zero.";
            case "Digits" -> "Enter a number with at most " + attributes.get("integer") + " digits before and "
                    + attributes.get("fraction") + " after the decimal point.";
            case "Email" -> "Enter a valid email address, for example name@example.com.";
            case "Pattern" -> "Use the format this field asks for.";
            case "Past", "PastOrPresent" -> "Enter a date or time in the past.";
            case "Future", "FutureOrPresent" -> "Enter a date or time in the future.";
            default -> FieldMessages.notAccepted();
        };
    }

    private static boolean inclusive(Map<String, Object> attributes) {
        return !Boolean.FALSE.equals(attributes.get("inclusive"));
    }

    private static String size(Map<String, Object> attributes) {
        long min = number(attributes.get("min"), 0);
        long max = number(attributes.get("max"), Integer.MAX_VALUE);
        if (min > 0 && max < Integer.MAX_VALUE) {
            return "Use between " + min + " and " + max + " characters.";
        }
        if (max < Integer.MAX_VALUE) {
            return FieldMessages.tooLong((int) max);
        }
        return min > 0 ? "Use at least " + min + " characters." : FieldMessages.notAccepted();
    }

    private static long number(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    /** Everything that names a jakarta.validation type lives here and nowhere else. */
    private static final class Api {

        private Api() {
        }

        static List<Violation> violations(Throwable failure) {
            if (!(failure instanceof jakarta.validation.ConstraintViolationException exception)) {
                return List.of();
            }
            List<Violation> found = new ArrayList<>();
            for (jakarta.validation.ConstraintViolation<?> violation : exception.getConstraintViolations()) {
                String kind = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
                found.add(new Violation(firstName(violation), sentence(kind,
                        violation.getConstraintDescriptor().getAttributes())));
            }
            return found;
        }

        private static String firstName(jakarta.validation.ConstraintViolation<?> violation) {
            for (jakarta.validation.Path.Node node : violation.getPropertyPath()) {
                if (node.getName() != null) {
                    return node.getName();
                }
            }
            return "";
        }
    }
}
