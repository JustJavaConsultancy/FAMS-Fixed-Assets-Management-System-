package com.example.fams.maintenance;

import java.time.LocalDate;

public enum MaintenanceFrequency {
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    BI_ANNUALLY,
    ANNUALLY;

    public LocalDate nextAfter(LocalDate date) {
        return switch (this) {
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case QUARTERLY -> date.plusMonths(3);
            case BI_ANNUALLY -> date.plusMonths(6);
            case ANNUALLY -> date.plusYears(1);
        };
    }

    public String displayName() {
        return switch (this) {
            case WEEKLY -> "Weekly";
            case MONTHLY -> "Monthly";
            case QUARTERLY -> "Quarterly";
            case BI_ANNUALLY -> "Bi-annually";
            case ANNUALLY -> "Annually";
        };
    }
}
