package com.claim.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.claim.demo.entity.ClaimReport;
import com.claim.demo.domain.ClaimStatus;

import java.util.Date;
import java.util.List;

@Repository
public interface ClaimReportRepository extends JpaRepository<ClaimReport, Long> {
    // Example method to fetch claim reports by status
    List<ClaimReport> findByClaimStatus(ClaimStatus claimStatus);

    // Example method to fetch claim reports by a date range
    List<ClaimReport> findByReportDateBetween(Date startDate, Date endDate);
}
