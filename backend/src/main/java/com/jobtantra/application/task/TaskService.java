package com.jobtantra.application.task;

import com.jobtantra.application.task.dto.TaskConfigurationUpdateRequest;
import com.jobtantra.application.task.dto.TaskCreateRequest;
import com.jobtantra.application.task.dto.TaskResponse;
import com.jobtantra.application.job.JobStateException;
import com.jobtantra.common.exception.ResourceNotFoundException;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.domain.model.Task;
import com.jobtantra.infrastructure.persistence.repository.JobRepository;
import com.jobtantra.infrastructure.persistence.repository.TaskRepository;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    private static final Set<String> HTTP_CONFIGURATION_KEYS = Set.of("url", "method", "body");

    private final JobRepository jobRepository;
    private final TaskRepository taskRepository;

    public TaskService(JobRepository jobRepository, TaskRepository taskRepository) {
        this.jobRepository = jobRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public TaskResponse create(UUID jobId, TaskCreateRequest request) {
        Job job = findJob(jobId);
        ensureDraft(job);
        String taskType = normalizeTaskType(request.taskType());
        Map<String, Object> configuration = validateConfiguration(taskType, request.configuration());
        Task task = new Task(job, request.name(), taskType, request.sequenceOrder(), configuration);
        return toResponse(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public List<TaskResponse> list(UUID jobId) {
        findJob(jobId);
        return taskRepository.findByJob_IdOrderBySequenceOrderAsc(jobId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskResponse get(UUID jobId, UUID taskId) {
        findJob(jobId);
        return toResponse(findTask(jobId, taskId));
    }

    @Transactional
    public TaskResponse updateConfiguration(UUID jobId, UUID taskId, TaskConfigurationUpdateRequest request) {
        Job job = findJob(jobId);
        ensureDraft(job);
        Task task = findTask(jobId, taskId);
        task.updateConfiguration(validateConfiguration(task.getTaskType(), request.configuration()));
        return toResponse(taskRepository.saveAndFlush(task));
    }

    private Job findJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
    }

    private Task findTask(UUID jobId, UUID taskId) {
        return taskRepository.findByIdAndJob_Id(taskId, jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }

    private void ensureDraft(Job job) {
        if (job.getStatus() != JobStatus.DRAFT) {
            throw new JobStateException("Tasks can only be changed while the job is DRAFT");
        }
    }

    private String normalizeTaskType(String taskType) {
        String normalized = taskType == null ? "" : taskType.trim().toUpperCase(Locale.ROOT);
        if (!"NO_OP".equals(normalized) && !"HTTP".equals(normalized)) {
            throw new InvalidTaskRequestException("taskType must be NO_OP or HTTP");
        }
        return normalized;
    }

    private Map<String, Object> validateConfiguration(String taskType, Map<String, Object> requestedConfiguration) {
        Map<String, Object> configuration = requestedConfiguration == null
                ? new HashMap<>()
                : new HashMap<>(requestedConfiguration);
        if ("NO_OP".equals(taskType)) {
            if (!configuration.isEmpty()) {
                throw new InvalidTaskRequestException("NO_OP tasks do not accept configuration");
            }
            return configuration;
        }

        if (!HTTP_CONFIGURATION_KEYS.containsAll(configuration.keySet())) {
            throw new InvalidTaskRequestException("HTTP task configuration supports only url, method, and body");
        }
        Object urlValue = configuration.get("url");
        if (!(urlValue instanceof String url) || url.isBlank() || !isHttpUrl(url)) {
            throw new InvalidTaskRequestException("HTTP tasks require an absolute HTTP or HTTPS URL");
        }
        Object methodValue = configuration.get("method");
        if (methodValue != null) {
            if (!(methodValue instanceof String method)) {
                throw new InvalidTaskRequestException("HTTP task method must be GET or POST");
            }
            String normalizedMethod = method.toUpperCase(Locale.ROOT);
            if (!"GET".equals(normalizedMethod) && !"POST".equals(normalizedMethod)) {
                throw new InvalidTaskRequestException("HTTP task method must be GET or POST");
            }
            configuration.put("method", normalizedMethod);
        }
        Object bodyValue = configuration.get("body");
        if (bodyValue != null && !(bodyValue instanceof String)) {
            throw new InvalidTaskRequestException("HTTP task body must be a string");
        }
        String effectiveMethod = methodValue == null ? "GET" : methodValue.toString().toUpperCase(Locale.ROOT);
        if (bodyValue != null && "GET".equals(effectiveMethod)) {
            throw new InvalidTaskRequestException("HTTP task body is only supported with POST");
        }
        return configuration;
    }

    private boolean isHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getJob().getId(), task.getName(), task.getTaskType(),
            task.getSequenceOrder(), task.getStatus(), Collections.unmodifiableMap(new HashMap<>(task.getConfiguration())),
                task.getCreatedAt(), task.getUpdatedAt());
    }
}