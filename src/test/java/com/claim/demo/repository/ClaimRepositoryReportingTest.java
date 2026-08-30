package com.claim.demo.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ClaimRepositoryReportingTest {

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertClaims() {
        jdbcTemplate.update("""
                INSERT INTO users
                    (user_id, username, password_hash, email, role, created_at, status)
                VALUES (100, 'report-user', 'hash', 'report@example.com', 'CLAIMANT',
                        TIMESTAMP '2026-01-01 00:00:00', 'active')
                """);

        insertClaim(1001, "2026-01-01T08:00:00", "100.00", "MEDICAL", "SUBMITTED");
        insertClaim(1002, "2026-01-01T12:00:00", "300.00", "MEDICAL", "APPROVED");
        insertClaim(1003, "2026-01-02T09:00:00", "200.00", "AUTO", "REJECTED");
        insertClaim(1004, "2026-01-02T14:00:00", "400.00", "AUTO", "SETTLED");
        insertClaim(1005, "2026-01-03T10:00:00", "500.00", "HOME", "DRAFT");
    }

    @Test
    void calculatesOverallAndOutcomeAggregatesInOneDatabaseQuery() {
        ClaimRepository.OverallAggregate result = claimRepository.summarizeOverall();

        assertThat(result.getTotalClaims()).isEqualTo(5);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("1500.00");
        assertThat(result.getAverageAmount()).isEqualByComparingTo("300.00");
        assertThat(result.getPendingClaims()).isEqualTo(2);
        assertThat(result.getPendingAmount()).isEqualByComparingTo("600.00");
        assertThat(result.getApprovedClaims()).isEqualTo(1);
        assertThat(result.getApprovedAmount()).isEqualByComparingTo("300.00");
        assertThat(result.getRejectedClaims()).isEqualTo(1);
        assertThat(result.getRejectedAmount()).isEqualByComparingTo("200.00");
        assertThat(result.getSettledClaims()).isEqualTo(1);
        assertThat(result.getSettledAmount()).isEqualByComparingTo("400.00");
    }

    @Test
    void groupsStatusAndClaimTypeAggregatesWithoutLoadingClaimEntities() {
        var statuses = claimRepository.summarizeByStatus();
        var claimTypes = claimRepository.summarizeByClaimType();

        assertThat(statuses).hasSize(5);
        assertThat(statuses).anySatisfy(approved -> {
            assertThat(approved.getClaimStatus()).isEqualTo("APPROVED");
            assertThat(approved.getTotalClaims()).isEqualTo(1);
            assertThat(approved.getTotalAmount()).isEqualByComparingTo("300.00");
            assertThat(approved.getAverageAmount()).isEqualByComparingTo("300.00");
        });
        assertThat(claimTypes).anySatisfy(medical -> {
            assertThat(medical.getClaimType()).isEqualTo("MEDICAL");
            assertThat(medical.getTotalClaims()).isEqualTo(2);
            assertThat(medical.getTotalAmount()).isEqualByComparingTo("400.00");
            assertThat(medical.getAverageAmount()).isEqualByComparingTo("200.00");
        });
    }

    @Test
    void groupsDailyAggregatesWithinHalfOpenDateRange() {
        var result = claimRepository.summarizeDaily(
                LocalDateTime.parse("2026-01-01T00:00:00"),
                LocalDateTime.parse("2026-01-03T00:00:00"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReportDate().toLocalDate().toString()).isEqualTo("2026-01-01");
        assertThat(result.get(0).getTotalClaims()).isEqualTo(2);
        assertThat(result.get(0).getTotalAmount()).isEqualByComparingTo("400.00");
        assertThat(result.get(0).getAverageAmount()).isEqualByComparingTo("200.00");
        assertThat(result.get(1).getReportDate().toLocalDate().toString()).isEqualTo("2026-01-02");
        assertThat(result.get(1).getTotalAmount()).isEqualByComparingTo("600.00");
    }

    private void insertClaim(long claimId, String date, String amount, String type, String status) {
        jdbcTemplate.update("""
                INSERT INTO claims
                    (claim_id, user_id, claim_date, claim_amount, claim_type, description,
                     claim_status, last_updated)
                VALUES (?, 100, ?, ?, ?, 'Reporting fixture', ?, ?)
                """, claimId, LocalDateTime.parse(date), new BigDecimal(amount), type, status,
                LocalDateTime.parse(date));
    }
}
