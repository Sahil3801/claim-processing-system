package com.claim.demo.service;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.event.ClaimStatusEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimStatusEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ClaimStatusEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ClaimStatusEventPublisher(kafkaTemplate, "claims.status.v1");
    }

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishesWithClaimKeyOnlyAfterCommit() {
        ClaimStatusEvent event = event("event-1", 51L);
        when(kafkaTemplate.send("claims.status.v1", "51", event))
                .thenReturn(CompletableFuture.completedFuture(null));
        beginTransactionSynchronization();

        publisher.publishAfterCommit(event);

        verify(kafkaTemplate, never()).send("claims.status.v1", "51", event);
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCommit();
        verify(kafkaTemplate).send("claims.status.v1", "51", event);
    }

    @Test
    void doesNotPublishWhenDatabaseTransactionRollsBack() {
        ClaimStatusEvent event = event("event-2", 52L);
        beginTransactionSynchronization();

        publisher.publishAfterCommit(event);
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(kafkaTemplate, never()).send("claims.status.v1", "52", event);
    }

    @Test
    void brokerFailureCannotFailCommittedClaimOperation() {
        ClaimStatusEvent event = event("event-3", 53L);
        when(kafkaTemplate.send("claims.status.v1", "53", event))
                .thenThrow(new KafkaException("broker unavailable"));

        assertDoesNotThrow(() -> publisher.publishAfterCommit(event));
    }

    @Test
    void asynchronousDeliveryFailureCannotEscapeToClaimOperation() {
        ClaimStatusEvent event = event("event-4", 54L);
        when(kafkaTemplate.send("claims.status.v1", "54", event))
                .thenReturn(CompletableFuture.failedFuture(
                        new KafkaException("delivery retries exhausted")));

        assertDoesNotThrow(() -> publisher.publishAfterCommit(event));
        verify(kafkaTemplate).send("claims.status.v1", "54", event);
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private ClaimStatusEvent event(String eventId, Long claimId) {
        return new ClaimStatusEvent(
                eventId, claimId, ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED,
                7L, "owner@example.com", "officer", Instant.parse("2026-08-30T10:00:00Z"));
    }
}
