package com.example.fams.maintenance;

import java.time.LocalDate;

public record MaintenanceDueEvent(
        Long taskId,
        Long scheduleId,
        Long assetId,
        String assetCode,
        String assetName,
        String assetCategory,
        String serviceType,
        LocalDate dueDate,
        String responsibleParty,
        String responsibleRole
) {
}
