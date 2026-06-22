package com.example.fams.assets;

import java.util.Collections;
import java.util.List;

public class BulkUploadResult {
    private final int successCount;
    private final List<String> errors;

    public BulkUploadResult(int successCount, List<String> errors) {
        this.successCount = successCount;
        this.errors = errors == null ? Collections.emptyList() : errors;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public List<String> getErrors() {
        return errors;
    }
}

