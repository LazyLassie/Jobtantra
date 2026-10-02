package com.jobtantra.application.job.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RetryPolicyRequest(
        @NotNull @Min(0) Integer maxRetries,
        @NotNull @Min(0) Long initialBackoffSeconds,
        @NotNull @Min(0) Long maxBackoffSeconds,
        @NotNull @DecimalMin(value = "1.0") BigDecimal backoffMultiplier) {
}
