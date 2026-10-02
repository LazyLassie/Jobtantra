package com.jobtantra.application.schedule;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SchedulePoller {

    private final ScheduleService scheduleService;

    public SchedulePoller(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Scheduled(fixedDelayString = "${jobtantra.scheduler.fixed-delay-ms:1000}")
    public void poll() {
        scheduleService.processDueSchedules(Instant.now());
    }
}
