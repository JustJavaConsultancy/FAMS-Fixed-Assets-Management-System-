package com.example.fams.assets.dto;

import java.time.LocalDate;
import java.util.List;

public class BulkRetireRequestDto {
    private List<Long> assetIds;
    private String retirementReason;
    private String disposalMethod;
    private LocalDate retirementDate;
    private String notes;
    private boolean selectAllMatching = false;

    public List<Long> getAssetIds() {
        return assetIds;
    }

    public void setAssetIds(List<Long> assetIds) {
        this.assetIds = assetIds;
    }

    public String getRetirementReason() {
        return retirementReason;
    }

    public void setRetirementReason(String retirementReason) {
        this.retirementReason = retirementReason;
    }

    public String getDisposalMethod() {
        return disposalMethod;
    }

    public void setDisposalMethod(String disposalMethod) {
        this.disposalMethod = disposalMethod;
    }

    public LocalDate getRetirementDate() {
        return retirementDate;
    }

    public void setRetirementDate(LocalDate retirementDate) {
        this.retirementDate = retirementDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isSelectAllMatching() {
        return selectAllMatching;
    }

    public void setSelectAllMatching(boolean selectAllMatching) {
        this.selectAllMatching = selectAllMatching;
    }
}


