package com.newsagent.briefing;

import com.newsagent.profile.Profile;
import com.newsagent.profile.ProfileService;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BriefingSchedulerTest {

    @Test
    void triggersWhenDueAndTodayIsMissing() {
        ProfileService profiles = mock(ProfileService.class);
        BriefingService briefings = mock(BriefingService.class);
        when(profiles.get()).thenReturn(profileAt(LocalTime.now()));
        when(briefings.existsToday()).thenReturn(false);
        when(briefings.generateToday(false)).thenReturn(briefing());

        new BriefingScheduler(profiles, briefings).generateWhenDue();

        verify(briefings).generateToday(false);
    }

    @Test
    void skipsWhenTodayAlreadyExists() {
        ProfileService profiles = mock(ProfileService.class);
        BriefingService briefings = mock(BriefingService.class);
        when(profiles.get()).thenReturn(profileAt(LocalTime.now()));
        when(briefings.existsToday()).thenReturn(true);

        new BriefingScheduler(profiles, briefings).generateWhenDue();

        verify(briefings, never()).generateToday(anyBoolean());
    }

    @Test
    void skipsBeforeScheduledTime() {
        ProfileService profiles = mock(ProfileService.class);
        BriefingService briefings = mock(BriefingService.class);
        when(profiles.get()).thenReturn(profileAt(LocalTime.now().minusMinutes(30)));

        new BriefingScheduler(profiles, briefings).generateWhenDue();

        verify(briefings, never()).generateToday(anyBoolean());
    }

    private static Profile profileAt(LocalTime time) {
        return new Profile(
                "测试用户", "开发者", "", List.of("大模型"), List.of(), List.of(),
                List.of("infoq"), "zh", 8,
                time.format(DateTimeFormatter.ofPattern("HH:mm")),
                "INBOX", "2026-10-21T00:00:00Z");
    }

    private static Briefing briefing() {
        return new Briefing(
                "2026-10-21", "测试简报", "内容", "",
                List.of(), 1, "COMPLETED", "2026-10-21T00:00:00Z");
    }
}
