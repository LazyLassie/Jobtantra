package com.jobtantra.application.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobAttempt;
import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.infrastructure.persistence.repository.JobExecutionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final UUID EXECUTION_ID = UUID.randomUUID();

    @Mock private JobExecutionRepository executionRepository;

    private ExecutionClaimService claimService;

    @BeforeEach
    void setUp() {
        claimService = new ExecutionClaimService(executionRepository, 5);
    }

    @Test
    void claimTransitionsQueuedAttemptToRunningAndCompletesSuccessfully() {
        JobExecution execution = queuedExecution(new RetryPolicy(0, 0, 0, BigDecimal.ONE));
        stubQueued(execution);

        var claim = claimService.claimNext(NOW).orElseThrow();

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(execution.getLatestAttempt().getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(execution.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(65));

        when(executionRepository.findByIdForUpdate(null)).thenReturn(Optional.of(execution));
        claimService.complete(claim, null, NOW.plusSeconds(1));

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(execution.getLatestAttempt().getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(execution.getClaimToken()).isNull();
    }

    @Test
    void failedExecutionUsesSanitizedMessageWhenRetriesAreExhausted() {
        JobExecution execution = queuedExecution(new RetryPolicy(0, 0, 0, BigDecimal.ONE));
        stubQueued(execution);
        var claim = claimService.claimNext(NOW).orElseThrow();
        when(executionRepository.findByIdForUpdate(null)).thenReturn(Optional.of(execution));

        claimService.complete(claim, new IllegalStateException("database secret"), NOW.plusSeconds(1));

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getLatestAttempt().getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getLatestAttempt().getErrorMessage()).isEqualTo("Execution failed");
        assertThat(execution.getLatestAttempt().getFailureDetail()).contains("database secret");
    }

    @Test
    void failureQueuesRetryAfterConfiguredBackoff() {
        JobExecution execution = queuedExecution(new RetryPolicy(2, 10, 20, BigDecimal.valueOf(2)));
        stubQueued(execution);
        var claim = claimService.claimNext(NOW).orElseThrow();
        when(executionRepository.findByIdForUpdate(null)).thenReturn(Optional.of(execution));

        claimService.complete(claim, new IllegalStateException("failed"), NOW);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(execution.getAttempts()).hasSize(2);
        assertThat(execution.getAttempts().get(0).getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getLatestAttempt().getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(execution.getAvailableAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(execution.getClaimToken()).isNull();
    }

    @Test
    void expiredRunningAttemptTimesOutAndIsReclaimedAsNextAttempt() {
        JobExecution execution = queuedExecution(new RetryPolicy(1, 0, 0, BigDecimal.valueOf(2)));
        execution.getLatestAttempt().transitionTo(ExecutionStatus.RUNNING, NOW.minusSeconds(30));
        UUID previousToken = UUID.randomUUID();
        execution.claim(previousToken, NOW.minusSeconds(1));
        when(executionRepository.findExpiredRunningIdForUpdate(NOW)).thenReturn(Optional.of(EXECUTION_ID));
        when(executionRepository.findById(EXECUTION_ID)).thenReturn(Optional.of(execution));
        when(executionRepository.findNextQueuedIdForUpdate(NOW)).thenReturn(Optional.of(EXECUTION_ID));
        when(executionRepository.saveAndFlush(execution)).thenReturn(execution);

        var claim = claimService.claimNext(NOW).orElseThrow();

        assertThat(execution.getAttempts()).hasSize(2);
        assertThat(execution.getAttempts().get(0).getStatus()).isEqualTo(ExecutionStatus.TIMED_OUT);
        assertThat(execution.getAttempts().get(1).getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(execution.getClaimToken()).isNotEqualTo(previousToken);
        assertThat(claim.claimToken()).isEqualTo(execution.getClaimToken());

        when(executionRepository.findByIdForUpdate(null)).thenReturn(Optional.of(execution));
        claimService.complete(new ExecutionClaimService.ClaimedExecution(execution, previousToken), null,
            NOW.plusSeconds(1));
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(execution.getClaimToken()).isEqualTo(claim.claimToken());
    }

    private void stubQueued(JobExecution execution) {
        when(executionRepository.findExpiredRunningIdForUpdate(any())).thenReturn(Optional.empty());
        when(executionRepository.findNextQueuedIdForUpdate(NOW)).thenReturn(Optional.of(EXECUTION_ID));
        when(executionRepository.findById(EXECUTION_ID)).thenReturn(Optional.of(execution));
        when(executionRepository.saveAndFlush(execution)).thenReturn(execution);
    }

    private JobExecution queuedExecution(RetryPolicy policy) {
        Job job = new Job("claim-test", null, "owner", 1, 60, policy, Map.of());
        JobExecution execution = new JobExecution(job, "claim-test-key");
        execution.addAttempt();
        return execution;
    }
}