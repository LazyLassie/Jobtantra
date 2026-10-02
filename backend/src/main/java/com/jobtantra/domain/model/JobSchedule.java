package com.jobtantra.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_schedules", indexes = {
        @Index(name = "idx_job_schedules_due", columnList = "next_run_at")
})
public class JobSchedule extends AuditableEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private Job job;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 16)
    private ScheduleType scheduleType;

    @Column(name = "cron_expression", length = 120)
    private String cronExpression;

    @Column(name = "one_time_at")
    private Instant oneTimeAt;

    @NotNull
    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "last_scheduled_at")
    private Instant lastScheduledAt;

    protected JobSchedule() {
    }

    public JobSchedule(Job job, ScheduleType scheduleType, String cronExpression, Instant oneTimeAt,
            Instant nextRunAt) {
        this.job = job;
        this.scheduleType = scheduleType;
        this.cronExpression = cronExpression;
        this.oneTimeAt = oneTimeAt;
        this.nextRunAt = nextRunAt;
    }

    public void update(ScheduleType scheduleType, String cronExpression, Instant oneTimeAt, Instant nextRunAt) {
        this.scheduleType = scheduleType;
        this.cronExpression = cronExpression;
        this.oneTimeAt = oneTimeAt;
        this.nextRunAt = nextRunAt;
        this.lastScheduledAt = null;
    }

    public void markScheduled(Instant occurrence, Instant nextRunAt) {
        this.lastScheduledAt = occurrence;
        this.nextRunAt = nextRunAt;
    }

    public UUID getJobId() { return job.getId(); }
    public Job getJob() { return job; }
    public ScheduleType getScheduleType() { return scheduleType; }
    public String getCronExpression() { return cronExpression; }
    public Instant getOneTimeAt() { return oneTimeAt; }
    public Instant getNextRunAt() { return nextRunAt; }
    public Instant getLastScheduledAt() { return lastScheduledAt; }
}
