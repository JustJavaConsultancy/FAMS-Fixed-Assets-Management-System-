package com.example.fams.assets.dto;

public class BulkOperationItemResultDto {
    private Long assetId;
    private String assetCode;
    private boolean success;
    private String message;

    public BulkOperationItemResultDto() {}

    public BulkOperationItemResultDto(Long assetId, String assetCode, boolean success, String message) {
        this.assetId = assetId;
        this.assetCode = assetCode;
        this.success = success;
        this.message = message;
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

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

