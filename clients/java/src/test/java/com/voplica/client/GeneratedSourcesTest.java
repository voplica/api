package com.voplica.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What protoc made of the published tree: a stub class for every service the protos declare (read the way
 * clients/typescript/test/services.mjs reads them), and classes the registry smoke of .github/workflows/java.yml passes
 * on before anything is published.
 */
class GeneratedSourcesTest {
    private static final Path PROTO_DIR = Path.of(System.getProperty("voplica.protoDir"));
    private static final Path GRPC_SOURCES = Path.of(System.getProperty("voplica.grpcSources"));
    private static final Pattern SERVICE_NAME =
            Pattern.compile("public static final java\\.lang\\.String SERVICE_NAME = \"([\\w.]+)\";");

    @Test
    void everyDeclaredServiceHasAGeneratedStubClass() throws IOException {
        Set<String> expected = DeclaredServices.of(PROTO_DIR);
        assertFalse(expected.isEmpty(), "no service is declared under " + PROTO_DIR);
        List<Path> stubs;
        try (Stream<Path> walk = Files.walk(GRPC_SOURCES)) {
            stubs = walk.filter(path -> path.getFileName().toString().endsWith("Grpc.java")).collect(Collectors.toList());
        }
        Set<String> generated = new TreeSet<>();
        for (Path stub : stubs) {
            Matcher name = SERVICE_NAME.matcher(Files.readString(stub));
            assertTrue(name.find(), stub + " declares no SERVICE_NAME");
            assertTrue(generated.add(name.group(1)), name.group(1) + " is generated twice");
        }
        assertEquals(expected, generated);
        assertEquals(expected.size(), stubs.size(), "a stub class for no declared service");
    }

    @Test
    void theRegistrySmokePassesOnTheBuildsClasses() throws IOException {
        Set<String> expected = DeclaredServices.of(PROTO_DIR);
        String summary = RegistrySmoke.check(expected, null);
        assertTrue(summary.contains(": " + expected.size() + " services, "), summary);
    }

    @Test
    void theRegistrySmokeFailsWhenTheClientLacksADeclaredServiceOrIsAnotherVersion() throws IOException {
        Set<String> expected = new TreeSet<>(DeclaredServices.of(PROTO_DIR));
        expected.add("voplica.api.missing.MissingService");
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> RegistrySmoke.check(expected, null));
        assertTrue(missing.getMessage().contains("voplica.api.missing.MissingService"), missing.getMessage());
        IllegalStateException version = assertThrows(IllegalStateException.class,
                () -> RegistrySmoke.check(DeclaredServices.of(PROTO_DIR), "0.0.0"));
        assertTrue(version.getMessage().contains("not 0.0.0"), version.getMessage());
    }

    @Test
    void theExpectedServicesSurviveCommentsAndStringsThatLookLikeComments(@TempDir Path dir) throws IOException {
        Path proto = dir.resolve("acme/mime/mime.proto");
        Files.createDirectories(proto.getParent());
        Files.write(proto, List.of(
                "syntax = \"proto3\";",
                "package acme.mime;",
                "// service Commented {}",
                "option (accept) = \"image/*\";",
                "service RealService {}",
                "/* a note, closed here */",
                "/* service AlsoCommented {} */",
                "option (other) = 'a // b';",
                "service SecondService {",
                "}",
                // A declaration at the start of a line inside a block comment is commented out.
                "/*",
                "service MultiLineCommented {",
                "}",
                "*/",
                // A /* inside a line comment opens nothing, even with a */ further down.
                "// globs like /* are text in a line comment",
                "service AfterLineComment {",
                "}",
                "/* closed later */",
                ""));
        assertEquals(Set.of("acme.mime.RealService", "acme.mime.SecondService", "acme.mime.AfterLineComment"),
                DeclaredServices.of(dir));
    }
}
