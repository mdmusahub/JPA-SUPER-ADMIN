package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A Bean Validation failure becomes one plain sentence per property. Hibernate Validator is not on this module's test class
 * path (it is the host's choice), so the violations are hand-made; the sample app proves the real thing end to end.
 */
class ConstraintMessagesTest {

    static class Holder {
        @NotBlank
        String blank;
        @Size(min = 2, max = 9)
        String size;
        @Min(5)
        int min;
    }

    static Annotation annotation(String field) throws Exception {
        return Holder.class.getDeclaredField(field).getAnnotations()[0];
    }

    /** A violation made by hand: the property path, the constraint annotation and a validated value that must never leak. */
    static ConstraintViolation<?> violation(String property, Annotation constraint, Map<String, Object> attributes) {
        Path.Node node = (Path.Node) Proxy.newProxyInstance(ConstraintMessagesTest.class.getClassLoader(),
                new Class<?>[] {Path.Node.class}, (proxy, method, args) -> method.getName().equals("getName") ? property : null);
        Path path = (Path) Proxy.newProxyInstance(ConstraintMessagesTest.class.getClassLoader(), new Class<?>[] {Path.class},
                (proxy, method, args) -> method.getName().equals("iterator") ? List.of(node).iterator() : null);
        ConstraintDescriptor<?> descriptor = (ConstraintDescriptor<?>) Proxy.newProxyInstance(
                ConstraintMessagesTest.class.getClassLoader(), new Class<?>[] {ConstraintDescriptor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAnnotation" -> constraint;
                    case "getAttributes" -> attributes;
                    default -> null;
                });
        return (ConstraintViolation<?>) Proxy.newProxyInstance(ConstraintMessagesTest.class.getClassLoader(),
                new Class<?>[] {ConstraintViolation.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPropertyPath" -> path;
                    case "getConstraintDescriptor" -> descriptor;
                    case "getMessage" -> "message with needle-validated-value";
                    case "getInvalidValue" -> "needle-validated-value";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "violation";
                    default -> null;
                });
    }

    @Test
    void theApiIsOnTheClassPathHere() {
        assertThat(ConstraintMessages.available()).isTrue();
    }

    @Test
    void aViolationBecomesASentenceForItsPropertyWithoutTheHostsMessageOrTheValue() throws Exception {
        Set<ConstraintViolation<?>> violations = Set.of(violation("name", annotation("blank"), Map.of()));
        @SuppressWarnings({"unchecked", "rawtypes"})
        ConstraintViolationException failure = new ConstraintViolationException("raw message needle-validated-value", (Set) violations);

        List<ConstraintMessages.Violation> found = ConstraintMessages.violationsOf(failure);

        assertThat(found).containsExactly(new ConstraintMessages.Violation("name", FieldMessages.required()));
        assertThat(found.toString()).doesNotContain("needle");
    }

    @Test
    void theViolationIsFoundAnywhereInTheCauseChain() throws Exception {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ConstraintViolationException inner = new ConstraintViolationException("m",
                (Set) Set.of(violation("price", annotation("min"), Map.of("value", 5L))));
        Throwable wrapped = new PersistenceException("wrapper", new RuntimeException("again", inner));

        assertThat(ConstraintMessages.violationsOf(wrapped))
                .containsExactly(new ConstraintMessages.Violation("price", "Enter a number that is at least 5."));
    }

    @Test
    void anythingElseHasNoViolations() {
        assertThat(ConstraintMessages.violationsOf(new IllegalStateException("x"))).isEmpty();
        assertThat(ConstraintMessages.violationsOf(new PersistenceException("sql needle"))).isEmpty();
    }

    @Test
    void sentencesByKindOfConstraint() {
        assertThat(ConstraintMessages.sentence("NotNull", Map.of())).isEqualTo(FieldMessages.required());
        assertThat(ConstraintMessages.sentence("NotBlank", Map.of())).isEqualTo(FieldMessages.required());
        assertThat(ConstraintMessages.sentence("NotEmpty", Map.of())).isEqualTo(FieldMessages.required());
        assertThat(ConstraintMessages.sentence("Size", Map.of("min", 2, "max", 9))).isEqualTo("Use between 2 and 9 characters.");
        assertThat(ConstraintMessages.sentence("Size", Map.of("min", 0, "max", 120))).isEqualTo(FieldMessages.tooLong(120));
        assertThat(ConstraintMessages.sentence("Size", Map.of("min", 8, "max", Integer.MAX_VALUE)))
                .isEqualTo("Use at least 8 characters.");
        assertThat(ConstraintMessages.sentence("Min", Map.of("value", 0L))).isEqualTo("Enter a number that is at least 0.");
        assertThat(ConstraintMessages.sentence("DecimalMin", Map.of("value", "0.00", "inclusive", false)))
                .isEqualTo("Enter a number that is greater than 0.00.");
        assertThat(ConstraintMessages.sentence("Max", Map.of("value", 10L))).isEqualTo("Enter a number that is at most 10.");
        assertThat(ConstraintMessages.sentence("Positive", Map.of())).isEqualTo("Enter a number greater than zero.");
        assertThat(ConstraintMessages.sentence("Email", Map.of())).contains("valid email address");
        assertThat(ConstraintMessages.sentence("Pattern", Map.of())).isEqualTo("Use the format this field asks for.");
        assertThat(ConstraintMessages.sentence("Past", Map.of())).contains("past");
        assertThat(ConstraintMessages.sentence("Future", Map.of())).contains("future");
    }

    @Test
    void aConstraintTheConsoleHasNoSentenceForGetsTheGeneralOne() {
        assertThat(ConstraintMessages.sentence("TheHostsOwnConstraint", Map.of())).isEqualTo(FieldMessages.notAccepted());
        assertThat(FieldMessages.notAccepted()).doesNotContain("TheHostsOwnConstraint");
    }

    @Test
    void nothingIsLoadedFromTheValidationApiUnlessItIsPresent() throws Exception {
        // The jakarta.validation types are named only inside ConstraintMessages.Api; the outer class's own members and the
        // methods the service calls never mention them, so a host without the API never meets a NoClassDefFoundError.
        for (var method : ConstraintMessages.class.getDeclaredMethods()) {
            assertThat(method.toGenericString()).as(method.getName()).doesNotContain("jakarta.validation");
        }
    }
}
