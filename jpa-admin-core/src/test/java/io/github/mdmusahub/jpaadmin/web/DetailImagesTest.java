package io.github.mdmusahub.jpaadmin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.service.DetailRecord;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.DetailView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The detail page's picture strip is filled from the image cells (D-99), with the field's label as alternative text. */
class DetailImagesTest {

    private static DetailView build(DetailRecord.Item... items) {
        return DetailViewFactory.build("/admin", "member", "Member", new DetailRecord("7", "Ann", List.of(items)),
                ListQuery.defaults(), new DetailViewFactory.Offers(true, true, true));
    }

    private static DetailRecord.Item item(String label, Cell cell) {
        return DetailRecord.Item.of(label, cell);
    }

    private static Cell image(String url) {
        return new Cell(Cell.Type.IMAGE, "Photo", Cell.Tone.NONE, url, null);
    }

    @Test
    void everyImageCellIsInTheStripWithTheFieldLabelAsItsAlternativeText() {
        DetailView view = build(item("Portrait", image("/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png")),
                item("Badge", image("/files/2026/11/fedcba9876543210fedcba9876543210.webp")));

        assertThat(view.images()).extracting(DetailView.Image::url).containsExactly(
                "/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png",
                "/files/2026/11/fedcba9876543210fedcba9876543210.webp");
        assertThat(view.images()).extracting(DetailView.Image::label).containsExactly("Portrait", "Badge");
        assertThat(view.fields()).hasSize(2);
    }

    @Test
    void aFileAMissingFileAndPlainTextAreNeverInTheStrip() {
        DetailView view = build(
                item("Contract", new Cell(Cell.Type.FILE, "Contract (PDF)", Cell.Tone.NONE,
                        "/admin/files/vault/2026/11/0123456789abcdef0123456789abcdef.pdf", "0123456789abcdef0123456789abcdef.pdf")),
                item("Cover", new Cell(Cell.Type.TEXT, "Cover: file not found", Cell.Tone.NONE, null, null)),
                item("Note", Cell.text("<img src=x onerror=alert(1)>")));

        assertThat(view.images()).isEmpty();
    }

    @Test
    void aLabelThatIsMarkupIsKeptAsTextForThePageToEscape() {
        DetailView view = build(item("<b>Photo</b>", image("/admin/files/default/2026/11/0123456789abcdef0123456789abcdef.png")));

        assertThat(view.images().get(0).label()).isEqualTo("<b>Photo</b>");
    }

    @Test
    void aRecordWithNoUploadFieldHasNoStrip() {
        assertThat(build(item("Name", Cell.text("Ann"))).images()).isEmpty();
    }
}
