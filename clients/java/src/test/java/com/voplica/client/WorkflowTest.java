package com.voplica.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The shape of the publish workflow (.github/workflows/java.yml), read as lines the way
 * clients/typescript/test/workflow.test.mjs reads client.yml: it publishes only from a version tag; the GitHub Packages
 * jobs use the run's own GITHUB_TOKEN with the packages permission and mint no App token (an App installation token was
 * refused the creation of the organisation package, 403, 2026-10-25); the Maven Central secrets are named, never
 * spelt out, and reach only the job that publishes there; the generators are the build's releases, checked by sha256.
 */
class WorkflowTest {
    private static final List<String> WORKFLOW = read(Path.of(System.getProperty("voplica.workflow")));
    private static final Path CLIENT = Path.of(System.getProperty("voplica.workflow"))
            .getParent().getParent().getParent().resolve("clients/java");
    private static final Path SCRIPTS = CLIENT.resolve("scripts");
    private static final Set<String> CENTRAL_SECRETS = Set.of(
            "MAVEN_CENTRAL_USERNAME", "MAVEN_CENTRAL_PASSWORD", "MAVEN_GPG_PRIVATE_KEY", "MAVEN_GPG_PASSPHRASE");
    private static final Pattern SECRET = Pattern.compile("secrets\\.([A-Za-z0-9_]+)");
    private static final Pattern ENV_SECRET = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*): \\$\\{\\{ secrets\\.([A-Za-z0-9_]+) }}");

    private static List<String> read(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The lines of a top-level block ({@code on:}, {@code permissions:}, {@code env:}, {@code jobs:}), trimmed. */
    private static List<String> block(String key) {
        int start = WORKFLOW.indexOf(key + ":");
        assertTrue(start >= 0, "no top-level " + key);
        int end = start + 1;
        while (end < WORKFLOW.size() && (WORKFLOW.get(end).isEmpty() || WORKFLOW.get(end).startsWith(" "))) {
            end++;
        }
        return WORKFLOW.subList(start + 1, end).stream().map(String::trim).filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    /** The trimmed lines of one job. */
    private static List<String> job(String name) {
        return rawJob(name).stream().map(String::trim).collect(Collectors.toList());
    }

    /** The job's own keys ({@code if:}, {@code needs:}, ...), not its steps', trimmed. */
    private static List<String> jobKeys(String name) {
        return rawJob(name).stream().filter(line -> line.matches(" {4}\\S.*")).map(String::trim).collect(Collectors.toList());
    }

    private static List<String> rawJob(String name) {
        int jobs = WORKFLOW.indexOf("jobs:");
        int start = WORKFLOW.subList(jobs, WORKFLOW.size()).indexOf("  " + name + ":");
        assertTrue(jobs >= 0 && start >= 0, "no job " + name);
        start += jobs;
        int end = start + 1;
        while (end < WORKFLOW.size() && !WORKFLOW.get(end).matches(" {2}\\S.*")) {
            end++;
        }
        return WORKFLOW.subList(start, end);
    }

    private static Set<String> jobNames() {
        return WORKFLOW.subList(WORKFLOW.indexOf("jobs:"), WORKFLOW.size()).stream()
                .filter(line -> line.matches(" {2}[a-z][\\w-]*:")).map(line -> line.trim().replace(":", ""))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** The secrets one job names ({@code secrets.NAME}). */
    private static Set<String> secretsOf(String name) {
        Set<String> found = new TreeSet<>();
        for (String line : job(name)) {
            Matcher secret = SECRET.matcher(line);
            while (secret.find()) {
                found.add(secret.group(1));
            }
        }
        return found;
    }

    @Test
    void itRunsOnAVersionTagOnPullRequestsAndOnDispatchOnly() {
        List<String> on = block("on");
        assertEquals(List.of("push:", "tags: [\"v*\"]"), on.subList(0, 2), "the push trigger is the version tags alone");
        assertTrue(on.contains("pull_request:"), "no pull_request trigger");
        assertTrue(on.contains("workflow_dispatch:"), "no workflow_dispatch trigger");
        for (String trigger : List.of("pull_request_target", "workflow_run", "schedule", "branches")) {
            assertFalse(on.stream().anyMatch(line -> line.startsWith(trigger)), "the workflow runs on " + trigger);
        }
    }

    @Test
    void onlyAPushedVersionTagPublishesAndTheBuildGatesEveryPublish() {
        assertEquals(Set.of("build", "publish-github", "smoke", "publish-central"), jobNames());
        assertFalse(jobKeys("build").stream().anyMatch(line -> line.startsWith("if:")), "the build is skipped on a trigger");
        for (String publish : List.of("publish-github", "publish-central")) {
            assertTrue(jobKeys(publish).contains("if: github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')"),
                    publish + " publishes on something other than a pushed version tag");
        }
        assertTrue(jobKeys("publish-github").contains("needs: build"), "publish-github does not wait for the tests");
        assertTrue(jobKeys("smoke").contains("needs: publish-github"), "the smoke does not follow the GitHub Packages publish");
        // Maven Central keeps every version for ever: it waits for the GitHub Packages copy to be resolved and run.
        assertTrue(jobKeys("publish-central").contains("needs: [publish-github, smoke]"), "Maven Central does not wait for the smoke");
        assertTrue(job("build").stream().anyMatch(line -> line.startsWith("run: ./gradlew build ")), "the build job runs no tests");
    }

    @Test
    void noStepOrJobMayFailQuietly() {
        // A `continue-on-error` anywhere in build -> publish-github -> smoke would let the Maven Central publish, which
        // cannot be taken back, run past failing tests, a failing GitHub Packages publish or a failing smoke.
        for (String line : WORKFLOW) {
            assertFalse(line.trim().startsWith("continue-on-error"), "a failure is let through: " + line);
        }
    }

    @Test
    void theBuildSignsADryRunBundleWithASubkeyShapedThrowawayKey() {
        List<String> build = job("build");
        // The harder shape of an owner's key: a certify-only primary and a signing subkey, so VALIDSIG names the
        // subkey first and the primary last (central-bundle.sh checks the primary).
        int key = build.indexOf("gpg --batch --pinentry-mode loopback --passphrase throwaway --quick-gen-key "
                + "\"Dry run <dry-run@example.invalid>\" ed25519 cert 1d");
        int subkey = build.indexOf("gpg --batch --pinentry-mode loopback --passphrase throwaway --quick-add-key \"$fpr\" ed25519 sign 1d");
        int handed = build.indexOf("MAVEN_GPG_PRIVATE_KEY=\"$throwaway_key\" MAVEN_GPG_PASSPHRASE=throwaway \\");
        int bundle = build.indexOf("scripts/central-bundle.sh build/staging-deploy \"$version\" \"$RUNNER_TEMP/central-bundle.zip\"");
        int staged = build.indexOf("run: ./gradlew build publishMavenJavaPublicationToStagingRepository --no-daemon");
        assertTrue(key >= 0, "the dry run's key is not a certify-only primary");
        assertTrue(subkey > key, "the dry run's key has no signing subkey");
        assertTrue(handed > subkey && bundle == handed + 1, "the dry run does not hand its key to central-bundle.sh");
        assertTrue(staged >= 0 && staged < key, "the dry run does not follow the build that stages the layout");
    }

    @Test
    void theGradleDistributionIsPinnedBySha256() throws IOException {
        List<String> wrapper = Files.readAllLines(CLIENT.resolve("gradle/wrapper/gradle-wrapper.properties"));
        assertTrue(wrapper.stream().anyMatch(line -> line.matches("distributionSha256Sum=[0-9a-f]{64}")),
                "the wrapper downloads the Gradle distribution unchecked");
    }

    @Test
    void theRunGetsReadAccessAndOnlyTheGitHubPackagesJobsGetPackages() {
        assertEquals(List.of("contents: read"), block("permissions"));
        assertEquals(List.of("packages: write"), packagesLines("publish-github"));
        assertEquals(List.of("packages: read"), packagesLines("smoke"));
        assertEquals(List.of(), packagesLines("build"));
        assertEquals(List.of(), packagesLines("publish-central"));
        for (String line : WORKFLOW) {
            assertFalse(line.contains("id-token:"), "an OIDC token is requested: " + line);
            assertFalse(line.trim().startsWith("contents: write"), "the contents are writable: " + line);
        }
    }

    private static List<String> packagesLines(String name) {
        return job(name).stream().filter(line -> line.startsWith("packages:"))
                .map(line -> line.replaceAll("\\s*#.*", "")).collect(Collectors.toList());
    }

    @Test
    void theGitHubPackagesJobsUseTheRunTokenAndMintNoAppToken() {
        assertTrue(job("publish-github").contains("ORG_GRADLE_PROJECT_GitHubPackagesPassword: ${{ secrets.GITHUB_TOKEN }}"),
                "publish-github does not publish with GITHUB_TOKEN");
        assertTrue(job("smoke").contains("GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}"), "the smoke does not resolve with GITHUB_TOKEN");
        // The existence check speaks to the Maven registry as GitHub documents it and Maven and Gradle do: Basic.
        List<String> github = job("publish-github");
        assertTrue(github.contains("basic=$(printf '%s:%s' \"$GITHUB_ACTOR\" \"$GITHUB_TOKEN\" | base64 -w0)"),
                "the GitHub Packages check does not build Basic credentials from the actor and the run token");
        assertTrue(github.stream().anyMatch(line -> line.contains("printf 'Authorization: Basic %s\\n' \"$basic\"")),
                "the GitHub Packages check does not send Basic credentials");
        assertFalse(WORKFLOW.stream().anyMatch(line -> line.contains("Authorization: Bearer %s\\n' \"$GITHUB_TOKEN\"")),
                "the run token is sent as a Bearer token");
        for (String line : WORKFLOW) {
            String step = line.trim();
            assertFalse(step.contains("create-github-app-token"), "an App token is minted: " + line);
            assertFalse(step.startsWith("permission-"), "an App permission is requested: " + line);
            assertFalse(step.startsWith("app-id:") || step.startsWith("client-id:") || step.startsWith("private-key:"),
                    "an App credential is passed: " + line);
        }
    }

    @Test
    void theSecretsAreNamedOnlyInStepEnvironmentsAndTheCentralOnesOnlyInPublishCentral() {
        Set<String> named = new TreeSet<>();
        Map<String, String> mapped = new TreeMap<>();
        for (String line : WORKFLOW) {
            Matcher secret = SECRET.matcher(line);
            while (secret.find()) {
                named.add(secret.group(1));
            }
            if (line.contains("secrets.")) {
                // Nothing but `NAME: ${{ secrets.NAME }}` in a step's env: a secret is never spelt into a script.
                Matcher env = ENV_SECRET.matcher(line.trim());
                assertTrue(env.matches(), "a secret outside a step's env: " + line);
                mapped.merge(env.group(2), env.group(1), (a, b) -> a.equals(b) ? a : a + "," + b);
            }
            assertFalse(line.contains("secrets: inherit"), "the secrets are handed on wholesale: " + line);
        }
        Set<String> expected = new TreeSet<>(CENTRAL_SECRETS);
        expected.add("GITHUB_TOKEN");
        assertEquals(expected, named);
        for (String secret : CENTRAL_SECRETS) {
            assertEquals(secret, mapped.get(secret), secret + " reaches the step under another name");
        }
        // The build (pull requests included) gets no secret: its signing dry run uses a key it makes and throws away.
        assertEquals(Set.of(), secretsOf("build"));
        assertEquals(Set.of("GITHUB_TOKEN"), secretsOf("publish-github"));
        assertEquals(Set.of("GITHUB_TOKEN"), secretsOf("smoke"));
        assertEquals(CENTRAL_SECRETS, secretsOf("publish-central"));
    }

    @Test
    void everyCheckoutDropsItsCredentials() {
        int checkouts = 0;
        for (int i = 0; i < WORKFLOW.size(); i++) {
            if (WORKFLOW.get(i).trim().startsWith("- uses: actions/checkout@")) {
                checkouts++;
                assertEquals("with:", WORKFLOW.get(i + 1).trim(), "line " + (i + 2));
                assertEquals("persist-credentials: false", WORKFLOW.get(i + 2).trim(), "line " + (i + 3));
            }
        }
        assertEquals(4, checkouts, "one checkout per job");
    }

    @Test
    void theGeneratorsAreTheBuildsReleasesCheckedBySha256() throws IOException {
        Map<String, String> env = new TreeMap<>();
        for (String line : block("env")) {
            if (!line.startsWith("#")) {
                String[] pair = line.split(": ", 2);
                env.put(pair[0], pair[1].replace("\"", ""));
            }
        }
        assertEquals(System.getProperty("voplica.protocVersion"), env.get("PROTOC_VERSION"));
        assertEquals(System.getProperty("voplica.grpcVersion"), env.get("GRPC_JAVA_VERSION"));
        assertTrue(env.get("PROTOC_SHA256").matches("[0-9a-f]{64}"), "PROTOC_SHA256 is not a sha256");
        assertTrue(env.get("GRPC_JAVA_SHA256").matches("[0-9a-f]{64}"), "GRPC_JAVA_SHA256 is not a sha256");
        String install = Files.readString(SCRIPTS.resolve("install-generators.sh"));
        assertTrue(install.contains("echo \"$PROTOC_SHA256  $work/bin/protoc\" | sha256sum --check --strict"), "protoc is not checked");
        assertTrue(install.contains("echo \"$GRPC_JAVA_SHA256  $work/protoc-gen-grpc-java\" | sha256sum --check --strict"),
                "protoc-gen-grpc-java is not checked");
        for (String name : List.of("build", "publish-github")) {
            assertTrue(job(name).contains("scripts/install-generators.sh \"$RUNNER_TEMP/bin\""), name + " installs no checked generators");
        }
    }

    @Test
    void aVersionAlreadyOnARegistryIsANotice() {
        List<String> github = job("publish-github");
        assertTrue(github.contains("echo \"::notice::com.voplica:voplica-api:$VERSION is already on GitHub Packages\""));
        assertTrue(github.contains("2[0-9][0-9]|3[0-9][0-9])"),
                "a 2xx or a 3xx (the registry redirects to the stored file) means the version is present");
        assertTrue(github.contains("404)"), "only a 404 means the version is absent");
        assertTrue(github.contains("if [ \"$PRESENT\" != true ]; then"), "the GitHub Packages publish ignores a present version");
        List<String> central = job("publish-central");
        assertTrue(central.contains("run: scripts/central-portal.sh published \"$VERSION\""), "no check for a version on Maven Central");
        assertEquals(2, central.stream().filter(line -> line.equals("if: steps.present.outputs.present != 'true'")).count(),
                "the signing and the upload do not skip a version on Maven Central");
    }
}
