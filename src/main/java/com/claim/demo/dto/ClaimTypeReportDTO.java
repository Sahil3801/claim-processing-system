package com.claim.demo.dto;

import java.math.BigDecimal;

public record ClaimTypeReportDTO(
        String claimType,
        long totalClaims,
        BigDecimal totalClaimAmount,
        BigDecimal averageClaimAmount) {

    public ClaimTypeReportDTO(String claimType, long totalClaims, BigDecimal totalClaimAmount) {
        this(claimType, totalClaims, totalClaimAmount,
                ClaimOutcomeSummaryDTO.average(totalClaimAmount, totalClaims));
    }
}
