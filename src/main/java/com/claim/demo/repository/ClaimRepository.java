package com.claim.demo.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.entity.Claim;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {
    List<Claim> findByUser_UserId(Long userId);

    List<Claim> findByClaimStatusAndLastUpdatedBefore(ClaimStatus status, Date lastUpdated);

    Page<Claim> findByUser_Username(String username, Pageable pageable);

    Optional<Claim> findByIdempotencyKey(String idempotencyKey);

    Optional<Claim> findBySubmissionIdempotencyKey(String submissionIdempotencyKey);

    @Query("select c.claimStatus from Claim c where c.claimId = :claimId")
    Optional<ClaimStatus> findStatusByClaimId(@Param("claimId") Long claimId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.claimId = :claimId")
    Optional<Claim> findByIdForUpdate(@Param("claimId") Long claimId);

    @Query(value = """
            SELECT claim_status AS "claimStatus",
                   COUNT(*) AS "totalClaims",
                   COALESCE(SUM(claim_amount), 0) AS "totalAmount",
                   COALESCE(AVG(claim_amount), 0) AS "averageAmount"
            FROM claims
            GROUP BY claim_status
            ORDER BY claim_status
            """, nativeQuery = true)
    List<StatusAggregate> summarizeByStatus();

    @Query(value = """
            SELECT claim_type AS "claimType",
                   COUNT(*) AS "totalClaims",
                   COALESCE(SUM(claim_amount), 0) AS "totalAmount",
                   COALESCE(AVG(claim_amount), 0) AS "averageAmount"
            FROM claims
            GROUP BY claim_type
            ORDER BY claim_type
            """, nativeQuery = true)
    List<ClaimTypeAggregate> summarizeByClaimType();

    @Query(value = """
            SELECT COUNT(*) AS "totalClaims",
                   COALESCE(SUM(claim_amount), 0) AS "totalAmount",
                   COALESCE(AVG(claim_amount), 0) AS "averageAmount",
                   COUNT(CASE WHEN claim_status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW') THEN 1 END)
                       AS "pendingClaims",
                   COALESCE(SUM(CASE WHEN claim_status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW')
                                     THEN claim_amount ELSE 0 END), 0) AS "pendingAmount",
                   COUNT(CASE WHEN claim_status = 'APPROVED' THEN 1 END) AS "approvedClaims",
                   COALESCE(SUM(CASE WHEN claim_status = 'APPROVED' THEN claim_amount ELSE 0 END), 0)
                       AS "approvedAmount",
                   COUNT(CASE WHEN claim_status = 'REJECTED' THEN 1 END) AS "rejectedClaims",
                   COALESCE(SUM(CASE WHEN claim_status = 'REJECTED' THEN claim_amount ELSE 0 END), 0)
                       AS "rejectedAmount",
                   COUNT(CASE WHEN claim_status = 'SETTLED' THEN 1 END) AS "settledClaims",
                   COALESCE(SUM(CASE WHEN claim_status = 'SETTLED' THEN claim_amount ELSE 0 END), 0)
                       AS "settledAmount"
            FROM claims
            """, nativeQuery = true)
    OverallAggregate summarizeOverall();

    @Query(value = """
            SELECT CAST(claim_date AS DATE) AS "reportDate",
                   COUNT(*) AS "totalClaims",
                   COALESCE(SUM(claim_amount), 0) AS "totalAmount",
                   COALESCE(AVG(claim_amount), 0) AS "averageAmount"
            FROM claims
            WHERE claim_date >= :fromDate AND claim_date < :toDateExclusive
            GROUP BY CAST(claim_date AS DATE)
            ORDER BY CAST(claim_date AS DATE)
            """, nativeQuery = true)
    List<DailyAggregate> summarizeDaily(@Param("fromDate") LocalDateTime fromDate,
                                        @Param("toDateExclusive") LocalDateTime toDateExclusive);

    interface OverallAggregate {
        Long getTotalClaims();
        BigDecimal getTotalAmount();
        BigDecimal getAverageAmount();
        Long getPendingClaims();
        BigDecimal getPendingAmount();
        Long getApprovedClaims();
        BigDecimal getApprovedAmount();
        Long getRejectedClaims();
        BigDecimal getRejectedAmount();
        Long getSettledClaims();
        BigDecimal getSettledAmount();
    }

    interface StatusAggregate {
        String getClaimStatus();
        Long getTotalClaims();
        BigDecimal getTotalAmount();
        BigDecimal getAverageAmount();
    }

    interface ClaimTypeAggregate {
        String getClaimType();
        Long getTotalClaims();
        BigDecimal getTotalAmount();
        BigDecimal getAverageAmount();
    }

    interface DailyAggregate {
        java.sql.Date getReportDate();
        Long getTotalClaims();
        BigDecimal getTotalAmount();
        BigDecimal getAverageAmount();
    }

}
