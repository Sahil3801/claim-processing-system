package com.claim.demo.dto;

import com.claim.demo.domain.ClaimStatus;
import java.math.BigDecimal;
import java.util.Date;

public class ClaimDTO {
    private Long claimId;
    private Long userId;
    private String emailId;
    private Date claimDate;
    private BigDecimal claimAmount;
    private String claimType;
    private String description;
    private ClaimStatus claimStatus;
    private Date lastUpdated;

    // Constructors, getters, and setters

    public ClaimDTO() {}

    public ClaimDTO(Long claimId, Long userId, String emailId, Date claimDate, BigDecimal claimAmount,
                    String claimType, String description, ClaimStatus claimStatus, Date lastUpdated) {
        this.claimId = claimId;
        this.userId = userId;
        this.emailId = emailId;
        this.claimDate = claimDate;
        this.claimAmount = claimAmount;
        this.claimType = claimType;
        this.description = description;
        this.claimStatus = claimStatus;
        this.lastUpdated = lastUpdated;
    }

    // Getters and setters omitted for brevity
    
    // Getters
    public Long getClaimId() {
        return claimId;
    }

    public Long getUserId() {
        return userId;
    }

    public Date getClaimDate() {
        return claimDate;
    }

    public BigDecimal getClaimAmount() {
        return claimAmount;
    }

    public String getClaimType() {
        return claimType;
    }

    public String getDescription() {
        return description;
    }

    public ClaimStatus getClaimStatus() {
        return claimStatus;
    }
    public String getEmailId() {
        return emailId;
    }
    public Date getLastUpdated() {
        return lastUpdated;
    }

    // Setters
    public void setClaimId(Long claimId) {
        this.claimId = claimId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setEmailId(String emailId) {
        this.emailId = emailId;
    }

    public void setClaimDate(Date claimDate) {
        this.claimDate = claimDate;
    }

    public void setClaimAmount(BigDecimal claimAmount) {
        this.claimAmount = claimAmount;
    }

    public void setClaimType(String claimType) {
        this.claimType = claimType;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setClaimStatus(ClaimStatus claimStatus) {
        this.claimStatus = claimStatus;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
