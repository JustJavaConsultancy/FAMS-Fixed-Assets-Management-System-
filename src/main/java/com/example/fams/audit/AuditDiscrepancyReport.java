package com.example.fams.audit;

import java.util.List;
import java.util.Map;

public record AuditDiscrepancyReport(
        Long sessionId,
        String sessionTitle,
        long totalVerified,
        long discrepancyCount,
        Map<String, Long> countsByType,
        List<AuditResultView> discrepancies
) {
}
