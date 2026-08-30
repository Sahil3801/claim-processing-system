package com.claim.demo.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubActionsWorkflowTest {

    private static final Path WORKFLOW = Path.of(".github", "workflows", "ci.yml");

    @Test
    void isValidYaml() throws IOException {
        assertNotNull(new Yaml().load(Files.readString(WORKFLOW)));
    }

    @Test
    void runsAllRequiredVerificationOnPushAndPullRequests() throws IOException {
        String workflow = Files.readString(WORKFLOW);

        assertAll(
                () -> assertTrue(workflow.contains("  push:")),
                () -> assertTrue(workflow.contains("  pull_request:")),
                () -> assertTrue(workflow.contains("run: chmod +x mvnw")),
                () -> assertTrue(workflow.contains("./mvnw -B -ntp verify")),
                () -> assertTrue(workflow.contains("run: npm ci")),
                () -> assertTrue(workflow.contains("run: npm test")),
                () -> assertTrue(workflow.contains("run: npm run build")),
                () -> assertTrue(workflow.contains("uses: docker/build-push-action@v7")),
                () -> assertTrue(workflow.contains("push: false")),
                () -> assertTrue(workflow.contains("docker image inspect")));
    }

    @Test
    void usesLeastPrivilegeCachingAndNoDeploymentCredentials() throws IOException {
        String workflow = Files.readString(WORKFLOW);

        assertAll(
                () -> assertTrue(workflow.contains("permissions:\n  contents: read")),
                () -> assertTrue(workflow.contains("secrets.GITHUB_TOKEN")),
                () -> assertTrue(workflow.contains("persist-credentials: false")),
                () -> assertTrue(workflow.contains("cache: maven")),
                () -> assertTrue(workflow.contains("cache: npm")),
                () -> assertTrue(workflow.contains("cache-to: type=gha,mode=min")),
                () -> assertTrue(workflow.contains("cancel-in-progress: true")),
                () -> assertFalse(workflow.contains("id-token: write")),
                () -> assertFalse(workflow.contains("aws-actions/")),
                () -> assertFalse(workflow.contains("docker/login-action")));
    }
}
