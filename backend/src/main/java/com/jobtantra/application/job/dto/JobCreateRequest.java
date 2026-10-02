package com.jobtantra.application.job.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record JobCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2_000) String description,
        @NotBlank @Size(max = 255) String createdBy,
        @NotNull @Min(0) Integer priority,
        @NotNull @Positive Long timeoutSeconds,
        @NotNull @Valid RetryPolicyRequest retryPolicy,
        Map<String, Object> configuration) {
}
