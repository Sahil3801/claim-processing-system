package com.claim.demo.dto;

import com.claim.demo.domain.ClaimStatus;
import java.math.BigDecimal;

public class ClaimReportDTO {
    private ClaimStatus claimStatus;
    private Long totalClaims;
    private BigDecimal totalClaimAmount;
    private BigDecimal averageClaimAmount;

    // Constructors
    public ClaimReportDTO() {}

    public ClaimReportDTO(ClaimStatus claimStatus, Long totalClaims, BigDecimal totalClaimAmount) {
        this(claimStatus, totalClaims, totalClaimAmount,
                ClaimOutcomeSummaryDTO.average(totalClaimAmount, totalClaims));
    }

    public ClaimReportDTO(ClaimStatus claimStatus, Long totalClaims, BigDecimal totalClaimAmount,
                          BigDecimal averageClaimAmount) {
        this.claimStatus = claimStatus;
        this.totalClaims = totalClaims;
        this.totalClaimAmount = totalClaimAmount;
        this.averageClaimAmount = averageClaimAmount;
    }

    // Getters
    public ClaimStatus getClaimStatus() {
        return claimStatus;
    }

    public Long getTotalClaims() {
        return totalClaims;
    }

    public BigDecimal getTotalClaimAmount() {
        return totalClaimAmount;
    }

    public BigDecimal getAverageClaimAmount() {
        return averageClaimAmount;
    }

    // Setters
    public void setClaimStatus(ClaimStatus claimStatus) {
        this.claimStatus = claimStatus;
    }

    public void setTotalClaims(Long totalClaims) {
        this.totalClaims = totalClaims;
    }

    public void setTotalClaimAmount(BigDecimal totalClaimAmount) {
        this.totalClaimAmount = totalClaimAmount;
    }

    public void setAverageClaimAmount(BigDecimal averageClaimAmount) {
        this.averageClaimAmount = averageClaimAmount;
    }
}
