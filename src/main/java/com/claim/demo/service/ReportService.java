package com.claim.demo.service;

import com.claim.demo.dto.ClaimReportDTO;
import com.claim.demo.dto.ClaimOutcomeSummaryDTO;
import com.claim.demo.dto.ClaimTypeReportDTO;
import com.claim.demo.dto.ClaimsSummaryDTO;
import com.claim.demo.dto.DailyClaimReportDTO;
import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.exception.InvalidReportDateRangeException;
import com.claim.demo.repository.ClaimRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReportService {

    private final ClaimRepository claimRepository;

    public ReportService(ClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    @Transactional(readOnly = true)
    public ClaimsSummaryDTO generateClaimsSummary() {
        ClaimRepository.OverallAggregate row = claimRepository.summarizeOverall();
        return new ClaimsSummaryDTO(
                value(row.getTotalClaims()),
                amount(row.getTotalAmount()),
                amount(row.getAverageAmount()),
                ClaimOutcomeSummaryDTO.of(value(row.getPendingClaims()), row.getPendingAmount()),
                ClaimOutcomeSummaryDTO.of(value(row.getApprovedClaims()), row.getApprovedAmount()),
                ClaimOutcomeSummaryDTO.of(value(row.getRejectedClaims()), row.getRejectedAmount()),
                ClaimOutcomeSummaryDTO.of(value(row.getSettledClaims()), row.getSettledAmount()));
    }

    @Transactional(readOnly = true)
    public List<ClaimReportDTO> generateReportByStatus() {
        return claimRepository.summarizeByStatus().stream()
                .map(row -> new ClaimReportDTO(
                        ClaimStatus.from(row.getClaimStatus()),
                        value(row.getTotalClaims()),
                        amount(row.getTotalAmount()),
                        amount(row.getAverageAmount())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClaimTypeReportDTO> generateReportByClaimType() {
        return claimRepository.summarizeByClaimType().stream()
                .map(row -> new ClaimTypeReportDTO(
                        row.getClaimType(),
                        value(row.getTotalClaims()),
                        amount(row.getTotalAmount()),
                        amount(row.getAverageAmount())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DailyClaimReportDTO> generateDailyReport(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new InvalidReportDateRangeException(from, to);
        }

        return claimRepository.summarizeDaily(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                .map(row -> new DailyClaimReportDTO(
                        row.getReportDate().toLocalDate(),
                        value(row.getTotalClaims()),
                        amount(row.getTotalAmount()),
                        amount(row.getAverageAmount())))
                .toList();
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
