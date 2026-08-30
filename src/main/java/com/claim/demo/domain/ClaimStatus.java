package com.claim.demo.domain;

import com.claim.demo.exception.InvalidClaimStatusException;

import java.util.Locale;

public enum ClaimStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    SETTLED;

    public boolean canTransitionTo(ClaimStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }

        return switch (this) {
            case DRAFT -> nextStatus == SUBMITTED;
            case SUBMITTED -> nextStatus == UNDER_REVIEW;
            case UNDER_REVIEW -> nextStatus == APPROVED || nextStatus == REJECTED;
            case APPROVED -> nextStatus == SETTLED;
            case REJECTED, SETTLED -> false;
        };
    }

    public static ClaimStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidClaimStatusException(value);
        }

        try {
            return valueOf(value.trim()
                    .toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_'));
        } catch (IllegalArgumentException exception) {
            throw new InvalidClaimStatusException(value);
        }
    }
}
