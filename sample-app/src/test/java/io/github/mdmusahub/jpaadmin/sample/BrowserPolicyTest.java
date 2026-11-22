package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * The rule for a missing browser (Phase 14a, D-129), without a browser: a skip says it was not a pass and how to fix it, CI turns
 * a missing browser into a failure, and a browser test class never has its own way of finding one.
 */
class BrowserPolicyTest {

    private static final Path CHROME = Path.of("/usr/bin/google-chrome");

    @Test
    void whenABrowserIsThereTheTestRunsWithIt() {
        BrowserPolicy.Decision decision = BrowserPolicy.decide(Optional.of(CHROME), true);

        assertThat(decision.verdict()).isEqualTo(BrowserPolicy.Verdict.RUN);
        assertThat(BrowserPolicy.apply(decision)).isEqualTo(CHROME);
    }

    @Test
    void whenThereIsNoBrowserOnALaptopTheTestIsSkippedWithAReasonAndIsNeverAPass() {
        BrowserPolicy.Decision decision = BrowserPolicy.decide(Optional.empty(), false);

        assertThat(decision.verdict()).isEqualTo(BrowserPolicy.Verdict.SKIP);
        assertThat(decision.reason()).contains("SKIPPED, not passed").contains("Chrome or Edge").contains("-Dtest.chrome=")
                .contains("CHROME_BIN").contains(BrowserPolicy.REQUIRE_VARIABLE);
        assertThatThrownBy(() -> BrowserPolicy.apply(decision)).isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("not passed");
    }

    @Test
    void whenThereIsNoBrowserAndCiRequiresOneTheTestFailsAndDoesNotSkip() {
        BrowserPolicy.Decision decision = BrowserPolicy.decide(Optional.empty(), true);

        assertThat(decision.verdict()).isEqualTo(BrowserPolicy.Verdict.FAIL);
        assertThat(decision.reason()).contains("FAILED").contains(BrowserPolicy.REQUIRE_VARIABLE);
        assertThatThrownBy(() -> BrowserPolicy.apply(decision)).isInstanceOf(AssertionFailedError.class)
                .isNotInstanceOf(TestAbortedException.class);
    }

    @Test
    void everyCombinationGivesExactlyTheDocumentedVerdict() {
        for (boolean there : new boolean[] {true, false}) {
            for (boolean required : new boolean[] {true, false}) {
                BrowserPolicy.Verdict expected = there ? BrowserPolicy.Verdict.RUN
                        : required ? BrowserPolicy.Verdict.FAIL : BrowserPolicy.Verdict.SKIP;
                assertThat(BrowserPolicy.decide(there ? Optional.of(CHROME) : Optional.empty(), required).verdict())
                        .as("browser=" + there + ", required=" + required).isEqualTo(expected);
            }
        }
    }

    @Test
    void aSkipOrAFailureWithoutAReasonAndARunWithoutABrowserCannotBeBuilt() {
        assertThatThrownBy(() -> new BrowserPolicy.Decision(BrowserPolicy.Verdict.SKIP, null, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrowserPolicy.Decision(BrowserPolicy.Verdict.FAIL, null, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrowserPolicy.Decision(BrowserPolicy.Verdict.RUN, null, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theTestOnlySwitchMakesTheMachineLookBrowserless() {
        String before = System.getProperty(BrowserPolicy.NO_BROWSER_PROPERTY);
        try {
            System.setProperty(BrowserPolicy.NO_BROWSER_PROPERTY, "true");
            assertThat(BrowserPolicy.find()).isEmpty();
        } finally {
            if (before == null) {
                System.clearProperty(BrowserPolicy.NO_BROWSER_PROPERTY);
            } else {
                System.setProperty(BrowserPolicy.NO_BROWSER_PROPERTY, before);
            }
        }
    }

    @Test
    void everyBrowserTestAsksThePolicyAndNoClassFindsABrowserItsOwnWay() throws Exception {
        Path sources = Path.of(System.getProperty("basedir", ".")).resolve("src/test/java/io/github/mdmusahub/jpaadmin/sample");
        assertThat(sources).isDirectory();
        try (var files = java.nio.file.Files.list(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("BrowserPolicy.java") || name.equals("BrowserPolicyTest.java") || name.equals("HeadlessChrome.java")) {
                    continue;
                }
                String text = java.nio.file.Files.readString(file);
                assertThat(text).as(name + " must not look for a browser itself (use BrowserPolicy.require())")
                        .doesNotContain("HeadlessChrome.find()");
            }
        }
    }
}
