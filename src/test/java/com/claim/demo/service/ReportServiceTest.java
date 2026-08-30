package com.claim.demo.service;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.exception.InvalidReportDateRangeException;
import com.claim.demo.repository.ClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(claimRepository);
    }

    @Test
    void mapsOverallAndOutcomeSummaryFromLiveAggregate() {
        ClaimRepository.OverallAggregate row = mock(ClaimRepository.OverallAggregate.class);
        when(row.getTotalClaims()).thenReturn(6L);
        when(row.getTotalAmount()).thenReturn(new BigDecimal("1200.00"));
        when(row.getAverageAmount()).thenReturn(new BigDecimal("200.00"));
        when(row.getPendingClaims()).thenReturn(3L);
        when(row.getPendingAmount()).thenReturn(new BigDecimal("450.00"));
        when(row.getApprovedClaims()).thenReturn(1L);
        when(row.getApprovedAmount()).thenReturn(new BigDecimal("250.00"));
        when(row.getRejectedClaims()).thenReturn(1L);
        when(row.getRejectedAmount()).thenReturn(new BigDecimal("100.00"));
        when(row.getSettledClaims()).thenReturn(1L);
        when(row.getSettledAmount()).thenReturn(new BigDecimal("400.00"));
        when(claimRepository.summarizeOverall()).thenReturn(row);

        var result = reportService.generateClaimsSummary();

        assertThat(result.totalClaims()).isEqualTo(6);
        assertThat(result.averageClaimAmount()).isEqualByComparingTo("200.00");
        assertThat(result.pending().totalClaims()).isEqualTo(3);
        assertThat(result.pending().averageClaimAmount()).isEqualByComparingTo("150.00");
        assertThat(result.approved().totalClaimAmount()).isEqualByComparingTo("250.00");
        assertThat(result.rejected().totalClaimAmount()).isEqualByComparingTo("100.00");
        assertThat(result.settled().totalClaimAmount()).isEqualByComparingTo("400.00");
        verify(claimRepository).summarizeOverall();
    }

    @Test
    void mapsStatusTypeAndInclusiveDailyReports() {
        ClaimRepository.StatusAggregate status = mock(ClaimRepository.StatusAggregate.class);
        when(status.getClaimStatus()).thenReturn("UNDER_REVIEW");
        when(status.getTotalClaims()).thenReturn(2L);
        when(status.getTotalAmount()).thenReturn(new BigDecimal("300.00"));
        when(status.getAverageAmount()).thenReturn(new BigDecimal("150.00"));
        when(claimRepository.summarizeByStatus()).thenReturn(List.of(status));

        ClaimRepository.ClaimTypeAggregate type = mock(ClaimRepository.ClaimTypeAggregate.class);
        when(type.getClaimType()).thenReturn("MEDICAL");
        when(type.getTotalClaims()).thenReturn(2L);
        when(type.getTotalAmount()).thenReturn(new BigDecimal("300.00"));
        when(type.getAverageAmount()).thenReturn(new BigDecimal("150.00"));
        when(claimRepository.summarizeByClaimType()).thenReturn(List.of(type));

        ClaimRepository.DailyAggregate day = mock(ClaimRepository.DailyAggregate.class);
        when(day.getReportDate()).thenReturn(Date.valueOf("2026-01-02"));
        when(day.getTotalClaims()).thenReturn(2L);
        when(day.getTotalAmount()).thenReturn(new BigDecimal("300.00"));
        when(day.getAverageAmount()).thenReturn(new BigDecimal("150.00"));
        when(claimRepository.summarizeDaily(anyDateTime(), anyDateTime())).thenReturn(List.of(day));

        assertThat(reportService.generateReportByStatus().get(0).getClaimStatus())
                .isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(reportService.generateReportByClaimType().get(0).averageClaimAmount())
                .isEqualByComparingTo("150.00");
        var daily = reportService.generateDailyReport(
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"));
        assertThat(daily.get(0).reportDate()).isEqualTo(LocalDate.parse("2026-01-02"));
        verify(claimRepository).summarizeDaily(
                LocalDateTime.parse("2026-01-01T00:00:00"),
                LocalDateTime.parse("2026-01-03T00:00:00"));
    }

    @Test
    void rejectsReversedDateRangeBeforeQueryingDatabase() {
        assertThatThrownBy(() -> reportService.generateDailyReport(
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-01-01")))
                .isInstanceOf(InvalidReportDateRangeException.class);

        verifyNoInteractions(claimRepository);
    }

    private LocalDateTime anyDateTime() {
        return org.mockito.ArgumentMatchers.any(LocalDateTime.class);
    }
}
