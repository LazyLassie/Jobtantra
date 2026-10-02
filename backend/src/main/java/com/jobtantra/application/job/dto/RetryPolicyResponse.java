package com.jobtantra.application.job.dto;

import java.math.BigDecimal;

public record RetryPolicyResponse(
        int maxRetries,
        long initialBackoffSeconds,
        long maxBackoffSeconds,
        BigDecimal backoffMultiplier) {
}
