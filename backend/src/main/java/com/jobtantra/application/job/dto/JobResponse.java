package com.jobtantra.application.job.dto;

import com.jobtantra.domain.model.JobStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String name,
        String description,
        JobStatus status,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        int priority,
        long timeoutSeconds,
        RetryPolicyResponse retryPolicy,
        Map<String, Object> configuration) {
}
