package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import io.github.mdmusahub.jpaadmin.testsupport.listmodel.Depot;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.FormView.Field.InputType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The typed read behind the forms: the fields, the choices, the starting values. One statement for a record, never a
 * sensitive or hidden column, never an entity (the records hold strings only), and the same refusals as the write.
 */
class FormQueryServiceTest extends AbstractFormServiceTest {

    private FormQueryService forms() {
        return FormQueryService.over(entityManager, registry);
    }

    private static FormView.Field field(FormPage page, String key) {
        return page.fields().stream().filter(candidate -> candidate.key().equals(key)).findFirst().orElseThrow();
    }

    private static List<String> keys(FormPage page) {
        return page.fields().stream().map(FormView.Field::key).toList();
    }

    // ---- the blank form ------------------------------------------------------------------------------------------------

    @Test
    void theCreateFormHasOneInputPerEditableFieldAndNothingElse() {
        FormPage page = forms().blank(entity("folio"));

        assertThat(page.recordId()).isNull();
        assertThat(page.version()).isNull();
        // Not the identifier, the version or the hidden field; the secret is there, masked.
        assertThat(keys(page)).containsExactly("code", "courier", "due", "home", "openedAt", "quantity", "secretNote", "status",
                "title", "urgent");
        assertThat(field(page, "title").inputType()).isEqualTo(InputType.TEXT);
        assertThat(field(page, "quantity").inputType()).isEqualTo(InputType.NUMBER);
        assertThat(field(page, "urgent").inputType()).isEqualTo(InputType.BOOLEAN);
        assertThat(field(page, "due").inputType()).isEqualTo(InputType.DATE);
        assertThat(field(page, "openedAt").inputType()).isEqualTo(InputType.DATETIME);
        assertThat(field(page, "status").inputType()).isEqualTo(InputType.SELECT);
        assertThat(field(page, "home").inputType()).isEqualTo(InputType.SELECT);
        assertThat(field(page, "secretNote").inputType()).isEqualTo(InputType.MASKED);
        assertThat(field(page, "secretNote").value()).isNull();
    }

    @Test
    void requiredMarkersFollowTheMetadata() {
        FormPage page = forms().blank(entity("folio"));

        assertThat(field(page, "title").required()).isTrue();
        assertThat(field(page, "quantity").required()).isTrue();
        assertThat(field(page, "home").required()).isTrue();
        assertThat(field(page, "code").required()).isFalse();
        assertThat(field(page, "courier").required()).isFalse();
        assertThat(field(page, "urgent").required()).isFalse(); // a checkbox always has an answer
    }

    @Test
    void theCreateFormStartsFromTheEntitysOwnDefaults() {
        FormPage page = forms().blank(entity("folio"));

        assertThat(field(page, "quantity").value()).isEqualTo("0");
        assertThat(field(page, "urgent").value()).isEqualTo("false");
        assertThat(field(page, "title").value()).isNull();
    }

    @Test
    void everySelectStartsWithABlankChoiceThatSaysWhetherAValueIsNeeded() {
        FormPage page = forms().blank(entity("folio"));

        assertThat(field(page, "status").options().get(0)).isEqualTo(new FormView.Option("", "None"));
        assertThat(field(page, "home").options().get(0)).isEqualTo(new FormView.Option("", "Choose..."));
        assertThat(field(page, "status").options().stream().map(FormView.Option::value).toList())
                .containsExactly("", "PENDING", "IN_TRANSIT", "DELIVERED", "LOST");
        assertThat(field(page, "status").options().get(2).label()).isEqualTo("In transit");
        assertThat(field(page, "home").options().stream().map(FormView.Option::label).toList())
                .containsExactly("Choose...", "Alpha depot", "Beta depot");
        assertThat(field(page, "home").options().get(1).value()).isEqualTo(String.valueOf(depotOne));
    }

    // ---- the edit form -------------------------------------------------------------------------------------------------

    @Test
    void theEditFormIsPrefilledFromTheRecordAsText() {
        FormPage page = forms().record(entity("folio"), String.valueOf(folio)).orElseThrow();

        assertThat(page.recordId()).isEqualTo(String.valueOf(folio));
        assertThat(page.version()).isEqualTo("0");
        assertThat(page.label()).isEqualTo("Existing folio");
        assertThat(field(page, "title").value()).isEqualTo("Existing folio");
        assertThat(field(page, "code").value()).isEqualTo("E-1");
        assertThat(field(page, "quantity").value()).isEqualTo("3");
        assertThat(field(page, "urgent").value()).isEqualTo("false");
        assertThat(field(page, "status").value()).isEqualTo("PENDING");
        assertThat(field(page, "due").value()).isEqualTo("2026-03-04");
        assertThat(field(page, "openedAt").value()).isEqualTo("2026-03-04T05:06:07.123");
        assertThat(field(page, "home").value()).isEqualTo(String.valueOf(depotOne));
        assertThat(field(page, "courier").value()).isNull();
    }

    @Test
    void theSecretIsAMaskedInputWithNoValueAndTheHelpSaysBlankKeepsIt() {
        FormPage page = forms().record(entity("folio"), String.valueOf(folio)).orElseThrow();

        FormView.Field secret = field(page, "secretNote");
        assertThat(secret.inputType()).isEqualTo(InputType.MASKED);
        assertThat(secret.value()).isNull();
        assertThat(secret.required()).isFalse();
        assertThat(secret.help()).isEqualTo("Leave blank to keep the current value.");
        assertThat(page.toString()).doesNotContain("old-secret-value").doesNotContain("stored:");
    }

    @Test
    void theRecordIsOneStatementThatSelectsNoSensitiveOrHiddenColumnAndNoEntity() {
        entityManager.getTransaction().begin();
        RecordingStatementInspector.clear();

        FormPage page = forms().record(entity("folio"), String.valueOf(folio)).orElseThrow();

        entityManager.getTransaction().commit();
        List<String> selects = RecordingStatementInspector.selects();
        // The record, then the choices of the two relations (home, courier): nothing per row.
        assertThat(selects).hasSize(3);
        String record = selects.stream().filter(sql -> sql.toLowerCase().contains("revision")).findFirst().orElseThrow();
        assertThat(record.toLowerCase()).doesNotContain("secret_note").doesNotContain("internal_note")
                .doesNotContain("secretnote").doesNotContain("internalnote");
        assertThat(page.fields()).isNotEmpty();
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(entityManager.getReference(io.github.mdmusahub.jpaadmin.testsupport.listmodel.Folio.class, folio)))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "99999", "-1", "1.5", "007", "", " ", "1e3"})
    void anIdThatIsNotARecordGivesNothing(String id) {
        assertThat(forms().record(entity("folio"), id)).isEmpty();
    }

    // ---- duplicate ---------------------------------------------------------------------------------------------------

    @Test
    void aDuplicateStartsWithOnlyTheValuesOfTheSeed() {
        CopySeed seed = new CopySeed("1", List.of(new CopySeed.Value("title", "Existing folio"),
                new CopySeed.Value("home", String.valueOf(depotTwo)), new CopySeed.Value("quantity", "3")));

        FormPage page = forms().copyOf(entity("folio"), seed);

        assertThat(page.recordId()).isNull();
        assertThat(page.version()).isNull();
        assertThat(field(page, "title").value()).isEqualTo("Existing folio");
        assertThat(field(page, "home").value()).isEqualTo(String.valueOf(depotTwo));
        assertThat(field(page, "quantity").value()).isEqualTo("3");
        // Not in the seed, so empty: not the entity's defaults, and a secret never.
        assertThat(field(page, "urgent").value()).isNull();
        assertThat(field(page, "secretNote").value()).isNull();
        assertThat(keys(page)).doesNotContain("id", "revision", "internalNote");
    }

    // ---- relations ---------------------------------------------------------------------------------------------------

    @Test
    void aRelationWithMoreThan200ChoicesIsANoticeWithNoOptions() {
        ListTestPersistence.inTransaction(em -> {
            for (int i = 0; i < 200; i++) {
                Depot depot = new Depot();
                depot.setName("Bulk depot " + i);
                em.persist(depot);
            }
        });

        FormView.Field home = field(forms().blank(entity("folio")), "home");

        assertThat(home.inputType()).isEqualTo(InputType.SELECT);
        assertThat(home.options()).isEmpty();
        assertThat(home.help()).isEqualTo(FieldMessages.tooManyChoicesNotice(200));
        assertThat(field(forms().record(entity("folio"), String.valueOf(folio)).orElseThrow(), "home").value())
                .as("the stored value is kept, not shown").isEqualTo(String.valueOf(depotOne));
    }

    @Test
    void exactly200ChoicesAreStillListed() {
        ListTestPersistence.inTransaction(em -> {
            for (int i = 0; i < 198; i++) { // 2 depots exist already
                Depot depot = new Depot();
                depot.setName("Bulk depot " + i);
                em.persist(depot);
            }
        });

        FormView.Field home = field(forms().blank(entity("folio")), "home");

        assertThat(home.options()).hasSize(201); // 200 and the blank choice
        assertThat(home.help()).isNull();
    }

    @Test
    void aRequiredRelationWithNothingToChooseIsANoticeToo() {
        ListTestPersistence.inTransaction(em -> {
            em.createQuery("delete from Folio").executeUpdate();
            em.createQuery("delete from Depot").executeUpdate();
        });

        FormView.Field home = field(forms().blank(entity("folio")), "home");

        assertThat(home.options()).isEmpty();
        assertThat(home.help()).isEqualTo(FieldMessages.noChoicesNotice());
        assertThat(field(forms().blank(entity("folio")), "courier").options()).extracting(FormView.Option::label)
                .contains("None");
    }

    // ---- SEC-11 ------------------------------------------------------------------------------------------------------

    @Test
    void theFormsOfWhatTheConsoleMayNotDoAreRefused() {
        RecordingStatementInspector.clear();

        assertThatThrownBy(() -> forms().blank(entity("sealed"))).isInstanceOf(ActionRefusedException.class);
        assertThatThrownBy(() -> forms().blank(entity("archive"))).isInstanceOf(ActionRefusedException.class);
        assertThatThrownBy(() -> forms().record(entity("frozen"), "1")).isInstanceOf(ActionRefusedException.class);
        assertThatThrownBy(() -> forms().record(entity("archive"), "1")).isInstanceOf(ActionRefusedException.class);
        assertThatThrownBy(() -> forms().copyOf(entity("sealed"), new CopySeed("1", List.of())))
                .isInstanceOf(ActionRefusedException.class);

        assertThat(RecordingStatementInspector.statements()).isEmpty();
    }

    @Test
    void anEntityTheConsoleDoesNotOfferHasNoForm() {
        FormQueryService unknown = FormQueryService.over(entityManager, AdminEntityRegistry.empty());

        assertThatThrownBy(() -> unknown.blank(entity("folio"))).isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> unknown.record(entity("folio"), String.valueOf(folio)))
                .isInstanceOf(RecordNotFoundException.class);
        assertThatThrownBy(() -> FormQueryService.unavailable().blank(entity("folio")))
                .isInstanceOf(RecordNotFoundException.class);
    }

    // ---- the page after a refused save ---------------------------------------------------------------------------------

    @Test
    void aReshownFormKeepsWhatWasTypedExceptTheSecretAndCarriesTheErrors() {
        FormPage page = forms().record(entity("folio"), String.valueOf(folio)).orElseThrow();
        Map<String, String> typed = Map.of("title", "Typed title", "quantity", "oops", "secretNote", "needle-typed-secret");

        FormPage again = page.reshown(typed::get, Map.of("quantity", "Enter a whole number."));

        assertThat(field(again, "title").value()).isEqualTo("Typed title");
        assertThat(field(again, "quantity").value()).isEqualTo("oops");
        assertThat(field(again, "quantity").error()).isEqualTo("Enter a whole number.");
        assertThat(field(again, "secretNote").value()).isNull();
        assertThat(again.toString()).doesNotContain("needle-typed-secret");
        assertThat(field(again, "code").value()).isEqualTo("E-1"); // not sent: what the form had
        assertThat(again.fields().stream().filter(f -> f.error() != null).map(FormView.Field::key)
                .collect(Collectors.toList())).containsExactly("quantity");
    }
}
