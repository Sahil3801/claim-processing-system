package com.claim.demo.service;

import com.claim.demo.entity.ClaimStatusOutboxEvent;
import com.claim.demo.repository.ClaimStatusOutboxRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Relays outbox rows to Kafka, oldest first, and marks each one published after the broker
 * acknowledges it. Delivery is at-least-once: a crash after the send but before the commit
 * re-sends the event, which the consumer skips using its processed-event marker.
 */
@Service
public class ClaimStatusOutboxRelay {

    private static final Logger logger = LogManager.getLogger(ClaimStatusOutboxRelay.class);

    private final ClaimStatusOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String topic;
    private final int batchSize;
    private final Duration sendTimeout;
    private final Duration retention;

    public ClaimStatusOutboxRelay(
            ClaimStatusOutboxRepository outboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            TransactionTemplate transactionTemplate,
            @Value("${claims.kafka.topics.claim-status:claims.status.v1}") String topic,
            @Value("${claims.outbox.relay.batch-size:100}") int batchSize,
            @Value("${claims.outbox.relay.send-timeout:PT10S}") Duration sendTimeout,
            @Value("${claims.outbox.retention:P7D}") Duration retention) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
        this.retention = retention;
    }

    /**
     * Publishes one batch and returns how many events were delivered. Stops at the first
     * failure so later events for the same claim are never delivered ahead of earlier ones.
     */
    public int publishPendingBatch() {
        Integer published = transactionTemplate.execute(status -> {
            List<ClaimStatusOutboxEvent> batch = outboxRepository.lockUnpublishedBatch(batchSize);
            int delivered = 0;
            for (ClaimStatusOutboxEvent outboxEvent : batch) {
                try {
                    send(outboxEvent);
                    outboxEvent.markPublished(Instant.now());
                    delivered++;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    outboxEvent.recordFailure("Interrupted while publishing");
                    break;
                } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                    String message = exception.getCause() != null
                            ? exception.getCause().getMessage()
                            : exception.getMessage();
                    outboxEvent.recordFailure(message);
                    logger.warn("Kafka publication failed for outbox event {} (attempt {}): {}",
                            outboxEvent.getEventId(), outboxEvent.getAttempts(), message);
                    break;
                }
            }
            return delivered;
        });
        return published == null ? 0 : published;
    }

    public int deleteExpiredPublishedEvents() {
        Instant cutoff = Instant.now().minus(retention);
        Integer deleted = transactionTemplate.execute(
                status -> outboxRepository.deletePublishedBefore(cutoff));
        return deleted == null ? 0 : deleted;
    }

    private void send(ClaimStatusOutboxEvent outboxEvent)
            throws InterruptedException, ExecutionException, TimeoutException {
        kafkaTemplate.send(topic, outboxEvent.getClaimId().toString(), outboxEvent.toEvent())
                .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        logger.debug("Published claim-status event {} for claim {}",
                outboxEvent.getEventId(), outboxEvent.getClaimId());
    }
}
