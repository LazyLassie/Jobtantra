package com.jobtantra.application.execution;

import com.jobtantra.application.job.dto.JobExecutionResponse;
import com.jobtantra.common.dto.PageResponse;
import com.jobtantra.common.exception.ResourceNotFoundException;
import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobAttempt;
import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.infrastructure.persistence.repository.JobAttemptRepository;
import com.jobtantra.infrastructure.persistence.repository.JobExecutionRepository;
import com.jobtantra.infrastructure.persistence.repository.JobRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionService.class);

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final JobAttemptRepository attemptRepository;
    private final MeterRegistry meterRegistry;

    public ExecutionService(JobRepository jobRepository, JobExecutionRepository executionRepository,
            JobAttemptRepository attemptRepository, MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
        this.attemptRepository = attemptRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public ExecutionCreationResult create(UUID jobId, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        Job job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        Optional<JobExecution> existing = executionRepository.findByJob_IdAndIdempotencyKey(jobId, idempotencyKey);
        if (existing.isPresent()) {
            return new ExecutionCreationResult(toResponse(existing.get()), false);
        }
        if (job.getStatus() != JobStatus.ACTIVE) {
            throw new ExecutionStateException("Only ACTIVE jobs can create executions");
        }
        return createNew(job, idempotencyKey);
    }

    @Transactional
    public JobExecutionResponse get(UUID executionId) {
        return toResponse(findExecution(executionId));
    }

    @Transactional
    public PageResponse<JobExecutionResponse> list(UUID jobId, ExecutionStatus status, Pageable pageable) {
        if (!jobRepository.existsById(jobId)) {
            throw new ResourceNotFoundException("Job not found: " + jobId);
        }
        Page<JobExecution> page = status == null
            ? executionRepository.findByJob_Id(jobId, pageable)
                : executionRepository.findByJob_IdAndStatus(jobId, status, pageable);
        return new PageResponse<>(page.getContent().stream().map(this::toResponse).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional
    public JobExecutionResponse cancel(UUID executionId) {
        JobExecution execution = lockedExecution(executionId);
        JobAttempt attempt = execution.getLatestAttempt();
        try {
            attempt.transitionTo(ExecutionStatus.CANCELLED, Instant.now());
        } catch (IllegalStateException exception) {
            throw new ExecutionStateException(exception.getMessage());
        }
        execution.updateStatus(ExecutionStatus.CANCELLED);
        execution.clearClaim();
        executionRepository.saveAndFlush(execution);
        meterRegistry.counter("jobtantra.executions.cancelled").increment();
        LOGGER.info("Execution cancelled jobId={} executionId={}", execution.getJobId(), execution.getId());
        return toResponse(execution);
    }

    @Transactional
    public JobExecutionResponse retry(UUID executionId) {
        JobExecution execution = lockedExecution(executionId);
        JobAttempt previous = execution.getLatestAttempt();
        if (previous.getStatus() != ExecutionStatus.FAILED && previous.getStatus() != ExecutionStatus.TIMED_OUT) {
            throw new ExecutionStateException("Only FAILED or TIMED_OUT executions can be retried");
        }
        Job job = execution.getJob();
        int retryCount = execution.getAttempts().size() - 1;
        if (retryCount >= job.getMaxRetries()) {
            throw new ExecutionStateException("Retry limit reached for execution: " + executionId);
        }
        execution.addAttempt();
        execution.queueForRetry(Instant.now());
        executionRepository.saveAndFlush(execution);
        return toResponse(execution);
    }

    private ExecutionCreationResult createNew(Job job, String idempotencyKey) {
        try {
            JobExecution execution = new JobExecution(job, idempotencyKey);
            execution.addAttempt();
            execution.updateStatus(ExecutionStatus.QUEUED);
            JobExecution saved = executionRepository.saveAndFlush(execution);
            meterRegistry.counter("jobtantra.executions.created").increment();
            LOGGER.info("Execution created and dispatched jobId={} executionId={}", job.getId(), saved.getId());
            return new ExecutionCreationResult(toResponse(saved), true);
        } catch (DataIntegrityViolationException exception) {
            return executionRepository.findByJob_IdAndIdempotencyKey(job.getId(), idempotencyKey)
                    .map(existing -> new ExecutionCreationResult(toResponse(existing), false))
                    .orElseThrow(() -> exception);
        }
    }

    private JobExecution findExecution(UUID executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));
    }

    private JobExecution lockedExecution(UUID executionId) {
        return executionRepository.findByIdForUpdate(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new MissingIdempotencyKeyException("Idempotency-Key must be present and contain at most 255 characters");
        }
    }

    private JobExecutionResponse toResponse(JobExecution execution) {
        JobAttempt attempt = execution.getLatestAttempt();
        return new JobExecutionResponse(execution.getId(), execution.getJobId(), attempt.getId(), execution.getStatus(),
                attempt.getAttemptNumber(), attempt.getWorker() == null ? null : attempt.getWorker().getId(),
                attempt.getStartedAt(), attempt.getCompletedAt(), attempt.getErrorCode(), attempt.getErrorMessage(),
                execution.getCreatedAt(), execution.getUpdatedAt());
    }
}
