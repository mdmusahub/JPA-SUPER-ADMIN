package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Two promises that live in build files, not in code, and that nothing else would notice losing (Phase 14a, D-130):
 *
 * <ul>
 *   <li><b>Trace output stays out of the public log.</b> This module's tests log SQL with bound values when they check what the
 *       console logs, and the surefire settings {@code reuseForks=false} (a test class cannot leak a log level to the next) and
 *       {@code redirectTestOutputToFile=true} (test output goes to {@code target/surefire-reports}, so the CI log shows no row
 *       value) are what keep that true. Mutation (e): remove the second setting and this test fails.</li>
 *   <li><b>CI cannot hide a missing Docker or browser.</b> The workflow sets {@code JPA_ADMIN_REQUIRE_ENGINES} and
 *       {@code JPA_ADMIN_REQUIRE_BROWSER} to {@code true} on the step that runs the build, reports what was skipped, and downloads
 *       no browser (the runner's own Chrome is used).</li>
 * </ul>
 */
class BuildSettingsGuardTest {

    private static Path moduleFolder() {
        return Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    }

    /** The text of a child of the surefire plugin's {@code configuration}, comments ignored (it is read as XML). */
    private static String surefireSetting(String name) throws Exception {
        Document pom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(moduleFolder().resolve("pom.xml").toFile());
        NodeList plugins = pom.getElementsByTagName("plugin");
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            if (!"maven-surefire-plugin".equals(text(plugin, "artifactId"))) {
                continue;
            }
            NodeList configurations = plugin.getElementsByTagName("configuration");
            if (configurations.getLength() == 0) {
                return null;
            }
            return text((Element) configurations.item(0), name);
        }
        return null;
    }

    private static String text(Element parent, String child) {
        NodeList found = parent.getElementsByTagName(child);
        return found.getLength() == 0 ? null : found.item(0).getTextContent().trim();
    }

    @Test
    void everyTestClassRunsInItsOwnJvmSoNoLogLevelLeaks() throws Exception {
        assertThat(surefireSetting("reuseForks")).as("reuseForks in sample-app/pom.xml").isEqualTo("false");
    }

    @Test
    void testOutputGoesToFilesSoThePublicCiLogShowsNoRowValue() throws Exception {
        assertThat(surefireSetting("redirectTestOutputToFile")).as("redirectTestOutputToFile in sample-app/pom.xml")
                .isEqualTo("true");
    }

    // ---- the workflow --------------------------------------------------------------------------------------------------------

    private static String workflow() throws IOException {
        Path file = moduleFolder().resolve("../.github/workflows/ci.yml").normalize();
        assumeTrue(Files.exists(file), "SKIPPED, not passed: the workflow file is not part of this source tree");
        return Files.readString(file);
    }

    /** The text of the step that starts with {@code - name: <name>} up to the next step. */
    private static String step(String workflow, String name) {
        int start = workflow.indexOf("- name: " + name);
        assertThat(start).as("a step named \"" + name + "\" in ci.yml").isGreaterThanOrEqualTo(0);
        int next = workflow.indexOf("\n      - name:", start + 1);
        return next < 0 ? workflow.substring(start) : workflow.substring(start, next);
    }

    @Test
    void theBuildStepRequiresDockerAndABrowserSoNeitherCanBeSkippedOnCi() throws Exception {
        String build = step(workflow(), "Build and test");

        assertThat(build).contains("JPA_ADMIN_REQUIRE_ENGINES: \"true\"").contains("JPA_ADMIN_REQUIRE_BROWSER: \"true\"")
                .contains("mvn -B clean verify");
    }

    @Test
    void whatRanAndWhatWasSkippedIsReportedEvenWhenTheBuildFails() throws Exception {
        String report = step(workflow(), "Report what ran and what was skipped");

        assertThat(report).contains("if: always()").contains("report-tests.sh");
        assertThat(moduleFolder().resolve("../.github/scripts/report-tests.sh")).exists();
    }

    @Test
    void noBrowserIsDownloadedByTheWorkflowTheRunnersOwnChromeIsUsed() throws Exception {
        String workflow = workflow().toLowerCase(java.util.Locale.ROOT);

        assertThat(workflow).doesNotContain("setup-chrome").doesNotContain("browser-actions").doesNotContain("puppeteer")
                .doesNotContain("playwright").doesNotContain("apt-get install").doesNotContain("chromedriver");
    }
}
