package com.claim.demo.service;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.entity.ProcessedKafkaEvent;
import com.claim.demo.event.ClaimStatusEvent;
import com.claim.demo.repository.ProcessedKafkaEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimStatusEventConsumerTest {

    @Mock
    private ProcessedKafkaEventRepository processedEventRepository;

    @Mock
    private EmailService emailService;

    @Test
    void processesEventAndRecordsIdempotencyMarker() {
        ClaimStatusEvent event = event("event-61", 61L);
        ClaimStatusEventConsumer consumer = consumer();

        consumer.consume(event);

        verify(emailService).sendEmail(
                "owner@example.com",
                "Claim Status Update",
                "Your claim #61 has been updated from UNDER_REVIEW to APPROVED");
        ArgumentCaptor<ProcessedKafkaEvent> marker =
                ArgumentCaptor.forClass(ProcessedKafkaEvent.class);
        verify(processedEventRepository).saveAndFlush(marker.capture());
        assertEquals("event-61", marker.getValue().getEventId());
        assertEquals(61L, marker.getValue().getClaimId());
    }

    @Test
    void duplicateEventDoesNotRepeatNotificationSideEffect() {
        ClaimStatusEvent event = event("event-62", 62L);
        when(processedEventRepository.existsById("event-62")).thenReturn(true);

        consumer().consume(event);

        verify(emailService, never()).sendEmail(any(), any(), any());
        verify(processedEventRepository, never()).saveAndFlush(any(ProcessedKafkaEvent.class));
    }

    @Test
    void notificationFailurePropagatesSoTransactionRollsBackMarkerForRetry() {
        ClaimStatusEvent event = event("event-63", 63L);
        org.mockito.Mockito.doThrow(new IllegalStateException("mail unavailable"))
                .when(emailService).sendEmail(any(), any(), any());

        assertThrows(IllegalStateException.class, () -> consumer().consume(event));

        verify(processedEventRepository).saveAndFlush(any(ProcessedKafkaEvent.class));
    }

    @Test
    void recordsEventWithoutNotificationWhenNoRecipientExists() {
        ClaimStatusEvent event = new ClaimStatusEvent(
                "event-64", 64L, ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED,
                7L, " ", "officer", Instant.parse("2026-08-30T10:00:00Z"));

        consumer().consume(event);

        verify(processedEventRepository).saveAndFlush(any(ProcessedKafkaEvent.class));
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    private ClaimStatusEventConsumer consumer() {
        return new ClaimStatusEventConsumer(processedEventRepository, emailService);
    }

    private ClaimStatusEvent event(String eventId, Long claimId) {
        return new ClaimStatusEvent(
                eventId, claimId, ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED,
                7L, "owner@example.com", "officer", Instant.parse("2026-08-30T10:00:00Z"));
    }
}
