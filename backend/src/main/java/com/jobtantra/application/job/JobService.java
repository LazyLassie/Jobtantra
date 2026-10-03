package com.jobtantra.application.job;

import com.jobtantra.application.job.dto.JobCreateRequest;
import com.jobtantra.application.job.dto.JobExecutionResponse;
import com.jobtantra.application.job.dto.JobResponse;
import com.jobtantra.application.job.dto.JobUpdateRequest;
import com.jobtantra.application.job.dto.RetryPolicyRequest;
import com.jobtantra.application.job.dto.RetryPolicyResponse;
import com.jobtantra.common.dto.PageResponse;
import com.jobtantra.common.exception.ResourceNotFoundException;
import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.infrastructure.persistence.repository.JobExecutionRepository;
import com.jobtantra.infrastructure.persistence.repository.JobRepository;
import com.jobtantra.infrastructure.persistence.repository.TaskRepository;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class JobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final TaskRepository taskRepository;
    private final MeterRegistry meterRegistry;

    public JobService(JobRepository jobRepository, JobExecutionRepository jobExecutionRepository,
            TaskRepository taskRepository, MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.taskRepository = taskRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public JobResponse create(JobCreateRequest request, String ownerUsername) {
        RetryPolicy retryPolicy = toRetryPolicy(request.retryPolicy());
        Job job = new Job(request.name(), request.description(), ownerUsername, request.priority(),
                request.timeoutSeconds(), retryPolicy, request.configuration());
        Job saved = jobRepository.save(job);
        meterRegistry.counter("jobtantra.jobs.created").increment();
        LOGGER.info("Job created jobId={}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public PageResponse<JobResponse> list(JobStatus status, Pageable pageable, String username, boolean admin) {
        Page<Job> jobs;
        if (admin) {
            jobs = status == null ? jobRepository.findAll(pageable) : jobRepository.findByStatus(status, pageable);
        } else {
            jobs = status == null
                    ? jobRepository.findByCreatedBy(username, pageable)
                    : jobRepository.findByCreatedByAndStatus(username, status, pageable);
        }
        return new PageResponse<>(jobs.getContent().stream().map(this::toResponse).toList(), jobs.getNumber(),
                jobs.getSize(), jobs.getTotalElements(), jobs.getTotalPages(), jobs.isFirst(), jobs.isLast());
    }

    @Transactional
    public JobResponse get(UUID id) {
        return toResponse(findJob(id));
    }

    @Transactional
    public JobResponse update(UUID id, JobUpdateRequest request) {
        Job job = findJob(id);
        ensureMetadataEditable(job);

        String name = request.name() == null ? job.getName() : request.name();
        if (name.isBlank()) {
            throw new InvalidJobRequestException("Job name must not be blank");
        }
        int priority = request.priority() == null ? job.getPriority() : request.priority();
        long timeoutSeconds = request.timeoutSeconds() == null ? job.getTimeoutSeconds() : request.timeoutSeconds();
        RetryPolicy retryPolicy = request.retryPolicy() == null ? job.getRetryPolicy() : toRetryPolicy(request.retryPolicy());
        Map<String, Object> configuration = request.configuration() == null
                ? job.getConfiguration()
                : new HashMap<>(request.configuration());

        job.updateMetadata(name, request.description() == null ? job.getDescription() : request.description(),
                priority, timeoutSeconds, retryPolicy, configuration);
        return toResponse(job);
    }

    @Transactional
    public void delete(UUID id) {
        Job job = findJob(id);
        if (job.getStatus() != JobStatus.DRAFT) {
            throw new JobStateException("Only DRAFT jobs can be deleted");
        }
        if (taskRepository.countByJob_Id(id) > 0 || jobExecutionRepository.countByJob_Id(id) > 0) {
            throw new JobStateException("A job with tasks or execution history cannot be deleted");
        }
        jobRepository.delete(job);
    }

    @Transactional
    public JobResponse cancel(UUID id) {
        Job job = findJob(id);
        try {
            job.transitionTo(JobStatus.CANCELLED);
        } catch (IllegalStateException exception) {
            throw new JobStateException(exception.getMessage());
        }
        return toResponse(job);
    }

    @Transactional
    public JobResponse activate(UUID id) {
        Job job = findJob(id);
        try {
            job.transitionTo(JobStatus.ACTIVE);
            jobRepository.saveAndFlush(job);
        } catch (IllegalStateException exception) {
            throw new JobStateException(exception.getMessage());
        }
        return toResponse(job);
    }

    @Transactional
    public List<JobExecutionResponse> executions(UUID id) {
        findJob(id);
        return jobExecutionRepository.findByJob_IdOrderByCreatedAtDesc(id).stream()
                .map(this::toExecutionResponse)
                .toList();
    }

    private Job findJob(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + id));
    }

    private void ensureMetadataEditable(Job job) {
        if (job.getStatus() == JobStatus.CANCELLED || job.getStatus() == JobStatus.ARCHIVED) {
            throw new JobStateException("Job metadata cannot be changed after cancellation or archival");
        }
    }

    private RetryPolicy toRetryPolicy(RetryPolicyRequest request) {
        if (request.maxBackoffSeconds() < request.initialBackoffSeconds()) {
            throw new InvalidJobRequestException("maxBackoffSeconds must be greater than or equal to initialBackoffSeconds");
        }
        return new RetryPolicy(request.maxRetries(), request.initialBackoffSeconds(), request.maxBackoffSeconds(),
                request.backoffMultiplier());
    }

    private JobResponse toResponse(Job job) {
        RetryPolicy retryPolicy = job.getRetryPolicy();
        return new JobResponse(job.getId(), job.getName(), job.getDescription(), job.getStatus(), job.getCreatedAt(),
                job.getUpdatedAt(), job.getCreatedBy(), job.getPriority(), job.getTimeoutSeconds(),
                new RetryPolicyResponse(retryPolicy.getMaxRetries(), retryPolicy.getInitialBackoffSeconds(),
                        retryPolicy.getMaxBackoffSeconds(), retryPolicy.getBackoffMultiplier()),
                Collections.unmodifiableMap(new HashMap<>(job.getConfiguration())));
    }

    private JobExecutionResponse toExecutionResponse(JobExecution execution) {
        com.jobtantra.domain.model.JobAttempt attempt = execution.getLatestAttempt();
        return new JobExecutionResponse(execution.getId(), execution.getJob().getId(), attempt.getId(), execution.getStatus(),
            attempt.getAttemptNumber(), attempt.getWorker() == null ? null : attempt.getWorker().getId(),
            attempt.getStartedAt(), attempt.getCompletedAt(), attempt.getErrorCode(),
            attempt.getErrorMessage(), execution.getCreatedAt(), execution.getUpdatedAt());
    }
}
