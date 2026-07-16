package com.example.fams.depreciation;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service for calculating depreciation using different methods.
 * Supports: Straight-Line, Reducing Balance, and Double Declining Balance
 */
@Service
public class DepreciationCalculationService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Calculate annual depreciation based on the method
     */
    public BigDecimal calculateAnnualDepreciation(
            BigDecimal assetCost,
            BigDecimal residualValue,
            Integer usefulLifeYears,
            DepreciationMethod method,
            Integer yearNumber,
            BigDecimal accumulatedDepreciation) {

        if (assetCost == null || usefulLifeYears == null || usefulLifeYears <= 0) {
            return BigDecimal.ZERO;
        }

        if (residualValue == null) {
            residualValue = BigDecimal.ZERO;
        }

        // Check if asset is fully depreciated
        if (isFullyDepreciated(assetCost, residualValue, accumulatedDepreciation)) {
            return BigDecimal.ZERO;
        }

        return switch (method) {
            case STRAIGHT_LINE -> calculateStraightLine(assetCost, residualValue, usefulLifeYears);
            case REDUCING_BALANCE -> calculateReducingBalance(assetCost, residualValue, usefulLifeYears, accumulatedDepreciation, yearNumber);
            case DOUBLE_DECLINING_BALANCE -> calculateDoubleDecliningBalance(assetCost, residualValue, usefulLifeYears, accumulatedDepreciation, yearNumber);
        };
    }

    /**
     * Straight-Line Depreciation: (Cost - Residual Value) / Useful Life
     * Same amount each year
     */
    private BigDecimal calculateStraightLine(BigDecimal assetCost, BigDecimal residualValue, Integer usefulLifeYears) {
        BigDecimal depreciableAmount = assetCost.subtract(residualValue);
        BigDecimal yearsDecimal = new BigDecimal(usefulLifeYears);
        return depreciableAmount.divide(yearsDecimal, SCALE, ROUNDING_MODE);
    }

    /**
     * Reducing Balance / Declining Balance:
     * Uses a fixed rate applied to the book value (original cost - accumulated depreciation)
     * Rate = 1 - (Residual / Cost) ^ (1/Years)
     */
    private BigDecimal calculateReducingBalance(
            BigDecimal assetCost,
            BigDecimal residualValue,
            Integer usefulLifeYears,
            BigDecimal accumulatedDepreciation,
            Integer yearNumber) {

        // Declining-balance rate: 1 - (residual / cost) ^ (1 / usefulLife)
        // (with residualValue already defaulted to ZERO by the caller)
        BigDecimal base = residualValue.divide(assetCost, SCALE + 4, ROUNDING_MODE);
        double exponent = 1.0 / usefulLifeYears;
        BigDecimal basePow = BigDecimal.valueOf(Math.pow(base.doubleValue(), exponent));
        BigDecimal rate = BigDecimal.ONE.subtract(basePow).setScale(SCALE + 2, ROUNDING_MODE);
        if (rate.signum() < 0) {
            rate = BigDecimal.ZERO;
        }

        // Current book value
        BigDecimal bookValue = assetCost.subtract(accumulatedDepreciation);

        // Calculate depreciation for this year
        BigDecimal depreciation = bookValue.multiply(rate).setScale(SCALE, ROUNDING_MODE);

        // Ensure we don't depreciate below residual value
        BigDecimal depreciableLimit = assetCost.subtract(residualValue);
        if (accumulatedDepreciation.add(depreciation).compareTo(depreciableLimit) > 0) {
            depreciation = depreciableLimit.subtract(accumulatedDepreciation);
        }

        return depreciation.max(BigDecimal.ZERO);
    }

    /**
     * Double Declining Balance (DDB):
     * Uses double the straight-line rate applied to the declining book value
     * Rate = 2 / Useful Life
     */
    private BigDecimal calculateDoubleDecliningBalance(
            BigDecimal assetCost,
            BigDecimal residualValue,
            Integer usefulLifeYears,
            BigDecimal accumulatedDepreciation,
            Integer yearNumber) {

        // Double declining rate: 2 / useful life
        BigDecimal rate = new BigDecimal(2).divide(new BigDecimal(usefulLifeYears), SCALE + 2, ROUNDING_MODE);

        // Current book value
        BigDecimal bookValue = assetCost.subtract(accumulatedDepreciation);

        // Calculate depreciation for this year using DDB rate
        BigDecimal depreciation = bookValue.multiply(rate).setScale(SCALE, ROUNDING_MODE);

        // Ensure we don't depreciate below residual value
        BigDecimal depreciableLimit = assetCost.subtract(residualValue);
        if (accumulatedDepreciation.add(depreciation).compareTo(depreciableLimit) > 0) {
            depreciation = depreciableLimit.subtract(accumulatedDepreciation);
        }

        return depreciation.max(BigDecimal.ZERO);
    }

    /**
     * Check if an asset is fully depreciated
     */
    public boolean isFullyDepreciated(BigDecimal assetCost, BigDecimal residualValue, BigDecimal accumulatedDepreciation) {
        if (assetCost == null || accumulatedDepreciation == null) {
            return false;
        }
        if (residualValue == null) {
            residualValue = BigDecimal.ZERO;
        }
        BigDecimal depreciableAmount = assetCost.subtract(residualValue);
        return accumulatedDepreciation.compareTo(depreciableAmount) >= 0;
    }

    /**
     * Calculate monthly depreciation (annual / 12)
     */
    public BigDecimal calculateMonthlyDepreciation(BigDecimal annualDepreciation) {
        if (annualDepreciation == null) {
            return BigDecimal.ZERO;
        }
        return annualDepreciation.divide(new BigDecimal(12), SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate depreciation for partial year
     */
    public BigDecimal calculatePartialYearDepreciation(BigDecimal annualDepreciation, Integer months) {
        if (annualDepreciation == null || months == null || months <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal monthlyRate = annualDepreciation.divide(new BigDecimal(12), SCALE + 2, ROUNDING_MODE);
        return monthlyRate.multiply(new BigDecimal(months)).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate book value
     */
    public BigDecimal calculateBookValue(BigDecimal assetCost, BigDecimal accumulatedDepreciation) {
        if (assetCost == null) {
            return BigDecimal.ZERO;
        }
        if (accumulatedDepreciation == null) {
            accumulatedDepreciation = BigDecimal.ZERO;
        }
        return assetCost.subtract(accumulatedDepreciation).max(BigDecimal.ZERO);
    }

    /**
     * Prorate an annual depreciation charge down to the fraction of the year the period covers.
     * <ul>
     *   <li>monthly  → 1/12 of the annual charge</li>
     *   <li>quarterly → 1/4 of the annual charge</li>
     *   <li>annual   → the full annual charge</li>
     * </ul>
     * Period type is derived from the depreciation period code by {@code DepreciationService};
     * the values accepted here are "monthly", "quarterly", and "annual" (anything else → annual).
     */
    public BigDecimal prorateCharge(BigDecimal annualCharge, String periodType) {
        if (annualCharge == null) {
            return BigDecimal.ZERO;
        }
        if (periodType == null) {
            return annualCharge.setScale(SCALE, ROUNDING_MODE);
        }
        return switch (periodType.toLowerCase()) {
            case "monthly" -> annualCharge.divide(new BigDecimal(12), SCALE + 2, ROUNDING_MODE).setScale(SCALE, ROUNDING_MODE);
            case "quarterly" -> annualCharge.divide(new BigDecimal(4), SCALE + 2, ROUNDING_MODE).setScale(SCALE, ROUNDING_MODE);
            default -> annualCharge.setScale(SCALE, ROUNDING_MODE);
        };
    }
}

