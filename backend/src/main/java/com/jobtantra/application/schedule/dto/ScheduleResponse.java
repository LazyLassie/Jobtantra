package com.jobtantra.application.schedule.dto;

import com.jobtantra.domain.model.ScheduleType;
import java.time.Instant;
import java.util.UUID;

public record ScheduleResponse(
        UUID id,
        UUID jobId,
        ScheduleType type,
        String cronExpression,
        Instant oneTimeAt,
        Instant nextRunAt,
        Instant lastScheduledAt) {
}
