package com.example.fams.lifecycle;

import com.example.fams.assets.Asset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_lifecycle_workflows")
public class AssetLifecycleWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LifecycleWorkflowType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LifecycleWorkflowStatus status;

    @Column(length = 80)
    private String processInstanceId;

    @Column(nullable = false)
    private LocalDate requestedEffectiveDate;

    @Column(nullable = false, length = 160)
    private String requestedBy;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Column(length = 160)
    private String fromEmployee;

    @Column(length = 160)
    private String toEmployee;

    @Column(length = 160)
    private String fromDepartment;

    @Column(length = 160)
    private String toDepartment;

    @Column(length = 160)
    private String fromBranch;

    @Column(length = 160)
    private String toBranch;

    @Column(length = 160)
    private String fromLocation;

    @Column(length = 160)
    private String toLocation;

    @Column(length = 80)
    private String disposalMethod;

    @Column(precision = 19, scale = 2)
    private BigDecimal disposalProceeds;

    @Column(precision = 19, scale = 2)
    private BigDecimal accumulatedDepreciation;

    @Column(precision = 19, scale = 2)
    private BigDecimal netBookValue;

    @Column(precision = 19, scale = 2)
    private BigDecimal financialImpact;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void beforeCreate() {
        LocalDateTime now = LocalDateTime.now();
        requestedAt = now;
        updatedAt = now;
        if (status == null) {
            status = LifecycleWorkflowStatus.PENDING_APPROVAL;
        }
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public LifecycleWorkflowType getType() {
        return type;
    }

    public void setType(LifecycleWorkflowType type) {
        this.type = type;
    }

    public LifecycleWorkflowStatus getStatus() {
        return status;
    }

    public void setStatus(LifecycleWorkflowStatus status) {
        this.status = status;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public LocalDate getRequestedEffectiveDate() {
        return requestedEffectiveDate;
    }

    public void setRequestedEffectiveDate(LocalDate requestedEffectiveDate) {
        this.requestedEffectiveDate = requestedEffectiveDate;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public String getFromEmployee() {
        return fromEmployee;
    }

    public void setFromEmployee(String fromEmployee) {
        this.fromEmployee = fromEmployee;
    }

    public String getToEmployee() {
        return toEmployee;
    }

    public void setToEmployee(String toEmployee) {
        this.toEmployee = toEmployee;
    }

    public String getFromDepartment() {
        return fromDepartment;
    }

    public void setFromDepartment(String fromDepartment) {
        this.fromDepartment = fromDepartment;
    }

    public String getToDepartment() {
        return toDepartment;
    }

    public void setToDepartment(String toDepartment) {
        this.toDepartment = toDepartment;
    }

    public String getFromBranch() {
        return fromBranch;
    }

    public void setFromBranch(String fromBranch) {
        this.fromBranch = fromBranch;
    }

    public String getToBranch() {
        return toBranch;
    }

    public void setToBranch(String toBranch) {
        this.toBranch = toBranch;
    }

    public String getFromLocation() {
        return fromLocation;
    }

    public void setFromLocation(String fromLocation) {
        this.fromLocation = fromLocation;
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

    public BigDecimal getFinancialImpact() {
        return financialImpact;
    }

    public void setFinancialImpact(BigDecimal financialImpact) {
        this.financialImpact = financialImpact;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
