package com.example.fams.lifecycle;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class LifecycleWorkflowForm {

    @NotNull(message = "Asset is required.")
    private Long assetId;

    @NotNull(message = "Workflow type is required.")
    private LifecycleWorkflowType type;

    @NotNull(message = "Requested effective date is required.")
    private LocalDate requestedEffectiveDate;

    @Size(max = 160)
    private String toEmployee;

    @Size(max = 160)
    private String toDepartment;

    @Size(max = 160)
    private String toBranch;

    @Size(max = 160)
    private String toLocation;

    @Size(max = 80)
    private String disposalMethod;

    @DecimalMin(value = "0.00", message = "Disposal proceeds cannot be negative.")
    private BigDecimal disposalProceeds = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", message = "Accumulated depreciation cannot be negative.")
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", message = "Net book value cannot be negative.")
    private BigDecimal netBookValue = BigDecimal.ZERO;

    @Size(max = 2000)
    private String reason;

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
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
}
