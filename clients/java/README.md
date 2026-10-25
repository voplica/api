# com.voplica:voplica-api

The Voplica API for Java: every message, enum and gRPC service stub of the published protobuf definitions, generated
with protoc and grpc-java's plugin for [protobuf-java](https://protobuf.dev/reference/java/) and
[grpc-java](https://grpc.io/docs/languages/java/). Java 17 or later.

The documentation: https://docs.voplica.com/clients/

## Install

From Maven Central, no token needed. With Gradle:

```kotlin
dependencies {
    implementation("com.voplica:voplica-api:VERSION")
}
```

With Maven:

```xml
<dependency>
  <groupId>com.voplica</groupId>
  <artifactId>voplica-api</artifactId>
  <version>VERSION</version>
</dependency>
```

Replace `VERSION` with the API's version: the newest on Maven Central, which is the `VERSION` file at the root of
[voplica/api](https://github.com/voplica/api).

The package is also published to GitHub Packages, at `https://maven.pkg.github.com/voplica/api`, which needs a GitHub
token with the `read:packages` scope even for a public package.

The generated classes need the protobuf-java runtime they are generated for, or a later one; the package's POM names
it. If another dependency brings an older protobuf-java (3.x), declare the POM's version yourself.

## Requests and responses as JSON

The native gRPC endpoint opens soon. Today the JSON API is served, and these classes give you type-safe requests and
responses for it: `JsonFormat` of `com.google.protobuf:protobuf-java-util` (at the protobuf-java version above) turns a
message into the JSON the API takes, and the API's JSON back into a message. Every RPC is a `POST` of its request to
`https://api.voplica.com/<package>.<Service>/<Method>`; an API key goes in the `Authorization` header:

```java
import com.google.protobuf.util.JsonFormat;
import com.voplica.api.payment.GetBillingInfoRequest;
import com.voplica.api.payment.GetBillingInfoResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

String apiKey = System.getenv("VOPLICA_API_KEY");
HttpRequest request = HttpRequest.newBuilder(
                URI.create("https://api.voplica.com/voplica.api.payment.PaymentService/GetBillingInfo"))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(
                JsonFormat.printer().print(GetBillingInfoRequest.getDefaultInstance())))
        .build();
HttpResponse<String> answer = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
if (answer.statusCode() != 200) {
    throw new IllegalStateException("HTTP " + answer.statusCode() + ": " + answer.body());
}
GetBillingInfoResponse.Builder response = GetBillingInfoResponse.newBuilder();
JsonFormat.parser().ignoringUnknownFields().merge(answer.body(), response);
System.out.println(response.getBillingInfo());
```

`ignoringUnknownFields()` keeps a client working when a later version of the API adds a field.

## gRPC

Each service has a `<Service>Grpc` class with its stubs (`newStub`, `newBlockingStub`, `newFutureStub`) for a grpc-java
channel, which needs a transport such as `io.grpc:grpc-netty-shaded`; they are for the native gRPC endpoint once it
opens.

## Building it

From `clients/java`, with protoc and protoc-gen-grpc-java of the releases `build.gradle` names on the `PATH` (or named
with `-Pvoplica.protoc=<path>` and `-Pvoplica.protocGenGrpcJava=<path>`):

```sh
./gradlew build
```

The sources are generated from `../../proto` at build time and never committed. `.github/workflows/java.yml` builds and
tests the package on every pull request that touches it, and publishes it on every version tag.

## Versions and licence

The package's version is the API's version (`VERSION` at the root of [voplica/api](https://github.com/voplica/api)).
Licensed under the Apache License 2.0.
