package com.example.fams.depreciation;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public DepreciationController(DepreciationService depreciationService,
                                 DepreciationParametersRepository parametersRepository) {
        this.depreciationService = depreciationService;
        this.parametersRepository = parametersRepository;
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

    /**
     * Configure depreciation parameters for a category
     */
    @PostMapping("/configure/category")
    public ResponseEntity<Map<String, Object>> configureCategoryDepreciation(
            @RequestBody DepreciationParameters parameters) {
        try {
            parameters.setAssetId(null); // Category-wide, not asset-specific
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
                asOfDate = LocalDate.now();
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
            DepreciationRunResult result = depreciationService.runDepreciation(depreciationPeriod, periodEndDate);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.getStatus().equals("COMPLETED"));
            response.put("period", result.getPeriod());
            response.put("runDate", result.getRunDate());
            response.put("status", result.getStatus());
            response.put("processedCount", result.getProcessedCount());
            response.put("successCount", result.getSuccessCount());
            response.put("failureCount", result.getFailureCount());

            if (result.getStatus().equals("FAILED")) {
                response.put("error", result.getErrorMessage());
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
