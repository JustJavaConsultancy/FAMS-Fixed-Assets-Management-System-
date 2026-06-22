package com.example.fams.assets.dto;

import java.time.LocalDate;
import java.util.List;

public class BulkTransferRequestDto {
    private List<Long> assetIds;
    private String transferToLocation;
    private String transferToDepartment;
    private LocalDate transferDate;
    private String transferReason;
    private String notes;
    private boolean selectAllMatching = false;

    public List<Long> getAssetIds() {
        return assetIds;
    }

    public void setAssetIds(List<Long> assetIds) {
        this.assetIds = assetIds;
    }

    public String getTransferToLocation() {
        return transferToLocation;
    }

    public void setTransferToLocation(String transferToLocation) {
        this.transferToLocation = transferToLocation;
    }

    public String getTransferToDepartment() {
        return transferToDepartment;
    }

    public void setTransferToDepartment(String transferToDepartment) {
        this.transferToDepartment = transferToDepartment;
    }

    public LocalDate getTransferDate() {
        return transferDate;
    }

    public void setTransferDate(LocalDate transferDate) {
        this.transferDate = transferDate;
    }

    public String getTransferReason() {
        return transferReason;
    }

    public void setTransferReason(String transferReason) {
        this.transferReason = transferReason;
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


