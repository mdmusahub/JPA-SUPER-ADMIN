package io.github.mdmusahub.jpaadmin.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.annotation.AdminResource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The startup check of {@code @AdminBadge}, {@code @AdminLookup} and {@code @AdminResource(plural)} (Phase 13, D-120 to
 * D-122), on hand-made metadata: every wrong thing is named by entity and field, nothing the host wrote is repeated, and
 * what the console does not show is not judged.
 */
class AnnotationProblemsTest {

    /** Entities carry the annotation the check re-reads for the plural. */
    @AdminResource(plural = "People")
    static class Fine {
    }

    @AdminResource(plural = "123456789012345678901234567890123456789012345678901234567890X")
    static class TooLong {
    }

    @AdminResource(plural = "Peo\u0007ple")
    static class WithControlCharacter {
    }

    @AdminResource(plural = "   ")
    static class BlankPlural {
    }

    static class Unannotated {
    }

    private static AdminField text(String name, BadgeSpec badge) {
        return new AdminField(name, name, String.class, AdminField.Kind.TEXT, List.of(), null, false, false, true, false, false,
                false, null, badge, null);
    }

    private static AdminField id() {
        return new AdminField("id", "ID", Long.class, AdminField.Kind.NUMBER, List.of(), null, true, false, false, false,
                false, false);
    }

    private static AdminField secret(String name) {
        return new AdminField(name, name, String.class, AdminField.Kind.TEXT, List.of(), null, false, false, true, false, true,
                false);
    }

    private static AdminField relation(String name, String target, String lookup) {
        return new AdminField(name, name, Object.class, AdminField.Kind.TO_ONE, List.of(), target, false, false, true, false,
                false, false, null, null, lookup);
    }

    private static AdminEntity entity(Class<?> type, String urlName, AdminField... fields) {
        List<AdminField> all = new ArrayList<>(List.of(id()));
        all.addAll(List.of(fields));
        return new AdminEntity(type, type.getSimpleName(), urlName, type.getSimpleName(), "Entities", "table", "id", null, all);
    }

    private static AdminEntityRegistry registry(AdminEntity... entities) {
        return new AdminEntityRegistry(List.of(entities), List.of());
    }

    // ---- nothing wrong -------------------------------------------------------------------------------------------------

    @Test
    void metadataWithoutAnyAnnotationHasNoProblem() {
        assertThat(AnnotationProblems.of(registry(entity(Unannotated.class, "unannotated", text("name", null))))).isEmpty();
        assertThat(AnnotationProblems.of(AdminEntityRegistry.empty())).isEmpty();
    }

    // ---- the plural ---------------------------------------------------------------------------------------------------

    @Test
    void aGoodPluralIsUsedAndNotReported() {
        AdminEntity fine = new AdminEntity(Fine.class, "Fine", "fine", "Person", "Entities", "table", "id", null,
                List.of(id()), AdminEntity.Access.FULL, "People");

        assertThat(AnnotationProblems.of(registry(fine))).isEmpty();
        assertThat(fine.plural()).isEqualTo("People");
    }

    @Test
    void aPluralThatIsNotGivenIsTheGuess() {
        assertThat(entity(Unannotated.class, "unannotated").plural()).isEqualTo("Unannotateds");
        assertThat(AdminEntity.guessPlural("Entry")).isEqualTo("Entries");
        assertThat(AdminEntity.guessPlural("Box")).isEqualTo("Boxes");
    }

    @Test
    void aBlankPluralIsTheGuessAndNotAProblem() {
        AdminEntity blank = new AdminEntity(BlankPlural.class, "Blank", "blank", "Blank", "Entities", "table", "id", null,
                List.of(id()), AdminEntity.Access.FULL, "   ");

        assertThat(blank.plural()).isEqualTo("Blanks");
        assertThat(AnnotationProblems.of(registry(blank))).isEmpty();
    }

    @Test
    void aPluralThatIsTooLongOrHoldsAControlCharacterIsReportedByTheEntityAndNeverUsed() {
        AdminEntity tooLong = new AdminEntity(TooLong.class, "TooLong", "toolong", "Too long", "Entities", "table", "id", null,
                List.of(id()), AdminEntity.Access.FULL, "123456789012345678901234567890123456789012345678901234567890X");
        AdminEntity control = new AdminEntity(WithControlCharacter.class, "Control", "control", "Control", "Entities", "table",
                "id", null, List.of(id()), AdminEntity.Access.FULL, "Peo\u0007ple");

        List<String> problems = AnnotationProblems.of(registry(tooLong, control));

        assertThat(problems).hasSize(2);
        assertThat(problems.get(0)).contains("@AdminResource(plural)").contains(TooLong.class.getName());
        assertThat(problems.get(1)).contains(WithControlCharacter.class.getName());
        // The sentence gives its own example ("People"), so only the host's exact text is searched for.
        assertThat(problems).allSatisfy(problem -> assertThat(problem).doesNotContain("1234567890")
                .doesNotContain("Peo\u0007ple").doesNotContain("\u0007"));
        assertThat(tooLong.plural()).as("the guess, never the bad text").isEqualTo("Too longs");
        assertThat(control.plural()).isEqualTo("Controls");
    }

    @Test
    void theLongestAcceptedPluralIsSixtyCharacters() {
        assertThat(AdminEntity.acceptablePlural("x".repeat(60))).isTrue();
        assertThat(AdminEntity.acceptablePlural("x".repeat(61))).isFalse();
        assertThat(AdminEntity.acceptablePlural("  People  ")).isTrue();
        assertThat(AdminEntity.acceptablePlural("")).isFalse();
        assertThat(AdminEntity.acceptablePlural(null)).isFalse();
        assertThat(AdminEntity.acceptablePlural("a\nb")).isFalse();
    }

    // ---- the badge ----------------------------------------------------------------------------------------------------

    @Test
    void aGoodStringBadgeIsNotReported() {
        AdminEntity entity = entity(Unannotated.class, "unannotated", text("grade", new BadgeSpec(List.of("A=green", "B=red"))));

        assertThat(AnnotationProblems.of(registry(entity))).isEmpty();
    }

    @Test
    void aGoodEnumBadgeIsNotReportedAndAnUnknownConstantIs() {
        AdminField good = new AdminField("state", "State", Thread.State.class, AdminField.Kind.ENUM, List.of("OK", "LATE"), null,
                false, false, true, false, false, false, null, new BadgeSpec(List.of("OK=green", "LATE=red")), null);
        AdminField unknown = new AdminField("phase", "Phase", Thread.State.class, AdminField.Kind.ENUM, List.of("OK", "LATE"),
                null, false, false, true, false, false, false, null, new BadgeSpec(List.of("OK=green", "NOPE=red", "ALSO=red")),
                null);

        assertThat(AnnotationProblems.of(registry(entity(Unannotated.class, "a", good)))).isEmpty();
        List<String> problems = AnnotationProblems.of(registry(entity(Unannotated.class, "a", unknown)));

        assertThat(problems).singleElement().asString().contains(Unannotated.class.getName() + ".phase")
                .contains("2 values").contains("not a constant").doesNotContain("NOPE").doesNotContain("ALSO");
    }

    @Test
    void aBadgeOnAnythingButAnEnumOrAStringIsReported() {
        AdminField number = new AdminField("price", "Price", Integer.class, AdminField.Kind.NUMBER, List.of(), null, false,
                false, true, false, false, false, null, new BadgeSpec(List.of("1=green")), null);

        assertThat(AnnotationProblems.of(registry(entity(Unannotated.class, "a", number)))).singleElement().asString()
                .contains("@AdminBadge on " + Unannotated.class.getName() + ".price").contains("enum or a String");
    }

    @Test
    void everyWrongEntryIsReportedWithItsPositionAndNeverRepeated() {
        AdminField field = text("grade", new BadgeSpec(List.of("A=#ff0000", "B", "=red", "C=green", "C=red",
                "D=red;background:url(http://evil.example/x)")));

        List<String> problems = AnnotationProblems.of(registry(entity(Unannotated.class, "a", field)));

        assertThat(problems).hasSize(5);
        assertThat(problems).anySatisfy(problem -> assertThat(problem).contains("entry 1"));
        assertThat(problems).anySatisfy(problem -> assertThat(problem).contains("entry 2").contains("no '='"));
        assertThat(problems).anySatisfy(problem -> assertThat(problem).contains("entry 3").contains("no value"));
        assertThat(problems).anySatisfy(problem -> assertThat(problem).contains("entry 5 lists a value that entry 4"));
        assertThat(problems).anySatisfy(problem -> assertThat(problem).contains("entry 6"));
        assertThat(problems).allSatisfy(problem -> assertThat(problem).startsWith("@AdminBadge on " + Unannotated.class.getName()
                + ".grade").doesNotContain("#ff0000").doesNotContain("evil.example").doesNotContain("background"));
    }

    @Test
    void aSensitiveFieldIsMaskedSoItsBadgeIsIgnoredAndNotReported() {
        AdminField masked = new AdminField("token", "Token", String.class, AdminField.Kind.TEXT, List.of(), null, false, false,
                true, false, true, false, null, new BadgeSpec(List.of("whatever")), null);

        assertThat(AnnotationProblems.of(registry(entity(Unannotated.class, "a", masked)))).isEmpty();
    }

    @Test
    void aHiddenFieldIsNotJudged() {
        AdminField hidden = new AdminField("grade", "Grade", String.class, AdminField.Kind.TEXT, List.of(), null, false, false,
                true, false, false, true, null, new BadgeSpec(List.of("nonsense")), null);

        assertThat(AnnotationProblems.of(registry(entity(Unannotated.class, "a", hidden)))).isEmpty();
    }

    // ---- the lookup ---------------------------------------------------------------------------------------------------

    private static AdminEntity target() {
        return entity(Fine.class, "fine", text("name", null), text("serial", null), secret("secretNote"),
                new AdminField("size", "Size", Integer.class, AdminField.Kind.NUMBER, List.of(), null, false, false, true, false,
                        false, false),
                new AdminField("hiddenText", "Hidden text", String.class, AdminField.Kind.TEXT, List.of(), null, false, false,
                        true, false, false, true));
    }

    private static List<String> lookupProblems(String lookup) {
        AdminEntity source = entity(Unannotated.class, "source", relation("dial", "fine", lookup));
        return AnnotationProblems.of(registry(target(), source));
    }

    @Test
    void aLookupThatNamesAVisibleNonSensitiveTextPropertyIsFine() {
        assertThat(lookupProblems("name")).isEmpty();
        assertThat(lookupProblems("serial")).isEmpty();
    }

    @Test
    void aLookupThatNamesNothingTheIdentifierASecretANumberOrAHiddenPropertyIsReported() {
        for (String lookup : new String[] {"nope", "", " ", "id", "secretNote", "size", "hiddenText", "Name", "name ", null}) {
            if (lookup == null) {
                continue; // an annotation always has a value
            }
            assertThat(lookupProblems(lookup)).as("lookup=\"%s\"", lookup).singleElement().asString()
                    .contains("@AdminLookup on " + Unannotated.class.getName() + ".dial")
                    .contains(Fine.class.getName()).contains("visible, non-sensitive text");
        }
    }

    @Test
    void aLookupProblemNeverRepeatsThePropertyNameTheHostWrote() {
        assertThat(lookupProblems("secretNote").get(0)).doesNotContain("secretNote");
        assertThat(lookupProblems("nope").get(0)).doesNotContain("nope");
    }

    @Test
    void aLookupOnAFieldThatIsNotAToOneRelationIsReported() {
        AdminField plain = new AdminField("name", "Name", String.class, AdminField.Kind.TEXT, List.of(), null, false, false,
                true, false, false, false, null, null, "serial");

        assertThat(AnnotationProblems.of(registry(target(), entity(Unannotated.class, "source", plain)))).singleElement()
                .asString().contains("to-one relation");
    }

    @Test
    void aLookupOnARelationToAnEntityThatIsNotInTheConsoleHasNothingToCheck() {
        AdminEntity source = entity(Unannotated.class, "source", relation("dial", null, "whatever"));

        assertThat(AnnotationProblems.of(registry(source))).isEmpty();
    }

    @Test
    void usableLabelIsTheOneRuleForWhatMayBeALabel() {
        AdminEntity target = target();

        assertThat(AnnotationProblems.usableLabel(target, "name")).contains("name");
        assertThat(AnnotationProblems.usableLabel(target, "serial")).contains("serial");
        assertThat(AnnotationProblems.usableLabel(target, "secretNote")).isEmpty();
        assertThat(AnnotationProblems.usableLabel(target, "id")).isEmpty();
        assertThat(AnnotationProblems.usableLabel(target, "size")).isEmpty();
        assertThat(AnnotationProblems.usableLabel(target, "hiddenText")).isEmpty();
        assertThat(AnnotationProblems.usableLabel(target, "missing")).isEmpty();
        assertThat(AnnotationProblems.usableLabel(target, null)).isEmpty();
    }
}
