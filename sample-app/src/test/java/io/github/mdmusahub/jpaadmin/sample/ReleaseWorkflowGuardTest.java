package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The promises of the three workflows that nothing else would notice losing (Phase 14b, D-137 to D-141). The files are parsed as
 * YAML (comments are ignored, so a commented-out line is not a setting) and the pins are read as text, because a comment is
 * part of the pin.
 *
 * <ul>
 *   <li>{@code release.yml} runs only when a tag {@code v*} is pushed, can only read the repository, uses Java 17, and hands each
 *       secret to the one step that needs it, as an environment variable, without ever printing it.</li>
 *   <li>{@code docs.yml} runs only when the maintainer starts it by hand.</li>
 *   <li>Every action in every workflow is pinned to a full commit SHA with its release in a comment.</li>
 *   <li>{@code ci.yml} never sees a secret and never runs the release profile.</li>
 * </ul>
 */
class ReleaseWorkflowGuardTest {

    private static final List<String> WORKFLOWS = List.of(".github/workflows/ci.yml", ".github/workflows/release.yml",
            ".github/workflows/docs.yml");

    private static final List<String> SECRET_NAMES = List.of("GPG_PRIVATE_KEY", "GPG_PASSPHRASE", "CENTRAL_TOKEN_USERNAME",
            "CENTRAL_TOKEN_PASSWORD");

    /** {@code uses: owner/repo@<40 hex> # v1.2.3}: the SHA, then the release it belongs to. */
    private static final Pattern PINNED_ACTION = Pattern.compile(
            "^\\s*(?:-\\s+)?uses:\\s+[\\w.-]+/[\\w./-]+@[0-9a-f]{40}\\s+#\\s+v\\d+\\.\\d+\\.\\d+\\s*$");

    // ---- reading ------------------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> workflow(String path) throws IOException {
        Object loaded = new Yaml().load(RepositoryFiles.text(path));
        assertThat(loaded).as(path + " is a YAML mapping").isInstanceOf(Map.class);
        return (Map<String, Object>) loaded;
    }

    /** YAML 1.1 reads the key {@code on} as the boolean {@code true}; accept either spelling. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> triggers(Map<String, Object> workflow) {
        Object on = workflow.containsKey(Boolean.TRUE) ? workflow.get(Boolean.TRUE) : workflow.get("on");
        assertThat(on).as("the 'on:' block").isInstanceOf(Map.class);
        return (Map<String, Object>) on;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> job(Map<String, Object> workflow, String name) {
        return (Map<String, Object>) ((Map<String, Object>) workflow.get("jobs")).get(name);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Map<String, Object> job) {
        return (List<Map<String, Object>>) job.get("steps");
    }

    private static Map<String, Object> step(Map<String, Object> job, String namePrefix) {
        return steps(job).stream().filter(s -> String.valueOf(s.get("name")).startsWith(namePrefix)).findFirst()
                .orElseThrow(() -> new AssertionError("no step whose name starts with \"" + namePrefix + "\""));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    private static String run(Map<String, Object> step) {
        return String.valueOf(step.getOrDefault("run", ""));
    }

    /** The secret names a step passes in through its {@code env}, found by looking for {@code secrets.} in the values. */
    private static Set<String> secretsPassed(Map<String, Object> step) {
        Set<String> names = new java.util.TreeSet<>();
        map(step.get("env")).forEach((name, value) -> {
            if (String.valueOf(value).contains("secrets.")) {
                names.add(name);
            }
        });
        return names;
    }

    // ---- every workflow -----------------------------------------------------------------------------------------------------

    @Test
    void everyActionOfEveryWorkflowIsPinnedToAFullCommitShaWithItsReleaseInAComment() throws IOException {
        for (String path : WORKFLOWS) {
            List<String> uses = new ArrayList<>();
            for (String line : RepositoryFiles.text(path).split("\n")) {
                if (line.stripLeading().startsWith("#")) {
                    continue;
                }
                if (line.contains("uses:")) {
                    uses.add(line);
                }
            }
            assertThat(uses).as("actions in " + path).isNotEmpty();
            for (String line : uses) {
                assertThat(PINNED_ACTION.matcher(line).matches()).as(path + ": " + line.strip()).isTrue();
            }
        }
    }

    @Test
    void noWorkflowAsksForMoreThanReadAccessToTheContentsAtTheTop() throws IOException {
        for (String path : WORKFLOWS) {
            assertThat(map(workflow(path).get("permissions"))).as("top-level permissions of " + path)
                    .isEqualTo(Map.of("contents", "read"));
        }
    }

    @Test
    void theCiWorkflowNeverSeesASecretAndRunsTheReleaseProfileOnlyWithoutSigningAndStopsBeforeAnyUpload() throws IOException {
        String ci = RepositoryFiles.text(".github/workflows/ci.yml");

        assertThat(ci).doesNotContain("secrets.").doesNotContain("central-publishing").doesNotContain("GPG_")
                .doesNotContain("-P release").doesNotContain("deploy").doesNotContain("CENTRAL_TOKEN");
        List<String> releaseRuns = ci.lines().filter(line -> line.contains("-Prelease")).map(String::strip).toList();
        assertThat(releaseRuns).as("every line of ci.yml that asks for the release profile")
                .containsExactly("run: mvn -B -Prelease -DskipTests -Dgpg.skip=true verify");
        Map<String, Object> job = job(workflow(".github/workflows/ci.yml"), "build");
        assertThat(step(job, "Check that the release profile still builds").get("if")).isEqualTo("matrix.java == 17");
    }

    // ---- release.yml --------------------------------------------------------------------------------------------------------

    @Test
    void theReleaseRunsOnlyWhenATagThatStartsWithVIsPushed() throws IOException {
        Map<String, Object> on = triggers(workflow(".github/workflows/release.yml"));

        assertThat(on.keySet()).as("the only trigger").containsExactly("push");
        assertThat(map(on.get("push")).keySet()).as("what the push trigger filters on").containsExactly("tags");
        assertThat(map(on.get("push")).get("tags")).isEqualTo(List.of("v*"));
    }

    @Test
    void theReleaseJobCanOnlyReadTheRepositoryAndKeepsNoSecretInTheWholeJobsEnvironment() throws IOException {
        Map<String, Object> workflow = workflow(".github/workflows/release.yml");
        Map<String, Object> job = job(workflow, "release");

        assertThat(job.containsKey("permissions")).as("a job-level permissions block that could widen the read-only default")
                .isFalse();
        assertThat(String.valueOf(workflow.get("env"))).doesNotContain("secrets.");
        assertThat(String.valueOf(job.get("env"))).doesNotContain("secrets.");
        assertThat(String.valueOf(job.get("runs-on"))).isEqualTo("ubuntu-latest");
    }

    @Test
    void theReleaseUsesJava17AndTheCentralServerEntryWithEnvironmentPlaceholders() throws IOException {
        Map<String, Object> job = job(workflow(".github/workflows/release.yml"), "release");
        Map<String, Object> java = map(step(job, "Set up Java").get("with"));
        Map<String, Object> checkout = map(step(job, "Check out").get("with"));

        assertThat(String.valueOf(java.get("java-version"))).isEqualTo("17");
        assertThat(java.get("distribution")).isEqualTo("temurin");
        assertThat(java.get("server-id")).isEqualTo("central");
        assertThat(java.get("server-username-env-var")).isEqualTo("CENTRAL_TOKEN_USERNAME");
        assertThat(java.get("server-password-env-var")).isEqualTo("CENTRAL_TOKEN_PASSWORD");
        assertThat(java.containsKey("cache")).as("a Maven cache in a release job").isFalse();
        assertThat(java.containsKey("gpg-private-key")).as("setup-java's own key import (the default keyring)").isFalse();
        assertThat(checkout.get("persist-credentials")).isEqualTo(Boolean.FALSE);
    }

    @Test
    void theTestsRunFirstWithNoSecretAndTheUploadIsTheLastMavenStep() throws IOException {
        Map<String, Object> job = job(workflow(".github/workflows/release.yml"), "release");
        List<String> order = steps(job).stream().map(s -> String.valueOf(s.get("name"))).toList();
        Map<String, Object> tests = step(job, "Build and test");

        assertThat(run(tests)).isEqualTo("mvn -B clean verify");
        assertThat(secretsPassed(tests)).as("secrets in the step that runs the tests").isEmpty();
        assertThat(map(tests.get("env")).get("JPA_ADMIN_REQUIRE_ENGINES")).isEqualTo("true");
        assertThat(map(tests.get("env")).get("JPA_ADMIN_REQUIRE_BROWSER")).isEqualTo("true");
        assertThat(order.indexOf(tests.get("name"))).isLessThan(order.indexOf(step(job, "Import the signing key").get("name")));
        assertThat(run(step(job, "Sign, then check"))).contains("mvn -B -Prelease -DskipTests verify")
                .contains("scripts/check-central-bundle.sh --verify");
        assertThat(run(step(job, "Upload the bundle"))).isEqualTo("mvn -B -Prelease -DskipTests deploy");
        assertThat(order.indexOf(step(job, "Sign, then check").get("name")))
                .isLessThan(order.indexOf(step(job, "Upload the bundle").get("name")));
    }

    @Test
    void eachSecretReachesOnlyTheStepsThatNeedItAsAnEnvironmentVariable() throws IOException {
        Map<String, Object> job = job(workflow(".github/workflows/release.yml"), "release");

        assertThat(secretsPassed(step(job, "Import the signing key"))).containsExactly("GPG_PRIVATE_KEY");
        assertThat(secretsPassed(step(job, "Sign, then check"))).containsExactly("GPG_PASSPHRASE");
        assertThat(secretsPassed(step(job, "Upload the bundle"))).containsExactly("CENTRAL_TOKEN_PASSWORD",
                "CENTRAL_TOKEN_USERNAME", "GPG_PASSPHRASE");
        assertThat(map(step(job, "Import the signing key").get("env")).get("GPG_PRIVATE_KEY"))
                .isEqualTo("${{ secrets.GPG_PRIVATE_KEY }}");
        for (Map<String, Object> step : steps(job)) {
            String name = String.valueOf(step.get("name"));
            assertThat(run(step)).as("a secret expression inside the script of '" + name + "'").doesNotContain("secrets.");
            assertThat(String.valueOf(step.get("with"))).as("a secret as an input of '" + name + "'").doesNotContain("secrets.");
            if (!List.of("Import the signing key", "Sign, then check", "Upload the bundle").stream().anyMatch(name::startsWith)) {
                assertThat(secretsPassed(step)).as("secrets passed to '" + name + "'").isEmpty();
            }
        }
    }

    @Test
    void noScriptEverPrintsASecretOrTurnsOnTracingThatCouldPrintOne() throws IOException {
        Map<String, Object> job = job(workflow(".github/workflows/release.yml"), "release");
        List<String> linesThatNameASecret = new ArrayList<>();
        for (Map<String, Object> step : steps(job)) {
            String script = run(step);
            assertThat(script).as("tracing in '" + step.get("name") + "'").doesNotContain("set -x").doesNotContain("set -o xtrace")
                    .doesNotContain("--debug").doesNotContainPattern("\\bmvn\\b[^\\n]* -X\\b").doesNotContain("printenv");
            for (String line : script.split("\n")) {
                if (SECRET_NAMES.stream().anyMatch(line::contains)) {
                    linesThatNameASecret.add(line.strip());
                }
            }
        }

        // The one line in the whole workflow that names a secret variable: the key goes into a pipe, straight into gpg.
        assertThat(linesThatNameASecret).containsExactly(
                "printf '%s' \"${GPG_PRIVATE_KEY}\" | gpg --homedir \"${gnupg_home}\" --batch --quiet --import");
    }

    @Test
    void theKeyGoesIntoATemporaryKeyringThatIsStoppedAndDeletedWhateverHappens() throws IOException {
        Map<String, Object> job = job(workflow(".github/workflows/release.yml"), "release");
        String importStep = run(step(job, "Import the signing key"));
        Map<String, Object> last = steps(job).get(steps(job).size() - 1);

        assertThat(importStep).contains("mktemp -d \"${RUNNER_TEMP}/").contains("GNUPGHOME=").contains(">> \"${GITHUB_ENV}\"")
                .contains("--homedir \"${gnupg_home}\"");
        assertThat(last.get("name")).isEqualTo("Remove the temporary keyring");
        assertThat(last.get("if")).isEqualTo("always()");
        assertThat(run(last)).contains("gpgconf --homedir \"${GNUPGHOME}\" --kill gpg-agent").contains("rm -rf \"${GNUPGHOME}\"");
    }

    @Test
    void theTagMustBeTheVersionOfThePomAndAFixedVersion() throws IOException {
        Map<String, Object> job = job(workflow(".github/workflows/release.yml"), "release");
        String check = run(step(job, "Check that the tag is the version"));

        assertThat(check).contains("*-SNAPSHOT").contains("GITHUB_REF_NAME").contains("\"v${version}\"");
        Matcher version = Pattern.compile("sed -n '([^']+)' pom.xml").matcher(check);
        assertThat(version.find()).as("the sed expression that reads the version").isTrue();
        assertThat(version.group(1)).isEqualTo("s|^  <version>\\(.*\\)</version>.*|\\1|p");
    }

    // ---- docs.yml -----------------------------------------------------------------------------------------------------------

    @Test
    void theDocumentationIsBuiltAndPublishedOnlyWhenTheMaintainerStartsItByHand() throws IOException {
        Map<String, Object> on = triggers(workflow(".github/workflows/docs.yml"));

        assertThat(on.keySet()).as("the only trigger").containsExactly("workflow_dispatch");
    }

    @Test
    void theDocsBuildIsStrictAndOnlyTheDeployJobCanWritePages() throws IOException {
        Map<String, Object> workflow = workflow(".github/workflows/docs.yml");
        Map<String, Object> build = job(workflow, "build");
        Map<String, Object> deploy = job(workflow, "deploy");

        assertThat(run(step(build, "Build the site"))).isEqualTo("mkdocs build --strict");
        assertThat(run(step(build, "Install the pinned"))).isEqualTo(
                "python -m pip install --require-hashes --no-deps -r docs/requirements.txt");
        assertThat(build.containsKey("permissions")).as("a job-level permissions block on the build job").isFalse();
        assertThat(map(deploy.get("permissions"))).isEqualTo(Map.of("pages", "write", "id-token", "write"));
        assertThat(deploy.get("needs")).isEqualTo("build");
        assertThat(RepositoryFiles.text(".github/workflows/docs.yml")).doesNotContain("secrets.").doesNotContain("-Prelease");
    }
}
