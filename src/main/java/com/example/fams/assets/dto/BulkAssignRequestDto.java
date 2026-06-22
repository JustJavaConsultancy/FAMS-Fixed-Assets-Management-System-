package com.example.fams.assets.dto;

import java.time.LocalDate;
import java.util.List;

public class BulkAssignRequestDto {
    private List<Long> assetIds;
    private String assignedTo;
    private String department;
    private LocalDate assignmentDate;
    private String notes;
    private boolean selectAllMatching = false;

    public List<Long> getAssetIds() {
        return assetIds;
    }

    public void setAssetIds(List<Long> assetIds) {
        this.assetIds = assetIds;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public LocalDate getAssignmentDate() {
        return assignmentDate;
    }

    public void setAssignmentDate(LocalDate assignmentDate) {
        this.assignmentDate = assignmentDate;
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


