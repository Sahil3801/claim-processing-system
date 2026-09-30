package com.claim.demo.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RailwayDeploymentConfigurationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void railwayProfileUsesInjectedPortAndRunsWithoutKafka() throws IOException {
        String railway = Files.readString(Path.of(
                "src", "main", "resources", "application-railway.properties"));

        assertThat(railway)
                .contains("server.port=${PORT:8080}")
                .contains("claims.outbox.relay.enabled=false")
                .contains("spring.kafka.listener.auto-startup=false")
                .contains("claims.kafka.create-topics=false")
                .doesNotContain("server.address");
    }

    @Test
    void backendServiceBuildsTheApiImageAndWaitsForHealth() throws IOException {
        JsonNode config = objectMapper.readTree(Files.readString(Path.of("railway.json")));

        assertThat(config.at("/build/builder").asText()).isEqualTo("DOCKERFILE");
        assertThat(config.at("/build/dockerfilePath").asText()).isEqualTo("Dockerfile");
        assertThat(config.at("/deploy/healthcheckPath").asText()).isEqualTo("/actuator/health");
    }

    @Test
    void frontendServiceProxiesApiToPrivateBackendAndServesSpaRoutes() throws IOException {
        JsonNode config = objectMapper.readTree(Files.readString(Path.of("frontend", "railway.json")));
        String caddyfile = Files.readString(Path.of("frontend", "Caddyfile"));
        String dockerfile = Files.readString(Path.of("frontend", "Dockerfile"));

        assertThat(config.at("/build/builder").asText()).isEqualTo("DOCKERFILE");
        assertThat(caddyfile)
                .contains(":{$PORT:8080}")
                .contains("reverse_proxy {$BACKEND_URL}")
                .contains("try_files {path} /index.html");
        assertThat(dockerfile).contains("npm ci", "npm run build", "COPY --from=build /app/dist /srv");
    }
}
