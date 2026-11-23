package io.github.mdmusahub.jpaadmin.sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Reads the build and workflow files of this repository for the release guard tests (Phase 14b, D-137 to D-141): poms as XML
 * (comments are ignored, so a commented-out setting is not a setting), workflows as text. The files are found from the module's
 * folder ({@code basedir}, which Maven's test runner sets), one level up.
 */
final class RepositoryFiles {

    private RepositoryFiles() {
    }

    /** The folder of the repository (the parent of this module's folder). */
    static Path root() {
        Path module = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
        return module.resolve("..").normalize();
    }

    static Path file(String relativePath) {
        return root().resolve(relativePath).normalize();
    }

    static String text(String relativePath) throws IOException {
        return Files.readString(file(relativePath));
    }

    /** A pom (or any XML file) parsed without namespaces, so a path is written {@code /project/profiles/profile}. */
    static Document xml(String relativePath) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(relativePath).toFile());
    }

    /** The text of every node the XPath finds, trimmed, in document order. */
    static List<String> texts(Document document, String xpath) throws Exception {
        NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath().evaluate(xpath, document, XPathConstants.NODESET);
        List<String> found = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            found.add(nodes.item(i).getTextContent().trim());
        }
        return found;
    }

    /** The text of the first node the XPath finds, or {@code null} when there is none. */
    static String first(Document document, String xpath) throws Exception {
        List<String> found = texts(document, xpath);
        return found.isEmpty() ? null : found.get(0);
    }

    static int count(Document document, String xpath) throws Exception {
        return ((NodeList) XPathFactory.newInstance().newXPath().evaluate(xpath, document, XPathConstants.NODESET)).getLength();
    }
}
