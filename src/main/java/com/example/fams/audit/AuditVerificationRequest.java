package com.example.fams.audit;

public class AuditVerificationRequest {

    private Long assetId;
    private String scannedCode;
    private AuditVerificationStatus resultStatus;
    private String actualLocation;
    private String conditionNotes;

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
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

    public String getActualLocation() {
        return actualLocation;
    }

    public void setActualLocation(String actualLocation) {
        this.actualLocation = actualLocation;
    }

    public String getConditionNotes() {
        return conditionNotes;
    }

    public void setConditionNotes(String conditionNotes) {
        this.conditionNotes = conditionNotes;
    }
}
