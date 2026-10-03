package com.jobtantra.application.task.dto;

import com.jobtantra.domain.model.TaskStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID jobId,
        String name,
        String taskType,
        int sequenceOrder,
        TaskStatus status,
        Map<String, Object> configuration,
        Instant createdAt,
        Instant updatedAt) {
}