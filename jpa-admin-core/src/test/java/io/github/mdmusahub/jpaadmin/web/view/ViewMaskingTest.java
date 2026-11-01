package io.github.mdmusahub.jpaadmin.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SEC-1: a sensitive value never reaches a view-model record. */
class ViewMaskingTest {

    @Test
    void maskedCellCarriesOnlyTheFixedMask() {
        Cell cell = Cell.masked();

        assertThat(cell.type()).isEqualTo(Cell.Type.MASKED);
        assertThat(cell.display()).isEqualTo(Cell.MASK).hasSize(8);
        assertThat(cell.tone()).isEqualTo(Cell.Tone.NONE);
        assertThat(cell.href()).isNull();
        assertThat(cell.title()).isNull();
    }

    @Test
    void maskIsTheSameWhateverTheRealValueWas() {
        assertThat(Cell.masked()).isEqualTo(Cell.masked());
        assertThat(Cell.masked().toString()).doesNotContain("password", "secret");
    }

    @Test
    void maskedCellRejectsAnyValueHrefTitleOrTone() {
        assertThatThrownBy(() -> new Cell(Cell.Type.MASKED, "hunter2", Cell.Tone.NONE, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cell(Cell.Type.MASKED, "", Cell.Tone.NONE, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cell(Cell.Type.MASKED, Cell.MASK, Cell.Tone.NONE, "/somewhere", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cell(Cell.Type.MASKED, Cell.MASK, Cell.Tone.NONE, null, "real value on hover"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cell(Cell.Type.MASKED, Cell.MASK, Cell.Tone.RED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cellHasNoComponentThatCouldHoldTheRawValue() {
        List<String> names = Arrays.stream(Cell.class.getRecordComponents()).map(RecordComponent::getName).toList();

        assertThat(names).containsExactly("type", "display", "tone", "href", "title");
    }

    @Test
    void maskedFormFieldHasNoValue() {
        FormView.Field field = Samples.maskedField();

        assertThat(field.inputType()).isEqualTo(FormView.Field.InputType.MASKED);
        assertThat(field.value()).isNull();
    }

    @Test
    void maskedFormFieldRejectsAValue() {
        assertThatThrownBy(() -> new FormView.Field("secret", "Secret", FormView.Field.InputType.MASKED, "hunter2",
                false, null, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FormView.Field("secret", "Secret", FormView.Field.InputType.MASKED, "",
                false, null, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonMaskedFieldsMayHaveValues() {
        assertThatCode(Samples::textField).doesNotThrowAnyException();
        assertThat(Samples.textField().value()).isEqualTo("Alpha");
    }

    @Test
    void maskedCellsInsideLargerRecordsStayMasked() {
        assertThat(Samples.list().rows().get(0).cells().get(1).display()).isEqualTo(Cell.MASK);
        assertThat(Samples.detail().fields().get(1).cell().display()).isEqualTo(Cell.MASK);
    }
}
