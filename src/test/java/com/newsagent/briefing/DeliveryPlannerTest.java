package com.newsagent.briefing;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryPlannerTest {

    @Test
    void runsWithinTenMinutesAfterScheduledTime() {
        assertTrue(DeliveryPlanner.isDue("08:30", LocalTime.of(8, 30)));
        assertTrue(DeliveryPlanner.isDue("08:30", LocalTime.of(8, 39)));
    }

    @Test
    void skipsOutsideWindow() {
        assertFalse(DeliveryPlanner.isDue("08:30", LocalTime.of(8, 29)));
        assertFalse(DeliveryPlanner.isDue("08:30", LocalTime.of(8, 40)));
        assertFalse(DeliveryPlanner.isDue("08:30", LocalTime.of(23, 59)));
    }

    @Test
    void skipsInvalidConfiguration() {
        assertFalse(DeliveryPlanner.isDue("not-a-time", LocalTime.NOON));
        assertFalse(DeliveryPlanner.isDue("", LocalTime.NOON));
        assertFalse(DeliveryPlanner.isDue(null, LocalTime.NOON));
    }
}
