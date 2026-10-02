package com.jobtantra.application.schedule.dto;

import com.jobtantra.domain.model.ScheduleType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ScheduleRequest(
        @NotNull ScheduleType type,
        String cronExpression,
        Instant oneTimeAt) {
}
