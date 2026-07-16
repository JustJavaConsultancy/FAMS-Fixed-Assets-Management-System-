package com.example.fams.lifecycle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Bulk counterpart of {@link LifecycleWorkflowForm}: the same movement/disposal fields
 * are captured once and applied to every selected asset, each as its own approval workflow.
 */
public class BulkLifecycleRequestDto {

    private List<Long> assetIds;
    private LifecycleWorkflowType type;
    private LocalDate requestedEffectiveDate;
    private String toEmployee;
    private String toDepartment;
    private String toBranch;
    private String toLocation;
    private String disposalMethod;
    private BigDecimal disposalProceeds = BigDecimal.ZERO;
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;
    private BigDecimal netBookValue = BigDecimal.ZERO;
    private String reason;
    private boolean selectAllMatching = false;

    public List<Long> getAssetIds() {
        return assetIds;
    }

    public void setAssetIds(List<Long> assetIds) {
        this.assetIds = assetIds;
    }

    public LifecycleWorkflowType getType() {
        return type;
    }

    public void setType(LifecycleWorkflowType type) {
        this.type = type;
    }

    public LocalDate getRequestedEffectiveDate() {
        return requestedEffectiveDate;
    }

    public void setRequestedEffectiveDate(LocalDate requestedEffectiveDate) {
        this.requestedEffectiveDate = requestedEffectiveDate;
    }

    public String getToEmployee() {
        return toEmployee;
    }

    public void setToEmployee(String toEmployee) {
        this.toEmployee = toEmployee;
    }

    public String getToDepartment() {
        return toDepartment;
    }

    public void setToDepartment(String toDepartment) {
        this.toDepartment = toDepartment;
    }

    public String getToBranch() {
        return toBranch;
    }

    public void setToBranch(String toBranch) {
        this.toBranch = toBranch;
    }

    public String getToLocation() {
        return toLocation;
    }

    public void setToLocation(String toLocation) {
        this.toLocation = toLocation;
    }

    public String getDisposalMethod() {
        return disposalMethod;
    }

    public void setDisposalMethod(String disposalMethod) {
        this.disposalMethod = disposalMethod;
    }

    public BigDecimal getDisposalProceeds() {
        return disposalProceeds;
    }

    public void setDisposalProceeds(BigDecimal disposalProceeds) {
        this.disposalProceeds = disposalProceeds;
    }

    public BigDecimal getAccumulatedDepreciation() {
        return accumulatedDepreciation;
    }

    public void setAccumulatedDepreciation(BigDecimal accumulatedDepreciation) {
        this.accumulatedDepreciation = accumulatedDepreciation;
    }

    public BigDecimal getNetBookValue() {
        return netBookValue;
    }

    public void setNetBookValue(BigDecimal netBookValue) {
        this.netBookValue = netBookValue;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isSelectAllMatching() {
        return selectAllMatching;
    }

    public void setSelectAllMatching(boolean selectAllMatching) {
        this.selectAllMatching = selectAllMatching;
    }
}
