package com.jobtantra.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "job_executions", indexes = {
        @Index(name = "idx_job_executions_job_status", columnList = "job_id, status"),
    @Index(name = "idx_job_executions_job_created_at", columnList = "job_id, created_at"),
    @Index(name = "idx_job_executions_available_at", columnList = "status, available_at"),
    @Index(name = "idx_job_executions_lease_expires_at", columnList = "status, lease_expires_at")
})
public class JobExecution extends AuditableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExecutionStatus status = ExecutionStatus.QUEUED;

    @NotNull
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @NotNull
    @Column(name = "available_at", nullable = false)
    private Instant availableAt = Instant.now();

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    @OneToMany(mappedBy = "execution", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobAttempt> attempts = new ArrayList<>();

    protected JobExecution() {
    }

    public JobExecution(Job job, String idempotencyKey) {
        this.job = job;
        this.idempotencyKey = idempotencyKey;
    }

    /** Compatibility constructor for the Phase 2 domain tests and response shape. */
    public JobExecution(Job job, int attemptNumber) {
        this(job, "legacy-" + UUID.randomUUID());
        for (int attempt = 1; attempt <= attemptNumber; attempt++) {
            addAttempt();
        }
    }

    public JobAttempt addAttempt() {
        JobAttempt attempt = new JobAttempt(this, attempts.size() + 1);
        attempts.add(attempt);
        return attempt;
    }

    public void updateStatus(ExecutionStatus status) {
        this.status = status;
    }

    public void claim(UUID claimToken, Instant leaseExpiresAt) {
        this.status = ExecutionStatus.RUNNING;
        this.claimToken = claimToken;
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public void queueForRetry(Instant availableAt) {
        this.status = ExecutionStatus.QUEUED;
        this.availableAt = availableAt;
        clearClaim();
    }

    public void clearClaim() {
        this.claimToken = null;
        this.leaseExpiresAt = null;
    }

    public void transitionTo(ExecutionStatus targetStatus, Instant now) {
        getLatestAttempt().transitionTo(targetStatus, now);
        updateStatus(targetStatus);
    }

    public void recordError(String errorCode, String errorMessage) {
        getLatestAttempt().recordError(errorCode, errorMessage, null);
    }

    public int getAttemptNumber() { return getLatestAttempt().getAttemptNumber(); }
    public Worker getWorker() { return getLatestAttempt().getWorker(); }
    public Instant getStartedAt() { return getLatestAttempt().getStartedAt(); }
    public Instant getCompletedAt() { return getLatestAttempt().getCompletedAt(); }
    public String getErrorCode() { return getLatestAttempt().getErrorCode(); }
    public String getErrorMessage() { return getLatestAttempt().getErrorMessage(); }

    public Job getJob() {
        return job;
    }

    public UUID getJobId() {
        return job.getId();
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public UUID getClaimToken() {
        return claimToken;
    }

    public List<JobAttempt> getAttempts() {
        return attempts;
    }

    public JobAttempt getLatestAttempt() {
        if (attempts.isEmpty()) {
            throw new IllegalStateException("Execution has no attempts");
        }
        return attempts.get(attempts.size() - 1);
    }
}
