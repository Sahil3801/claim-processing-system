package com.claim.demo.controller;

import com.claim.demo.dto.ClaimReportDTO;
import com.claim.demo.dto.ClaimTypeReportDTO;
import com.claim.demo.dto.ClaimsSummaryDTO;
import com.claim.demo.dto.DailyClaimReportDTO;
import com.claim.demo.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final ReportService reportService;

    public ReportsController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ClaimsSummaryDTO getClaimsSummary() {
        return reportService.generateClaimsSummary();
    }

    @GetMapping("/status")
    public List<ClaimReportDTO> getClaimsReportByStatus() {
        return reportService.generateReportByStatus();
    }

    @GetMapping("/claim-types")
    public List<ClaimTypeReportDTO> getClaimsReportByType() {
        return reportService.generateReportByClaimType();
    }

    @GetMapping("/daily")
    public List<DailyClaimReportDTO> getDailyClaimsReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.generateDailyReport(from, to);
    }
}
