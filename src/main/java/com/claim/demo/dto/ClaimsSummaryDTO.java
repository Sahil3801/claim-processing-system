package com.claim.demo.dto;

import java.math.BigDecimal;

public record ClaimsSummaryDTO(
        long totalClaims,
        BigDecimal totalClaimAmount,
        BigDecimal averageClaimAmount,
        ClaimOutcomeSummaryDTO pending,
        ClaimOutcomeSummaryDTO approved,
        ClaimOutcomeSummaryDTO rejected,
        ClaimOutcomeSummaryDTO settled) {

    public ClaimsSummaryDTO(long totalClaims, BigDecimal totalClaimAmount) {
        this(totalClaims, totalClaimAmount, average(totalClaimAmount, totalClaims),
                ClaimOutcomeSummaryDTO.empty(), ClaimOutcomeSummaryDTO.empty(),
                ClaimOutcomeSummaryDTO.empty(), ClaimOutcomeSummaryDTO.empty());
    }

    private static BigDecimal average(BigDecimal amount, long count) {
        return ClaimOutcomeSummaryDTO.average(amount, count);
    }
}
