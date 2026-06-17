package com.example.fams.lifecycle;

import java.time.LocalDateTime;

public record LifecycleTimelineItem(
        LifecycleEventType eventType,
        String title,
        String details,
        String actor,
        LocalDateTime eventAt
) {
}
