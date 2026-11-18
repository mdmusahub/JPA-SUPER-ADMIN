package io.github.mdmusahub.jpaadmin.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The fixed signature table: five types recognised by their first bytes, nothing else (UPL-1, UPL-5). */
class FileSignaturesTest {

    private static FileSignatures.Detected detect(byte[] bytes) {
        return FileSignatures.detect(Arrays.copyOf(bytes, Math.max(bytes.length, FileSignatures.HEAD_BYTES)), bytes.length)
                .orElse(null);
    }

    @Test
    void theFiveKnownTypesAreRecognisedFromRealFiles() {
        assertThat(detect(UploadTestFiles.png()).mime()).isEqualTo("image/png");
        assertThat(detect(UploadTestFiles.jpeg()).mime()).isEqualTo("image/jpeg");
        assertThat(detect(UploadTestFiles.gif()).mime()).isEqualTo("image/gif");
        assertThat(detect(UploadTestFiles.webp()).mime()).isEqualTo("image/webp");
        assertThat(detect(UploadTestFiles.pdf()).mime()).isEqualTo("application/pdf");
    }

    @Test
    void onlyImagesAreShownInlineAndEveryExtensionIsShortAndLowerCase() {
        assertThat(FileSignatures.forMime("image/png").orElseThrow().showInline()).isTrue();
        assertThat(FileSignatures.forMime("image/jpeg").orElseThrow().showInline()).isTrue();
        assertThat(FileSignatures.forMime("image/gif").orElseThrow().showInline()).isTrue();
        assertThat(FileSignatures.forMime("image/webp").orElseThrow().showInline()).isTrue();
        assertThat(FileSignatures.forMime("application/pdf").orElseThrow().showInline()).isFalse();
        assertThat(FileSignatures.knownTypes()).containsExactly("image/png", "image/jpeg", "image/gif", "image/webp",
                "application/pdf");
        for (String mime : FileSignatures.knownTypes()) {
            assertThat(FileSignatures.forMime(mime).orElseThrow().extension()).matches("[a-z0-9]{2,4}");
            assertThat(FileSignatures.forExtension(FileSignatures.forMime(mime).orElseThrow().extension())).isPresent();
        }
    }

    @Test
    void mimeLookupIgnoresCaseAndSpacesAndKnowsNothingElse() {
        assertThat(FileSignatures.forMime(" IMAGE/PNG ")).isPresent();
        assertThat(FileSignatures.forMime("image/svg+xml")).isEmpty();
        assertThat(FileSignatures.forMime("text/html")).isEmpty();
        assertThat(FileSignatures.forMime("application/zip")).isEmpty();
        assertThat(FileSignatures.forMime(null)).isEmpty();
        assertThat(FileSignatures.forExtension("svg")).isEmpty();
        assertThat(FileSignatures.forExtension("html")).isEmpty();
        assertThat(FileSignatures.forExtension(null)).isEmpty();
    }

    @Test
    void thingsThatAreNotInTheTableAreNothing() {
        assertThat(detect(UploadTestFiles.executable())).isNull();
        assertThat(detect(UploadTestFiles.html())).isNull();
        assertThat(detect("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(detect("PK\u0003\u0004 a zip".getBytes(StandardCharsets.ISO_8859_1))).isNull();
        assertThat(detect("plain text".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(detect(new byte[16])).isNull();
        assertThat(FileSignatures.detect(null, 5)).isEmpty();
        assertThat(FileSignatures.detect(new byte[16], 0)).isEmpty();
    }

    @Test
    void aPngNeedsItsHeaderChunkNotJustTheEightByteSignature() {
        byte[] signatureOnly = Arrays.copyOf(UploadTestFiles.png(), 16);
        signatureOnly[12] = 'X';

        assertThat(detect(signatureOnly)).isNull();
        assertThat(FileSignatures.detect(UploadTestFiles.png(), 8)).isEmpty();
    }

    @Test
    void aWebpNeedsRiffAndWebpNotJustRiff() {
        byte[] wave = "RIFF\u0016\u0000\u0000\u0000WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(detect(wave)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GIF87a", "GIF89a"})
    void bothGifVersionsAreRecognised(String header) {
        assertThat(detect(header.getBytes(StandardCharsets.ISO_8859_1)).mime()).isEqualTo("image/gif");
        assertThat(detect("GIF90a".getBytes(StandardCharsets.ISO_8859_1))).isNull();
    }

    @Test
    void aPdfMustStartAtTheFirstByte() {
        assertThat(detect(" %PDF-1.4".getBytes(StandardCharsets.ISO_8859_1))).isNull();
        assertThat(detect("%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1))).isNotNull();
    }

    @Test
    void aShortHeadNeverReadsPastWhatIsReal() {
        assertThat(FileSignatures.detect(new byte[] {(byte) 0xFF, (byte) 0xD8}, 2)).isEmpty();
        assertThat(FileSignatures.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 3)).isPresent();
        assertThat(FileSignatures.detect(new byte[] {'%', 'P', 'D'}, 3)).isEmpty();
    }
}
