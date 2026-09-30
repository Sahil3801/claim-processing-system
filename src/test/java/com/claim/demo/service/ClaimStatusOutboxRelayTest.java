package com.claim.demo.service;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.entity.ClaimStatusOutboxEvent;
import com.claim.demo.event.ClaimStatusEvent;
import com.claim.demo.repository.ClaimStatusOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimStatusOutboxRelayTest {

    @Mock
    private ClaimStatusOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ClaimStatusOutboxRelay relay;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        relay = new ClaimStatusOutboxRelay(outboxRepository, kafkaTemplate, transactionTemplate,
                "claims.status.v1", 100, Duration.ofSeconds(1), Duration.ofDays(7));
    }

    @Test
    void publishesOldestFirstKeyedByClaimAndMarksEachPublished() {
        ClaimStatusOutboxEvent first = outboxEvent("event-1", 51L);
        ClaimStatusOutboxEvent second = outboxEvent("event-2", 52L);
        when(outboxRepository.lockUnpublishedBatch(100)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(eq("claims.status.v1"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        int published = relay.publishPendingBatch();

        assertEquals(2, published);
        InOrder order = inOrder(kafkaTemplate);
        order.verify(kafkaTemplate).send("claims.status.v1", "51", first.toEvent());
        order.verify(kafkaTemplate).send("claims.status.v1", "52", second.toEvent());
        assertNotNull(first.getPublishedAt());
        assertNotNull(second.getPublishedAt());
        assertEquals(1, first.getAttempts());
    }

    @Test
    void stopsAtFirstFailureSoLaterEventsAreNotDeliveredOutOfOrder() {
        ClaimStatusOutboxEvent failing = outboxEvent("event-3", 53L);
        ClaimStatusOutboxEvent later = outboxEvent("event-4", 53L);
        when(outboxRepository.lockUnpublishedBatch(100)).thenReturn(List.of(failing, later));
        when(kafkaTemplate.send("claims.status.v1", "53", failing.toEvent()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker unavailable")));

        int published = relay.publishPendingBatch();

        assertEquals(0, published);
        assertNull(failing.getPublishedAt());
        assertEquals(1, failing.getAttempts());
        assertEquals("broker unavailable", failing.getLastError());
        verify(kafkaTemplate, never()).send("claims.status.v1", "53", later.toEvent());
        assertEquals(0, later.getAttempts());
    }

    @Test
    void synchronousSendErrorIsRecordedForRetry() {
        ClaimStatusOutboxEvent event = outboxEvent("event-5", 54L);
        when(outboxRepository.lockUnpublishedBatch(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("claims.status.v1"), any(), any()))
                .thenThrow(new KafkaException("metadata timeout"));

        assertEquals(0, relay.publishPendingBatch());
        assertNull(event.getPublishedAt());
        assertEquals("metadata timeout", event.getLastError());
    }

    private ClaimStatusOutboxEvent outboxEvent(String eventId, Long claimId) {
        return ClaimStatusOutboxEvent.from(new ClaimStatusEvent(
                eventId, claimId, ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED,
                7L, "claimant@example.com", "officer", Instant.parse("2026-01-01T00:00:00Z")));
    }
}
