package com.claim.demo.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDeploymentConfigurationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void demoProfileUsesInjectedPortAndRunsWithOnlyPostgres() throws IOException {
        String demo = Files.readString(Path.of(
                "src", "main", "resources", "application-demo.properties"));

        assertThat(demo)
                .contains("server.port=${PORT:8080}")
                .contains("claims.outbox.relay.enabled=false")
                .contains("spring.kafka.listener.auto-startup=false")
                .contains("claims.kafka.create-topics=false")
                .contains("claims.cache.enabled=${CLAIMS_CACHE_ENABLED:false}")
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

    @Test
    @SuppressWarnings("unchecked")
    void renderBlueprintRunsFreeApiWithDemoProfileAndNoCommittedSecrets() throws IOException {
        Map<String, Object> blueprint = new Yaml().load(Files.readString(Path.of("render.yaml")));
        Map<String, Object> api = ((List<Map<String, Object>>) blueprint.get("services")).get(0);
        List<Map<String, Object>> envVars = (List<Map<String, Object>>) api.get("envVars");

        assertThat(api)
                .containsEntry("runtime", "docker")
                .containsEntry("plan", "free")
                .containsEntry("healthCheckPath", "/actuator/health");
        assertThat(envVars).anySatisfy(env -> assertThat(env)
                .containsEntry("key", "SPRING_PROFILES_ACTIVE").containsEntry("value", "production,demo"));
        assertThat(envVars).anySatisfy(env -> assertThat(env)
                .containsEntry("key", "JWT_SECRET").containsEntry("generateValue", true));
        for (String secret : new String[]{"DB_JDBC_URL", "DB_USERNAME", "DB_PASSWORD"}) {
            assertThat(envVars).anySatisfy(env -> assertThat(env)
                    .containsEntry("key", secret).containsEntry("sync", false).doesNotContainKey("value"));
        }
    }

    @Test
    void vercelProxiesApiToRenderAndFallsBackToTheSpa() throws IOException {
        JsonNode rewrites = objectMapper.readTree(
                Files.readString(Path.of("frontend", "vercel.json"))).get("rewrites");

        assertThat(rewrites.get(0).get("source").asText()).isEqualTo("/api/:path*");
        assertThat(rewrites.get(0).get("destination").asText()).endsWith(".onrender.com/api/:path*");
        assertThat(rewrites.get(rewrites.size() - 1).get("destination").asText()).isEqualTo("/index.html");
    }
}
