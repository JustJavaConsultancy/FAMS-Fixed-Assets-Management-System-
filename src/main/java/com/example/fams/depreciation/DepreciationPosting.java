package com.example.fams.depreciation;

import jakarta.persistence.*;
import com.example.fams.common.AppClock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "depreciation_postings")
public class DepreciationPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long assetId;

    @Column(nullable = false, length = 32)
    private String assetCode;

    @Column(nullable = false, length = 160)
    private String assetName;

    @Column(nullable = false, length = 120)
    private String category;

    @Column(nullable = false, length = 120)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepreciationMethod depreciationMethod;

    // Depreciation period (e.g., 2024-Q1, 2024-12)
    @Column(nullable = false, length = 32)
    private String depreciationPeriod;

    // The fiscal year for this entry
    @Column(nullable = false)
    private Integer fiscalYear;

    // Original cost at time of this depreciation
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal assetCost;

    // Accumulated depreciation at start of period
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal openingAccumulatedDepreciation;

    // Depreciation charged in this period
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal depreciationCharge;

    // Accumulated depreciation at end of period
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal closingAccumulatedDepreciation;

    // Book value at end of period
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal bookValue;

    // Residual/salvage value
    @Column(precision = 19, scale = 2)
    private BigDecimal residualValue;

    // Whether this asset is fully depreciated
    @Column(nullable = false)
    private Boolean fullyDepreciated = false;

    // Useful life in years at time of calculation
    @Column(nullable = false)
    private Integer usefulLifeYears;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void beforeCreate() {
        createdAt = AppClock.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getAssetName() {
        return assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public DepreciationMethod getDepreciationMethod() {
        return depreciationMethod;
    }

    public void setDepreciationMethod(DepreciationMethod depreciationMethod) {
        this.depreciationMethod = depreciationMethod;
    }

    public String getDepreciationPeriod() {
        return depreciationPeriod;
    }

    public void setDepreciationPeriod(String depreciationPeriod) {
        this.depreciationPeriod = depreciationPeriod;
    }

    public Integer getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(Integer fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public BigDecimal getAssetCost() {
        return assetCost;
    }

    public void setAssetCost(BigDecimal assetCost) {
        this.assetCost = assetCost;
    }

    public BigDecimal getOpeningAccumulatedDepreciation() {
        return openingAccumulatedDepreciation;
    }

    public void setOpeningAccumulatedDepreciation(BigDecimal openingAccumulatedDepreciation) {
        this.openingAccumulatedDepreciation = openingAccumulatedDepreciation;
    }

    public BigDecimal getDepreciationCharge() {
        return depreciationCharge;
    }

    public void setDepreciationCharge(BigDecimal depreciationCharge) {
        this.depreciationCharge = depreciationCharge;
    }

    public BigDecimal getClosingAccumulatedDepreciation() {
        return closingAccumulatedDepreciation;
    }

    public void setClosingAccumulatedDepreciation(BigDecimal closingAccumulatedDepreciation) {
        this.closingAccumulatedDepreciation = closingAccumulatedDepreciation;
    }

    public BigDecimal getBookValue() {
        return bookValue;
    }

    public void setBookValue(BigDecimal bookValue) {
        this.bookValue = bookValue;
    }

    public BigDecimal getResidualValue() {
        return residualValue;
    }

    public void setResidualValue(BigDecimal residualValue) {
        this.residualValue = residualValue;
    }

    public Boolean getFullyDepreciated() {
        return fullyDepreciated;
    }

    public void setFullyDepreciated(Boolean fullyDepreciated) {
        this.fullyDepreciated = fullyDepreciated;
    }

    public Integer getUsefulLifeYears() {
        return usefulLifeYears;
    }

    public void setUsefulLifeYears(Integer usefulLifeYears) {
        this.usefulLifeYears = usefulLifeYears;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

