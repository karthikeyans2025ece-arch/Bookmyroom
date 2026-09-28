package com.example.bookmyroom.controller;

import com.example.bookmyroom.dto.OccupancyReportResponse;
import com.example.bookmyroom.service.OccupancyReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Occupancy reports")
public class ReportController {

    private final OccupancyReportService reportService;

    public ReportController(OccupancyReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Monthly occupancy report, e.g. ?month=2026-10")
    @GetMapping("/occupancy")
    public ResponseEntity<OccupancyReportResponse> getOccupancyReport(@RequestParam("month") String month) {
        return ResponseEntity.ok(reportService.getMonthlyReport(month));
    }
}
