package com.example.fams.lifecycle;

public class BulkLifecycleItemResult {
    private final Long assetId;
    private final String assetCode;
    private final boolean success;
    private final String message;

    public BulkLifecycleItemResult(Long assetId, String assetCode, boolean success, String message) {
        this.assetId = assetId;
        this.assetCode = assetCode;
        this.success = success;
        this.message = message;
    }

    public Long getAssetId() {
        return assetId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
