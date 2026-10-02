package com.jobtantra.application.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.infrastructure.persistence.repository.JobExecutionRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionWorkerServiceTest {

    @Mock private JobExecutionRepository executionRepository;
    @Mock private ExecutionHandler executionHandler;

    private ExecutionWorkerService worker;

    @BeforeEach
    void setUp() {
        worker = new ExecutionWorkerService(executionRepository, executionHandler);
    }

    @Test
    void processesQueuedExecutionSuccessfully() {
        JobExecution execution = queuedExecution();
        when(executionRepository.findFirstQueuedForUpdate()).thenReturn(Optional.of(execution));

        assertThat(worker.processNextPending()).isTrue();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(execution.getLatestAttempt().getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        verify(executionRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(execution);
    }

    @Test
    void sanitizesFailureAndMarksExecutionFailed() throws Exception {
        JobExecution execution = queuedExecution();
        when(executionRepository.findFirstQueuedForUpdate()).thenReturn(Optional.of(execution));
        doThrow(new IllegalStateException("database secret and stack details"))
                .when(executionHandler).execute(execution);

        worker.processNextPending();

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getLatestAttempt().getErrorCode()).isEqualTo("EXECUTION_FAILED");
        assertThat(execution.getLatestAttempt().getErrorMessage()).isEqualTo("Execution failed");
        assertThat(execution.getLatestAttempt().getFailureDetail())
                .contains("IllegalStateException")
                .doesNotContain("stack trace");
    }

    @Test
    void doesNotProcessWhenNoQueuedExecutionExists() {
        when(executionRepository.findFirstQueuedForUpdate()).thenReturn(Optional.empty());

        assertThat(worker.processNextPending()).isFalse();
    }

    private JobExecution queuedExecution() {
        Job job = new Job("local", null, "worker-test", 1, 60, RetryPolicy.defaults(), Map.of());
        JobExecution execution = new JobExecution(job, "worker-test-key");
        execution.addAttempt();
        return execution;
    }
}
