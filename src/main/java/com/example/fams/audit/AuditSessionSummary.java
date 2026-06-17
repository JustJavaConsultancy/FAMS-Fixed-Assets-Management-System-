package com.example.fams.audit;

import java.time.LocalDateTime;

public record AuditSessionSummary(
        Long id,
        String title,
        String auditorName,
        String scopeLocation,
        AuditSessionStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        long verifiedCount,
        long missingCount,
        long discrepancyCount
) {
}
