package com.voplica.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.voplica.api.ai.model.GetRecommendedBaseModelVariantRequest;
import com.voplica.api.payment.GetBillingInfoRequest;
import com.voplica.api.payment.GetBillingInfoResponse;
import com.voplica.types.base.FileType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The JSON example of README.md ("Requests and responses as JSON"), compiled here so that it keeps compiling, and the
 * JsonFormat conversions of the generated messages it relies on. Nothing is called.
 */
class JsonApiExampleTest {
    private static final Path CLIENT = Path.of(System.getProperty("voplica.workflow"))
            .getParent().getParent().getParent().resolve("clients/java");

    /** README.md's example, line for line between the two markers. */
    static GetBillingInfoResponse billingInfo() throws IOException, InterruptedException {
        // README example: begin
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
        // README example: end
        return response.build();
    }

    @Test
    void theReadmeShowsTheExampleThisTestCompiles() throws IOException {
        List<String> readme = Files.readAllLines(CLIENT.resolve("README.md"));
        int open = readme.indexOf("```java");
        assertTrue(open >= 0, "README.md has no Java example");
        List<String> block = readme.subList(open + 1, nextFence(readme, open));
        List<String> source = Files.readAllLines(CLIENT.resolve("src/test/java/com/voplica/client/JsonApiExampleTest.java"));
        List<String> imports = source.stream().filter(line -> line.startsWith("import ")).collect(Collectors.toList());
        List<String> body = stripped(source.subList(source.indexOf("        // README example: begin") + 1,
                source.indexOf("        // README example: end")));
        List<String> readmeImports = new ArrayList<>();
        List<String> readmeBody = new ArrayList<>();
        for (String line : block) {
            (line.startsWith("import ") ? readmeImports : readmeBody).add(line);
        }
        assertTrue(imports.containsAll(readmeImports), "README.md imports what this test does not: " + readmeImports);
        assertEquals(body, stripped(readmeBody));
    }

    private static int nextFence(List<String> lines, int open) {
        for (int i = open + 1; i < lines.size(); i++) {
            if (lines.get(i).equals("```")) {
                return i;
            }
        }
        throw new AssertionError("README.md's Java example is not closed");
    }

    private static List<String> stripped(List<String> lines) {
        return lines.stream().map(String::strip).filter(line -> !line.isEmpty()).collect(Collectors.toList());
    }

    @Test
    void aRequestPrintsAsTheApisJsonAndParsesBack() throws InvalidProtocolBufferException {
        assertEquals("{\n}", JsonFormat.printer().print(GetBillingInfoRequest.getDefaultInstance()));
        GetRecommendedBaseModelVariantRequest request =
                GetRecommendedBaseModelVariantRequest.newBuilder().setOutputFileType(FileType.IMAGE_FILE).build();
        String json = JsonFormat.printer().omittingInsignificantWhitespace().print(request);
        assertEquals("{\"outputFileType\":\"IMAGE_FILE\"}", json);
        GetRecommendedBaseModelVariantRequest.Builder parsed = GetRecommendedBaseModelVariantRequest.newBuilder();
        JsonFormat.parser().merge(json, parsed);
        assertEquals(request, parsed.build());
    }

    @Test
    void aResponseWithAFieldThisVersionDoesNotKnowStillParses() throws InvalidProtocolBufferException {
        String answer = "{\"billingInfo\":{\"accountId\":\"acc-1\",\"permanentCreditsMilli\":\"1500\",\"aFieldFromLater\":true}}";
        GetBillingInfoResponse.Builder response = GetBillingInfoResponse.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(answer, response);
        assertEquals("acc-1", response.getBillingInfo().getAccountId());
        assertEquals(1500L, response.getBillingInfo().getPermanentCreditsMilli());
    }
}
