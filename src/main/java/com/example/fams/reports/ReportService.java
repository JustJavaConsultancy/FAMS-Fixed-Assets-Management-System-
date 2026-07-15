package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.core.config.SecurityScopeService.ReportScope;
import com.example.fams.depreciation.DepreciationService;
import com.example.fams.maintenance.MaintenanceService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates report data sets. All queries are:
 * <ul>
 *     <li><b>Role-scoped</b> — the caller's {@link ReportScope} restricts the underlying
 *         asset set at the database level (department heads → their departments,
 *         employees → their custody, admins/auditors/asset managers → global).</li>
 *     <li><b>Cached</b> — results are cached in the {@code reports} cache keyed by report
 *         type + scope + filters, so bursts of identical requests are served without
 *         hitting the database (high-concurrency support). TTL is the configured SLA window.</li>
 * </ul>
 */
@Service
public class ReportService {

    private final AssetRepository assetRepository;
    private final DepreciationService depreciationService;
    private final MaintenanceService maintenanceService;

    public ReportService(AssetRepository assetRepository,
                         DepreciationService depreciationService,
                         MaintenanceService maintenanceService) {
        this.assetRepository = assetRepository;
        this.depreciationService = depreciationService;
        this.maintenanceService = maintenanceService;
    }

    /**
     * Load the asset set the current caller is permitted to see, filtered at the database
     * level by their role scope.
     */
    private List<Asset> scopedAssets(ReportScope scope) {
        if (scope == null || scope.isGlobal()) {
            return assetRepository.findAllByOrderByCreatedAtDesc();
        }
        if (scope.isEmptyRestriction()) {
            // Department head with no department / employee with no identity → see nothing.
            return List.of();
        }
        if (scope.isDepartment()) {
            List<String> lowered = scope.departments().stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
            return assetRepository.findByDepartmentInIgnoreCaseOrderByCreatedAtDesc(lowered);
        }
        // CUSTODIAN — custodians are already lower-cased by SecurityScopeService.
        return assetRepository.findByCustodianInIgnoreCaseOrderByCreatedAtDesc(scope.custodians());
    }

    private boolean hasValue(String value, String ignored) {
        return value != null && !value.isEmpty() && !value.equals(ignored);
    }

    /**
     * Generate Asset Register Report - complete list of all assets
     */
    @Cacheable(cacheNames = "reports",
            key = "'assetRegister|' + #scope.cacheKey() + '|' + #category + '|' + #department + '|' + #status")
    @Transactional(readOnly = true)
    public ReportData generateAssetRegisterReport(ReportScope scope, String category, String department, String status) {
        ReportData report = new ReportData();
        report.setReportType("assetRegister");
        report.setReportName("Asset Register");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(hasValue(category, "All Categories") ? category : null);
        report.setDepartment(hasValue(department, "Global Operations") ? department : null);
        report.setStatus(status != null && !status.isEmpty() ? status : null);

        List<Asset> assets = scopedAssets(scope);

        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty()) {
            assets = assets.stream()
                    .filter(a -> a.getStatus() != null && a.getStatus().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        return finish(report, assets);
    }

    /**
     * Generate Inventory Summary Report - summary by category
     */
    @Cacheable(cacheNames = "reports", key = "'inventorySummary|' + #scope.cacheKey() + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateInventorySummaryReport(ReportScope scope, String department) {
        ReportData report = new ReportData();
        report.setReportType("inventorySummary");
        report.setReportName("Inventory Summary");
        report.setGeneratedDate(LocalDate.now());
        report.setDepartment(department);

        List<Asset> assets = scopedAssets(scope);
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        return finish(report, assets);
    }

    /**
     * Generate Location Audit Report - assets by location/department
     */
    @Cacheable(cacheNames = "reports", key = "'locationAudit|' + #scope.cacheKey() + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateLocationAuditReport(ReportScope scope, String department) {
        ReportData report = new ReportData();
        report.setReportType("locationAudit");
        report.setReportName("Location Audit");
        report.setGeneratedDate(LocalDate.now());
        report.setDepartment(department);

        List<Asset> assets = scopedAssets(scope);
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        return finish(report, assets);
    }

    /**
     * Generate Valuation Report - asset values and depreciation
     */
    @Cacheable(cacheNames = "reports",
            key = "'valuation|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateValuationReport(ReportScope scope, String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("valuationReport");
        report.setReportName("Asset Valuation Report");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(category);
        report.setDepartment(department);

        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        return finish(report, assets);
    }

    /**
     * Generate Depreciation Schedule Report
     */
    @Cacheable(cacheNames = "reports",
            key = "'depreciationSchedule|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateDepreciationScheduleReport(ReportScope scope, String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("depreciationSchedule");
        report.setReportName("Depreciation Schedule");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(category);
        report.setDepartment(department);

        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        return finish(report, assets);
    }

    /**
     * Generate Purchase Analysis Report
     */
    @Cacheable(cacheNames = "reports",
            key = "'purchaseAnalysis|' + #scope.cacheKey() + '|' + #startDate + '|' + #endDate")
    @Transactional(readOnly = true)
    public ReportData generatePurchaseAnalysisReport(ReportScope scope, LocalDate startDate, LocalDate endDate) {
        ReportData report = new ReportData();
        report.setReportType("purchaseAnalysis");
        report.setReportName("Purchase Analysis");
        report.setGeneratedDate(LocalDate.now());
        report.setStartDate(startDate);
        report.setEndDate(endDate);

        List<Asset> assets = scopedAssets(scope);
        if (startDate != null && endDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getPurchaseDate() != null
                            && !a.getPurchaseDate().isBefore(startDate)
                            && !a.getPurchaseDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }
        return finish(report, assets);
    }

    /**
     * Generate Maintenance History Report.
     * Applies the requested date range against the asset purchase date (asset-level date
     * available on the report row); defaults to the last month when unspecified.
     */
    @Cacheable(cacheNames = "reports",
            key = "'maintenanceHistory|' + #scope.cacheKey() + '|' + #startDate + '|' + #endDate")
    @Transactional(readOnly = true)
    public ReportData generateMaintenanceHistoryReport(ReportScope scope, LocalDate startDate, LocalDate endDate) {
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now().minusMonths(1);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();

        ReportData report = new ReportData();
        report.setReportType("maintenanceHistory");
        report.setReportName("Service History");
        report.setGeneratedDate(LocalDate.now());
        report.setStartDate(effectiveStart);
        report.setEndDate(effectiveEnd);

        List<Asset> assets = scopedAssets(scope).stream()
                .filter(a -> a.getPurchaseDate() != null
                        && !a.getPurchaseDate().isBefore(effectiveStart)
                        && !a.getPurchaseDate().isAfter(effectiveEnd))
                .collect(Collectors.toList());
        return finish(report, assets);
    }

    /**
     * Generate Warranty Expiry Report
     */
    @Cacheable(cacheNames = "reports", key = "'warrantyExpiry|' + #scope.cacheKey()")
    @Transactional(readOnly = true)
    public ReportData generateWarrantyExpiryReport(ReportScope scope) {
        ReportData report = new ReportData();
        report.setReportType("warrantyExpiry");
        report.setReportName("Warranty Expiry");
        report.setGeneratedDate(LocalDate.now());

        List<Asset> assets = scopedAssets(scope).stream()
                .filter(a -> a.getWarrantyExpiry() != null)
                .collect(Collectors.toList());
        return finish(report, assets);
    }

    /**
     * Generate Missing Assets Report - assets not in stock/assigned status
     */
    @Cacheable(cacheNames = "reports", key = "'missingAssets|' + #scope.cacheKey()")
    @Transactional(readOnly = true)
    public ReportData generateMissingAssetsReport(ReportScope scope) {
        ReportData report = new ReportData();
        report.setReportType("missingAssets");
        report.setReportName("Missing Asset Report");
        report.setGeneratedDate(LocalDate.now());

        List<Asset> assets = scopedAssets(scope).stream()
                .filter(a -> !"In Stock".equalsIgnoreCase(a.getStatus())
                        && !"Assigned".equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.toList());
        return finish(report, assets);
    }

    /**
     * Generate Disposal Logs Report
     */
    @Cacheable(cacheNames = "reports", key = "'disposalLogs|' + #scope.cacheKey()")
    @Transactional(readOnly = true)
    public ReportData generateDisposalLogsReport(ReportScope scope) {
        ReportData report = new ReportData();
        report.setReportType("disposalLogs");
        report.setReportName("Disposal Logs");
        report.setGeneratedDate(LocalDate.now());

        List<Asset> assets = scopedAssets(scope).stream()
                .filter(a -> "Disposed".equalsIgnoreCase(a.getStatus())
                        || "Retired".equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.toList());
        return finish(report, assets);
    }

    /**
     * Map assets to rows, attach them to the report and compute totals.
     */
    private ReportData finish(ReportData report, List<Asset> assets) {
        List<ReportRow> rows = assets.stream()
                .map(this::assetToReportRow)
                .collect(Collectors.toList());
        report.setRows(rows);
        calculateTotals(report, rows);
        return report;
    }

    /**
     * Convert Asset entity to ReportRow
     */
    private ReportRow assetToReportRow(Asset asset) {
        return new ReportRow(
                asset.getId(),
                asset.getAssetCode(),
                asset.getName(),
                asset.getCategory(),
                asset.getDepartment(),
                asset.getCustodian(),
                asset.getPurchaseDate(),
                asset.getPurchaseCost(),
                BigDecimal.ZERO, // TODO: get from depreciation if available
                asset.getPurchaseCost() != null ? asset.getPurchaseCost() : BigDecimal.ZERO,
                asset.getStatus()
        );
    }

    /**
     * Calculate totals for a report
     */
    private void calculateTotals(ReportData report, List<ReportRow> rows) {
        if (rows == null || rows.isEmpty()) {
            report.setEmpty(true);
            report.setTotalRecords(0);
            report.setTotalValue(BigDecimal.ZERO);
            report.setTotalDepreciation(BigDecimal.ZERO);
            report.setTotalNetBookValue(BigDecimal.ZERO);
            return;
        }

        BigDecimal totalValue = rows.stream()
                .map(ReportRow::acquisitionCost)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDepreciation = rows.stream()
                .map(ReportRow::accumulatedDepreciation)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNetValue = rows.stream()
                .map(ReportRow::netBookValue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        report.setTotalValue(totalValue);
        report.setTotalDepreciation(totalDepreciation);
        report.setTotalNetBookValue(totalNetValue);
        report.setTotalRecords(rows.size());
        report.setEmpty(false);
    }
}
