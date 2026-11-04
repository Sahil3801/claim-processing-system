package com.claim.demo.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reports whether the Kafka broker answers a cluster description within a short timeout.
 * Registered only when the outbox relay publishes to Kafka, so deployments without a broker
 * (the free demo) do not report a dependency they never use.
 */
@Component("kafkaHealthIndicator")
@ConditionalOnProperty(name = "claims.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaHealthIndicator extends AbstractHealthIndicator implements DisposableBean {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final AdminClient adminClient;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        super("Kafka health check failed");
        Map<String, Object> config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        int timeoutMs = (int) TIMEOUT.toMillis();
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeoutMs);
        this.adminClient = AdminClient.create(config);
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        DescribeClusterResult cluster = adminClient.describeCluster(
                new DescribeClusterOptions().timeoutMs((int) TIMEOUT.toMillis()));
        String clusterId = cluster.clusterId().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        int nodes = cluster.nodes().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).size();
        builder.up().withDetail("clusterId", clusterId).withDetail("nodes", nodes);
    }

    @Override
    public void destroy() {
        adminClient.close(Duration.ofSeconds(1));
    }
}
