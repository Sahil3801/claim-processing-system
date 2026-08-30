package com.claim.demo.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DockerComposeConfigurationTest {

    @Test
    void composeDefinesHealthCheckedResourceBoundApplicationStack() throws IOException {
        Map<String, Object> compose;
        try (InputStream input = Files.newInputStream(Path.of("docker-compose.yml"))) {
            compose = new Yaml().load(input);
        }

        Map<String, Object> services = map(compose.get("services"));
        assertThat(services.keySet()).containsExactlyInAnyOrder("app", "postgres", "redis", "kafka");

        for (String serviceName : services.keySet()) {
            Map<String, Object> service = map(services.get(serviceName));
            assertThat(service).containsKeys("healthcheck", "mem_limit", "cpus", "pids_limit");
        }

        Map<String, Object> app = map(services.get("app"));
        Map<String, Object> dependencies = map(app.get("depends_on"));
        assertThat(dependencies.keySet()).containsExactlyInAnyOrder("postgres", "redis", "kafka");
        dependencies.values().forEach(dependency ->
                assertThat(map(dependency)).containsEntry("condition", "service_healthy"));
        assertThat(app).containsEntry("read_only", true);

        Map<String, Object> appEnvironment = map(app.get("environment"));
        assertThat(appEnvironment)
                .containsEntry("DB_HOST", "postgres")
                .containsEntry("REDIS_HOST", "redis")
                .containsEntry("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
        assertThat(appEnvironment.get("DB_PASSWORD").toString()).contains("POSTGRES_PASSWORD");
        assertThat(appEnvironment.get("JWT_SECRET").toString()).contains("JWT_SECRET");

        assertThat(map(services.get("postgres")).get("image").toString()).doesNotContain("latest");
        assertThat(map(services.get("redis")).get("image").toString()).doesNotContain("latest");
        assertThat(map(services.get("kafka")).get("image").toString()).doesNotContain("latest");
    }

    @Test
    void dockerfileUsesSeparateBuildStageAndNonRootRuntimeUser() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile)
                .contains(" AS build")
                .contains("FROM eclipse-temurin:17-jre-alpine AS runtime")
                .contains("USER spring:spring")
                .contains("ENTRYPOINT [\"java\", \"-jar\", \"/app/app.jar\"]");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
