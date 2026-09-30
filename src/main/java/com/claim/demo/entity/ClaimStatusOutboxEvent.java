package com.claim.demo.entity;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.event.ClaimStatusEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A claim-status event waiting in the transactional outbox. It is inserted in the same
 * database transaction as the claim change and marked published once Kafka acknowledges it.
 */
@Entity
@Table(name = "claim_status_outbox")
public class ClaimStatusOutboxEvent {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36, unique = true, updatable = false)
    private String eventId;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private Long claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 32, updatable = false)
    private ClaimStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 32, updatable = false)
    private ClaimStatus newStatus;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "user_email", length = 255, updatable = false)
    private String userEmail;

    @Column(name = "changed_by", nullable = false, length = 100, updatable = false)
    private String changedBy;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    protected ClaimStatusOutboxEvent() {
    }

    public static ClaimStatusOutboxEvent from(ClaimStatusEvent event) {
        ClaimStatusOutboxEvent outboxEvent = new ClaimStatusOutboxEvent();
        outboxEvent.eventId = event.eventId();
        outboxEvent.claimId = event.claimId();
        outboxEvent.previousStatus = event.previousStatus();
        outboxEvent.newStatus = event.newStatus();
        outboxEvent.userId = event.userId();
        outboxEvent.userEmail = event.userEmail();
        outboxEvent.changedBy = event.changedBy();
        outboxEvent.occurredAt = event.occurredAt();
        return outboxEvent;
    }

    public ClaimStatusEvent toEvent() {
        return new ClaimStatusEvent(
                eventId, claimId, previousStatus, newStatus, userId, userEmail, changedBy, occurredAt);
    }

    public void markPublished(Instant publishedAt) {
        this.attempts++;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attempts++;
        this.lastError = error == null || error.length() <= MAX_ERROR_LENGTH
                ? error
                : error.substring(0, MAX_ERROR_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getClaimId() {
        return claimId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
