package com.voplica.client;

import com.voplica.api.ai.model.GetRecommendedBaseModelVariantRequest;
import com.voplica.api.ai.model.GetRecommendedBaseModelVariantResponse;
import com.voplica.api.ai.model.ModelVariantServiceGrpc;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The registry smoke of .github/workflows/java.yml: compiled and run on Java 17 in an empty Maven project against
 * com.voplica:voplica-api as resolved from GitHub Packages, with the services the published protos declare
 * ({@code package.Service}, comma-separated: {@code java DeclaredServices.java proto}) and the version as its
 * arguments. The client must hold exactly those services, each with its three stubs, and the request and response of
 * every method must load and round-trip through its marshaller. Nothing is called. The build's own tests run it
 * against the build's classes.
 */
public final class RegistrySmoke {
    private static final List<String> STUB_FACTORIES = List.of("newStub", "newBlockingStub", "newFutureStub");

    /** A channel for making stubs: nothing is ever called through it. */
    private static final Channel NO_CALLS = new Channel() {
        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> method, CallOptions options) {
            throw new UnsupportedOperationException("the smoke calls nothing: " + method.getFullMethodName());
        }

        @Override
        public String authority() {
            return "api.voplica.com";
        }
    };

    private RegistrySmoke() {
    }

    public static void main(String[] args) throws Exception {
        Set<String> expected = new TreeSet<>();
        for (String name : (args.length > 0 ? args[0] : "").split(",")) {
            if (!name.isEmpty()) {
                expected.add(name);
            }
        }
        System.out.println(check(expected, args.length > 1 ? args[1] : null));
    }

    /**
     * Checks the client on the classpath against the declared services, and against {@code expectedVersion} (the jar's
     * Implementation-Version) unless it is null; returns the summary line.
     */
    public static String check(Set<String> expected, String expectedVersion) throws IOException {
        if (expected.isEmpty()) {
            throw new IllegalArgumentException("no service to expect: pass the declared services, comma-separated");
        }
        // Named classes, compiled against as an API user does: the generated API's shape, not only what reflection finds.
        MethodDescriptor<GetRecommendedBaseModelVariantRequest, GetRecommendedBaseModelVariantResponse> named =
                ModelVariantServiceGrpc.getGetRecommendedBaseModelVariantMethod();
        String namedMethod = "voplica.api.ai.model.ModelVariantService/GetRecommendedBaseModelVariant";
        if (!named.getFullMethodName().equals(namedMethod)) {
            throw new IllegalStateException(namedMethod + " is named " + named.getFullMethodName());
        }
        if (GetRecommendedBaseModelVariantRequest.newBuilder().build().getSerializedSize() != 0) {
            throw new IllegalStateException("an empty GetRecommendedBaseModelVariantRequest is not empty on the wire");
        }
        String version = ModelVariantServiceGrpc.class.getPackage().getImplementationVersion();
        if (expectedVersion != null && !expectedVersion.equals(version)) {
            throw new IllegalStateException("the jar is version " + version + ", not " + expectedVersion);
        }

        Map<String, Class<?>> services = generatedServices(location(ModelVariantServiceGrpc.class));
        if (!services.keySet().equals(expected)) {
            throw new IllegalStateException("the client holds " + services.keySet() + "; the protos declare " + expected);
        }
        int methods = 0;
        for (Map.Entry<String, Class<?>> service : services.entrySet()) {
            ServiceDescriptor descriptor = (ServiceDescriptor) invoke(service.getValue(), "getServiceDescriptor");
            if (!descriptor.getName().equals(service.getKey())) {
                throw new IllegalStateException(service.getValue().getName() + " describes " + descriptor.getName());
            }
            for (String factory : STUB_FACTORIES) {
                if (invoke(service.getValue(), factory, NO_CALLS) == null) {
                    throw new IllegalStateException(service.getValue().getName() + "." + factory + " made no stub");
                }
            }
            for (MethodDescriptor<?, ?> method : descriptor.getMethods()) {
                if (!service.getKey().equals(method.getServiceName())) {
                    throw new IllegalStateException(method.getFullMethodName() + " is not a method of " + service.getKey());
                }
                roundTrip(method);
                methods++;
            }
        }
        return String.format("com.voplica:voplica-api %s: %d services, %d methods, three stubs for every service",
                version == null ? "(the build's classes)" : version, services.size(), methods);
    }

    /** The jar, or the directory of classes, a class was loaded from. */
    private static Path location(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Every generated service class under {@code root} (a {@code *Grpc} class with a SERVICE_NAME), by service name. */
    private static Map<String, Class<?>> generatedServices(Path root) throws IOException {
        List<String> entries = new ArrayList<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.map(path -> root.relativize(path).toString().replace(File.separatorChar, '/')).forEach(entries::add);
            }
        } else {
            try (JarFile jar = new JarFile(root.toFile())) {
                entries.addAll(jar.stream().map(entry -> entry.getName()).collect(Collectors.toList()));
            }
        }
        Map<String, Class<?>> services = new TreeMap<>();
        for (String entry : entries) {
            if (!entry.endsWith("Grpc.class") || entry.contains("$")) {
                continue;
            }
            String className = entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
            Class<?> type;
            try {
                type = Class.forName(className, true, RegistrySmoke.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(className + " is in " + root + " but does not load", e);
            }
            Field name;
            try {
                name = type.getField("SERVICE_NAME");
            } catch (NoSuchFieldException e) {
                continue;
            }
            if (Modifier.isStatic(name.getModifiers()) && name.getType() == String.class) {
                try {
                    Class<?> earlier = services.put((String) name.get(null), type);
                    if (earlier != null) {
                        throw new IllegalStateException(name.get(null) + " is generated twice: " + earlier + ", " + type);
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return services;
    }

    private static Object invoke(Class<?> type, String method, Channel... channel) {
        try {
            return channel.length == 0
                    ? type.getMethod(method).invoke(null)
                    : type.getMethod(method, Channel.class).invoke(null, channel[0]);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException(type.getName() + " has no public static " + method, e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(type.getName() + "." + method + " failed", e.getCause());
        }
    }

    /** The default request and response go through the method's marshallers: their classes load, and are empty on the wire. */
    private static <RequestT, ResponseT> void roundTrip(MethodDescriptor<RequestT, ResponseT> method) throws IOException {
        RequestT request = method.parseRequest(new ByteArrayInputStream(new byte[0]));
        ResponseT response = method.parseResponse(new ByteArrayInputStream(new byte[0]));
        try (InputStream requestBytes = method.streamRequest(request);
                InputStream responseBytes = method.streamResponse(response)) {
            if (requestBytes.readAllBytes().length != 0 || responseBytes.readAllBytes().length != 0) {
                throw new IllegalStateException(method.getFullMethodName() + ": a default message is not empty on the wire");
            }
        }
        if (!(request instanceof com.google.protobuf.Message) || !(response instanceof com.google.protobuf.Message)) {
            throw new IllegalStateException(method.getFullMethodName() + " does not carry protobuf messages");
        }
    }
}
