package com.example.fams.dashboard;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.audit.AuditSession;
import com.example.fams.audit.AuditSessionRepository;
import com.example.fams.audit.AuditSessionStatus;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.depreciation.DepreciationPostingRepository;
import com.example.fams.depreciation.DepreciationService;
import com.example.fams.depreciation.DepreciationSummary;
import com.example.fams.lifecycle.AssetLifecycleHistory;
import com.example.fams.lifecycle.AssetLifecycleHistoryRepository;
import com.example.fams.lifecycle.AssetLifecycleWorkflow;
import com.example.fams.lifecycle.AssetLifecycleWorkflowRepository;
import com.example.fams.lifecycle.LifecycleWorkflowStatus;
import com.example.fams.lifecycle.LifecycleWorkflowType;
import com.example.fams.maintenance.MaintenanceRecord;
import com.example.fams.maintenance.MaintenanceStatus;
import com.example.fams.maintenance.MaintenanceType;
import com.example.fams.maintenance.MaintenanceTask;
import com.example.fams.maintenance.MaintenanceTaskRepository;
import com.example.fams.maintenance.MaintenanceRecordRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Computes the global dashboard KPI snapshot and caches it so KPIs refresh within
 * the configured SLA window ({@code fams.kpi.cache-ttl-seconds}, default 60s) instead
 * of running expensive full-table scans on every request.
 *
 * <p>This is a dedicated bean (separate from {@link DashboardModelService}) so Spring's
 * caching proxy actually intercepts {@link #getGlobalDashboard()} — an internal call from
 * within the same bean would bypass the cache.
 *
 * <p>Global scope is correct for the consumers of this snapshot: {@code /dashboard}
 * (asset managers + fallback) and {@code /admin/dashboard} (admins), both of which
 * legitimately see organisation-wide figures.
 */
@Service
public class DashboardKpiService {

    private static final List<String> CHART_COLORS = List.of(
            "#98001a", "#8F0F1E", "#c0182a", "#151b29", "#A05C00", "#5d5e65", "#1558A8"
    );
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter DATE_TIME_LABEL = DateTimeFormatter.ofPattern("MMM d, h:mm a");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM");

    private final AssetRepository assetRepository;
    private final MaintenanceTaskRepository maintenanceTaskRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final AuditSessionRepository auditSessionRepository;
    private final AssetLifecycleHistoryRepository lifecycleHistoryRepository;
    private final AssetLifecycleWorkflowRepository lifecycleWorkflowRepository;
    private final DepreciationService depreciationService;
    private final DepreciationPostingRepository depreciationPostingRepository;

    public DashboardKpiService(AssetRepository assetRepository,
                               MaintenanceTaskRepository maintenanceTaskRepository,
                               MaintenanceRecordRepository maintenanceRecordRepository, MaintenanceRecordRepository maintenanceRecordRepository1,
                               AuditSessionRepository auditSessionRepository,
                               AssetLifecycleHistoryRepository lifecycleHistoryRepository,
                               AssetLifecycleWorkflowRepository lifecycleWorkflowRepository,
                               DepreciationService depreciationService,
                               DepreciationPostingRepository depreciationPostingRepository) {
        this.assetRepository = assetRepository;
        this.maintenanceTaskRepository = maintenanceTaskRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository1;
        this.auditSessionRepository = auditSessionRepository;
        this.lifecycleHistoryRepository = lifecycleHistoryRepository;
        this.lifecycleWorkflowRepository = lifecycleWorkflowRepository;
        this.depreciationService = depreciationService;
        this.depreciationPostingRepository = depreciationPostingRepository;
    }

    /**
     * Build (or return the cached) map of dashboard model attributes.
     * Cached under a single key so all concurrent dashboard hits share one computation
     * per TTL window.
     */
    @Cacheable(cacheNames = "dashboardKpis", key = "'global'")
    @Transactional(readOnly = true)
    public Map<String, Object> getGlobalDashboard() {
        Map<String, Object> model = new LinkedHashMap<>();

        List<Asset> assets = assetRepository.findByStatusNotIgnoreCaseOrderByCreatedAtDesc("Disposed");
        int totalAssets = assets.size();
        BigDecimal totalAssetValue = assets.stream()
                .map(Asset::getPurchaseCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long assignedAssets = assets.stream()
                .filter(asset -> equalsIgnoreCase(asset.getStatus(), "Assigned"))
                .count();
        long assetsInMaintenance = assets.stream()
                .filter(asset -> equalsIgnoreCase(asset.getStatus(), "Maintenance"))
                .count();
        long activeAudits = auditSessionRepository.findByStatusOrderByStartedAtDesc(AuditSessionStatus.ACTIVE).size();
        long openMaintenance = countMaintenance(MaintenanceStatus.DUE)
                + countMaintenance(MaintenanceStatus.OPEN)
                + countMaintenance(MaintenanceStatus.IN_PROGRESS)
                + countMaintenance(MaintenanceStatus.OVERDUE);

        DepreciationSummary depreciationSummary = depreciationService.getLatestSummary();
        BigDecimal bookValue = defaultDecimal(depreciationSummary.getTotalBookValue());
        BigDecimal accumulatedDepreciation = defaultDecimal(depreciationSummary.getTotalAccumulatedDepreciation());
        BigDecimal depreciationPercentage = depreciationSummary.getTotalDepreciationPercentage();
        BigDecimal resalePercentage = depreciationSummary.getRecoveryPotentialPercentage();

        List<AuditSession> audits = auditSessionRepository.findAllByOrderByStartedAtDesc();
        long completedAudits = audits.stream()
                .filter(audit -> audit.getStatus() == AuditSessionStatus.COMPLETED)
                .count();
        long pendingDisposals = lifecycleWorkflowRepository.findAllByOrderByRequestedAtDesc().stream()
                .filter(workflow -> workflow.getType() == LifecycleWorkflowType.DISPOSAL)
                .filter(workflow -> workflow.getStatus() == LifecycleWorkflowStatus.PENDING_APPROVAL)
                .count();

        int maintenanceHealth = totalAssets == 0 ? 0 : BigDecimal.valueOf((totalAssets - Math.min(openMaintenance, totalAssets)) * 100.0 / totalAssets)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        int complianceScore = audits.isEmpty() ? 0 : BigDecimal.valueOf(completedAudits * 100.0 / audits.size())
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        model.put("totalAssets", formatInteger(totalAssets));
        model.put("totalAssetsRaw", totalAssets);
        model.put("totalAssetValue", formatCurrency(totalAssetValue));
        model.put("assignedAssets", formatInteger(assignedAssets));
        model.put("activeAudits", formatInteger(activeAudits));
        model.put("openMaintenance", formatInteger(openMaintenance));
        model.put("assetsInMaintenance", formatInteger(assetsInMaintenance));
        model.put("bookValue", formatCurrency(bookValue));
        model.put("accumulatedDepreciation", formatCurrency(accumulatedDepreciation));
        model.put("depreciationPercentage", formatPercent(depreciationPercentage));
        model.put("resalePercentage", formatPercent(resalePercentage));
        model.put("averageAssetAge", averageAssetAge(assets));
        model.put("periodLabel", "As of " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        model.put("maintenanceHealth", maintenanceHealth + "%");
        model.put("maintenanceHealthRaw", maintenanceHealth);
        model.put("complianceScore", complianceScore + "%");
        model.put("complianceScoreRaw", complianceScore);
        model.put("pendingDisposals", formatInteger(pendingDisposals));
        model.put("pendingDisposalsRaw", pendingDisposals);
        model.put("completedAudits", formatInteger(completedAudits));
        model.put("totalAudits", formatInteger(audits.size()));

        List<BreakdownRow> categoryBreakdown = buildBreakdownRows(
                assets.stream().collect(Collectors.groupingBy(asset -> cleanLabel(asset.getCategory(), "Uncategorized"), LinkedHashMap::new, Collectors.counting())),
                totalAssets
        );
        List<BreakdownRow> statusBreakdown = buildBreakdownRows(
                assets.stream().collect(Collectors.groupingBy(asset -> cleanLabel(asset.getStatus(), "Unknown"), LinkedHashMap::new, Collectors.counting())),
                totalAssets
        );

        model.put("categoryBreakdown", categoryBreakdown);
        model.put("statusBreakdown", statusBreakdown);
        model.put("statusSegments", buildStatusSegments(statusBreakdown));
        model.put("maintenanceItems", maintenanceTaskRepository.findTop5ByStatusNotOrderByDueDateAscCreatedAtDesc(MaintenanceStatus.COMPLETED).stream()
                .map(this::toMaintenanceItem)
                .toList());
        model.put("activityItems", lifecycleHistoryRepository.findTop8ByOrderByEventAtDesc().stream()
                .map(this::toActivityItem)
                .toList());
        model.put("depreciationBars", buildDepreciationBars());
        model.put("assetValueBars", buildAssetValueBars(assets));
        model.put("disposalItems", buildDisposalItems());
        model.put("pendingMaintenanceRequests", formatInteger(maintenanceRecordRepository.countByStatus(MaintenanceStatus.OPEN)));
        model.put("maintenanceRequestItems", maintenanceRecordRepository
                .findTop8ByStatusOrderByMaintenanceDateDescCreatedAtDesc(MaintenanceStatus.OPEN).stream()
                .map(this::toMaintenanceRequestItem)
                .toList());

        return model;
    }

    private List<BreakdownRow> buildBreakdownRows(Map<String, Long> counts, int total) {
        List<Map.Entry<String, Long>> sorted = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .toList();
        List<BreakdownRow> rows = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Long> entry = sorted.get(i);
            int percentage = total == 0 ? 0 : BigDecimal.valueOf(entry.getValue() * 100.0 / total)
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
            rows.add(new BreakdownRow(
                    entry.getKey(),
                    entry.getValue(),
                    formatInteger(entry.getValue()),
                    percentage,
                    CHART_COLORS.get(i % CHART_COLORS.size())
            ));
        }
        return rows;
    }

    private List<StatusSegment> buildStatusSegments(List<BreakdownRow> rows) {
        double circumference = 251.2;
        double offset = 0;
        List<StatusSegment> segments = new ArrayList<>();
        for (BreakdownRow row : rows) {
            double length = circumference * row.percentage() / 100.0;
            segments.add(new StatusSegment(row.color(), String.format(Locale.US, "%.1f", length), String.format(Locale.US, "%.1f", offset)));
            offset += length;
        }
        return segments;
    }

    private MaintenanceItem toMaintenanceItem(MaintenanceTask task) {
        Asset asset = task.getAsset();
        String assetName = asset != null ? cleanLabel(asset.getName(), "Asset") : cleanLabel(task.getAssetCategory(), "Asset category");
        String assetCode = asset != null ? cleanLabel(asset.getAssetCode(), "") : "";
        String title = assetCode.isBlank() ? assetName : assetName + " (" + assetCode + ")";
        String dueLabel = task.getDueDate() == null ? "No due date" : task.getDueDate().format(DATE_LABEL);
        return new MaintenanceItem(
                title,
                cleanLabel(task.getServiceType(), "Maintenance"),
                dueLabel,
                task.getStatus() == null ? "UNKNOWN" : task.getStatus().name().replace('_', ' '),
                task.getStatus() == MaintenanceStatus.OVERDUE
        );
    }

    private ActivityItem toActivityItem(AssetLifecycleHistory history) {
        Asset asset = history.getAsset();
        String assetLabel = asset == null ? "" : cleanLabel(asset.getAssetCode(), asset.getName());
        String description = cleanLabel(history.getTitle(), "Lifecycle event");
        if (!assetLabel.isBlank()) {
            description = description + " - " + assetLabel;
        }
        String actor = cleanLabel(history.getActor(), "System");
        String when = history.getEventAt() == null ? "" : history.getEventAt().format(DATE_TIME_LABEL);
        return new ActivityItem(actor, description, when, activityColor(history.getEventType() == null ? "" : history.getEventType().name()));
    }

    private List<DepreciationBar> buildDepreciationBars() {
        List<DepreciationPosting> postings = depreciationPostingRepository.findByFiscalYearOrderByDepreciationPeriodDescAssetCode(Year.now().getValue());
        if (postings.isEmpty()) {
            return List.of();
        }
        Map<String, BigDecimal> chargesByMonth = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            chargesByMonth.put(LocalDate.of(Year.now().getValue(), month, 1).format(MONTH_LABEL), BigDecimal.ZERO);
        }
        for (DepreciationPosting posting : postings) {
            String period = posting.getDepreciationPeriod();
            if (period == null || period.length() < 7) {
                continue;
            }
            int month;
            try {
                month = Integer.parseInt(period.substring(5, 7));
            } catch (NumberFormatException ex) {
                continue;
            }
            if (month < 1 || month > 12) {
                continue;
            }
            String label = LocalDate.of(Year.now().getValue(), month, 1).format(MONTH_LABEL);
            chargesByMonth.computeIfPresent(label, (key, value) -> value.add(defaultDecimal(posting.getDepreciationCharge())));
        }
        BigDecimal max = chargesByMonth.values().stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        if (max.signum() == 0) {
            return List.of();
        }
        return chargesByMonth.entrySet().stream()
                .map(entry -> {
                    int height = max.signum() == 0 ? 0 : entry.getValue().multiply(BigDecimal.valueOf(100)).divide(max, 0, RoundingMode.HALF_UP).intValue();
                    return new DepreciationBar(entry.getKey(), Math.max(height, entry.getValue().signum() == 0 ? 0 : 8), formatCurrency(entry.getValue()));
                })
                .toList();
    }

    private List<ChartBar> buildAssetValueBars(List<Asset> assets) {
        Map<String, BigDecimal> valuesByMonth = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            valuesByMonth.put(LocalDate.of(Year.now().getValue(), month, 1).format(MONTH_LABEL), BigDecimal.ZERO);
        }
        for (Asset asset : assets) {
            if (asset.getPurchaseDate() == null || asset.getPurchaseDate().getYear() != Year.now().getValue()) {
                continue;
            }
            String month = asset.getPurchaseDate().format(MONTH_LABEL);
            valuesByMonth.computeIfPresent(month, (key, value) -> value.add(defaultDecimal(asset.getPurchaseCost())));
        }
        BigDecimal max = valuesByMonth.values().stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        if (max.signum() == 0) {
            return List.of();
        }
        return valuesByMonth.entrySet().stream()
                .map(entry -> {
                    int height = entry.getValue().multiply(BigDecimal.valueOf(100)).divide(max, 0, RoundingMode.HALF_UP).intValue();
                    return new ChartBar(entry.getKey(), Math.max(height, entry.getValue().signum() == 0 ? 0 : 8), formatCurrency(entry.getValue()));
                })
                .toList();
    }

    private List<DisposalItem> buildDisposalItems() {
        return lifecycleWorkflowRepository.findAllByOrderByRequestedAtDesc().stream()
                .filter(workflow -> workflow.getType() == LifecycleWorkflowType.DISPOSAL)
                .filter(workflow -> workflow.getStatus() == LifecycleWorkflowStatus.PENDING_APPROVAL)
                .limit(5)
                .map(this::toDisposalItem)
                .toList();
    }

    private DisposalItem toDisposalItem(AssetLifecycleWorkflow workflow) {
        Asset asset = workflow.getAsset();
        String assetCode = asset == null ? "Asset" : cleanLabel(asset.getAssetCode(), cleanLabel(asset.getName(), "Asset"));
        String reason = cleanLabel(workflow.getReason(), cleanLabel(workflow.getDisposalMethod(), "Pending disposal approval"));
        return new DisposalItem(assetCode, reason, workflow.getRequestedAt() == null ? "" : workflow.getRequestedAt().format(DATE_TIME_LABEL));
    }

    private MaintenanceRequestItem toMaintenanceRequestItem(MaintenanceRecord record) {
        Asset asset = record.getAsset();
        String assetCode = asset == null ? "Asset" : cleanLabel(asset.getAssetCode(), cleanLabel(asset.getName(), "Asset"));
        String issue = cleanLabel(record.getIssueDescription(), "Maintenance request");
        return new MaintenanceRequestItem(
                assetCode,
                issue,
                cleanLabel(record.getRequestedBy(), "Employee"),
                record.getMaintenanceDate() == null ? "" : record.getMaintenanceDate().format(DATE_LABEL));
    }

    private long countMaintenance(MaintenanceStatus status) {
        try {
            return maintenanceTaskRepository.countByStatus(status);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String averageAssetAge(List<Asset> assets) {
        List<BigDecimal> ages = assets.stream()
                .map(Asset::getPurchaseDate)
                .filter(Objects::nonNull)
                .map(date -> BigDecimal.valueOf(Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now())))
                        .divide(BigDecimal.valueOf(365), 1, RoundingMode.HALF_UP))
                .toList();
        if (ages.isEmpty()) {
            return "No purchase dates";
        }
        BigDecimal total = ages.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(ages.size()), 1, RoundingMode.HALF_UP) + " years";
    }

    private String formatCurrency(BigDecimal value) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-NG"));
        formatter.setMaximumFractionDigits(2);
        return formatter.format(defaultDecimal(value));
    }

    private String formatInteger(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    private String formatPercent(BigDecimal value) {
        return defaultDecimal(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean equalsIgnoreCase(String value, String expected) {
        return value != null && value.equalsIgnoreCase(expected);
    }

    private String cleanLabel(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String activityColor(String eventType) {
        return switch (eventType) {
            case "REGISTRATION" -> "border-primary";
            case "TRANSFER" -> "border-status-info";
            case "MAINTENANCE" -> "border-status-warning";
            case "DISPOSAL", "REJECTION" -> "border-error";
            default -> "border-secondary";
        };
    }

    public record BreakdownRow(String label, long count, String formattedCount, int percentage, String color) {
    }

    public record StatusSegment(String color, String dashLength, String offset) {
    }

    public record MaintenanceItem(String title, String serviceType, String dueLabel, String status, boolean overdue) {
    }

    public record ActivityItem(String actor, String description, String when, String colorClass) {
    }

    public record DepreciationBar(String month, int height, String value) {
    }

    public record ChartBar(String month, int height, String value) {
    }

    public record DisposalItem(String assetCode, String reason, String requestedAt) {
    }

    public record MaintenanceRequestItem(String assetCode, String issue, String requestedBy, String requestedAt) {
    }
}
