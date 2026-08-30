package com.claim.demo.controller;

import com.claim.demo.domain.ClaimStatus;
import com.claim.demo.dto.ClaimReportDTO;
import com.claim.demo.dto.ClaimOutcomeSummaryDTO;
import com.claim.demo.dto.ClaimTypeReportDTO;
import com.claim.demo.dto.ClaimsSummaryDTO;
import com.claim.demo.dto.DailyClaimReportDTO;
import com.claim.demo.service.ReportService;
import com.claim.demo.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportsController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @MockBean
    private JwtService jwtService;

    @Test
    void exposesSummaryStatusAndClaimTypeReports() throws Exception {
        when(reportService.generateClaimsSummary()).thenReturn(new ClaimsSummaryDTO(
                3, new BigDecimal("450.00"), new BigDecimal("150.00"),
                ClaimOutcomeSummaryDTO.of(1, new BigDecimal("100.00")),
                ClaimOutcomeSummaryDTO.of(1, new BigDecimal("150.00")),
                ClaimOutcomeSummaryDTO.empty(),
                ClaimOutcomeSummaryDTO.of(1, new BigDecimal("200.00"))));
        when(reportService.generateReportByStatus()).thenReturn(List.of(
                new ClaimReportDTO(ClaimStatus.SUBMITTED, 2L, new BigDecimal("300.00"))));
        when(reportService.generateReportByClaimType()).thenReturn(List.of(
                new ClaimTypeReportDTO("MEDICAL", 2, new BigDecimal("300.00"))));
        when(reportService.generateDailyReport(
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"))).thenReturn(List.of(
                new DailyClaimReportDTO(LocalDate.parse("2026-01-01"), 2,
                        new BigDecimal("300.00"), new BigDecimal("150.00"))));

        mockMvc.perform(get("/api/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClaims").value(3))
                .andExpect(jsonPath("$.totalClaimAmount").value(450.00))
                .andExpect(jsonPath("$.averageClaimAmount").value(150.00))
                .andExpect(jsonPath("$.pending.totalClaims").value(1))
                .andExpect(jsonPath("$.approved.totalClaimAmount").value(150.00))
                .andExpect(jsonPath("$.rejected.totalClaims").value(0))
                .andExpect(jsonPath("$.settled.totalClaimAmount").value(200.00));
        mockMvc.perform(get("/api/reports/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claimStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].totalClaims").value(2))
                .andExpect(jsonPath("$[0].averageClaimAmount").value(150.00));
        mockMvc.perform(get("/api/reports/claim-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].claimType").value("MEDICAL"))
                .andExpect(jsonPath("$[0].totalClaimAmount").value(300.00))
                .andExpect(jsonPath("$[0].averageClaimAmount").value(150.00));
        mockMvc.perform(get("/api/reports/daily")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportDate").value("2026-01-01"))
                .andExpect(jsonPath("$[0].totalClaims").value(2))
                .andExpect(jsonPath("$[0].averageClaimAmount").value(150.00));
    }

    @Test
    void rejectsInvalidDailyDateParameter() throws Exception {
        mockMvc.perform(get("/api/reports/daily")
                        .param("from", "not-a-date")
                        .param("to", "2026-01-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }
}
