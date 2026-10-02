package com.jobtantra.application.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobAttempt;
import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.infrastructure.persistence.repository.JobAttemptRepository;
import com.jobtantra.infrastructure.persistence.repository.JobExecutionRepository;
import com.jobtantra.infrastructure.persistence.repository.JobRepository;
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
class ExecutionServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobExecutionRepository executionRepository;
    @Mock
    private JobAttemptRepository attemptRepository;

    private ExecutionService service;
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ExecutionService(jobRepository, executionRepository, attemptRepository);
    }

    @Test
    void createsQueuedExecutionWithFirstAttemptForActiveJob() {
        Job job = job(JobStatus.ACTIVE);
        when(jobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));
        when(executionRepository.findByJob_IdAndIdempotencyKey(JOB_ID, "request-1")).thenReturn(Optional.empty());
        when(executionRepository.saveAndFlush(any(JobExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(JOB_ID, "request-1");

        assertThat(response.created()).isTrue();
        assertThat(response.response().status()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(response.response().attemptNumber()).isOne();
        verify(executionRepository).saveAndFlush(any(JobExecution.class));
    }

    @Test
    void returnsExistingExecutionForDuplicateIdempotencyKey() {
        Job job = job(JobStatus.ACTIVE);
        JobExecution existing = execution(job, "request-1", ExecutionStatus.QUEUED);
        when(jobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));
        when(executionRepository.findByJob_IdAndIdempotencyKey(JOB_ID, "request-1")).thenReturn(Optional.of(existing));

        var response = service.create(JOB_ID, "request-1");

        assertThat(response.created()).isFalse();
        assertThat(response.response().id()).isEqualTo(existing.getId());
    }

    @Test
    void rejectsCreationForInactiveJob() {
        when(jobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job(JobStatus.DRAFT)));

        assertThatThrownBy(() -> service.create(JOB_ID, "request-1"))
                .isInstanceOf(ExecutionStateException.class)
                .hasMessage("Only ACTIVE jobs can create executions");
    }

    @Test
    void cancelsQueuedExecution() {
        JobExecution execution = execution(job(JobStatus.ACTIVE), "request-1", ExecutionStatus.QUEUED);
        when(executionRepository.findByIdForUpdate(EXECUTION_ID)).thenReturn(Optional.of(execution));
        when(executionRepository.saveAndFlush(execution)).thenReturn(execution);

        var response = service.cancel(EXECUTION_ID);

        assertThat(response.status()).isEqualTo(ExecutionStatus.CANCELLED);
        verify(executionRepository).saveAndFlush(execution);
    }

    @Test
    void retriesFailedExecutionUnderSameExecution() {
        JobExecution execution = execution(job(JobStatus.ACTIVE), "request-1", ExecutionStatus.FAILED);
        UUID executionIdBefore = execution.getId();
        when(executionRepository.findByIdForUpdate(EXECUTION_ID)).thenReturn(Optional.of(execution));
        when(executionRepository.saveAndFlush(execution)).thenReturn(execution);

        var response = service.retry(EXECUTION_ID);

        assertThat(response.id()).isEqualTo(executionIdBefore);
        assertThat(response.status()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(response.attemptNumber()).isEqualTo(2);
    }

    @Test
    void rejectsRetryWhenMaximumRetriesReached() {
        Job job = new Job("daily", null, "owner", 1, 60,
                new RetryPolicy(0, 1, 1, BigDecimal.ONE), Map.of());
        job.transitionTo(JobStatus.ACTIVE);
        JobExecution execution = execution(job, "request-1", ExecutionStatus.FAILED);
        when(executionRepository.findByIdForUpdate(EXECUTION_ID)).thenReturn(Optional.of(execution));

        assertThatThrownBy(() -> service.retry(EXECUTION_ID))
                .isInstanceOf(ExecutionStateException.class)
                .hasMessageContaining("Retry limit reached");
    }

    @Test
    void neverExposesFailureDetailInResponse() {
        JobExecution execution = execution(job(JobStatus.ACTIVE), "request-1", ExecutionStatus.FAILED);
        execution.getLatestAttempt().recordError("TASK_FAILED", "Task failed", "java.lang.AssertionError: secret stack");
        when(executionRepository.findById(EXECUTION_ID)).thenReturn(Optional.of(execution));

        var response = service.get(EXECUTION_ID);

        assertThat(response.errorMessage()).isEqualTo("Task failed");
    }

    private Job job(JobStatus status) {
        Job job = new Job("daily", null, "owner", 1, 60, RetryPolicy.defaults(), Map.of());
        if (status != JobStatus.DRAFT) {
            job.transitionTo(status);
        }
        return job;
    }

    private JobExecution execution(Job job, String key, ExecutionStatus status) {
        JobExecution execution = new JobExecution(job, key);
        JobAttempt attempt = execution.addAttempt();
        if (status != ExecutionStatus.QUEUED) {
            attempt.transitionTo(ExecutionStatus.RUNNING, Instant.now());
            attempt.transitionTo(status, Instant.now());
            execution.updateStatus(status);
        }
        return execution;
    }
}
