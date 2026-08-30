package com.claim.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_kafka_events")
public class ProcessedKafkaEvent {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedKafkaEvent() {
    }

    public ProcessedKafkaEvent(String eventId, Long claimId, Instant processedAt) {
        this.eventId = eventId;
        this.claimId = claimId;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getClaimId() {
        return claimId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
