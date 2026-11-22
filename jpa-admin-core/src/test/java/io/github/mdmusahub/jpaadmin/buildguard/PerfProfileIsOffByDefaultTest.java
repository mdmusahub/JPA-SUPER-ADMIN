package io.github.mdmusahub.jpaadmin.buildguard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The performance measurements must never run in a normal build (Phase 14a, D-131). They load 200,000 rows and run for minutes, so
 * three things keep them out, and this test fails the build if any of them goes:
 *
 * <ol>
 *   <li>the Maven profile {@code perf} has no {@code <activation>}, so it is never on unless somebody types {@code -Pperf};</li>
 *   <li>the default surefire configuration of this module excludes everything under {@code .../perf/**};</li>
 *   <li>every measurement class carries its own switch ({@code @EnabledIfSystemProperty} on {@code jpa.admin.perf}), so even a run
 *       with {@code -Dtest=...} skips with a reason.</li>
 * </ol>
 *
 * It also checks that this JVM was not started with the switch on. Mutation (c): give the profile {@code activeByDefault} (or drop
 * the exclusion) and this test fails.
 */
class PerfProfileIsOffByDefaultTest {

    private static final String SWITCH = "jpa.admin.perf";

    private static Path moduleFolder() {
        return Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    }

    private static Document pom() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(moduleFolder().resolve("pom.xml").toFile());
    }

    private static List<Element> children(Node parent, String name) {
        List<Element> found = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getTagName().equals(name)) {
                found.add(element);
            }
        }
        return found;
    }

    private static Element child(Node parent, String name) {
        List<Element> found = children(parent, name);
        return found.isEmpty() ? null : found.get(0);
    }

    private static Element surefire(Element build) {
        Element plugins = build == null ? null : child(build, "plugins");
        if (plugins == null) {
            return null;
        }
        for (Element plugin : children(plugins, "plugin")) {
            Element artifact = child(plugin, "artifactId");
            if (artifact != null && "maven-surefire-plugin".equals(artifact.getTextContent().trim())) {
                return plugin;
            }
        }
        return null;
    }

    private static Element profile(Document pom, String id) {
        Element profiles = child(pom.getDocumentElement(), "profiles");
        assertThat(profiles).as("a <profiles> section in jpa-admin-core/pom.xml").isNotNull();
        for (Element profile : children(profiles, "profile")) {
            Element name = child(profile, "id");
            if (name != null && id.equals(name.getTextContent().trim())) {
                return profile;
            }
        }
        return null;
    }

    private static List<String> texts(Element parent, String group, String item) {
        List<String> values = new ArrayList<>();
        Element container = parent == null ? null : child(parent, group);
        if (container != null) {
            children(container, item).forEach(element -> values.add(element.getTextContent().trim()));
        }
        return values;
    }

    // ---- the profile --------------------------------------------------------------------------------------------------------

    @Test
    void theProfileExistsAndHasNoActivationSoItIsNeverOnUnlessAskedFor() throws Exception {
        Element perf = profile(pom(), "perf");

        assertThat(perf).as("the profile \"perf\"").isNotNull();
        assertThat(child(perf, "activation")).as("an activation element would be a way to switch the profile on by itself")
                .isNull();
    }

    @Test
    void theProfileSwitchesTheMeasurementsOnAndSelectsOnlyThem() throws Exception {
        Element perf = profile(pom(), "perf");
        Element plugin = surefire(child(perf, "build"));
        assertThat(plugin).as("surefire in the profile").isNotNull();
        Element configuration = child(plugin, "configuration");

        assertThat(texts(configuration, "includes", "include")).containsExactly("**/perf/*PerfTest.java");
        Element properties = child(configuration, "systemPropertyVariables");
        assertThat(child(properties, SWITCH)).as("the profile sets " + SWITCH).isNotNull();
        assertThat(child(properties, SWITCH).getTextContent().trim()).isEqualTo("true");
    }

    // ---- the default build -------------------------------------------------------------------------------------------------------

    @Test
    void theDefaultBuildExcludesEverythingUnderThePerfFolder() throws Exception {
        Element plugin = surefire(child(pom().getDocumentElement(), "build"));

        assertThat(plugin).as("surefire in the default build").isNotNull();
        assertThat(texts(child(plugin, "configuration"), "excludes", "exclude")).contains("**/perf/**");
        assertThat(texts(child(plugin, "configuration"), "includes", "include"))
                .as("the default build selects no measurement of its own").noneMatch(include -> include.contains("perf"));
    }

    @Test
    void theDefaultBuildDoesNotSetTheSwitch() throws Exception {
        Element plugin = surefire(child(pom().getDocumentElement(), "build"));
        Element configuration = child(plugin, "configuration");

        assertThat(child(configuration, "systemPropertyVariables")).as("no system property in the default surefire configuration")
                .satisfiesAnyOf(properties -> assertThat(properties).isNull(),
                        properties -> assertThat(child(properties, SWITCH)).isNull());
    }

    @Test
    void thisJvmWasNotStartedWithTheSwitchOn() {
        assertThat(System.getProperty(SWITCH)).as("the normal build must not carry -D" + SWITCH + "=true")
                .isNotEqualToIgnoringCase("true");
    }

    // ---- the measurement classes ---------------------------------------------------------------------------------------------------

    @Test
    void everyMeasurementClassHasItsOwnSwitchAndLivesWhereTheDefaultBuildDoesNotLook() throws Exception {
        Path classes = Path.of(System.getProperty("basedir", ".")).resolve("target/test-classes/io/github/mdmusahub/jpaadmin/perf");
        List<String> names;
        try (Stream<Path> files = Files.list(classes)) {
            names = files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith("PerfTest.class")).sorted()
                    .toList();
        }

        assertThat(names).as("the measurement classes found").contains("H2PerfTest.class", "PostgreSqlPerfTest.class");
        for (String name : names) {
            Class<?> type = Class.forName("io.github.mdmusahub.jpaadmin.perf." + name.replace(".class", ""));
            EnabledIfSystemProperty gate = type.getAnnotation(EnabledIfSystemProperty.class);
            assertThat(gate).as(name + " must carry @EnabledIfSystemProperty itself (an inherited switch is not enough)").isNotNull();
            assertThat(gate.named()).isEqualTo(SWITCH);
            assertThat(gate.matches()).isEqualTo("true");
            assertThat(gate.disabledReason()).as("a skipped measurement says why").contains("not part of a normal build");
            assertThat(type.getPackageName()).endsWith(".perf");
        }
    }

    @Test
    void noOtherTestClassIsNamedLikeAMeasurementOrReadsTheSwitch() throws IOException {
        Path sources = moduleFolder().resolve("src/test/java");
        try (Stream<Path> walk = Files.walk(sources)) {
            for (Path file : walk.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = sources.relativize(file).toString().replace('\\', '/');
                if (relative.contains("/perf/") || relative.contains("/buildguard/")) {
                    continue;
                }
                assertThat(relative).as("a measurement class outside the perf folder would be selected by the default build")
                        .doesNotEndWith("PerfTest.java");
                assertThat(Files.readString(file)).as(relative + " must not read the measurement switch")
                        .doesNotContain("\"" + SWITCH + "\"");
            }
        }
    }
}
