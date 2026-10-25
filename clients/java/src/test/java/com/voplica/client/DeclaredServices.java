package com.voplica.client;

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

/**
 * The services the client must hold: every {@code service} the .proto files under a directory declare (the published
 * protos the client is generated from), as {@code package.Service}; clients/typescript/test/services.mjs reads them the
 * same way. Also a program on its own, for the registry smoke of .github/workflows/java.yml:
 * {@code java DeclaredServices.java <proto dir>} prints them comma-separated, sorted.
 */
public final class DeclaredServices {
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern SERVICE = Pattern.compile("^\\s*service\\s+(\\w+)\\s*\\{", Pattern.MULTILINE);

    private DeclaredServices() {
    }

    public static void main(String[] args) throws IOException {
        System.out.println(String.join(",", of(Path.of(args.length > 0 ? args[0] : "proto"))));
    }

    /** The services declared under {@code protoDir}, sorted. */
    public static Set<String> of(Path protoDir) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(protoDir)) {
            files = walk.filter(path -> path.toString().endsWith(".proto") && Files.isRegularFile(path))
                    .sorted()
                    .collect(Collectors.toList());
        }
        Set<String> found = new TreeSet<>();
        for (Path file : files) {
            String text = withoutCommentsAndStrings(Files.readString(file));
            Matcher pkg = PACKAGE.matcher(text);
            String prefix = pkg.find() ? pkg.group(1) + "." : "";
            Matcher service = SERVICE.matcher(text);
            while (service.find()) {
                found.add(prefix + service.group(1));
            }
        }
        return found;
    }

    /**
     * The text with its comments removed and its string literals emptied, in one pass in source order: a {@code /*}
     * inside a string ("image/*") must not open a comment that swallows the declarations up to a later close. A string
     * ends on its own line, and a comment that never closes is text, as in services.mjs.
     */
    static String withoutCommentsAndStrings(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            char next = i + 1 < n ? text.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < n && text.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*' && text.indexOf("*/", i + 2) >= 0) {
                i = text.indexOf("*/", i + 2) + 2;
            } else if ((c == '"' || c == '\'') && stringEnd(text, i) >= 0) {
                out.append("\"\"");
                i = stringEnd(text, i) + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** The index of the quote that closes the string opened at {@code start}, or -1 when none does on its line. */
    private static int stringEnd(String text, int start) {
        char quote = text.charAt(start);
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == quote) {
                return i;
            }
            if (c == '\n' || (c == '\\' && (i + 1 >= text.length() || text.charAt(i + 1) == '\n'))) {
                return -1;
            }
            i += c == '\\' ? 2 : 1;
        }
        return -1;
    }
}
