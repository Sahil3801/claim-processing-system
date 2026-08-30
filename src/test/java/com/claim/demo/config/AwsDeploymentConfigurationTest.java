package com.claim.demo.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AwsDeploymentConfigurationTest {

    private static final Path AWS_ROOT = Path.of("deploy", "aws");

    @Test
    void ec2ComposeUsesExternalRdsAndPrivateLocalInfrastructure() throws IOException {
        Map<String, Object> compose = new Yaml().load(
                Files.readString(AWS_ROOT.resolve("docker-compose.ec2.yml")));
        Map<String, Object> services = map(compose.get("services"));

        assertThat(services.keySet()).containsExactlyInAnyOrder("app", "redis", "kafka", "proxy");
        assertThat(services).doesNotContainKey("postgres");

        Map<String, Object> app = map(services.get("app"));
        Map<String, Object> appEnvironment = map(app.get("environment"));
        assertThat(appEnvironment)
                .containsEntry("SPRING_PROFILES_ACTIVE", "production")
                .containsKeys("DB_JDBC_URL", "DB_USERNAME", "DB_PASSWORD")
                .containsEntry("REDIS_HOST", "redis")
                .containsEntry("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
        assertThat(app).containsKeys("healthcheck", "mem_limit", "cpus", "pids_limit");
        assertThat(app).doesNotContainKey("ports");

        for (String privateService : new String[]{"redis", "kafka"}) {
            Map<String, Object> service = map(services.get(privateService));
            assertThat(service).doesNotContainKey("ports");
            assertThat(service).containsKeys("healthcheck", "mem_limit", "cpus", "pids_limit");
        }

        Map<String, Object> proxy = map(services.get("proxy"));
        assertThat(proxy).containsKeys("ports", "healthcheck", "mem_limit", "cpus", "pids_limit");
        assertThat(proxy.get("ports").toString()).contains("80:80", "443:443");
    }

    @Test
    void productionProfileAndScriptsApplySmallInstanceGuardrails() throws IOException {
        String productionProperties = Files.readString(Path.of(
                "src", "main", "resources", "application-production.properties"));
        String baseProperties = Files.readString(Path.of(
                "src", "main", "resources", "application.properties"));

        assertThat(baseProperties).contains("${DB_JDBC_URL:");
        assertThat(productionProperties)
                .contains("spring.datasource.hikari.maximum-pool-size=${DB_POOL_MAX_SIZE:8}")
                .contains("management.health.redis.enabled=${REDIS_HEALTH_ENABLED:false}")
                .contains("server.shutdown=graceful");

        for (String scriptName : new String[]{"bootstrap-ec2.sh", "deploy.sh", "health-check.sh"}) {
            String script = Files.readString(AWS_ROOT.resolve("scripts").resolve(scriptName));
            assertThat(script).startsWith("#!/usr/bin/env bash\nset -Eeuo pipefail");
        }
    }

    @Test
    void runbookDocumentsNetworkSecretsBackupsAndManualDeployment() throws IOException {
        String runbook = Files.readString(AWS_ROOT.resolve("README.md"));

        assertThat(runbook)
                .contains("Public access **No**")
                .contains("PostgreSQL TCP 5432, source")
                .contains("application port 8080 are not published by Compose")
                .contains("chmod 600 .env.production")
                .contains("sslmode=verify-full")
                .contains("7-day automated-backup retention period")
                .contains("No script in this directory creates, modifies, or deletes AWS resources");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
