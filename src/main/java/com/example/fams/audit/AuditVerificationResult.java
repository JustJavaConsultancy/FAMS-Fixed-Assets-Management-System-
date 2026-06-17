package com.example.fams.audit;

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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "audit_verification_results",
        uniqueConstraints = @UniqueConstraint(name = "uk_audit_result_session_asset", columnNames = {"session_id", "asset_id"})
)
public class AuditVerificationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AuditSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false, length = 80)
    private String scannedCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditVerificationStatus resultStatus;

    @Column(nullable = false, length = 260)
    private String expectedLocation;

    @Column(length = 260)
    private String actualLocation;

    @Column(nullable = false, length = 80)
    private String registerStatus;

    @Column(length = 80)
    private String discrepancyType;

    @Column(columnDefinition = "text")
    private String conditionNotes;

    @Column(nullable = false)
    private LocalDateTime verifiedAt;

    @PrePersist
    @PreUpdate
    void beforeSave() {
        verifiedAt = LocalDateTime.now();
        discrepancyType = resolveDiscrepancyType();
    }

    private String resolveDiscrepancyType() {
        if (resultStatus == null || resultStatus == AuditVerificationStatus.FOUND) {
            return null;
        }
        return switch (resultStatus) {
            case MISSING -> "Missing";
            case DAMAGED -> "Damaged";
            case LOCATION_MISMATCH -> "Location mismatch";
            case FOUND -> null;
        };
    }

    public Long getId() {
        return id;
    }

    public AuditSession getSession() {
        return session;
    }

    public void setSession(AuditSession session) {
        this.session = session;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public String getScannedCode() {
        return scannedCode;
    }

    public void setScannedCode(String scannedCode) {
        this.scannedCode = scannedCode;
    }

    public AuditVerificationStatus getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(AuditVerificationStatus resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getExpectedLocation() {
        return expectedLocation;
    }

    public void setExpectedLocation(String expectedLocation) {
        this.expectedLocation = expectedLocation;
    }

    public String getActualLocation() {
        return actualLocation;
    }

    public void setActualLocation(String actualLocation) {
        this.actualLocation = actualLocation;
    }

    public String getRegisterStatus() {
        return registerStatus;
    }

    public void setRegisterStatus(String registerStatus) {
        this.registerStatus = registerStatus;
    }

    public String getDiscrepancyType() {
        return discrepancyType;
    }

    public String getConditionNotes() {
        return conditionNotes;
    }

    public void setConditionNotes(String conditionNotes) {
        this.conditionNotes = conditionNotes;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }
}
