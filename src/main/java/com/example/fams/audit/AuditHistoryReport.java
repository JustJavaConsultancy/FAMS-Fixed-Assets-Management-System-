package com.example.fams.audit;

import java.time.LocalDate;
import java.util.List;

public record AuditHistoryReport(
        LocalDate from,
        LocalDate to,
        long completedAuditCount,
        List<AuditSessionSummary> audits
) {
}
