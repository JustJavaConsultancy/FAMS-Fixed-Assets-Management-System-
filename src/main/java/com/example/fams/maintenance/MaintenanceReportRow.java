package com.example.fams.maintenance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MaintenanceReportRow(
        Long assetId,
        String assetCode,
        String assetName,
        String category,
        MaintenanceType type,
        String serviceProvider,
        LocalDate maintenanceDate,
        LocalDate resolutionDate,
        BigDecimal cost,
        MaintenanceStatus status
) {
}
