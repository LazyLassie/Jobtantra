package com.jobtantra.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Embeddable
public class RetryPolicy {

    @Min(0)
    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Min(0)
    @Column(name = "initial_backoff_seconds", nullable = false)
    private long initialBackoffSeconds;

    @Min(0)
    @Column(name = "max_backoff_seconds", nullable = false)
    private long maxBackoffSeconds;

    @NotNull
    @DecimalMin(value = "1.0", inclusive = true)
    @Column(name = "backoff_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal backoffMultiplier;

    protected RetryPolicy() {
    }

    public RetryPolicy(int maxRetries, long initialBackoffSeconds, long maxBackoffSeconds, BigDecimal backoffMultiplier) {
        this.maxRetries = maxRetries;
        this.initialBackoffSeconds = initialBackoffSeconds;
        this.maxBackoffSeconds = maxBackoffSeconds;
        this.backoffMultiplier = backoffMultiplier;
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, 30, 3_600, BigDecimal.valueOf(2.0));
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getInitialBackoffSeconds() {
        return initialBackoffSeconds;
    }

    public long getMaxBackoffSeconds() {
        return maxBackoffSeconds;
    }

    public BigDecimal getBackoffMultiplier() {
        return backoffMultiplier;
    }
}
