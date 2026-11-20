package io.github.mdmusahub.jpaadmin.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code @AdminBadge} entries (Phase 13, D-120): the palette is fixed, a tone is a name and never a colour, and nothing the
 * host wrote is repeated in a problem sentence.
 */
class BadgeSpecTest {

    private static BadgeSpec spec(String... entries) {
        return new BadgeSpec(List.of(entries));
    }

    @Test
    void thePaletteIsTheFourTonesTheConsoleHasStylesFor() {
        assertThat(BadgeSpec.PALETTE).containsExactly("green", "amber", "red", "grey");
    }

    @Test
    void everyToneOfThePaletteIsAccepted() {
        BadgeSpec badge = spec("A=green", "B=amber", "C=red", "D=grey");

        assertThat(badge.problems()).isEmpty();
        assertThat(badge.usable()).containsEntry("A", "green").containsEntry("B", "amber").containsEntry("C", "red")
                .containsEntry("D", "grey");
    }

    @Test
    void theToneIsComparedWithoutCaseAndWithoutSpacesAroundIt() {
        BadgeSpec badge = spec("A=GREEN", "B = Amber ", " C =rEd");

        assertThat(badge.problems()).isEmpty();
        assertThat(badge.toneOf("A")).contains("green");
        assertThat(badge.toneOf("B")).contains("amber");
        assertThat(badge.toneOf("C")).contains("red");
    }

    @Test
    void theValueIsComparedAsWrittenSoUpperAndLowerCaseDiffer() {
        BadgeSpec badge = spec("Paid=green");

        assertThat(badge.toneOf("Paid")).contains("green");
        assertThat(badge.toneOf("paid")).isEmpty();
        assertThat(badge.toneOf("PAID")).isEmpty();
        assertThat(badge.toneOf(null)).isEmpty();
    }

    @Test
    void theToneIsTheTextAfterTheLastEqualsSignSoAValueMayContainOne() {
        BadgeSpec badge = spec("a=b=green");

        assertThat(badge.problems()).isEmpty();
        assertThat(badge.toneOf("a=b")).contains("green");
    }

    @ParameterizedTest
    @ValueSource(strings = {"X=#ff0000", "X=#f00", "X=rgb(255,0,0)", "X=red;background:url(http://evil.example/x)",
            "X=red;", "X=blue", "X=Blue", "X=none", "X=", "X= ", "X=badge-green", "X=green red", "X=\"red\"",
            "X=expression(alert(1))", "X=javascript:alert(1)", "X=<script>", "X=var(--x)", "X=currentColor", "X=0"})
    void anythingThatIsNotAPaletteNameIsRefusedAndNeverUsable(String entry) {
        BadgeSpec badge = spec(entry);

        assertThat(badge.usable()).isEmpty();
        assertThat(badge.toneOf("X")).isEmpty();
        assertThat(badge.problems()).hasSize(1);
        assertThat(badge.problems().get(0)).contains("entry 1").contains("green, amber, red, grey");
    }

    @ParameterizedTest
    @ValueSource(strings = {"X=#ff0000", "X=red;background:url(http://evil.example/x)", "X=rgb(255,0,0)",
            "X=<script>alert(1)</script>"})
    void aProblemSentenceNeverRepeatsWhatWasWritten(String entry) {
        assertThat(spec(entry).problems()).allSatisfy(problem -> assertThat(problem).doesNotContain("#ff0000")
                .doesNotContain("evil.example").doesNotContain("rgb").doesNotContain("script").doesNotContain("alert")
                .doesNotContain("X="));
    }

    @Test
    void anEntryWithoutAnEqualsSignAndAnEntryWithoutAValueAreRefused() {
        BadgeSpec badge = spec("green", "=green", "  =green", "OK=green");

        assertThat(badge.usable()).containsOnlyKeys("OK");
        assertThat(badge.problems()).hasSize(3);
        assertThat(badge.problems().get(0)).startsWith("entry 1").contains("no '='");
        assertThat(badge.problems().get(1)).startsWith("entry 2").contains("no value");
        assertThat(badge.problems().get(2)).startsWith("entry 3").contains("no value");
    }

    @Test
    void aValueListedTwiceIsAProblemAndTheFirstEntryWins() {
        BadgeSpec badge = spec("OK=green", "LATE=red", "OK=red");

        assertThat(badge.problems()).containsExactly("entry 3 lists a value that entry 1 already lists");
        assertThat(badge.toneOf("OK")).contains("green");
    }

    @Test
    void noEntriesAtAllIsNotAProblemAndDoesNothing() {
        BadgeSpec badge = new BadgeSpec(List.of());

        assertThat(badge.problems()).isEmpty();
        assertThat(badge.usable()).isEmpty();
    }

    @Test
    void aFieldHasABadgeOnlyWhenItIsAnEnumOrAStringHasUsableEntriesAndIsNotSensitive() {
        BadgeSpec good = spec("A=green");
        BadgeSpec bad = spec("A=#fff");
        List<String> constants = List.of("A", "B");

        assertThat(field(AdminField.Kind.ENUM, Enum.class, constants, good, false).hasBadge()).isTrue();
        assertThat(field(AdminField.Kind.TEXT, String.class, List.of(), good, false).hasBadge()).isTrue();
        assertThat(field(AdminField.Kind.TEXT, String.class, List.of(), good, true).hasBadge()).as("sensitive").isFalse();
        assertThat(field(AdminField.Kind.TEXT, String.class, List.of(), bad, false).hasBadge()).as("no usable entry").isFalse();
        assertThat(field(AdminField.Kind.TEXT, String.class, List.of(), null, false).hasBadge()).as("no annotation").isFalse();
        assertThat(field(AdminField.Kind.NUMBER, Integer.class, List.of(), good, false).hasBadge()).as("a number").isFalse();
        assertThat(field(AdminField.Kind.TEXT, java.util.UUID.class, List.of(), good, false).hasBadge()).as("not a String")
                .isFalse();
    }

    private static AdminField field(AdminField.Kind kind, Class<?> type, List<String> constants, BadgeSpec badge,
                                    boolean sensitive) {
        return new AdminField("status", "Status", type, kind, constants, null, false, false, true, false, sensitive, false,
                null, badge, null);
    }
}
