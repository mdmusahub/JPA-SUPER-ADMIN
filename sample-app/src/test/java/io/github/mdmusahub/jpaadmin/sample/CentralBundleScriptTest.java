package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code scripts/check-central-bundle.sh} run for real against a made-up copy of a build (Phase 14b, D-140): the real poms of this
 * repository and a {@code target} folder for each module with the files a release build leaves, holding a few bytes each. No key
 * and no network are needed (the {@code --verify} option is the dry run's, not tested here). The script must pass on a complete
 * tree and fail, naming the file, when any one file is missing, empty or spoiled, when a module that is not published has a file
 * that could be uploaded, or when the version is a snapshot.
 *
 * <p>The script is bash. On Windows the Git for Windows bash is looked for in its usual folders. When there is no bash the test is
 * skipped with that reason on a laptop and fails when the {@code CI} variable is set, as the other tests that need a tool do
 * (D-129).
 */
class CentralBundleScriptTest {

    private static final String VERSION = "0.1.0";

    /** The module folder, the artifactId and whether it is a jar module (a pom module has no jar, sources or javadoc). */
    private record Module(String folder, String artifactId, boolean jar) {
        List<String> files() {
            List<String> names = new ArrayList<>();
            names.add(artifactId + "-" + VERSION + ".pom");
            if (jar) {
                names.add(artifactId + "-" + VERSION + ".jar");
                names.add(artifactId + "-" + VERSION + "-sources.jar");
                names.add(artifactId + "-" + VERSION + "-javadoc.jar");
            }
            return names;
        }
    }

    private static final List<Module> PUBLISHED = List.of(new Module(".", "jpa-super-admin", false),
            new Module("jpa-admin-core", "jpa-admin-core", true), new Module("jpa-admin-ui", "jpa-admin-ui", true),
            new Module("jpa-admin-autoconfigure", "jpa-admin-autoconfigure", true),
            new Module("jpa-admin-starter", "springboot-super-admin", false));

    private static final List<String> UNPUBLISHED_FOLDERS = List.of("jpa-admin-ui-preview", "sample-app");

    @TempDir
    Path tree;

    private String bash;

    @BeforeEach
    void makeACompleteBuild() throws IOException {
        bash = findBash();
        for (String pom : List.of("pom.xml", "jpa-admin-core/pom.xml", "jpa-admin-ui/pom.xml", "jpa-admin-autoconfigure/pom.xml",
                "jpa-admin-ui-preview/pom.xml", "jpa-admin-starter/pom.xml", "sample-app/pom.xml")) {
            Path target = tree.resolve(pom);
            Files.createDirectories(target.getParent());
            Files.copy(RepositoryFiles.file(pom), target);
        }
        for (Module module : PUBLISHED) {
            for (String file : module.files()) {
                write(target(module).resolve(file), "bytes of " + file);
                write(target(module).resolve(file + ".asc"), "-----BEGIN PGP SIGNATURE-----\n\nmade-up\n-----END PGP SIGNATURE-----\n");
            }
        }
        for (String folder : UNPUBLISHED_FOLDERS) {
            write(tree.resolve(folder).resolve("target").resolve(folder + "-" + VERSION + ".jar"), "only the plain jar");
        }
    }

    private Path target(Module module) {
        return tree.resolve(module.folder()).resolve("target").normalize();
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    // ---- running the script -------------------------------------------------------------------------------------------------

    private static String findBash() {
        List<String> candidates = new ArrayList<>();
        String fromEnvironment = System.getenv("JPA_ADMIN_BASH");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            candidates.add(fromEnvironment);
        }
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            candidates.add("C:\\Program Files\\Git\\bin\\bash.exe");
            candidates.add("C:\\Program Files\\Git\\usr\\bin\\bash.exe");
        } else {
            candidates.add("/bin/bash");
            candidates.add("/usr/bin/bash");
            candidates.add("/usr/local/bin/bash");
        }
        for (String candidate : candidates) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        boolean ci = System.getenv("CI") != null;
        String reason = "no bash was found (looked in " + candidates + "; set JPA_ADMIN_BASH to its path)";
        if (ci) {
            throw new AssertionError("CI requires bash for scripts/check-central-bundle.sh: " + reason);
        }
        assumeTrue(false, "SKIPPED, not passed: " + reason);
        return null;
    }

    private record Result(int exit, String output) {
    }

    private Result run(String... options) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(bash);
        command.add(RepositoryFiles.file("scripts/check-central-bundle.sh").toString().replace('\\', '/'));
        command.addAll(List.of(options));
        command.add(tree.toString().replace('\\', '/'));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(120, TimeUnit.SECONDS)).as("the script finished").isTrue();
        return new Result(process.exitValue(), output);
    }

    // ---- the tests ----------------------------------------------------------------------------------------------------------

    @Test
    void aCompleteBuildPassesAndTheScriptNamesWhatItDecidedWasPublished() throws Exception {
        Result result = run();

        assertThat(result.exit()).as(result.output()).isZero();
        assertThat(result.output()).contains("OK: 14 file(s) checked")
                .contains("published:   jpa-super-admin jpa-admin-core jpa-admin-ui jpa-admin-autoconfigure springboot-super-admin")
                .contains("unpublished:  jpa-admin-ui-preview sample-app");
    }

    static Stream<String> everyFileOfThePublishedModules() {
        List<String> files = new ArrayList<>();
        for (Module module : PUBLISHED) {
            for (String file : module.files()) {
                files.add(module.folder() + "/target/" + file);
                files.add(module.folder() + "/target/" + file + ".asc");
            }
        }
        return files.stream();
    }

    @ParameterizedTest(name = "[{index}] without {0}")
    @MethodSource("everyFileOfThePublishedModules")
    void theScriptFailsAndNamesTheFileWhenAnyOneFileOfAnyPublishedModuleIsMissing(String relative) throws Exception {
        Files.delete(tree.resolve(relative).normalize());

        Result result = run();

        assertThat(result.exit()).as(result.output()).isEqualTo(1);
        assertThat(result.output()).contains("MISSING:").contains(Path.of(relative).getFileName().toString())
                .contains("FAILED: 1 problem(s)");
    }

    @Test
    void fourteenFilesAreCheckedAndTheirFourteenSignatures() {
        assertThat(everyFileOfThePublishedModules().count()).isEqualTo(2 * (1 + 4 + 4 + 4 + 1));
    }

    @Test
    void anEmptyFileAndAJunkSignatureAreNotAFile() throws Exception {
        Path jar = tree.resolve("jpa-admin-core/target/jpa-admin-core-0.1.0.jar");
        Files.writeString(jar, "");
        Result empty = run();
        assertThat(empty.exit()).isEqualTo(1);
        assertThat(empty.output()).contains("MISSING:").contains("it is empty");

        Files.writeString(jar, "bytes of jpa-admin-core-0.1.0.jar");
        Files.writeString(tree.resolve("jpa-admin-ui/target/jpa-admin-ui-0.1.0.jar.asc"), "not a signature\n");
        Result junk = run();
        assertThat(junk.exit()).isEqualTo(1);
        assertThat(junk.output()).contains("it is not an armored signature");
    }

    @Test
    void aWholeMissingTargetFolderIsReportedWithTheCommandThatMakesIt() throws Exception {
        try (Stream<Path> walk = Files.walk(tree.resolve("jpa-admin-autoconfigure/target"))) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }

        Result result = run();

        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.output()).contains("MISSING:").contains("jpa-admin-autoconfigure/target").contains("mvn -B -Prelease verify");
    }

    @Test
    void aModuleThatIsNotPublishedMustLeaveNothingThatCouldBeUploaded() throws Exception {
        write(tree.resolve("sample-app/target/sample-app-0.1.0.jar.asc"), "-----BEGIN PGP SIGNATURE-----\n");
        write(tree.resolve("jpa-admin-ui-preview/target/jpa-admin-ui-preview-0.1.0-javadoc.jar"), "x");

        Result result = run();

        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.output()).contains("UNEXPECTED:").contains("sample-app-0.1.0.jar.asc")
                .contains("jpa-admin-ui-preview-0.1.0-javadoc.jar").contains("FAILED: 2 problem(s)");
    }

    @Test
    void aSnapshotIsNeverAReleaseWhateverFilesExist() throws Exception {
        Path pom = tree.resolve("pom.xml");
        Files.writeString(pom, Files.readString(pom).replaceFirst("(?m)^  <version>0\\.1\\.0</version>", "  <version>0.1.0-SNAPSHOT</version>"));

        Result result = run();

        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.output()).contains("a release is not a snapshot");
    }

    @Test
    void aModuleThatStopsOptingOutOfPublishingIsThenCheckedLikeTheOthers() throws Exception {
        Path pom = tree.resolve("sample-app/pom.xml");
        Files.writeString(pom, Files.readString(pom).replace("<skipPublishing>true</skipPublishing>", ""));

        Result result = run();

        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.output()).contains("MISSING:").contains("sample-app-0.1.0.pom");
    }

    @Test
    void theScriptPrintsFileNamesAndNothingThatLooksLikeASecret() throws Exception {
        Result result = run();

        assertThat(result.output()).doesNotContainIgnoringCase("passphrase").doesNotContainIgnoringCase("token")
                .doesNotContainIgnoringCase("password");
    }
}
