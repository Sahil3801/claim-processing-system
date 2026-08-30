package com.claim.demo.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ClaimOutcomeSummaryDTO(
        long totalClaims,
        BigDecimal totalClaimAmount,
        BigDecimal averageClaimAmount) {

    public static ClaimOutcomeSummaryDTO of(long count, BigDecimal amount) {
        BigDecimal normalizedAmount = amount == null ? BigDecimal.ZERO : amount;
        return new ClaimOutcomeSummaryDTO(count, normalizedAmount, average(normalizedAmount, count));
    }

    public static ClaimOutcomeSummaryDTO empty() {
        return of(0, BigDecimal.ZERO);
    }

    static BigDecimal average(BigDecimal amount, long count) {
        if (amount == null || count == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
