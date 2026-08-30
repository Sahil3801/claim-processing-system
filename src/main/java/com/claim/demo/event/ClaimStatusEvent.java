package com.claim.demo.event;

import com.claim.demo.domain.ClaimStatus;

import java.time.Instant;
import java.util.UUID;

public record ClaimStatusEvent(
        String eventId,
        Long claimId,
        ClaimStatus previousStatus,
        ClaimStatus newStatus,
        Long userId,
        String userEmail,
        String changedBy,
        Instant occurredAt) {

    public static ClaimStatusEvent create(
            Long claimId,
            ClaimStatus previousStatus,
            ClaimStatus newStatus,
            Long userId,
            String userEmail,
            String changedBy,
            Instant occurredAt) {
        return new ClaimStatusEvent(
                UUID.randomUUID().toString(), claimId, previousStatus, newStatus,
                userId, userEmail, changedBy, occurredAt);
    }
}
