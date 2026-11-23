package io.github.mdmusahub.jpaadmin.sample;

import static io.github.mdmusahub.jpaadmin.sample.RepositoryFiles.count;
import static io.github.mdmusahub.jpaadmin.sample.RepositoryFiles.first;
import static io.github.mdmusahub.jpaadmin.sample.RepositoryFiles.texts;
import static io.github.mdmusahub.jpaadmin.sample.RepositoryFiles.xml;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * The promises of the release configuration that live in the poms (Phase 14b, D-137 to D-141). They are read as XML, so a
 * setting that is commented out does not count. Nothing here needs a key, a network or a Central account.
 *
 * <ul>
 *   <li>A normal build never signs, never makes a source or javadoc jar and never publishes: the four release plugins are only in
 *       the profile {@code release}, and that profile cannot switch itself on.</li>
 *   <li>Only the five published modules can be published: {@code sample-app} and {@code jpa-admin-ui-preview} switch every
 *       release step off in their own poms and are named in the Central plugin's exclusion list, and {@code host-check} is not
 *       part of the reactor at all.</li>
 *   <li>A release is staged, not published: {@code autoPublish} is {@code false}.</li>
 *   <li>The signing passphrase and the Central token are never in a pom.</li>
 * </ul>
 */
class ReleaseSettingsGuardTest {

    private static final String RELEASE = "/project/profiles/profile[id='release']";

    private static final List<String> THE_FOUR_PLUGINS = List.of("maven-source-plugin", "maven-javadoc-plugin", "maven-gpg-plugin",
            "central-publishing-maven-plugin");

    private static final List<String> EVERY_MODULE_POM = List.of("pom.xml", "jpa-admin-core/pom.xml", "jpa-admin-ui/pom.xml",
            "jpa-admin-autoconfigure/pom.xml", "jpa-admin-ui-preview/pom.xml", "jpa-admin-starter/pom.xml", "sample-app/pom.xml");

    // ---- a normal build does not sign or publish ---------------------------------------------------------------------------

    @Test
    void theReleaseProfileExistsAndCannotSwitchItselfOn() throws Exception {
        Document root = xml("pom.xml");

        assertThat(count(root, RELEASE)).as("a profile with the id release in the root pom").isEqualTo(1);
        assertThat(count(root, RELEASE + "/activation")).as("an <activation> element of the release profile").isZero();
        assertThat(count(root, "//activeByDefault")).as("any activeByDefault in the root pom").isZero();
        // No property or file or JDK test can be written without <activation>, so this is also the whole of "off by default".
    }

    @Test
    void noPomOffersAReleaseProfileThatIsOnByDefault() throws Exception {
        for (String pom : EVERY_MODULE_POM) {
            Document document = xml(pom);
            assertThat(count(document, "//activeByDefault")).as("activeByDefault in " + pom).isZero();
            assertThat(count(document, "//profile/activation")).as("a profile with an <activation> in " + pom).isZero();
        }
    }

    @Test
    void noReleasePluginIsInTheDefaultBuildOfAnyModule() throws Exception {
        for (String pom : EVERY_MODULE_POM) {
            Document document = xml(pom);
            List<String> inTheDefaultBuild = new ArrayList<>(texts(document, "/project/build/plugins/plugin/artifactId"));
            inTheDefaultBuild.addAll(texts(document, "/project/build/pluginManagement/plugins/plugin/artifactId"));

            assertThat(inTheDefaultBuild).as("plugins outside a profile in " + pom).doesNotContainAnyElementsOf(THE_FOUR_PLUGINS);
            assertThat(count(document, "/project/build//extensions")).as("a build extension outside a profile in " + pom).isZero();
        }
    }

    @Test
    void theReleaseProfileHoldsExactlyTheFourApprovedPluginsAndNothingElse() throws Exception {
        Document root = xml("pom.xml");

        assertThat(texts(root, RELEASE + "/build/plugins/plugin/artifactId")).containsExactlyInAnyOrderElementsOf(THE_FOUR_PLUGINS);
        assertThat(count(root, RELEASE + "/dependencies")).as("dependencies added by the release profile").isZero();
        assertThat(count(root, RELEASE + "/modules")).as("modules added by the release profile").isZero();
    }

    @Test
    void thePluginsThatTheBootBomDoesNotManageHaveAnExactVersion() throws Exception {
        Document root = xml("pom.xml");
        String plugin = RELEASE + "/build/plugins/plugin[artifactId='%s']/version";

        assertThat(first(root, plugin.formatted("central-publishing-maven-plugin"))).isEqualTo("0.12.0");
        assertThat(first(root, plugin.formatted("maven-gpg-plugin"))).isEqualTo("3.2.8");
        // source (3.4.0) and javadoc (3.12.0) come from the Spring Boot 4.1.2 BOM: they must not carry a version of their own
        assertThat(first(root, plugin.formatted("maven-source-plugin"))).isNull();
        assertThat(first(root, plugin.formatted("maven-javadoc-plugin"))).isNull();
    }

    // ---- staged, never published on its own ---------------------------------------------------------------------------------

    @Test
    void aReleaseIsStagedAndWaitsForTheMaintainerToPressPublish() throws Exception {
        Document root = xml("pom.xml");
        String central = RELEASE + "/build/plugins/plugin[artifactId='central-publishing-maven-plugin']";

        assertThat(first(root, central + "/configuration/autoPublish")).as("autoPublish").isEqualTo("false");
        assertThat(first(root, central + "/configuration/publishingServerId")).isEqualTo("central");
        assertThat(first(root, central + "/configuration/waitUntil")).isEqualToIgnoringCase("validated");
        assertThat(first(root, central + "/extensions")).isEqualTo("true");
    }

    // ---- no secret in a pom -----------------------------------------------------------------------------------------------

    @Test
    void thePassphraseAndTheTokenAreReadFromTheEnvironmentAndNeverWrittenInAPom() throws Exception {
        Document root = xml("pom.xml");
        String gpg = RELEASE + "/build/plugins/plugin[artifactId='maven-gpg-plugin']/configuration";

        assertThat(first(root, gpg + "/passphraseEnvName")).isEqualTo("GPG_PASSPHRASE");
        assertThat(first(root, gpg + "/bestPractices")).as("bestPractices fails the build if a passphrase would leak")
                .isEqualTo("true");
        assertThat(texts(root, gpg + "/gpgArguments/arg")).containsExactly("--pinentry-mode", "loopback");
        for (String pom : EVERY_MODULE_POM) {
            Document document = xml(pom);
            assertThat(count(document, "//passphrase")).as("a <passphrase> element in " + pom).isZero();
            assertThat(count(document, "//keyname")).as("a <keyname> element in " + pom).isZero();
            assertThat(count(document, "//plugin[artifactId='central-publishing-maven-plugin']//*[self::username or self::password"
                    + " or self::token]")).as("a Central credential in " + pom).isZero();
            assertThat(count(document, "//*[contains(local-name(), 'gpg.passphrase')]")).as("gpg.passphrase in " + pom).isZero();
        }
    }

    // ---- the modules ------------------------------------------------------------------------------------------------------

    private static boolean skipsPublishing(Document pom) throws Exception {
        return "true".equals(first(pom, "/project/properties/skipPublishing"));
    }

    @Test
    void theFivePublishedModulesAreTheParentTheThreeLibrariesAndTheStarter() throws Exception {
        List<String> published = new ArrayList<>();
        List<String> unpublished = new ArrayList<>();
        for (String module : texts(xml("pom.xml"), "/project/modules/module")) {
            Document pom = xml(module + "/pom.xml");
            String artifactId = first(pom, "/project/artifactId");
            (skipsPublishing(pom) ? unpublished : published).add(artifactId);
        }

        assertThat(first(xml("pom.xml"), "/project/artifactId")).isEqualTo("jpa-super-admin");
        assertThat(published).containsExactlyInAnyOrder("jpa-admin-core", "jpa-admin-ui", "jpa-admin-autoconfigure",
                "springboot-super-admin");
        assertThat(unpublished).containsExactlyInAnyOrder("sample-app", "jpa-admin-ui-preview");
    }

    @Test
    void theTwoUnpublishedModulesOfTheReactorSwitchEveryReleaseStepOffInEveryBuild() throws Exception {
        for (String pom : List.of("sample-app/pom.xml", "jpa-admin-ui-preview/pom.xml")) {
            Document document = xml(pom);
            for (String property : List.of("maven.source.skip", "maven.javadoc.skip", "gpg.skip", "maven.deploy.skip",
                    "skipPublishing")) {
                assertThat(first(document, "/project/properties/" + property)).as(property + " in " + pom).isEqualTo("true");
            }
            assertThat(count(document, "//profiles")).as("a profile in " + pom + " could undo this").isZero();
        }
    }

    @Test
    void theCentralPluginNamesTheSameTwoModulesInItsExclusionList() throws Exception {
        Document root = xml("pom.xml");

        assertThat(texts(root, RELEASE + "/build/plugins/plugin[artifactId='central-publishing-maven-plugin']"
                + "/configuration/excludeArtifacts/artifact")).containsExactlyInAnyOrder("sample-app", "jpa-admin-ui-preview");
    }

    @Test
    void hostCheckIsOutsideTheReactorSoNothingOfTheReleaseCanReachIt() throws Exception {
        List<String> modules = texts(xml("pom.xml"), "/project/modules/module");
        Document hostCheck = xml("host-check/pom.xml");

        assertThat(modules).doesNotContain("host-check");
        assertThat(first(hostCheck, "/project/parent/artifactId")).isEqualTo("spring-boot-starter-parent");
        assertThat(first(hostCheck, "/project/groupId")).isNotEqualTo("io.github.mdmusahub");
        assertThat(count(hostCheck, "//plugin[artifactId='central-publishing-maven-plugin' or artifactId='maven-gpg-plugin']"
                + " | //distributionManagement")).as("anything that could publish host-check").isZero();
    }

    @Test
    void theReactorHasTheSixModulesAndNoOtherAndHostCheckIsNotOneOfThem() throws Exception {
        List<String> modules = texts(xml("pom.xml"), "/project/modules/module");

        assertThat(modules).containsExactlyInAnyOrder("jpa-admin-core", "jpa-admin-ui", "jpa-admin-autoconfigure",
                "jpa-admin-ui-preview", "jpa-admin-starter", "sample-app");
    }

    // ---- what Maven Central asks of every published pom ------------------------------------------------------------------------

    @Test
    void theRootPomCarriesTheMetadataThatCentralRequires() throws Exception {
        Document root = xml("pom.xml");

        assertThat(first(root, "/project/groupId")).isEqualTo("io.github.mdmusahub");
        assertThat(first(root, "/project/name")).isNotBlank();
        assertThat(first(root, "/project/description")).isNotBlank();
        assertThat(first(root, "/project/url")).isEqualTo("https://github.com/mdmusahub/JPA-SUPER-ADMIN");
        assertThat(first(root, "/project/licenses/license/name")).isEqualTo("Apache License, Version 2.0");
        assertThat(first(root, "/project/licenses/license/url")).isEqualTo("https://www.apache.org/licenses/LICENSE-2.0.txt");
        assertThat(first(root, "/project/developers/developer/name")).isEqualTo("Musa Malik");
        assertThat(first(root, "/project/developers/developer/email")).isEqualTo("mdmusaalam98019@gmail.com");
        assertThat(first(root, "/project/developers/developer/url")).isEqualTo("https://github.com/mdmusahub");
        assertThat(first(root, "/project/scm/url")).isEqualTo("https://github.com/mdmusahub/JPA-SUPER-ADMIN");
        assertThat(first(root, "/project/scm/connection")).startsWith("scm:git:https://github.com/mdmusahub/JPA-SUPER-ADMIN");
        assertThat(first(root, "/project/scm/developerConnection")).startsWith("scm:git:ssh://git@github.com/mdmusahub/");
        assertThat(first(root, "/project/issueManagement/url")).isEqualTo("https://github.com/mdmusahub/JPA-SUPER-ADMIN/issues");
    }

    @Test
    void everyPublishedPomHasANameAndADescriptionAndTheModulesDoNotRepeatTheRootMetadata() throws Exception {
        for (String pom : List.of("jpa-admin-core/pom.xml", "jpa-admin-ui/pom.xml", "jpa-admin-autoconfigure/pom.xml",
                "jpa-admin-starter/pom.xml")) {
            Document document = xml(pom);
            assertThat(first(document, "/project/name")).as("name in " + pom).isNotBlank();
            assertThat(first(document, "/project/description")).as("description in " + pom).isNotBlank();
            assertThat(count(document, "/project/scm | /project/url | /project/developers | /project/licenses"))
                    .as("metadata that would override the root's in " + pom).isZero();
        }
    }

    @Test
    void everyModuleIsOnTheVersionOfTheRootAndTheHostCheckDependsOnThatVersion() throws Exception {
        String version = first(xml("pom.xml"), "/project/version");

        assertThat(version).matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
        for (String pom : EVERY_MODULE_POM.subList(1, EVERY_MODULE_POM.size())) {
            assertThat(first(xml(pom), "/project/parent/version")).as("parent version in " + pom).isEqualTo(version);
            assertThat(first(xml(pom), "/project/parent/groupId")).isEqualTo("io.github.mdmusahub");
            assertThat(first(xml(pom), "/project/parent/artifactId")).isEqualTo("jpa-super-admin");
            assertThat(count(xml(pom), "/project/version")).as("a version of its own in " + pom).isZero();
        }
        assertThat(first(xml("host-check/pom.xml"), "/project/dependencies/dependency[artifactId='springboot-super-admin']/version"))
                .as("the version host-check asks for").isEqualTo(version);
    }
}
