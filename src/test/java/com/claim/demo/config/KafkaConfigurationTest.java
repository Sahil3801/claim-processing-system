package com.claim.demo.config;

import com.claim.demo.event.ClaimStatusEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class KafkaConfigurationTest {

    @Test
    void createsConsistentPrimaryAndDeadLetterTopics() {
        KafkaProducerConfig config = new KafkaProducerConfig();

        NewTopic primary = config.claimStatusTopic("claims.status.v1", 1, 1);
        NewTopic deadLetter = config.claimStatusDeadLetterTopic("claims.status.v1.dlt", 1, 1);

        assertEquals("claims.status.v1", primary.name());
        assertEquals("claims.status.v1.dlt", deadLetter.name());
        assertEquals(1, primary.numPartitions());
        assertEquals(Short.valueOf((short) 1), primary.replicationFactor());
    }

    @Test
    void configuresTypedConsumerRetryAndDeadLetterHandler() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of("localhost:9092"));
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ConsumerFactory<String, ClaimStatusEvent> consumerFactory =
                config.claimStatusConsumerFactory(properties);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        DefaultErrorHandler errorHandler = config.claimStatusErrorHandler(
                template, "claims.status.v1.dlt", 1000L, 2L);

        ConcurrentKafkaListenerContainerFactory<String, ClaimStatusEvent> containerFactory =
                config.claimStatusKafkaListenerContainerFactory(consumerFactory, errorHandler);

        assertNotNull(consumerFactory);
        assertNotNull(errorHandler);
        assertNotNull(containerFactory);
    }
}
