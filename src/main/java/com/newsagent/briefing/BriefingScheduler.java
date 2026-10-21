package com.newsagent.briefing;

import com.newsagent.profile.Profile;
import com.newsagent.profile.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
@ConditionalOnProperty(name = "app.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class BriefingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BriefingScheduler.class);

    private final ProfileService profileService;
    private final BriefingService briefingService;

    public BriefingScheduler(ProfileService profileService, BriefingService briefingService) {
        this.profileService = profileService;
        this.briefingService = briefingService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void generateWhenDue() {
        Profile profile = profileService.get();
        if (!DeliveryPlanner.isDue(profile.deliverTime(), LocalTime.now())) {
            return;
        }
        if (briefingService.existsToday()) {
            return;
        }
        try {
            Briefing briefing = briefingService.generateToday(false);
            log.info("定时简报已生成: date={}, stopReason={}",
                    briefing.date(), briefing.stopReason());
        } catch (RuntimeException exception) {
            log.error("定时简报生成失败: {}", exception.getMessage(), exception);
        }
    }
}
