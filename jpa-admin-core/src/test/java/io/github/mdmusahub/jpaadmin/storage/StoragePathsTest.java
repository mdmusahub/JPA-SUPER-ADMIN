package io.github.mdmusahub.jpaadmin.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The name formats that make traversal impossible by construction (UPL-4). */
class StoragePathsTest {

    private static final String NAME = "0123456789abcdef0123456789abcdef.png";

    @ParameterizedTest
    @ValueSource(strings = {"default", "photos", "a", "my-store", "s3"})
    void goodStorageNames(String name) {
        assertThat(StoragePaths.validStorageName(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Default", "1abc", "-a", "a b", "a/b", "a.b", "a_b", "..", "a\\b",
            "abcdefghijklmnopqrstuvwxyzabcdefg"})
    void badStorageNames(String name) {
        assertThat(StoragePaths.validStorageName(name)).isFalse();
    }

    @Test
    void nullIsNoStorageName() {
        assertThat(StoragePaths.validStorageName(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "avatars", "docs/invoices", "a-b_c", "A1/b2/c3/d4"})
    void goodFolders(String folder) {
        assertThat(StoragePaths.folderProblem(folder)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "../x", "a/../b", "/abs", "a/", "a//b", "a/./b", ".", ".hidden", "a b", "a\\b", "C:\\x", "C:",
            "a:b", "a\u0000b", "a\tb", "aux", "AUX", "Nul", "docs/com1", "lpt9", "a/b/c/d/e", "%2e%2e", "a%2fb",
            "abcdefghijklmnopqrstuvwxyzabcdefg"})
    void badFoldersAreRefusedWithAReasonThatDoesNotRepeatThem(String folder) {
        assertThat(StoragePaths.folderProblem(folder)).isPresent();
        // A one or two character input (".", "..") is a substring of any sentence, so only longer inputs can be checked.
        if (folder.length() >= 3) {
            assertThat(StoragePaths.folderProblem(folder).orElseThrow()).doesNotContain(folder);
        }
    }

    @Test
    void nullFolderIsAProblem() {
        assertThat(StoragePaths.folderProblem(null)).isPresent();
    }

    @Test
    void folderPartsSplitOnSlashes() {
        assertThat(StoragePaths.folderParts("")).isEmpty();
        assertThat(StoragePaths.folderParts("a/b")).containsExactly("a", "b");
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/2026/11/" + NAME, "a/b/c/d/2026/01/" + NAME, "2026/12/" + NAME})
    void goodKeys(String key) {
        assertThat(StoragePaths.validKey(key)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", NAME, "../2026/11/" + NAME, "2026/11/../" + NAME, "2026/13/" + NAME, "2026/00/" + NAME,
            "26/11/" + NAME, "2026/11/0123456789abcdef0123456789abcde.png", "2026/11/0123456789ABCDEF0123456789abcdef.png",
            "2026/11/0123456789abcdef0123456789abcdef", "2026/11/0123456789abcdef0123456789abcdef.png.exe",
            "2026/11/0123456789abcdef0123456789abcdef.p/g", "/2026/11/" + NAME, "2026/11/" + NAME + "/",
            "2026\\11\\" + NAME, "2026/11/" + NAME + "\u0000", "a/b/c/d/e/2026/11/" + NAME, "aux/2026/11/" + NAME,
            "docs/./2026/11/" + NAME, "2026//11/" + NAME, "%2e%2e/2026/11/" + NAME, "2026/11/%2e%2e"})
    void badKeys(String key) {
        assertThat(StoragePaths.validKey(key)).isFalse();
    }

    @Test
    void aVeryLongKeyIsNotAKey() {
        assertThat(StoragePaths.validKey("a".repeat(5000))).isFalse();
        assertThat(StoragePaths.validKey(null)).isFalse();
    }

    @Test
    void keyPartsKeepTheFolderThenYearMonthAndName() {
        assertThat(StoragePaths.keyParts("docs/scans/2026/11/" + NAME).orElseThrow())
                .isEqualTo(List.of("docs", "scans", "2026", "11", NAME));
    }
}
