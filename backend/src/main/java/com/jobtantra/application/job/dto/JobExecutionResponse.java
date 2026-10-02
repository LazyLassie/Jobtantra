package com.jobtantra.application.job.dto;

import com.jobtantra.domain.model.ExecutionStatus;
import java.time.Instant;
import java.util.UUID;

public record JobExecutionResponse(
        UUID id,
        UUID jobId,
        UUID attemptId,
        ExecutionStatus status,
        int attemptNumber,
        UUID workerId,
        Instant startedAt,
        Instant completedAt,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {
}
