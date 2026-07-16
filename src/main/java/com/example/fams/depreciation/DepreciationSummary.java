package com.example.fams.depreciation;

import java.math.BigDecimal;

public class DepreciationSummary {
    private BigDecimal totalOriginalCost;
    private BigDecimal totalAccumulatedDepreciation;
    private BigDecimal totalBookValue;
    private int assetCount;
    private int configuredAssetCount;
    private int fullyDepreciatedCount;

    public BigDecimal getTotalOriginalCost() {
        return totalOriginalCost;
    }

    public void setTotalOriginalCost(BigDecimal totalOriginalCost) {
        this.totalOriginalCost = totalOriginalCost;
    }

    public BigDecimal getTotalAccumulatedDepreciation() {
        return totalAccumulatedDepreciation;
    }

    public void setTotalAccumulatedDepreciation(BigDecimal totalAccumulatedDepreciation) {
        this.totalAccumulatedDepreciation = totalAccumulatedDepreciation;
    }

    public BigDecimal getTotalBookValue() {
        return totalBookValue;
    }

    public void setTotalBookValue(BigDecimal totalBookValue) {
        this.totalBookValue = totalBookValue;
    }

    public int getAssetCount() {
        return assetCount;
    }

    public void setAssetCount(int assetCount) {
        this.assetCount = assetCount;
    }

    public int getConfiguredAssetCount() {
        return configuredAssetCount;
    }

    public void setConfiguredAssetCount(int configuredAssetCount) {
        this.configuredAssetCount = configuredAssetCount;
    }

    public int getFullyDepreciatedCount() {
        return fullyDepreciatedCount;
    }

    public void setFullyDepreciatedCount(int fullyDepreciatedCount) {
        this.fullyDepreciatedCount = fullyDepreciatedCount;
    }

    public BigDecimal getRecoveryPotentialPercentage() {
        if (totalOriginalCost == null || totalOriginalCost.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return totalBookValue.divide(totalOriginalCost, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal(100));
    }

    public BigDecimal getTotalDepreciationPercentage() {
        if (totalOriginalCost == null || totalOriginalCost.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return totalAccumulatedDepreciation.divide(totalOriginalCost, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal(100));
    }
}

