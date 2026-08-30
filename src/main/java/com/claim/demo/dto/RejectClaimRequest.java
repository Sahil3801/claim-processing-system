package com.claim.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectClaimRequest(
        @NotBlank(message = "reason is required when rejecting a claim")
        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason) {
}
