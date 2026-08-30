package com.claim.demo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaProducerConfig {

    @Bean
    @ConditionalOnProperty(
            name = "claims.kafka.create-topics", havingValue = "true", matchIfMissing = true)
    public NewTopic claimStatusTopic(
            @Value("${claims.kafka.topics.claim-status:claims.status.v1}") String topic,
            @Value("${claims.kafka.topic-partitions:1}") int partitions,
            @Value("${claims.kafka.topic-replicas:1}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    @ConditionalOnProperty(
            name = "claims.kafka.create-topics", havingValue = "true", matchIfMissing = true)
    public NewTopic claimStatusDeadLetterTopic(
            @Value("${claims.kafka.topics.claim-status-dlt:claims.status.v1.dlt}") String topic,
            @Value("${claims.kafka.topic-partitions:1}") int partitions,
            @Value("${claims.kafka.topic-replicas:1}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }
}
