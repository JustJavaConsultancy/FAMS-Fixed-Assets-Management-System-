package com.example.fams.audit;

import java.time.LocalDateTime;

public record AuditResultView(
        Long id,
        Long assetId,
        String assetCode,
        String assetName,
        String expectedLocation,
        String actualLocation,
        String registerStatus,
        AuditVerificationStatus resultStatus,
        String discrepancyType,
        String conditionNotes,
        LocalDateTime verifiedAt
) {
}
