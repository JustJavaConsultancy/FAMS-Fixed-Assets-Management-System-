package com.example.fams.depreciation;

import com.example.fams.common.AppClock;
import java.math.BigDecimal;
import java.time.LocalDate;

public class DepreciationValidation {

    public static class ValidationResult {
        private boolean valid;
        private String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
    }

    /**
     * Validate depreciation parameters
     */
    public static ValidationResult validateParameters(DepreciationParameters params) {
        if (params.getMethod() == null) {
            return ValidationResult.error("Depreciation method must be selected");
        }

        if (params.getUsefulLifeYears() == null || params.getUsefulLifeYears() <= 0) {
            return ValidationResult.error("Useful life must be greater than 0 years");
        }

        if (params.getUsefulLifeYears() > 100) {
            return ValidationResult.error("Useful life cannot exceed 100 years");
        }

        if (params.getResidualValue() != null && params.getResidualValue().signum() < 0) {
            return ValidationResult.error("Residual value cannot be negative");
        }

        if (params.getEffectiveFromDate() == null) {
            return ValidationResult.error("Effective from date is required");
        }

        return ValidationResult.ok();
    }

    /**
     * Validate asset has required fields for depreciation
     */
    public static ValidationResult validateAssetForDepreciation(Long assetId, String assetStatus, BigDecimal purchaseCost) {
        if (assetId == null || assetId <= 0) {
            return ValidationResult.error("Asset ID is invalid");
        }

        if (assetStatus != null && (assetStatus.equalsIgnoreCase("Disposed") || assetStatus.equalsIgnoreCase("Scrapped"))) {
            return ValidationResult.error("Cannot depreciate disposed or scrapped assets");
        }

        if (purchaseCost == null || purchaseCost.signum() <= 0) {
            return ValidationResult.error("Asset purchase cost must be recorded and greater than zero");
        }

        return ValidationResult.ok();
    }

    /**
     * Validate period end date
     */
    public static ValidationResult validatePeriodEndDate(LocalDate periodEndDate) {
        if (periodEndDate == null) {
            return ValidationResult.error("Period end date is required");
        }

        if (periodEndDate.isAfter(AppClock.today().plusDays(1))) {
            return ValidationResult.error("Period end date cannot be in the future");
        }

        return ValidationResult.ok();
    }

    /**
     * Validate depreciation period format
     */
    public static ValidationResult validateDepreciationPeriod(String period) {
        if (period == null || period.isBlank()) {
            return ValidationResult.error("Depreciation period cannot be empty");
        }

        // Expected formats: YYYY-MM, YYYY-Q1, YYYY-Q2, YYYY-Q3, YYYY-Q4
        if (!period.matches("^\\d{4}-(\\d{2}|Q[1-4])$")) {
            return ValidationResult.error("Invalid period format. Use YYYY-MM or YYYY-Q1/Q2/Q3/Q4");
        }

        return ValidationResult.ok();
    }

    /**
     * Validate residual value doesn't exceed cost
     */
    public static ValidationResult validateResidualValue(BigDecimal cost, BigDecimal residualValue) {
        if (cost == null || residualValue == null) {
            return ValidationResult.ok();
        }

        if (residualValue.compareTo(cost) > 0) {
            return ValidationResult.error("Residual value cannot exceed asset cost");
        }

        return ValidationResult.ok();
    }
}

