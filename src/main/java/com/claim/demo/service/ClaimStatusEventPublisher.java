package com.claim.demo.service;

import com.claim.demo.event.ClaimStatusEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Schedules Kafka publication after the surrounding database transaction commits.
 * Final broker failures are logged and never change an already committed claim operation.
 */
@Service
public class ClaimStatusEventPublisher {

    private static final Logger logger = LogManager.getLogger(ClaimStatusEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public ClaimStatusEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${claims.kafka.topics.claim-status:claims.status.v1}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publishAfterCommit(ClaimStatusEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(event);
                }
            });
            return;
        }
        publish(event);
    }

    void publish(ClaimStatusEvent event) {
        try {
            kafkaTemplate.send(topic, event.claimId().toString(), event)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            logger.error("Kafka delivery failed for claim-status event {}: {}",
                                    event.eventId(), exception.getMessage());
                        } else {
                            logger.debug("Published claim-status event {} for claim {}",
                                    event.eventId(), event.claimId());
                        }
                    });
        } catch (RuntimeException exception) {
            logger.error("Kafka publication failed for claim-status event {}: {}",
                    event.eventId(), exception.getMessage());
        }
    }
}
