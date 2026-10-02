package com.jobtantra.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Entity
@Table(name = "job_execution_attempts", indexes = {
        @Index(name = "idx_attempts_execution_number", columnList = "execution_id, attempt_number"),
        @Index(name = "idx_attempts_status_created_at", columnList = "status, created_at")
})
public class JobAttempt extends AuditableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private JobExecution execution;

    @Min(1)
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExecutionStatus status = ExecutionStatus.QUEUED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 2_000)
    private String errorMessage;

    @Column(name = "failure_detail", length = 4_000)
    private String failureDetail;

    protected JobAttempt() {
    }

    public JobAttempt(JobExecution execution, int attemptNumber) {
        this.execution = execution;
        this.attemptNumber = attemptNumber;
    }

    public void transitionTo(ExecutionStatus targetStatus, Instant now) {
        if (targetStatus == null || !isValidTransition(status, targetStatus)) {
            throw new IllegalStateException("Invalid attempt status transition from " + status + " to " + targetStatus);
        }
        status = targetStatus;
        if (targetStatus == ExecutionStatus.RUNNING && startedAt == null) {
            startedAt = now;
        }
        if (targetStatus == ExecutionStatus.SUCCEEDED || targetStatus == ExecutionStatus.FAILED
                || targetStatus == ExecutionStatus.CANCELLED || targetStatus == ExecutionStatus.TIMED_OUT) {
            completedAt = now;
        }
    }

    public void recordError(String errorCode, String errorMessage, String failureDetail) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.failureDetail = failureDetail;
    }

    private boolean isValidTransition(ExecutionStatus current, ExecutionStatus target) {
        return switch (current) {
            case QUEUED -> target == ExecutionStatus.RUNNING || target == ExecutionStatus.CANCELLED;
            case RUNNING -> target == ExecutionStatus.SUCCEEDED || target == ExecutionStatus.FAILED
                    || target == ExecutionStatus.CANCELLED || target == ExecutionStatus.TIMED_OUT;
            case SUCCEEDED, FAILED, CANCELLED, TIMED_OUT -> false;
        };
    }

    public JobExecution getExecution() { return execution; }
    public int getAttemptNumber() { return attemptNumber; }
    public ExecutionStatus getStatus() { return status; }
    public Worker getWorker() { return worker; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getFailureDetail() { return failureDetail; }
}