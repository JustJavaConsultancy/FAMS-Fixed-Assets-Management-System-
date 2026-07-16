package com.example.fams.depreciation;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.common.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DepreciationService {

    private final DepreciationParametersRepository parametersRepository;
    private final DepreciationPostingRepository postingRepository;
    private final AssetRepository assetRepository;
    private final DepreciationCalculationService calculationService;

    public DepreciationService(DepreciationParametersRepository parametersRepository,
                              DepreciationPostingRepository postingRepository,
                              AssetRepository assetRepository,
                              DepreciationCalculationService calculationService) {
        this.parametersRepository = parametersRepository;
        this.postingRepository = postingRepository;
        this.assetRepository = assetRepository;
        this.calculationService = calculationService;
    }

    /**
     * Get or create depreciation parameters for an asset
     */
    @Transactional(readOnly = true)
    public Optional<DepreciationParameters> getParametersForAsset(Long assetId, LocalDate asOfDate) {
        return parametersRepository.findByAssetIdAndEffectiveFromDateLessThanEqualAndIsActiveTrue(assetId, asOfDate);
    }

    /**
     * Save depreciation parameters
     */
    @Transactional
    public DepreciationParameters saveParameters(DepreciationParameters parameters) {
        if (parameters.getEffectiveFromDate() == null) {
            parameters.setEffectiveFromDate(AppClock.today());
        }
        return parametersRepository.save(parameters);
    }

    /**
     * End current parameters and create new ones (for future-dated changes)
     */
    @Transactional
    public void updateParametersWithEffectiveDate(Long parametersId, DepreciationParameters newParameters) {
        Optional<DepreciationParameters> existing = parametersRepository.findById(parametersId);
        existing.ifPresent(current -> {
            // End the current parameters
            current.setEffectiveToDate(newParameters.getEffectiveFromDate().minusDays(1));
            current.setIsActive(newParameters.getEffectiveFromDate().isAfter(AppClock.today()));
            parametersRepository.save(current);
        });

        // Save the new parameters
        parametersRepository.save(newParameters);
    }

    /**
     * Get category-wide parameters
     */
    @Transactional(readOnly = true)
    public List<DepreciationParameters> getCategoryParameters(String category) {
        return parametersRepository.findByCategoryAndIsActiveTrueAndAssetIdIsNull(category);
    }

    /**
     * Run depreciation for a specific period (e.g., "2024-12" for December 2024,
     * "2024-06" for June, "2024-Q1" for Q1). The period code also encodes whether the
     * charge should be prorated (monthly = 1/12, quarterly = 1/4, annual = full).
     */
    @Transactional
    public DepreciationRunResult runDepreciation(String depreciationPeriod, LocalDate periodEndDate) {
        DepreciationRunResult result = new DepreciationRunResult();
        result.setPeriod(depreciationPeriod);
        result.setRunDate(AppClock.today());

        try {
            // Get all assets
            List<Asset> assets = assetRepository.findAll();
            Integer fiscalYear = periodEndDate.getYear();
            String periodType = resolvePeriodType(depreciationPeriod);

            for (Asset asset : assets) {
                try {
                    DepreciationPosting posting = calculateDepreciationForAsset(asset, depreciationPeriod, periodEndDate, fiscalYear, periodType);
                    if (posting != null) {
                        // Replace any existing posting for this asset + period (idempotent re-run).
                        deleteExistingPosting(asset.getId(), depreciationPeriod);
                        postingRepository.save(posting);
                        result.addSuccessfulAsset(asset.getAssetCode());
                    } else {
                        result.addFailedAsset(asset.getAssetCode(), "No depreciation parameters configured for this asset");
                    }
                } catch (Exception e) {
                    result.addFailedAsset(asset.getAssetCode(), e.getMessage());
                }
            }

            if (result.getFailureCount() > 0) {
                result.setStatus("COMPLETED_WITH_ERRORS");
            } else {
                result.setStatus("COMPLETED");
            }
        } catch (Exception e) {
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * Derive the period type (and therefore the proration fraction) from a depreciation
     * period code. Codes produced by the UI:
     *   "YYYY-MM"  → monthly  (1/12)
     *   "YYYY-Q1".."YYYY-Q4" → quarterly (1/4)
     *   "YYYY-12"  → annual (full)
     */
    public static String resolvePeriodType(String depreciationPeriod) {
        if (depreciationPeriod == null) {
            return "annual";
        }
        if (depreciationPeriod.matches(".*-Q[1-4]$")) {
            return "quarterly";
        }
        if (depreciationPeriod.matches(".*-\\d{2}$")) {
            String month = depreciationPeriod.substring(depreciationPeriod.length() - 2);
            return "12".equals(month) ? "annual" : "monthly";
        }
        return "annual";
    }

    /**
     * Calculate depreciation for a single asset
     */
    @Transactional(readOnly = true)
    public DepreciationPosting calculateDepreciationForAsset(Asset asset, String depreciationPeriod, LocalDate periodEndDate, Integer fiscalYear, String periodType) {
        // Resolve depreciation parameters: asset-specific first, then category-wide fallback
        Optional<DepreciationParameters> parameters = resolveParameters(asset);

        if (parameters.isEmpty()) {
            // No depreciation parameters configured
            return null;
        }

        DepreciationParameters params = parameters.get();

        // Check if asset is disposed or not active
        if (!isAssetDepreciable(asset)) {
            return null;
        }

        BigDecimal assetCost = asset.getPurchaseCost();
        BigDecimal residualValue = params.getResidualValue();

        DepreciationPosting posting = new DepreciationPosting();
        posting.setAssetId(asset.getId());
        posting.setAssetCode(asset.getAssetCode());
        posting.setAssetName(asset.getName());
        posting.setCategory(asset.getCategory());
        posting.setDepartment(asset.getDepartment());
        posting.setDepreciationMethod(params.getMethod());
        posting.setDepreciationPeriod(depreciationPeriod);
        posting.setFiscalYear(fiscalYear);
        posting.setAssetCost(assetCost);
        posting.setUsefulLifeYears(params.getUsefulLifeYears());
        posting.setResidualValue(residualValue);

        // Get previous posting to get opening accumulated depreciation
        DepreciationPosting previousPosting = postingRepository.findFirstByAssetIdOrderByDepreciationPeriodDesc(asset.getId());
        BigDecimal openingAccumulated = previousPosting != null ? previousPosting.getClosingAccumulatedDepreciation() : BigDecimal.ZERO;
        posting.setOpeningAccumulatedDepreciation(openingAccumulated);

        // Calculate the annual depreciation charge for this asset
        BigDecimal annualCharge = calculationService.calculateAnnualDepreciation(
                assetCost,
                residualValue,
                params.getUsefulLifeYears(),
                params.getMethod(),
                1,
                openingAccumulated
        );

        // Prorate to the period being closed (monthly → 1/12, quarterly → 1/4, annual → full)
        BigDecimal depreciationCharge = calculationService.prorateCharge(annualCharge, periodType);
        posting.setDepreciationCharge(depreciationCharge);

        // Calculate closing accumulated depreciation (clamped so it never exceeds cost - residual)
        BigDecimal depreciableLimit = assetCost.subtract(residualValue == null ? BigDecimal.ZERO : residualValue);
        BigDecimal closingAccumulated = openingAccumulated.add(depreciationCharge);
        if (closingAccumulated.compareTo(depreciableLimit) > 0) {
            closingAccumulated = depreciableLimit;
        }
        posting.setClosingAccumulatedDepreciation(closingAccumulated);

        // Calculate book value
        BigDecimal bookValue = calculationService.calculateBookValue(assetCost, closingAccumulated);
        posting.setBookValue(bookValue);

        // Check if fully depreciated
        boolean fullyDep = calculationService.isFullyDepreciated(assetCost, residualValue, closingAccumulated);
        posting.setFullyDepreciated(fullyDep);

        return posting;
    }

    /**
     * Resolve the depreciation parameters that apply to an asset: prefer an asset-specific
     * (still-active) configuration; if none exists, fall back to the active category-wide
     * configuration for the asset's category.
     */
    @Transactional(readOnly = true)
    public Optional<DepreciationParameters> resolveParameters(Asset asset) {
        Optional<DepreciationParameters> assetParams = getParametersForAsset(asset.getId(), AppClock.today());
        if (assetParams.isPresent()) {
            return assetParams;
        }
        List<DepreciationParameters> categoryParams = getCategoryParameters(asset.getCategory());
        return categoryParams.stream().findFirst();
    }

    /**
     * Delete any existing posting for the given asset + period so a re-run replaces rather
     * than duplicates it (keeps accumulated depreciation from being double-counted).
     */
    @Transactional
    public void deleteExistingPosting(Long assetId, String depreciationPeriod) {
        List<DepreciationPosting> existing = postingRepository.findByAssetIdAndDepreciationPeriod(assetId, depreciationPeriod);
        if (!existing.isEmpty()) {
            postingRepository.deleteAll(existing);
        }
    }

    /**
     * Get depreciation history for an asset
     */
    @Transactional(readOnly = true)
    public List<DepreciationPosting> getDepreciationHistory(Long assetId) {
        return postingRepository.findByAssetIdOrderByDepreciationPeriodDesc(assetId);
    }

    /**
     * Get depreciation report for a period
     */
    @Transactional(readOnly = true)
    public DepreciationReport getDepreciationReport(String depreciationPeriod) {
        List<DepreciationPosting> postings = postingRepository.findByDepreciationPeriodOrderByAssetCode(depreciationPeriod);
        return buildReport(postings, depreciationPeriod);
    }

    /**
     * Get depreciation by category for a period
     */
    @Transactional(readOnly = true)
    public List<DepreciationCategoryReport> getCategoryReport(String depreciationPeriod) {
        List<DepreciationPosting> postings = postingRepository.findByDepreciationPeriodOrderByAssetCode(depreciationPeriod);

        return postings.stream()
                .collect(Collectors.groupingBy(DepreciationPosting::getCategory))
                .entrySet()
                .stream()
                .map(entry -> buildCategoryReport(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(DepreciationCategoryReport::getCategory))
                .collect(Collectors.toList());
    }

    /**
     * Get depreciation by department for a period
     */
    @Transactional(readOnly = true)
    public List<DepreciationDepartmentReport> getDepartmentReport(String depreciationPeriod) {
        List<DepreciationPosting> postings = postingRepository.findByDepreciationPeriodOrderByAssetCode(depreciationPeriod);

        return postings.stream()
                .collect(Collectors.groupingBy(DepreciationPosting::getDepartment))
                .entrySet()
                .stream()
                .map(entry -> buildDepartmentReport(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(DepreciationDepartmentReport::getDepartment))
                .collect(Collectors.toList());
    }

    private DepreciationReport buildReport(List<DepreciationPosting> postings, String period) {
        DepreciationReport report = new DepreciationReport();
        report.setPeriod(period);
        report.setPostings(postings);

        BigDecimal totalOriginalCost = postings.stream()
                .map(DepreciationPosting::getAssetCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAccumulated = postings.stream()
                .map(DepreciationPosting::getClosingAccumulatedDepreciation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCharge = postings.stream()
                .map(DepreciationPosting::getDepreciationCharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBookValue = postings.stream()
                .map(DepreciationPosting::getBookValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        report.setTotalOriginalCost(totalOriginalCost);
        report.setTotalAccumulatedDepreciation(totalAccumulated);
        report.setTotalDepreciationCharge(totalCharge);
        report.setTotalBookValue(totalBookValue);
        report.setAssetCount(postings.size());
        report.setFullyDepreciatedCount((int) postings.stream().filter(DepreciationPosting::getFullyDepreciated).count());

        return report;
    }

    private DepreciationCategoryReport buildCategoryReport(String category, List<DepreciationPosting> postings) {
        DepreciationCategoryReport report = new DepreciationCategoryReport();
        report.setCategory(category);
        report.setPostings(postings);

        BigDecimal totalOriginalCost = postings.stream()
                .map(DepreciationPosting::getAssetCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAccumulated = postings.stream()
                .map(DepreciationPosting::getClosingAccumulatedDepreciation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCharge = postings.stream()
                .map(DepreciationPosting::getDepreciationCharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBookValue = postings.stream()
                .map(DepreciationPosting::getBookValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        report.setTotalOriginalCost(totalOriginalCost);
        report.setTotalAccumulatedDepreciation(totalAccumulated);
        report.setTotalDepreciationCharge(totalCharge);
        report.setTotalBookValue(totalBookValue);
        report.setAssetCount(postings.size());

        return report;
    }

    private DepreciationDepartmentReport buildDepartmentReport(String department, List<DepreciationPosting> postings) {
        DepreciationDepartmentReport report = new DepreciationDepartmentReport();
        report.setDepartment(department);
        report.setPostings(postings);

        BigDecimal totalOriginalCost = postings.stream()
                .map(DepreciationPosting::getAssetCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAccumulated = postings.stream()
                .map(DepreciationPosting::getClosingAccumulatedDepreciation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCharge = postings.stream()
                .map(DepreciationPosting::getDepreciationCharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBookValue = postings.stream()
                .map(DepreciationPosting::getBookValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        report.setTotalOriginalCost(totalOriginalCost);
        report.setTotalAccumulatedDepreciation(totalAccumulated);
        report.setTotalDepreciationCharge(totalCharge);
        report.setTotalBookValue(totalBookValue);
        report.setAssetCount(postings.size());

        return report;
    }

    private boolean isAssetDepreciable(Asset asset) {
        if (asset == null || asset.getPurchaseCost() == null) {
            return false;
        }

        // Check status - asset should be in use or depreciated
        String status = asset.getStatus();
        return status != null && !status.equalsIgnoreCase("Disposed") && !status.equalsIgnoreCase("Scrapped");
    }

    /**
     * Get latest summary for all assets
     */
    @Transactional(readOnly = true)
    public DepreciationSummary getLatestSummary() {
        List<Asset> assets = assetRepository.findAll();
        DepreciationSummary summary = new DepreciationSummary();

        BigDecimal totalOriginalCost = BigDecimal.ZERO;
        BigDecimal totalAccumulated = BigDecimal.ZERO;
        BigDecimal totalBookValue = BigDecimal.ZERO;
        int fullyDepreciatedCount = 0;

        // Configured assets = those with asset-specific OR category-wide active parameters.
        int configuredAssetCount = countConfiguredAssets(assets);

        for (Asset asset : assets) {
            if (!isAssetDepreciable(asset)) {
                continue;
            }

            totalOriginalCost = totalOriginalCost.add(asset.getPurchaseCost());

            DepreciationPosting latest = postingRepository.findFirstByAssetIdOrderByDepreciationPeriodDesc(asset.getId());
            if (latest != null) {
                totalAccumulated = totalAccumulated.add(latest.getClosingAccumulatedDepreciation());
                totalBookValue = totalBookValue.add(latest.getBookValue());
                if (latest.getFullyDepreciated()) {
                    fullyDepreciatedCount++;
                }
            }
        }

        summary.setTotalOriginalCost(totalOriginalCost);
        summary.setTotalAccumulatedDepreciation(totalAccumulated);
        summary.setTotalBookValue(totalBookValue);
        summary.setAssetCount(assets.size());
        summary.setConfiguredAssetCount(configuredAssetCount);
        summary.setFullyDepreciatedCount(fullyDepreciatedCount);

        return summary;
    }

    /**
     * Count assets that have depreciation configured, either via an asset-specific
     * parameter or via an active category-wide parameter for the asset's category.
     */
    private int countConfiguredAssets(List<Asset> assets) {
        List<DepreciationParameters> allParams = parametersRepository.findAll();
        java.util.Set<Long> assetParamIds = allParams.stream()
                .filter(p -> p.getAssetId() != null)
                .map(DepreciationParameters::getAssetId)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> categoryParams = allParams.stream()
                .filter(p -> p.getAssetId() == null && p.getCategory() != null)
                .map(DepreciationParameters::getCategory)
                .collect(java.util.stream.Collectors.toSet());

        int count = 0;
        for (Asset asset : assets) {
            if (assetParamIds.contains(asset.getId())
                    || (asset.getCategory() != null && categoryParams.contains(asset.getCategory()))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get every depreciation posting (used by the dashboard filters/export, which need the
     * full dataset rather than only the most-recent slice).
     */
    @Transactional(readOnly = true)
    public List<DepreciationPosting> getAllPostings() {
        return postingRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /**
     * Get latest depreciation postings by period, most recent first
     */
    @Transactional(readOnly = true)
    public List<DepreciationPosting> getLatestPostingsByPeriod(int limit) {
        List<DepreciationPosting> allPostings = postingRepository.findAll();

        return allPostings.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get the most recent depreciation period
     */
    @Transactional(readOnly = true)
    public Optional<String> getLatestDepreciationPeriod() {
        List<DepreciationPosting> allPostings = postingRepository.findAll();

        if (allPostings.isEmpty()) {
            return Optional.empty();
        }

        return allPostings.stream()
                .map(DepreciationPosting::getDepreciationPeriod)
                .sorted()
                .reduce((first, second) -> second);
    }

    /**
     * Get dashboard.html data for the depreciation management page
     */
    @Transactional(readOnly = true)
    public DepreciationDashboardData getDashboardData(int postsToShow) {
        DepreciationSummary summary = getLatestSummary();
        List<DepreciationPosting> latestPostings = getLatestPostingsByPeriod(postsToShow);
        Optional<String> lastPeriod = getLatestDepreciationPeriod();

        LocalDateTime lastCalculatedAt = latestPostings.stream()
                .map(DepreciationPosting::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new DepreciationDashboardData(
                lastCalculatedAt,
                lastPeriod.orElse(null),
                latestPostings,
                summary
        );
    }
}
