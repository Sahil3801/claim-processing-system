package com.claim.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyClaimReportDTO(
        LocalDate reportDate,
        long totalClaims,
        BigDecimal totalClaimAmount,
        BigDecimal averageClaimAmount) {
}
