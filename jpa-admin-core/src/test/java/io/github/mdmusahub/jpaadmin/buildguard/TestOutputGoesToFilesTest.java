package io.github.mdmusahub.jpaadmin.buildguard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The output of this module's tests must not reach the public CI log (Phase 14a, D-130). Three tests here set the log level to TRACE on
 * purpose (to prove that an upload ticket is never logged), and at TRACE Hibernate prints the values it binds to a record. Surefire's
 * {@code redirectTestOutputToFile} sends all test output to {@code target/surefire-reports}; this test fails the build if the setting
 * is removed from the default configuration of this module.
 */
class TestOutputGoesToFilesTest {

    @Test
    void theDefaultSurefireConfigurationRedirectsTestOutputToFiles() throws Exception {
        Document pom = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(Path.of(System.getProperty("basedir", ".")).resolve("pom.xml").toFile());
        Element build = (Element) pom.getDocumentElement().getElementsByTagName("build").item(0);
        String value = null;
        NodeList plugins = build.getElementsByTagName("plugin");
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            if (plugin.getParentNode().getParentNode() == build
                    && "maven-surefire-plugin".equals(plugin.getElementsByTagName("artifactId").item(0).getTextContent().trim())) {
                NodeList setting = plugin.getElementsByTagName("redirectTestOutputToFile");
                value = setting.getLength() == 0 ? null : setting.item(0).getTextContent().trim();
            }
        }

        assertThat(value).as("redirectTestOutputToFile in the default surefire configuration of jpa-admin-core/pom.xml").isEqualTo("true");
    }
}
