package com.example.fams.depreciation;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
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
            parameters.setEffectiveFromDate(LocalDate.now());
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
            current.setIsActive(newParameters.getEffectiveFromDate().isAfter(LocalDate.now()));
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
     * Run depreciation for a specific period (e.g., "2024-12" for December 2024)
     */
    @Transactional
    public DepreciationRunResult runDepreciation(String depreciationPeriod, LocalDate periodEndDate) {
        DepreciationRunResult result = new DepreciationRunResult();
        result.setPeriod(depreciationPeriod);
        result.setRunDate(LocalDate.now());

        try {
            // Get all assets
            List<Asset> assets = assetRepository.findAll();
            Integer fiscalYear = periodEndDate.getYear();

            for (Asset asset : assets) {
                try {
                    DepreciationPosting posting = calculateDepreciationForAsset(asset, depreciationPeriod, periodEndDate, fiscalYear);
                    if (posting != null) {
                        postingRepository.save(posting);
                        result.addSuccessfulAsset(asset.getAssetCode());
                    }
                } catch (Exception e) {
                    result.addFailedAsset(asset.getAssetCode(), e.getMessage());
                }
            }

            result.setStatus("COMPLETED");
        } catch (Exception e) {
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * Calculate depreciation for a single asset
     */
    @Transactional(readOnly = true)
    public DepreciationPosting calculateDepreciationForAsset(Asset asset, String depreciationPeriod, LocalDate periodEndDate, Integer fiscalYear) {
        // Get depreciation parameters for this asset as of the period end date
        Optional<DepreciationParameters> parameters = getParametersForAsset(asset.getId(), periodEndDate);

        if (parameters.isEmpty()) {
            // No depreciation parameters configured
            return null;
        }

        DepreciationParameters params = parameters.get();

        // Check if asset is disposed or not active
        if (!isAssetDepreciable(asset)) {
            return null;
        }

        DepreciationPosting posting = new DepreciationPosting();
        posting.setAssetId(asset.getId());
        posting.setAssetCode(asset.getAssetCode());
        posting.setAssetName(asset.getName());
        posting.setCategory(asset.getCategory());
        posting.setDepartment(asset.getDepartment());
        posting.setDepreciationMethod(params.getMethod());
        posting.setDepreciationPeriod(depreciationPeriod);
        posting.setFiscalYear(fiscalYear);
        posting.setAssetCost(asset.getPurchaseCost());
        posting.setUsefulLifeYears(params.getUsefulLifeYears());
        posting.setResidualValue(params.getResidualValue());

        // Get previous posting to get opening accumulated depreciation
        DepreciationPosting previousPosting = postingRepository.findFirstByAssetIdOrderByDepreciationPeriodDesc(asset.getId());
        BigDecimal openingAccumulated = previousPosting != null ? previousPosting.getClosingAccumulatedDepreciation() : BigDecimal.ZERO;
        posting.setOpeningAccumulatedDepreciation(openingAccumulated);

        // Calculate depreciation charge
        BigDecimal depreciationCharge = calculationService.calculateAnnualDepreciation(
                asset.getPurchaseCost(),
                params.getResidualValue(),
                params.getUsefulLifeYears(),
                params.getMethod(),
                1,
                openingAccumulated
        );

        posting.setDepreciationCharge(depreciationCharge);

        // Calculate closing accumulated depreciation
        BigDecimal closingAccumulated = openingAccumulated.add(depreciationCharge);
        posting.setClosingAccumulatedDepreciation(closingAccumulated);

        // Calculate book value
        BigDecimal bookValue = calculationService.calculateBookValue(asset.getPurchaseCost(), closingAccumulated);
        posting.setBookValue(bookValue);

        // Check if fully depreciated
        boolean fullyDep = calculationService.isFullyDepreciated(asset.getPurchaseCost(), params.getResidualValue(), closingAccumulated);
        posting.setFullyDepreciated(fullyDep);

        return posting;
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
        summary.setFullyDepreciatedCount(fullyDepreciatedCount);

        return summary;
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
