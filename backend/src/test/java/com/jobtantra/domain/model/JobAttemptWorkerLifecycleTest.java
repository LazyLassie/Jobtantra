package com.jobtantra.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JobAttemptWorkerLifecycleTest {

    @Test
    void queuedAttemptMovesThroughRunningToSuccess() {
        Job job = new Job("local", null, "worker-test", 1, 60, RetryPolicy.defaults(), Map.of());
        JobExecution execution = new JobExecution(job, "worker-test-key");
        JobAttempt attempt = execution.addAttempt();

        attempt.transitionTo(ExecutionStatus.RUNNING, java.time.Instant.now());
        attempt.transitionTo(ExecutionStatus.SUCCEEDED, java.time.Instant.now());
        execution.updateStatus(ExecutionStatus.SUCCEEDED);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(attempt.getCompletedAt()).isNotNull();
    }
}
