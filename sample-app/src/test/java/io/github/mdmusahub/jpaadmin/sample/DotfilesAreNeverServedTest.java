package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Hidden files are never served under {@code {path}/assets/**}. A file or folder whose name starts with a dot
 * ({@code .gitkeep}, {@code .DS_Store}, {@code .env}, {@code .git/}) is not an asset, whatever it holds and wherever the
 * build put it.
 *
 * <p>The fixtures {@code .dotfile-fixture}, {@code css/.nested-dotfile-fixture} and {@code .hidden-dir/inside.txt} sit
 * in the real asset location ({@code classpath:/jpa-admin/assets/}) in this module's {@code src/test/resources}, next
 * to the visible control {@code visible-fixture.txt}. The control proves that location is reachable through the
 * handler, so a 404 for a dotfile is the rule at work and not a file that was never found.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DotfilesAreNeverServedTest {

    private static final String[] FIXTURE_TEXTS = {"dotfile-fixture-must-never-be-served",
            "nested-dotfile-fixture-must-never-be-served", "file-inside-hidden-dir-must-never-be-served"};

    @Autowired
    private MockMvc mvc;

    @Test
    void aVisibleFileInTheSameFolderIsServed() throws Exception {
        var response = mvc.perform(get("/admin/assets/visible-fixture.txt")).andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(response.getContentAsString()).contains("visible-fixture-is-served");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/assets/.gitkeep",                       // the empty marker file that used to ship in the folder
            "/admin/assets/.dotfile-fixture",               // exists in the asset folder: must still be refused
            "/admin/assets/css/.nested-dotfile-fixture",    // a dotfile inside a normal sub-folder
            "/admin/assets/.hidden-dir/inside.txt",         // a normal file inside a hidden folder
            "/admin/assets/%2edotfile-fixture",             // the dot written as %2e
            "/admin/assets/%2Edotfile-fixture",
            "/admin/assets/css/%2enested-dotfile-fixture",
            "/admin/assets/%2ehidden-dir/inside.txt",
            "/admin/assets/.DS_Store",                      // the usual suspects, whether or not they exist
            "/admin/assets/.env",
            "/admin/assets/.git/config",
            "/admin/assets/.well-known/security.txt"})
    void hiddenFilesAndFoldersAreNeverServed(String path) throws Exception {
        var response = mvc.perform(get(URI.create(path))).andReturn().getResponse();

        assertThat(response.getStatus()).as(path).isBetween(400, 499);
        for (String text : FIXTURE_TEXTS) {
            assertThat(response.getContentAsString()).as(path).doesNotContain(text);
        }
    }

    @Test
    void hiddenFilesAreRefusedWithNotFoundSoTheirExistenceIsNotRevealed() throws Exception {
        // A fixture that exists and one that does not must look the same from outside.
        int existing = mvc.perform(get("/admin/assets/.dotfile-fixture")).andReturn().getResponse().getStatus();
        int missing = mvc.perform(get("/admin/assets/.no-such-dotfile")).andReturn().getResponse().getStatus();

        assertThat(existing).isEqualTo(404).isEqualTo(missing);
    }
}
