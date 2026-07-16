package com.example.fams.common;

import com.example.fams.FamsApplication;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Single source of truth for the application's notion of "now".
 *
 * <p>All date/time captured by the depreciation (and other) modules must go through this
 * class so that the business timezone ({@link FamsApplication#APP_TIME_ZONE}) is applied
 * uniformly. {@code LocalDate.now()}/{@code LocalDateTime.now()} without a zone would use the
 * JVM default, which drifts when the app is hosted in a different zone than the business.</p>
 */
public final class AppClock {

    private static volatile Clock clock = Clock.system(ZoneId.of(FamsApplication.APP_TIME_ZONE));

    private AppClock() {
    }

    public static ZoneId zoneId() {
        return clock.getZone();
    }

    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * Replace the clock (used by tests). Passing {@code null} restores the default system clock.
     */
    public static void setClock(Clock clock) {
        AppClock.clock = clock == null ? Clock.system(ZoneId.of(FamsApplication.APP_TIME_ZONE)) : clock;
    }
}
