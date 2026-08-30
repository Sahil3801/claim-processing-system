package com.claim.demo.config;

import com.claim.demo.event.ClaimStatusEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, ClaimStatusEvent> claimStatusConsumerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(
                kafkaProperties.buildConsumerProperties(null));
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        JsonDeserializer<ClaimStatusEvent> valueDeserializer =
                new JsonDeserializer<>(ClaimStatusEvent.class, false);
        valueDeserializer.addTrustedPackages("com.claim.demo.event");

        return new DefaultKafkaConsumerFactory<>(
                properties,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public DefaultErrorHandler claimStatusErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${claims.kafka.topics.claim-status-dlt:claims.status.v1.dlt}") String dltTopic,
            @Value("${claims.kafka.retry.interval-ms:1000}") long retryInterval,
            @Value("${claims.kafka.retry.max-retries:2}") long maxRetries) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(dltTopic, record.partition()));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(retryInterval, maxRetries));
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ClaimStatusEvent>
            claimStatusKafkaListenerContainerFactory(
                    ConsumerFactory<String, ClaimStatusEvent> claimStatusConsumerFactory,
                    DefaultErrorHandler claimStatusErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ClaimStatusEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(claimStatusConsumerFactory);
        factory.setCommonErrorHandler(claimStatusErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
