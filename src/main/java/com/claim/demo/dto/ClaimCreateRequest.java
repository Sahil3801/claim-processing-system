package com.claim.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ClaimCreateRequest(
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be positive")
        Long userId,

        @NotNull(message = "claimAmount is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "claimAmount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "claimAmount must have at most 17 integer digits and 2 decimal places")
        BigDecimal claimAmount,

        @NotBlank(message = "claimType is required")
        @Size(max = 100, message = "claimType must not exceed 100 characters")
        String claimType,

        @NotBlank(message = "description is required")
        @Size(max = 2000, message = "description must not exceed 2000 characters")
        String description,

        @Email(message = "emailId must be a valid email address")
        @Size(max = 255, message = "emailId must not exceed 255 characters")
        String emailId) {
}
