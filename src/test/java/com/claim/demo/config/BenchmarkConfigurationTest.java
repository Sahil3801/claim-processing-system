package com.claim.demo.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkConfigurationTest {

    private static final Path ROOT = Path.of("benchmarks");

    @Test
    void composeDefinesAnIsolatedResourceBoundBenchmarkStack() throws IOException {
        Map<String, Object> compose = new Yaml().load(
                Files.readString(ROOT.resolve("docker-compose.benchmark.yml")));
        Map<String, Object> services = map(compose.get("services"));

        assertThat(compose.get("name")).isEqualTo("claims-benchmark");
        assertThat(services.keySet()).containsExactlyInAnyOrder(
                "postgres", "redis", "kafka", "app", "k6");

        Map<String, Object> postgres = map(services.get("postgres"));
        assertThat(map(postgres.get("environment")))
                .containsEntry("POSTGRES_DB", "claims_benchmark");
        assertThat(postgres).containsKeys("tmpfs", "healthcheck", "mem_limit", "cpus");

        Map<String, Object> appEnvironment = map(map(services.get("app")).get("environment"));
        assertThat(appEnvironment)
                .containsEntry("SPRING_PROFILES_ACTIVE", "benchmark")
                .containsKey("CLAIMS_CACHE_ENABLED")
                .containsKey("BENCHMARK_STATIC_USERS")
                .containsEntry("SPRING_KAFKA_LISTENER_AUTO_STARTUP", "false")
                .containsEntry("DB_NAME", "claims_benchmark");

        Map<String, Object> k6 = map(services.get("k6"));
        assertThat(k6.get("image")).isEqualTo("grafana/k6:2.2.0");
        assertThat(k6.get("profiles").toString()).contains("tools");
        assertThat(k6).containsKeys("mem_limit", "cpus", "pids_limit");
    }

    @Test
    void k6ScenariosCaptureRequiredLatencyThroughputAndErrors() throws IOException {
        String claimRead = Files.readString(ROOT.resolve("k6/claim-read.js"));
        String apiMix = Files.readString(ROOT.resolve("k6/api-mix.js"));
        String summary = Files.readString(ROOT.resolve("k6/summary.js"));

        assertThat(claimRead)
                .contains("GET /api/claims/:id")
                .contains("claim_detail_latency")
                .contains("claim_detail_errors")
                .contains("claim_detail_requests")
                .contains("responseType: \"text\"")
                .contains("p(50)", "p(95)", "p(99)");
        assertThat(apiMix)
                .contains("/api/claims/my")
                .contains("/api/reports/summary")
                .contains("POST /api/claims/:id/submit")
                .contains("api_latency", "api_errors", "api_requests");
        assertThat(summary)
                .contains("requests/second")
                .contains("errorRate");
    }

    @Test
    void runnerPreservesRawResultsAndGuardsIndexExperiment() throws IOException {
        String runner = Files.readString(ROOT.resolve("scripts/run-benchmarks.sh"));
        String indexRunner = Files.readString(ROOT.resolve("scripts/query-index-comparison.sh"));
        String data = Files.readString(ROOT.resolve("db/generate-synthetic-data.sql"));
        String readme = Files.readString(ROOT.resolve("README.md"));
        String tenThousandProfile = Files.readString(
                ROOT.resolve("profiles/10k-validation.env"));

        assertThat(runner)
                .contains("-raw.json.gz")
                .contains("BENCHMARK_REPETITIONS:-3")
                .contains("CLAIMS_CACHE_ENABLED")
                .contains("BENCHMARK_STATIC_USERS")
                .contains("CACHE_BENCHMARK_VUS")
                .contains("capture_app_cpu")
                .contains("pg_stat_reset()")
                .contains("summarize-results.sh");
        assertThat(indexRunner)
                .contains("ALLOW_BENCHMARK_INDEX_DDL")
                .contains("claims_benchmark")
                .contains("without-index-explain.json")
                .contains("with-index-explain.json")
                .contains("pgbench");
        assertThat(data)
                .contains("generate_series")
                .contains("claim_status_history")
                .contains("ANALYZE claims");
        assertThat(readme)
                .contains("Measured results are documented")
                .contains("analysis/20260831T045941Z.md")
                .contains("local synthetic tests, not deployed AWS measurements")
                .contains("Redis hit/miss/memory/command counters")
                .contains("run-10k-validation.sh")
                .contains("EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, FORMAT JSON)");
        assertThat(tenThousandProfile)
                .contains("CLAIM_COUNT=10000")
                .contains("CACHE_BENCHMARK_VUS=2")
                .contains("BENCHMARK_REPETITIONS=3");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
