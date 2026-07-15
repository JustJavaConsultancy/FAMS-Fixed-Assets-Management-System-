package com.example.fams.reports;

import com.example.fams.core.config.SecurityScopeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Serves role-scoped report exports as downloadable Excel (.xlsx) or PDF files.
 *
 * <p>Visibility is enforced server-side: every export is built through
 * {@link ReportService} using the caller's {@link SecurityScopeService.ReportScope}, so a
 * non-admin (department head / employee) can only export the slice they are permitted to see.
 *
 * <p>CSRF is disabled app-wide, so these GET endpoints can be triggered from the browser
 * directly (e.g. an anchor download) without a token.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportExportController {

    private static final String EXCEL_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_TYPE = MediaType.APPLICATION_PDF_VALUE;

    private final ReportService reportService;
    private final ReportExportService exportService;
    private final SecurityScopeService scopeService;

    public ReportExportController(ReportService reportService,
                                  ReportExportService exportService,
                                  SecurityScopeService scopeService) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.scopeService = scopeService;
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(@PathVariable String type,
                                         @RequestParam(defaultValue = "xlsx") String format,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) String department,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        SecurityScopeService.ReportScope scope = scopeService.getScope();
        ReportData report = resolve(type, scope, category, department, status, startDate, endDate);

        boolean asPdf = "pdf".equalsIgnoreCase(format);
        byte[] bytes = asPdf ? exportService.toPdf(report) : exportService.toExcel(report);

        String contentType = asPdf ? PDF_TYPE : EXCEL_TYPE;
        String extension = asPdf ? "pdf" : "xlsx";
        String filename = sanitize(report.getReportName()) + "_"
                + (report.getGeneratedDate() == null ? LocalDate.now() : report.getGeneratedDate())
                .format(DateTimeFormatter.ISO_LOCAL_DATE) + "." + extension;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }

    private ReportData resolve(String type,
                               SecurityScopeService.ReportScope scope,
                               String category,
                               String department,
                               String status,
                               LocalDate startDate,
                               LocalDate endDate) {
        return switch (type) {
            case "asset-register" ->
                    reportService.generateAssetRegisterReport(scope, category, department, status);
            case "inventory-summary" ->
                    reportService.generateInventorySummaryReport(scope, department);
            case "location-audit" ->
                    reportService.generateLocationAuditReport(scope, department);
            case "valuation" ->
                    reportService.generateValuationReport(scope, category, department);
            case "depreciation-schedule" ->
                    reportService.generateDepreciationScheduleReport(scope, category, department);
            case "purchase-analysis" ->
                    reportService.generatePurchaseAnalysisReport(scope, startDate, endDate);
            case "maintenance-history" ->
                    reportService.generateMaintenanceHistoryReport(scope, startDate, endDate);
            case "warranty-expiry" ->
                    reportService.generateWarrantyExpiryReport(scope);
            case "missing-assets" ->
                    reportService.generateMissingAssetsReport(scope);
            case "disposal-logs" ->
                    reportService.generateDisposalLogsReport(scope);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown report type: " + type);
        };
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "report";
        }
        String cleaned = name.trim().replaceAll("[^A-Za-z0-9 _-]", "").replaceAll("\\s+", "_");
        return cleaned.isEmpty() ? "report" : cleaned;
    }
}
