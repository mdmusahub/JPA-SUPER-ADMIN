package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mdmusahub.jpaadmin.preview.NameCheck.Allowed;
import io.github.mdmusahub.jpaadmin.preview.NameCheck.Hit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ARCH-12: no demo entity name in a library module, and the checker that proves it is itself tested.
 *
 * <p>The first test is the rule. The rest feed {@link NameCheck} made-up input, so weakening the checker (matching
 * substrings, forgetting stale allow-list entries, skipping files) fails here instead of passing quietly (AI-6).
 */
class NameCheckTest {

    private static Path repositoryRoot() {
        // Surefire runs with the module folder as the working directory.
        Path root = Path.of("").toAbsolutePath().getParent();
        assertThat(root).isNotNull();
        assertThat(root.resolve("pom.xml")).exists();
        assertThat(root.resolve("docs")).isDirectory();
        return root;
    }

    // ---- the rule itself --------------------------------------------------------------------------------------

    @Test
    void noLibraryModuleHoldsADemoEntityName() {
        Path root = repositoryRoot();
        List<Allowed> allowed = NameCheck.readAllowList(root.resolve(NameCheck.ALLOW_LIST));

        List<String> problems = NameCheck.problems(root, NameCheck.LIBRARY_MODULES, allowed);

        assertThat(problems).isEmpty();
    }

    @Test
    void theCheckReallyReadsTheLibraryModulesAndTheirTestsAndResources() {
        Path root = repositoryRoot();

        List<Hit> hits = NameCheck.hits(root, NameCheck.LIBRARY_MODULES);

        // The seven known hits of D-42: Spring's @Order in one main file and two test files.
        assertThat(hits).hasSize(7);
        assertThat(hits).allSatisfy(hit -> assertThat(hit.name()).isEqualTo("Order"));
        assertThat(hits.stream().map(Hit::path).distinct()).containsExactlyInAnyOrder(
                "jpa-admin-autoconfigure/src/main/java/io/github/mdmusahub/jpaadmin/autoconfigure/"
                        + "JpaAdminSecurityConfiguration.java",
                "jpa-admin-autoconfigure/src/test/java/io/github/mdmusahub/jpaadmin/autoconfigure/"
                        + "HostIsolationTest.java",
                "jpa-admin-autoconfigure/src/test/java/io/github/mdmusahub/jpaadmin/autoconfigure/"
                        + "JpaAdminSecurityModesTest.java");
    }

    @Test
    void theAllowListHasOneEntryPerKnownHitAndEveryEntryHasAReason() {
        List<Allowed> allowed = NameCheck.readAllowList(repositoryRoot().resolve(NameCheck.ALLOW_LIST));

        assertThat(allowed).hasSize(7);
        assertThat(allowed).allSatisfy(entry -> {
            assertThat(entry.reason()).isNotBlank();
            assertThat(entry.path()).isNotBlank();
            assertThat(entry.line()).isNotBlank();
        });
    }

    @Test
    void theAllowListIsOneFileAndItIsNotInAScannedModule() {
        Path allowList = repositoryRoot().resolve(NameCheck.ALLOW_LIST);

        assertThat(allowList).isRegularFile();
        assertThat(NameCheck.LIBRARY_MODULES).noneMatch(module -> NameCheck.ALLOW_LIST.startsWith(module + "/"));
    }

    // ---- the checker, on made-up input ------------------------------------------------------------------------

    @Test
    void aHitThatIsNotOnTheAllowListIsAProblem() {
        Hit hit = new Hit("jpa-admin-core/src/main/java/X.java", 12, "private Product product;", "Product");

        assertThat(describe(List.of(hit), List.of())).anyMatch(problem -> problem.contains("X.java:12")
                && problem.contains("Product") && problem.contains("ARCH-12"));
    }

    @Test
    void aHitOnTheAllowListIsNotAProblem() {
        Hit hit = new Hit("a/X.java", 1, "@Order(1)", "Order");
        Allowed entry = new Allowed("a/X.java", "@Order(1)", "Spring's annotation");

        assertThat(describe(List.of(hit), List.of(entry))).isEmpty();
    }

    @Test
    void anAllowListEntryThatMatchesNothingIsAProblemSoStaleEntriesCannotHide() {
        Allowed stale = new Allowed("a/Gone.java", "@Order(1)", "it used to be here");

        assertThat(describe(List.of(), List.of(stale)))
                .anyMatch(problem -> problem.contains("matches nothing") && problem.contains("Gone.java"));
    }

    @Test
    void anAllowListEntryWithoutAReasonIsAProblem() {
        Hit hit = new Hit("a/X.java", 1, "@Order(1)", "Order");

        assertThat(describe(List.of(hit), List.of(new Allowed("a/X.java", "@Order(1)", "   "))))
                .anyMatch(problem -> problem.contains("has no reason"));
    }

    @Test
    void anEntryAllowsOneLineOfOneFileAndNothingElse() {
        Allowed entry = new Allowed("a/X.java", "@Order(1)", "Spring's annotation");
        Hit otherLine = new Hit("a/X.java", 9, "private Order order;", "Order");
        Hit otherFile = new Hit("a/Y.java", 1, "@Order(1)", "Order");

        assertThat(describe(List.of(otherLine), List.of(entry)))
                .as("the same file, another line, is still a problem").isNotEmpty();
        assertThat(describe(List.of(otherFile), List.of(entry)))
                .as("the same line, another file, is still a problem").isNotEmpty();
    }

    @Test
    void theSameAllowedLineTwiceInOneFileNeedsOnlyOneEntry() {
        Allowed entry = new Allowed("a/X.java", "@Order(1)", "Spring's annotation");
        List<Hit> twice = List.of(new Hit("a/X.java", 3, "@Order(1)", "Order"),
                new Hit("a/X.java", 40, "@Order(1)", "Order"));

        assertThat(describe(twice, List.of(entry))).isEmpty();
    }

    // ---- the match ---------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"Order", "@Order(1)", "import x.Order;", "new Product()", "Category c", "A Tag here",
            "Customer,", "list.of(Product.class)", "/* Order */", "<module>Product</module>"})
    void aWholeWordHitIsFound(String line) throws Exception {
        assertThat(hitsInAFileHolding(line)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ordered.HIGHEST_PRECEDENCE", "import org.springframework.core.Ordered;", "getOrder()",
            "setOrder(1)", "reorder", "Orders", "Products", "Categories", "TagName", "Tagged", "myTag",
            "ProductX", "XProduct", "containsExactlyInAnyOrder", "orderBy", "customers", "product", "tag"})
    void somethingThatOnlyLooksLikeADemoNameIsNotAHit(String line) throws Exception {
        assertThat(hitsInAFileHolding(line)).as(line).isEmpty();
    }

    @Test
    void theMatchIsCaseSensitive() throws Exception {
        assertThat(hitsInAFileHolding("product")).isEmpty();
        assertThat(hitsInAFileHolding("PRODUCT")).isEmpty();
        assertThat(hitsInAFileHolding("Product")).isNotEmpty();
    }

    @Test
    void resourcesAndTemplatesAreReadAsWellAndBuildOutputIsNot() throws Exception {
        Path root = Files.createTempDirectory("name-check");
        Path module = Files.createDirectories(root.resolve("mod/src/main/resources/templates"));
        Files.writeString(module.resolve("page.html"), "<p>Product</p>\n");
        Path output = Files.createDirectories(root.resolve("mod/target/classes"));
        Files.writeString(output.resolve("page.html"), "<p>Customer</p>\n");
        Files.writeString(root.resolve("mod/pom.xml"), "<artifactId>Tag</artifactId>\n");

        List<Hit> hits = NameCheck.hits(root, List.of("mod"));

        assertThat(hits.stream().map(Hit::name)).containsExactlyInAnyOrder("Product", "Tag");
    }

    @Test
    void aMissingModuleFolderIsAnErrorAndNotAPass() {
        assertThatThrownBy(() -> NameCheck.hits(repositoryRoot(), List.of("jpa-admin-nothing")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- the allow-list format ---------------------------------------------------------------------------------

    @Test
    void theAllowListFormatIsReadStrictly() {
        assertThat(NameCheck.parseAllowList(List.of("# a comment", "", "path: a/X.java", "line: @Order(1)",
                "reason: Spring's annotation"))).containsExactly(new Allowed("a/X.java", "@Order(1)",
                "Spring's annotation"));
        assertThatThrownBy(() -> NameCheck.parseAllowList(List.of("line: @Order(1)")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> NameCheck.parseAllowList(List.of("path: a/X.java", "reason: no line")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> NameCheck.parseAllowList(List.of("path: a/X.java", "line: @Order(1)")))
                .as("an entry with no reason at the end of the file").isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> NameCheck.parseAllowList(List.of("whatever: something")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static List<String> describe(List<Hit> hits, List<Allowed> allowed) {
        return NameCheck.problems(hits, allowed);
    }

    private static List<Hit> hitsInAFileHolding(String line) throws Exception {
        Path root = Files.createTempDirectory("name-check");
        Path file = root.resolve("mod/src/main/java/X.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, line + "\n");
        return NameCheck.hits(root, List.of("mod"));
    }
}
