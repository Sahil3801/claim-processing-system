package com.claim.demo.entity;

import com.claim.demo.domain.ClaimStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "claim_reports")
public class ClaimReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private ClaimStatus claimStatus;

    @Column(name = "total_claims", nullable = false)
    private Long totalClaims;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalClaimAmount;

    @Column(name = "report_date", nullable = false)
    private Date reportDate;

    // Getters
    public Long getId() {
        return id;
    }

    public ClaimStatus getClaimStatus() {
        return claimStatus;
    }

    public Long getTotalClaims() {
        return totalClaims;
    }

    public BigDecimal getTotalClaimAmount() {
        return totalClaimAmount;
    }

    public Date getReportDate() {
        return reportDate;
    }

    // Setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setClaimStatus(ClaimStatus claimStatus) {
        this.claimStatus = claimStatus;
    }

    public void setTotalClaims(Long totalClaims) {
        this.totalClaims = totalClaims;
    }

    public void setTotalClaimAmount(BigDecimal totalClaimAmount) {
        this.totalClaimAmount = totalClaimAmount;
    }

    public void setReportDate(Date reportDate) {
        this.reportDate = reportDate;
    }
}
