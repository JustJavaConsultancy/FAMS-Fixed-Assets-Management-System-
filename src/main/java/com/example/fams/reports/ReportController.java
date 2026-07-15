package com.example.fams.reports;

import com.example.fams.core.config.SecurityScopeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final SecurityScopeService scopeService;

    public ReportController(ReportService reportService, SecurityScopeService scopeService) {
        this.reportService = reportService;
        this.scopeService = scopeService;
    }

    /**
     * Get Asset Register Report
     */
    @GetMapping("/asset-register")
    public ResponseEntity<Map<String, Object>> assetRegisterReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status) {

        try {
            ReportData report = reportService.generateAssetRegisterReport(scopeService.getScope(), category, department, status);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating asset register report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get Inventory Summary Report
     */
    @GetMapping("/inventory-summary")
    public ResponseEntity<Map<String, Object>> inventorySummaryReport(
            @RequestParam(required = false) String department) {

        try {
            ReportData report = reportService.generateInventorySummaryReport(scopeService.getScope(), department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating inventory summary: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get Location Audit Report
     */
    @GetMapping("/location-audit")
    public ResponseEntity<Map<String, Object>> locationAuditReport(
            @RequestParam(required = false) String department) {

        try {
            ReportData report = reportService.generateLocationAuditReport(scopeService.getScope(), department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating location audit report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get Valuation Report
     */
    @GetMapping("/valuation")
    public ResponseEntity<Map<String, Object>> valuationReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {

        try {
            ReportData report = reportService.generateValuationReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating valuation report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get Depreciation Schedule Report
     */
    @GetMapping("/depreciation-schedule")
    public ResponseEntity<Map<String, Object>> depreciationScheduleReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {

        try {
            ReportData report = reportService.generateDepreciationScheduleReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating depreciation schedule: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get Purchase Analysis Report
     */
    @GetMapping("/purchase-analysis")
    public ResponseEntity<Map<String, Object>> purchaseAnalysisReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            ReportData report = reportService.generatePurchaseAnalysisReport(scopeService.getScope(), startDate, endDate);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating purchase analysis: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get Maintenance History Report
     */
    @GetMapping("/maintenance-history")
    public ResponseEntity<Map<String, Object>> maintenanceHistoryReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            ReportData report = reportService.generateMaintenanceHistoryReport(scopeService.getScope(), startDate, endDate);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating maintenance history: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get Warranty Expiry Report
     */
    @GetMapping("/warranty-expiry")
    public ResponseEntity<Map<String, Object>> warrantyExpiryReport() {

        try {
            ReportData report = reportService.generateWarrantyExpiryReport(scopeService.getScope());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating warranty expiry report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get Missing Assets Report
     */
    @GetMapping("/missing-assets")
    public ResponseEntity<Map<String, Object>> missingAssetsReport() {

        try {
            ReportData report = reportService.generateMissingAssetsReport(scopeService.getScope());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating missing assets report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get Disposal Logs Report
     */
    @GetMapping("/disposal-logs")
    public ResponseEntity<Map<String, Object>> disposalLogsReport() {

        try {
            ReportData report = reportService.generateDisposalLogsReport(scopeService.getScope());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating disposal logs: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}

