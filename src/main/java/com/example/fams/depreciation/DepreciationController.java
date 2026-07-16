package com.example.fams.depreciation;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.fams.assets.Asset;
import com.example.fams.common.AppClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/depreciation")
public class DepreciationController {

    private final DepreciationService depreciationService;
    private final DepreciationParametersRepository parametersRepository;
    private final com.example.fams.assets.AssetRepository assetRepository;

    public DepreciationController(DepreciationService depreciationService,
                                 DepreciationParametersRepository parametersRepository,
                                 com.example.fams.assets.AssetRepository assetRepository) {
        this.depreciationService = depreciationService;
        this.parametersRepository = parametersRepository;
        this.assetRepository = assetRepository;
    }

    /**
     * Get depreciation summary
     */
    @GetMapping("/summary")
    public ResponseEntity<DepreciationSummary> getSummary() {
        try {
            DepreciationSummary summary = depreciationService.getLatestSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Get dashboard.html data (summary, latest postings, etc.)
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DepreciationDashboardData> getDashboardData(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            DepreciationDashboardData data = depreciationService.getDashboardData(limit);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Configure depreciation parameters for an asset
     */
    @PostMapping("/configure/asset/{assetId}")
    public ResponseEntity<Map<String, Object>> configureAssetDepreciation(
            @PathVariable Long assetId,
            @RequestBody DepreciationParameters parameters) {
        try {
            parameters.setAssetId(assetId);
            parameters.setCategory(null); // Asset-specific, not category-wide
            if (parameters.getEffectiveFromDate() == null) {
                parameters.setEffectiveFromDate(AppClock.today());
            }

            // Validate the parameters themselves
            DepreciationValidation.ValidationResult paramCheck = DepreciationValidation.validateParameters(parameters);
            if (!paramCheck.isValid()) {
                return badRequest(paramCheck.getErrorMessage());
            }

            // Validate residual value does not exceed the asset's cost
            Optional<Asset> asset = assetRepository.findById(assetId);
            if (asset.isEmpty()) {
                return badRequest("Asset not found: " + assetId);
            }
            BigDecimal cost = asset.get().getPurchaseCost();
            DepreciationValidation.ValidationResult residualCheck =
                    DepreciationValidation.validateResidualValue(cost, parameters.getResidualValue());
            if (!residualCheck.isValid()) {
                return badRequest(residualCheck.getErrorMessage());
            }

            DepreciationParameters saved = depreciationService.saveParameters(parameters);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Depreciation parameters saved successfully");
            response.put("data", saved);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error saving depreciation parameters: " + e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("error", message);
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Configure depreciation parameters for a category
     */
    @PostMapping("/configure/category")
    public ResponseEntity<Map<String, Object>> configureCategoryDepreciation(
            @RequestBody DepreciationParameters parameters) {
        try {
            parameters.setAssetId(null); // Category-wide, not asset-specific
            if (parameters.getEffectiveFromDate() == null) {
                parameters.setEffectiveFromDate(AppClock.today());
            }

            DepreciationValidation.ValidationResult paramCheck = DepreciationValidation.validateParameters(parameters);
            if (!paramCheck.isValid()) {
                return badRequest(paramCheck.getErrorMessage());
            }

            DepreciationParameters saved = depreciationService.saveParameters(parameters);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Category depreciation parameters saved successfully");
            response.put("data", saved);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error saving category parameters: " + e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get depreciation parameters for an asset
     */
    @GetMapping("/parameters/asset/{assetId}")
    public ResponseEntity<DepreciationParameters> getAssetParameters(
            @PathVariable Long assetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        try {
            if (asOfDate == null) {
                asOfDate = AppClock.today();
            }
            Optional<DepreciationParameters> parameters = depreciationService.getParametersForAsset(assetId, asOfDate);
            return parameters.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get category parameters
     */
    @GetMapping("/parameters/category/{category}")
    public ResponseEntity<List<DepreciationParameters>> getCategoryParameters(@PathVariable String category) {
        try {
            List<DepreciationParameters> parameters = depreciationService.getCategoryParameters(category);
            return ResponseEntity.ok(parameters);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update depreciation parameters with future effective date
     */
    @PutMapping("/parameters/{parametersId}/update-effective")
    public ResponseEntity<Map<String, Object>> updateParametersEffective(
            @PathVariable Long parametersId,
            @RequestBody DepreciationParameters newParameters) {
        try {
            depreciationService.updateParametersWithEffectiveDate(parametersId, newParameters);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Parameters updated with future effective date");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating parameters: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Run depreciation for a specific period
     */
    @PostMapping("/run-depreciation")
    public ResponseEntity<Map<String, Object>> runDepreciation(
            @RequestParam String depreciationPeriod,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEndDate) {
        try {
            // Validate the depreciation period format
            DepreciationValidation.ValidationResult periodCheck =
                    DepreciationValidation.validateDepreciationPeriod(depreciationPeriod);
            if (!periodCheck.isValid()) {
                return badRequest(periodCheck.getErrorMessage());
            }

            // Validate the period end date is not in the future
            DepreciationValidation.ValidationResult dateCheck =
                    DepreciationValidation.validatePeriodEndDate(periodEndDate);
            if (!dateCheck.isValid()) {
                return badRequest(dateCheck.getErrorMessage());
            }

            DepreciationRunResult result = depreciationService.runDepreciation(depreciationPeriod, periodEndDate);

            Map<String, Object> response = new HashMap<>();
            boolean fullySucceeded = "COMPLETED".equals(result.getStatus());
            response.put("success", fullySucceeded);
            response.put("period", result.getPeriod());
            response.put("runDate", result.getRunDate());
            response.put("status", result.getStatus());
            response.put("processedCount", result.getProcessedCount());
            response.put("successCount", result.getSuccessCount());
            response.put("failureCount", result.getFailureCount());

            if (!fullySucceeded) {
                response.put("error", result.getErrorMessage());
                if (result.getFailureCount() > 0) {
                    response.put("failedAssets", result.getFailedAssets());
                }
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error running depreciation: " + e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get depreciation report for a period
     */
    @GetMapping("/report/period/{period}")
    public ResponseEntity<DepreciationReport> getDepreciationReport(@PathVariable String period) {
        try {
            DepreciationReport report = depreciationService.getDepreciationReport(period);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get category breakdown for a period
     */
    @GetMapping("/report/category/{period}")
    public ResponseEntity<List<DepreciationCategoryReport>> getCategoryReport(@PathVariable String period) {
        try {
            List<DepreciationCategoryReport> report = depreciationService.getCategoryReport(period);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get department breakdown for a period
     */
    @GetMapping("/report/department/{period}")
    public ResponseEntity<List<DepreciationDepartmentReport>> getDepartmentReport(@PathVariable String period) {
        try {
            List<DepreciationDepartmentReport> report = depreciationService.getDepartmentReport(period);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get every depreciation posting (full dataset for the dashboard filters and export)
     */
    @GetMapping("/postings")
    public ResponseEntity<List<DepreciationPosting>> getAllPostings() {
        try {
            return ResponseEntity.ok(depreciationService.getAllPostings());
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get depreciation history for an asset
     */
    @GetMapping("/history/asset/{assetId}")
    public ResponseEntity<List<DepreciationPosting>> getAssetHistory(@PathVariable Long assetId) {
        try {
            List<DepreciationPosting> history = depreciationService.getDepreciationHistory(assetId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
