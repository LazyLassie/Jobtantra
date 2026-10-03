package com.jobtantra.application.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobExecutionRepository executionRepository;
    @Mock
    private JobAttemptRepository attemptRepository;

    private ExecutionService service;
    private SimpleMeterRegistry meterRegistry;
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new ExecutionService(jobRepository, executionRepository, attemptRepository, meterRegistry);
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
        assertThat(meterRegistry.get("jobtantra.executions.created").counter().count()).isEqualTo(1.0);
    }

    @Test
        void duplicateDispatchReturnsExistingExecutionWithoutCreatingAnother() {
        Job job = job(JobStatus.ACTIVE);
        JobExecution existing = execution(job, "request-1", ExecutionStatus.QUEUED);
        when(jobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));
        when(executionRepository.findByJob_IdAndIdempotencyKey(JOB_ID, "request-1")).thenReturn(Optional.of(existing));

        var firstResponse = service.create(JOB_ID, "request-1");
        var duplicateResponse = service.create(JOB_ID, "request-1");

        assertThat(firstResponse.created()).isFalse();
        assertThat(duplicateResponse.created()).isFalse();
        assertThat(duplicateResponse.response().id()).isEqualTo(firstResponse.response().id());
        assertThat(duplicateResponse.response().id()).isEqualTo(existing.getId());
        verify(executionRepository, times(2)).findByJob_IdAndIdempotencyKey(JOB_ID, "request-1");
        verify(executionRepository, never()).saveAndFlush(any(JobExecution.class));
        }

        @Test
        void differentIdempotencyKeysCreateDistinctLogicalExecutions() {
        Job job = job(JobStatus.ACTIVE);
        when(jobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));
        when(executionRepository.findByJob_IdAndIdempotencyKey(JOB_ID, "request-1"))
            .thenReturn(Optional.empty());
        when(executionRepository.findByJob_IdAndIdempotencyKey(JOB_ID, "request-2"))
            .thenReturn(Optional.empty());
        when(executionRepository.saveAndFlush(any(JobExecution.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var firstResponse = service.create(JOB_ID, "request-1");
        var secondResponse = service.create(JOB_ID, "request-2");

        ArgumentCaptor<JobExecution> executions = ArgumentCaptor.forClass(JobExecution.class);
        verify(executionRepository, times(2)).saveAndFlush(executions.capture());
        assertThat(firstResponse.created()).isTrue();
        assertThat(secondResponse.created()).isTrue();
        assertThat(executions.getAllValues()).extracting(JobExecution::getIdempotencyKey)
            .containsExactly("request-1", "request-2");
        assertThat(executions.getAllValues().get(0)).isNotSameAs(executions.getAllValues().get(1));
        }

        @Test
        void returnsExistingExecutionForIdempotencyKeyEvenWhenJobIsInactive() {
            Job job = job(JobStatus.ACTIVE);
            job.transitionTo(JobStatus.CANCELLED);
        JobExecution existing = execution(job, "request-1", ExecutionStatus.QUEUED);
        when(jobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));
        when(executionRepository.findByJob_IdAndIdempotencyKey(JOB_ID, "request-1"))
            .thenReturn(Optional.of(existing));

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
        assertThat(meterRegistry.get("jobtantra.executions.cancelled").counter().count()).isEqualTo(1.0);
    }

    @Test
    void retriesFailedExecutionUnderSameExecution() {
        JobExecution execution = execution(job(JobStatus.ACTIVE), "request-1", ExecutionStatus.FAILED);
        ReflectionTestUtils.setField(execution, "id", EXECUTION_ID);
        when(executionRepository.findByIdForUpdate(EXECUTION_ID)).thenReturn(Optional.of(execution));
        when(executionRepository.saveAndFlush(execution)).thenReturn(execution);

        var response = service.retry(EXECUTION_ID);

        assertThat(response.id()).isEqualTo(EXECUTION_ID);
        assertThat(response.status()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(response.attemptNumber()).isEqualTo(2);
        assertThat(execution.getAttempts()).hasSize(2);
        assertThat(execution.getIdempotencyKey()).isEqualTo("request-1");
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
