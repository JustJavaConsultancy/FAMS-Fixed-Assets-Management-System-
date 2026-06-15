package com.example.fams.maintenance;

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
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_tasks")
public class MaintenanceTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private MaintenanceSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(length = 120)
    private String assetCategory;

    @Column(nullable = false, length = 120)
    private String serviceType;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, length = 160)
    private String responsibleParty;

    @Column(nullable = false, length = 80)
    private String responsibleRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MaintenanceStatus status;

    @Column(nullable = false)
    private boolean eventPublished;

    private LocalDateTime eventPublishedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void beforeCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = MaintenanceStatus.DUE;
        }
    }

    public Long getId() {
        return id;
    }

    public MaintenanceSchedule getSchedule() {
        return schedule;
    }

    public void setSchedule(MaintenanceSchedule schedule) {
        this.schedule = schedule;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public String getAssetCategory() {
        return assetCategory;
    }

    public void setAssetCategory(String assetCategory) {
        this.assetCategory = assetCategory;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getResponsibleParty() {
        return responsibleParty;
    }

    public void setResponsibleParty(String responsibleParty) {
        this.responsibleParty = responsibleParty;
    }

    public String getResponsibleRole() {
        return responsibleRole;
    }

    public void setResponsibleRole(String responsibleRole) {
        this.responsibleRole = responsibleRole;
    }

    public MaintenanceStatus getStatus() {
        return status;
    }

    public void setStatus(MaintenanceStatus status) {
        this.status = status;
    }

    public boolean isEventPublished() {
        return eventPublished;
    }

    public void setEventPublished(boolean eventPublished) {
        this.eventPublished = eventPublished;
    }

    public LocalDateTime getEventPublishedAt() {
        return eventPublishedAt;
    }

    public void setEventPublishedAt(LocalDateTime eventPublishedAt) {
        this.eventPublishedAt = eventPublishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
