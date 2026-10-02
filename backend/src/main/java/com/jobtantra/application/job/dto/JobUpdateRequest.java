package com.jobtantra.application.job.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record JobUpdateRequest(
        @Size(max = 200) String name,
        @Size(max = 2_000) String description,
        @Min(0) Integer priority,
        @Positive Long timeoutSeconds,
        @Valid RetryPolicyRequest retryPolicy,
        Map<String, Object> configuration) {
}
