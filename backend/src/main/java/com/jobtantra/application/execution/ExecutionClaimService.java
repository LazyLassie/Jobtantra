package com.jobtantra.application.execution;

import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.JobAttempt;
import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.infrastructure.persistence.repository.JobExecutionRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExecutionClaimService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionClaimService.class);

    private final JobExecutionRepository executionRepository;
    private final long leaseGraceSeconds;
    private final MeterRegistry meterRegistry;

    public ExecutionClaimService(JobExecutionRepository executionRepository,
            @Value("${jobtantra.worker.lease-grace-seconds:30}") long leaseGraceSeconds,
            MeterRegistry meterRegistry) {
        this.executionRepository = executionRepository;
        this.leaseGraceSeconds = Math.max(0, leaseGraceSeconds);
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public Optional<ClaimedExecution> claimNext(Instant now) {
        recoverOneExpiredExecution(now);
        Optional<UUID> queuedId = executionRepository.findNextQueuedIdForUpdate(now);
        if (queuedId.isEmpty()) {
            return Optional.empty();
        }

        JobExecution execution = executionRepository.findById(queuedId.get()).orElseThrow();
        if (execution.getStatus() != ExecutionStatus.QUEUED) {
            return Optional.empty();
        }

        UUID claimToken = UUID.randomUUID();
        JobAttempt attempt = execution.getLatestAttempt();
        attempt.transitionTo(ExecutionStatus.RUNNING, now);
        int taskCount = Math.max(1, execution.getJob().getTasks().size());
        Duration leaseDuration = Duration.ofSeconds(execution.getJob().getTimeoutSeconds())
            .multipliedBy(taskCount)
            .plusSeconds(leaseGraceSeconds);
        execution.claim(claimToken, now.plus(leaseDuration));
        executionRepository.saveAndFlush(execution);
        LOGGER.info("Execution claimed and started jobId={} executionId={} attemptId={}",
            execution.getJobId(), execution.getId(), attempt.getId());

        execution.getAttempts().size();
        return Optional.of(new ClaimedExecution(execution, claimToken));
    }

    @Transactional
    public void complete(ClaimedExecution claim, Exception failure, Instant completedAt) {
        Optional<JobExecution> locked = executionRepository.findByIdForUpdate(claim.execution().getId());
        if (locked.isEmpty()) {
            return;
        }
        JobExecution execution = locked.get();
        if (execution.getStatus() != ExecutionStatus.RUNNING
                || !claim.claimToken().equals(execution.getClaimToken())) {
            return;
        }

        JobAttempt attempt = execution.getLatestAttempt();
        if (failure == null) {
            attempt.transitionTo(ExecutionStatus.SUCCEEDED, completedAt);
            execution.updateStatus(ExecutionStatus.SUCCEEDED);
            execution.clearClaim();
        } else {
            attempt.recordError("EXECUTION_FAILED", "Execution failed", diagnostic(failure));
            attempt.transitionTo(ExecutionStatus.FAILED, completedAt);
            execution.updateStatus(ExecutionStatus.FAILED);
            execution.clearClaim();
            scheduleRetryIfAllowed(execution, attempt.getAttemptNumber() - 1, completedAt);
        }
        executionRepository.saveAndFlush(execution);
        if (failure == null) {
            meterRegistry.counter("jobtantra.executions.success").increment();
            LOGGER.info("Execution completed successfully jobId={} executionId={}",
                    execution.getJobId(), execution.getId());
        } else {
            meterRegistry.counter("jobtantra.executions.failure").increment();
            LOGGER.info("Execution failed jobId={} executionId={}", execution.getJobId(), execution.getId());
        }
    }

    private void recoverOneExpiredExecution(Instant now) {
        Optional<UUID> expiredId = executionRepository.findExpiredRunningIdForUpdate(now);
        if (expiredId.isEmpty()) {
            return;
        }
        Optional<JobExecution> expired = executionRepository.findById(expiredId.get());
        if (expired.isEmpty()) {
            return;
        }
        JobExecution execution = expired.get();
        if (execution.getStatus() != ExecutionStatus.RUNNING || execution.getLeaseExpiresAt() == null
                || execution.getLeaseExpiresAt().isAfter(now)) {
            return;
        }

        JobAttempt attempt = execution.getLatestAttempt();
        attempt.recordError("EXECUTION_LEASE_EXPIRED", "Execution lease expired",
                "The worker lease expired before execution completed");
        attempt.transitionTo(ExecutionStatus.TIMED_OUT, now);
        execution.updateStatus(ExecutionStatus.TIMED_OUT);
        execution.clearClaim();
        scheduleRetryIfAllowed(execution, attempt.getAttemptNumber() - 1, now);
        executionRepository.saveAndFlush(execution);
    }

    private void scheduleRetryIfAllowed(JobExecution execution, int backoffExponent, Instant now) {
        int retryCount = execution.getAttempts().size() - 1;
        if (retryCount >= execution.getJob().getMaxRetries()) {
            return;
        }
        RetryPolicy policy = execution.getJob().getRetryPolicy();
        Instant availableAt = now.plusSeconds(backoffSeconds(policy, backoffExponent));
        execution.addAttempt();
        execution.queueForRetry(availableAt);
    }

    private long backoffSeconds(RetryPolicy policy, int exponent) {
        long delay = policy.getInitialBackoffSeconds();
        long maximum = policy.getMaxBackoffSeconds();
        BigDecimal multiplier = policy.getBackoffMultiplier();
        if (delay >= maximum || exponent <= 0 || multiplier.compareTo(BigDecimal.ONE) <= 0) {
            return Math.min(delay, maximum);
        }
        BigDecimal maximumValue = BigDecimal.valueOf(maximum);
        for (int count = 0; count < exponent && delay < maximum; count++) {
            BigDecimal next = BigDecimal.valueOf(delay).multiply(multiplier);
            delay = next.compareTo(maximumValue) >= 0 ? maximum : next.longValue();
        }
        return delay;
    }

    private String diagnostic(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getName() + (message == null ? "" : ": " + message);
    }

    public record ClaimedExecution(JobExecution execution, UUID claimToken) {
    }
}