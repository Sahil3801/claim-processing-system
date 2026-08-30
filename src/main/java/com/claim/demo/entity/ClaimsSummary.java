package com.claim.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "claims_summaries")
public class ClaimsSummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "report_generated", nullable = false)
    private Date reportGenerated;

    @Column(name = "number_of_claims", nullable = false)
    private Integer numberOfClaims;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    // Getters
    public Long getId() {
        return id;
    }

    public Date getReportGenerated() {
        return reportGenerated;
    }

    public Integer getNumberOfClaims() {
        return numberOfClaims;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    // Setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setReportGenerated(Date reportGenerated) {
        this.reportGenerated = reportGenerated;
    }

    public void setNumberOfClaims(Integer numberOfClaims) {
        this.numberOfClaims = numberOfClaims;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
