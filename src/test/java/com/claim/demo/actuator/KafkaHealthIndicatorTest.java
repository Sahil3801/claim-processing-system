package com.claim.demo.actuator;

import com.claim.demo.config.KafkaHealthIndicator;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.core.KafkaAdmin;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class KafkaHealthIndicatorTest {

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.2");

    @Test
    void reportsUpWithClusterDetailsWhenTheBrokerAnswers() throws Exception {
        KafkaHealthIndicator indicator = indicatorFor(KAFKA.getBootstrapServers());
        try {
            Health health = indicator.health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsKey("clusterId").containsEntry("nodes", 1);
        } finally {
            indicator.destroy();
        }
    }

    @Test
    void reportsDownQuicklyWhenTheBrokerIsUnreachable() throws Exception {
        KafkaHealthIndicator indicator = indicatorFor("localhost:1");
        try {
            long started = System.nanoTime();
            Health health = indicator.health();
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
        } finally {
            indicator.destroy();
        }
    }

    private static KafkaHealthIndicator indicatorFor(String bootstrapServers) {
        return new KafkaHealthIndicator(new KafkaAdmin(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)));
    }
}
