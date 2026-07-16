package com.example.fams.depreciation;

import jakarta.persistence.*;
import com.example.fams.common.AppClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "depreciation_parameters")
public class DepreciationParameters {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Can be null for asset-specific parameters, or contain category name for category-wide
    @Column(length = 120)
    private String category;

    // Reference to specific asset ID (null if category-wide)
    @Column
    private Long assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepreciationMethod method;

    // Useful life in years
    @Column(nullable = false)
    private Integer usefulLifeYears;

    // Residual value (scrap value)
    @Column(precision = 19, scale = 2)
    private BigDecimal residualValue;

    // Effective from date
    @Column(nullable = false)
    private LocalDate effectiveFromDate;

    // Effective end date (null if current)
    @Column
    private LocalDate effectiveToDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Boolean isActive = true;

    @PrePersist
    void beforeCreate() {
        LocalDateTime now = AppClock.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = AppClock.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public DepreciationMethod getMethod() {
        return method;
    }

    public void setMethod(DepreciationMethod method) {
        this.method = method;
    }

    public Integer getUsefulLifeYears() {
        return usefulLifeYears;
    }

    public void setUsefulLifeYears(Integer usefulLifeYears) {
        this.usefulLifeYears = usefulLifeYears;
    }

    public BigDecimal getResidualValue() {
        return residualValue;
    }

    public void setResidualValue(BigDecimal residualValue) {
        this.residualValue = residualValue;
    }

    public LocalDate getEffectiveFromDate() {
        return effectiveFromDate;
    }

    public void setEffectiveFromDate(LocalDate effectiveFromDate) {
        this.effectiveFromDate = effectiveFromDate;
    }

    public LocalDate getEffectiveToDate() {
        return effectiveToDate;
    }

    public void setEffectiveToDate(LocalDate effectiveToDate) {
        this.effectiveToDate = effectiveToDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}

