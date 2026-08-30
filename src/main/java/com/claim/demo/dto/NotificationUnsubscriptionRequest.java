package com.claim.demo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record NotificationUnsubscriptionRequest(
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be positive")
        Long userId) {
}
