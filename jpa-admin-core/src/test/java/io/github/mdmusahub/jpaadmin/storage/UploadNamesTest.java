package io.github.mdmusahub.jpaadmin.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** A client file name is shown back, never used as a path (UPL-3). */
class UploadNamesTest {

    @Test
    void anOrdinaryNameStaysAsItIs() {
        assertThat(UploadNames.display("holiday photo (1).png")).isEqualTo("holiday photo (1).png");
        assertThat(UploadNames.display("r\u00e9sum\u00e9 \u65e5\u672c.pdf")).isEqualTo("r\u00e9sum\u00e9 \u65e5\u672c.pdf");
    }

    @Test
    void onlyTheLastComponentOfAPathSurvives() {
        assertThat(UploadNames.display("C:\\Users\\me\\photo.png")).isEqualTo("photo.png");
        assertThat(UploadNames.display("/etc/passwd")).isEqualTo("passwd");
        assertThat(UploadNames.display("../../evil.png")).isEqualTo("evil.png");
        assertThat(UploadNames.display("a/b\\c/d.png")).isEqualTo("d.png");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".", "..", "/", "\\", "a/", "../", "\u0000", "\u202e", "  \t "})
    void nothingUsableBecomesAPlainFallback(String name) {
        assertThat(UploadNames.display(name)).isEqualTo("file");
    }

    @Test
    void controlAndDirectionCharactersAreRemoved() {
        assertThat(UploadNames.display("a\u0000b\nc\td.png")).isEqualTo("abcd.png");
        assertThat(UploadNames.display("photo\u202egnp.exe")).isEqualTo("photognp.exe");
        assertThat(UploadNames.display("a\u200bb\u2028c\u2066d\ufeff.png")).isEqualTo("abcd.png");
    }

    @Test
    void aLongNameIsCutAtOneHundredCharactersWithoutBreakingACharacter() {
        String cut = UploadNames.display("x".repeat(500) + ".png");
        String emoji = UploadNames.display("\ud83d\ude00".repeat(300));

        assertThat(cut).hasSize(100);
        assertThat(emoji.codePointCount(0, emoji.length())).isEqualTo(100);
        assertThat(Character.isHighSurrogate(emoji.charAt(emoji.length() - 2))).isTrue();
    }

    @Test
    void markupInANameIsKeptAsTextForThePageToEscape() {
        // Escaping is the page's job (SEC-9); this class must not pretend to do it by deleting things.
        assertThat(UploadNames.display("<img src=x onerror=alert(1)>.png")).isEqualTo("<img src=x onerror=alert(1)>.png");
    }
}
