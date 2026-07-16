package com.example.fams.lifecycle;

import java.util.List;

public class BulkLifecycleResult {
    private final int totalRequested;
    private final int succeeded;
    private final List<BulkLifecycleItemResult> items;

    public BulkLifecycleResult(int totalRequested, int succeeded, List<BulkLifecycleItemResult> items) {
        this.totalRequested = totalRequested;
        this.succeeded = succeeded;
        this.items = items;
    }

    public int getTotalRequested() {
        return totalRequested;
    }

    public int getSucceeded() {
        return succeeded;
    }

    public List<BulkLifecycleItemResult> getItems() {
        return items;
    }
}
