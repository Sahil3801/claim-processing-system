package com.claim.demo.dto;

import jakarta.validation.constraints.Size;

public record ClaimActionRequest(
        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason) {
}
