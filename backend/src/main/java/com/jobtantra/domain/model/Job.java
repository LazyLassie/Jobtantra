package com.jobtantra.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_jobs_status", columnList = "status"),
        @Index(name = "idx_jobs_created_by", columnList = "created_by")
})
public class Job extends AuditableEntity {

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @Size(max = 2_000)
    @Column(length = 2_000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status = JobStatus.DRAFT;

    @NotBlank
    @Size(max = 255)
    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Min(0)
    @Column(nullable = false)
    private int priority;

    @Positive
    @Column(name = "timeout_seconds", nullable = false)
    private long timeoutSeconds;

    @Valid
    @Embedded
    private RetryPolicy retryPolicy = RetryPolicy.defaults();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configuration = new HashMap<>();

    @OneToMany(mappedBy = "job", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Task> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "job", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<JobExecution> executions = new ArrayList<>();

    protected Job() {
    }

    public Job(String name, String description, String createdBy, int priority, long timeoutSeconds,
            RetryPolicy retryPolicy, Map<String, Object> configuration) {
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
        this.priority = priority;
        this.timeoutSeconds = timeoutSeconds;
        this.retryPolicy = retryPolicy == null ? RetryPolicy.defaults() : retryPolicy;
        this.configuration = configuration == null ? new HashMap<>() : new HashMap<>(configuration);
    }

    public void transitionTo(JobStatus targetStatus) {
        if (targetStatus == null || !isValidTransition(status, targetStatus)) {
            throw new IllegalStateException("Invalid job status transition from " + status + " to " + targetStatus);
        }
        status = targetStatus;
    }

    private boolean isValidTransition(JobStatus current, JobStatus target) {
        return switch (current) {
            case DRAFT -> target == JobStatus.ACTIVE || target == JobStatus.ARCHIVED;
            case ACTIVE -> target == JobStatus.PAUSED || target == JobStatus.CANCELLED || target == JobStatus.ARCHIVED;
            case PAUSED -> target == JobStatus.ACTIVE || target == JobStatus.CANCELLED || target == JobStatus.ARCHIVED;
            case CANCELLED, ARCHIVED -> false;
        };
    }

    public void updateMetadata(String name, String description, int priority, long timeoutSeconds,
            RetryPolicy retryPolicy, Map<String, Object> configuration) {
        this.name = name;
        this.description = description;
        this.priority = priority;
        this.timeoutSeconds = timeoutSeconds;
        this.retryPolicy = retryPolicy;
        this.configuration = configuration == null ? new HashMap<>() : new HashMap<>(configuration);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public int getPriority() {
        return priority;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public int getMaxRetries() {
        return retryPolicy.getMaxRetries();
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public List<JobExecution> getExecutions() {
        return executions;
    }
}
