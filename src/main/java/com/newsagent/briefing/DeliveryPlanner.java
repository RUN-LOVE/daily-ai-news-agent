package com.newsagent.briefing;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

public final class DeliveryPlanner {

    private static final Duration CATCH_UP_WINDOW = Duration.ofMinutes(10);

    private DeliveryPlanner() {
    }

    public static boolean isDue(String deliverTime, LocalTime now) {
        if (deliverTime == null || deliverTime.isBlank() || now == null) {
            return false;
        }
        try {
            LocalTime scheduled = LocalTime.parse(deliverTime);
            long minutes = Duration.between(scheduled, now).toMinutes();
            return minutes >= 0 && minutes < CATCH_UP_WINDOW.toMinutes();
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
