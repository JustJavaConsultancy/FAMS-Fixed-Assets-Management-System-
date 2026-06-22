package com.example.fams.assets.dto;

import java.util.ArrayList;
import java.util.List;

public class BulkOperationResultDto {
    private int totalRequested;
    private int processed;
    private int succeeded;
    private int failed;
    private List<BulkOperationItemResultDto> results = new ArrayList<>();

    public int getTotalRequested() {
        return totalRequested;
    }

    public void setTotalRequested(int totalRequested) {
        this.totalRequested = totalRequested;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public int getSucceeded() {
        return succeeded;
    }

    public void setSucceeded(int succeeded) {
        this.succeeded = succeeded;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<BulkOperationItemResultDto> getResults() {
        return results;
    }

    public void setResults(List<BulkOperationItemResultDto> results) {
        this.results = results;
    }

    public void addResult(BulkOperationItemResultDto item) {
        this.results.add(item);
        this.processed = this.results.size();
        if (item.isSuccess()) this.succeeded++; else this.failed++;
    }
}

