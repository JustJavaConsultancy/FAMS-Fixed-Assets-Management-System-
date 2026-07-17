package com.example.fams.assets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_checkouts")
public class AssetCheckout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false, length = 120)
    private String checkedOutBy;

    @Column(nullable = false)
    private LocalDate checkoutDate;

    @Column(nullable = false)
    private LocalDate dueReturnDate;

    @Column(length = 500)
    private String purpose;

    @Column(length = 120)
    private String conditionBeforeCheckout;

    @Column(length = 120)
    private String conditionAfterReturn;

    @Column(nullable = false, length = 40)
    private String status; // Pending Approval, Approved, Checked Out, Returned, Verified, Rejected, Available

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Request / approval details (employee requests checkout, asset manager approves)
    @Column(length = 120)
    private String requestedBy;

    @Column
    private LocalDateTime requestedAt;

    @Column(length = 120)
    private String approvedBy;

    @Column
    private LocalDateTime approvedAt;

    @Column(length = 500)
    private String rejectionReason;

    // Return details (populated when asset is returned)
    @Column
    private LocalDate returnDate;

    @Column(length = 500)
    private String returnNotes;

    @Column(length = 120)
    private String verifiedBy;

    @Column
    private LocalDateTime verifiedAt;

    @PrePersist
    void beforeCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null || status.isBlank()) {
            status = "Pending Approval";
        }
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public String getCheckedOutBy() {
        return checkedOutBy;
    }

    public void setCheckedOutBy(String checkedOutBy) {
        this.checkedOutBy = checkedOutBy;
    }

    public LocalDate getCheckoutDate() {
        return checkoutDate;
    }

    public void setCheckoutDate(LocalDate checkoutDate) {
        this.checkoutDate = checkoutDate;
    }

    public LocalDate getDueReturnDate() {
        return dueReturnDate;
    }

    public void setDueReturnDate(LocalDate dueReturnDate) {
        this.dueReturnDate = dueReturnDate;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getConditionBeforeCheckout() {
        return conditionBeforeCheckout;
    }

    public void setConditionBeforeCheckout(String conditionBeforeCheckout) {
        this.conditionBeforeCheckout = conditionBeforeCheckout;
    }

    public String getConditionAfterReturn() {
        return conditionAfterReturn;
    }

    public void setConditionAfterReturn(String conditionAfterReturn) {
        this.conditionAfterReturn = conditionAfterReturn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public String getReturnNotes() {
        return returnNotes;
    }

    public void setReturnNotes(String returnNotes) {
        this.returnNotes = returnNotes;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
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

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}

