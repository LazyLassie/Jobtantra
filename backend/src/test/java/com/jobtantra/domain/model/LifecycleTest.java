package com.jobtantra.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LifecycleTest {

    @Test
    void jobCanBeActivatedAndPausedButNotReactivatedAfterArchival() {
        Job job = new Job("daily-import", null, "scheduler", 10, 300, RetryPolicy.defaults(), Map.of());

        job.transitionTo(JobStatus.ACTIVE);
        job.transitionTo(JobStatus.PAUSED);
        job.transitionTo(JobStatus.ACTIVE);
        job.transitionTo(JobStatus.ARCHIVED);

        assertThat(job.getStatus()).isEqualTo(JobStatus.ARCHIVED);
        assertThatThrownBy(() -> job.transitionTo(JobStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void draftJobCannotBeCancelledBeforeActivation() {
        Job job = new Job("daily-import", null, "scheduler", 10, 300, RetryPolicy.defaults(), Map.of());

        assertThatThrownBy(() -> job.transitionTo(JobStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid job status transition from DRAFT to CANCELLED");
    }

    @Test
    void executionRecordsStartAndCompletionTimesDuringLifecycle() {
        Job job = new Job("daily-import", null, "scheduler", 10, 300, RetryPolicy.defaults(), Map.of());
        JobExecution execution = new JobExecution(job, 1);
        Instant startedAt = Instant.parse("2026-09-17T10:00:00Z");
        Instant completedAt = Instant.parse("2026-09-17T10:00:05Z");

        execution.transitionTo(ExecutionStatus.RUNNING, startedAt);
        execution.recordError("NONE", "");
        execution.transitionTo(ExecutionStatus.SUCCEEDED, completedAt);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(execution.getStartedAt()).isEqualTo(startedAt);
        assertThat(execution.getCompletedAt()).isEqualTo(completedAt);
        assertThatThrownBy(() -> execution.transitionTo(ExecutionStatus.RUNNING, completedAt))
                .isInstanceOf(IllegalStateException.class);
    }
}
