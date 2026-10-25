package com.voplica.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * What the build publishes: a POM with the metadata Maven Central requires, Java 17 classes, and the three jars Central
 * takes (the classes, the sources, the javadoc), each with the licence.
 */
class PublicationTest {
    private static final String VERSION = System.getProperty("voplica.version");
    private static final Path ROOT = Path.of(System.getProperty("voplica.workflow")).getParent().getParent().getParent();

    @Test
    void theVersionIsTheApisVersionFile() throws IOException {
        assertEquals(Files.readString(ROOT.resolve("VERSION")).trim(), VERSION);
        assertTrue(VERSION.matches("\\d+\\.\\d+\\.\\d+"), VERSION);
    }

    @Test
    void thePomCarriesWhatMavenCentralRequires() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document pom = factory.newDocumentBuilder().parse(Path.of(System.getProperty("voplica.pom")).toFile());
        Element project = pom.getDocumentElement();

        assertEquals("com.voplica", text(project, "groupId"));
        assertEquals("voplica-api", text(project, "artifactId"));
        assertEquals(VERSION, text(project, "version"));
        assertEquals("Voplica API", text(project, "name"));
        assertFalse(text(project, "description").isBlank(), "no description");
        assertEquals("https://docs.voplica.com/clients/", text(project, "url"));

        List<Element> licenses = children(child(project, "licenses"), "license");
        assertEquals(1, licenses.size(), "one licence");
        assertEquals("Apache-2.0", text(licenses.get(0), "name"));
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0.txt", text(licenses.get(0), "url"));

        List<Element> developers = children(child(project, "developers"), "developer");
        assertEquals(1, developers.size(), "one developer");
        assertEquals("Voplica", text(developers.get(0), "name"));
        assertEquals("voplica@voplica.com", text(developers.get(0), "email"));
        assertEquals("Voplica", text(developers.get(0), "organization"));
        assertEquals("https://voplica.com", text(developers.get(0), "organizationUrl"));

        Element scm = child(project, "scm");
        assertEquals("https://github.com/voplica/api", text(scm, "url"));
        assertEquals("scm:git:https://github.com/voplica/api.git", text(scm, "connection"));
        assertEquals("scm:git:ssh://git@github.com/voplica/api.git", text(scm, "developerConnection"));

        // The runtime and the stub library, at the versions the code is generated for, on the consumer's compile path.
        Map<String, String> dependencies = new TreeMap<>();
        for (Element dependency : children(child(project, "dependencies"), "dependency")) {
            assertEquals("compile", text(dependency, "scope"), text(dependency, "artifactId"));
            dependencies.put(text(dependency, "groupId") + ":" + text(dependency, "artifactId"), text(dependency, "version"));
        }
        String grpc = System.getProperty("voplica.grpcVersion");
        assertEquals(Map.of(
                "com.google.protobuf:protobuf-java", System.getProperty("voplica.protobufVersion"),
                "io.grpc:grpc-protobuf", grpc,
                "io.grpc:grpc-stub", grpc), dependencies);
    }

    @Test
    void theClassesAreJava17Bytecode() throws IOException {
        List<Path> classes;
        try (Stream<Path> walk = Files.walk(Path.of(System.getProperty("voplica.classes")))) {
            classes = walk.filter(path -> path.toString().endsWith(".class")).collect(Collectors.toList());
        }
        assertTrue(classes.size() > 100, "only " + classes.size() + " classes");
        for (Path type : classes) {
            try (DataInputStream in = new DataInputStream(Files.newInputStream(type))) {
                assertEquals(0xCAFEBABE, in.readInt(), type.toString());
                in.readUnsignedShort();
                assertEquals(61, in.readUnsignedShort(), type + " is not Java 17 bytecode (class file version 61)");
            }
        }
    }

    @Test
    void theJarHoldsTheClassesTheLicenceAndAModuleName() throws IOException {
        try (JarFile jar = new JarFile(System.getProperty("voplica.jar"))) {
            assertLicence(jar);
            assertNotNull(jar.getEntry("com/voplica/api/ai/model/ModelVariantServiceGrpc.class"));
            Attributes manifest = jar.getManifest().getMainAttributes();
            assertEquals("com.voplica.api", manifest.getValue("Automatic-Module-Name"));
            assertEquals(VERSION, manifest.getValue("Implementation-Version"));
        }
    }

    @Test
    void theSourcesJarHoldsEveryGeneratedSource() throws IOException {
        List<String> generated = new ArrayList<>();
        for (Path dir : List.of(Path.of(System.getProperty("voplica.grpcSources")),
                Path.of(System.getProperty("voplica.grpcSources")).resolveSibling("java"))) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(path -> path.toString().endsWith(".java"))
                        .map(path -> dir.relativize(path).toString().replace('\\', '/'))
                        .forEach(generated::add);
            }
        }
        assertTrue(generated.size() > 100, "only " + generated.size() + " generated sources");
        try (JarFile jar = new JarFile(System.getProperty("voplica.sourcesJar"))) {
            assertLicence(jar);
            for (String source : generated) {
                assertNotNull(jar.getEntry(source), "the sources jar lacks " + source);
            }
        }
    }

    @Test
    void theJavadocJarHoldsTheJavadoc() throws IOException {
        try (JarFile jar = new JarFile(System.getProperty("voplica.javadocJar"))) {
            assertLicence(jar);
            assertNotNull(jar.getEntry("index.html"), "the javadoc jar has no index.html");
            assertNotNull(jar.getEntry("com/voplica/api/ai/model/ModelVariantServiceGrpc.html"));
        }
    }

    private static void assertLicence(JarFile jar) throws IOException {
        assertNotNull(jar.getEntry("META-INF/LICENSE"), jar.getName() + " has no META-INF/LICENSE");
        try (InputStream licence = jar.getInputStream(jar.getEntry("META-INF/LICENSE"))) {
            assertArrayEquals(Files.readAllBytes(ROOT.resolve("LICENSE")), licence.readAllBytes(), jar.getName());
        }
    }

    private static Element child(Element parent, String name) {
        List<Element> found = children(parent, name);
        assertEquals(1, found.size(), "<" + parent.getTagName() + "> holds " + found.size() + " <" + name + ">");
        return found.get(0);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element && ((Element) node).getTagName().equals(name)) {
                found.add((Element) node);
            }
        }
        return found;
    }

    private static String text(Element parent, String name) {
        return child(parent, name).getTextContent().trim();
    }
}
