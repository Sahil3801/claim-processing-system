package com.claim.demo.entity;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.exception.InvalidClaimTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Date;


@Entity
@Table(name = "claims")
public class Claim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "claim_id")
    private Long claimId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
    private User user;

    @Column(name = "claim_date", nullable = false)
    private Date claimDate;

    @Column(name = "claim_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal claimAmount;
    
    @Column(name = "email_id", length = 255)
    private String emailId;

    @Column(name = "claim_type", nullable = false, length = 100)
    private String claimType;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_status", nullable = false, length = 32)
    private ClaimStatus claimStatus = ClaimStatus.DRAFT;

    @Column(name = "last_updated", nullable = false)
    private Date lastUpdated;

    @Column(name = "idempotency_key", length = 128, unique = true, updatable = false)
    private String idempotencyKey;

    @Column(name = "submission_idempotency_key", length = 128, unique = true)
    private String submissionIdempotencyKey;

    // Getters and setters omitted for brevity
    
    // Getters and setters
    
    public String getEmailId() {
        return emailId;
    }

    public void setEmailId(String emailId) {
        this.emailId = emailId;
    }
 
    public Long getClaimId() {
        return claimId;
    }

    public void setClaimId(Long claimId) {
        this.claimId = claimId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Date getClaimDate() {
        return claimDate;
    }

    public void setClaimDate(Date claimDate) {
        this.claimDate = claimDate;
    }

    public BigDecimal getClaimAmount() {
        return claimAmount;
    }

    public void setClaimAmount(BigDecimal claimAmount) {
        this.claimAmount = claimAmount;
    }

    public String getClaimType() {
        return claimType;
    }

    public void setClaimType(String claimType) {
        this.claimType = claimType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ClaimStatus getClaimStatus() {
        return claimStatus;
    }

    public ClaimStatus transitionTo(ClaimStatus newStatus) {
        ClaimStatus previousStatus = claimStatus;
        if (previousStatus == null || !previousStatus.canTransitionTo(newStatus)) {
            throw new InvalidClaimTransitionException(claimId, previousStatus, newStatus);
        }
        claimStatus = newStatus;
        return previousStatus;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSubmissionIdempotencyKey() {
        return submissionIdempotencyKey;
    }

    public void setSubmissionIdempotencyKey(String submissionIdempotencyKey) {
        this.submissionIdempotencyKey = submissionIdempotencyKey;
    }
}
