package com.jobtantra.application.execution;

import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.JobAttempt;
import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.common.exception.ResourceNotFoundException;
import com.jobtantra.infrastructure.persistence.repository.JobExecutionRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExecutionWorkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionWorkerService.class);
    private final JobExecutionRepository executionRepository;
    private final ExecutionHandler executionHandler;

    public ExecutionWorkerService(JobExecutionRepository executionRepository, ExecutionHandler executionHandler) {
        this.executionRepository = executionRepository;
        this.executionHandler = executionHandler;
    }

    @Transactional
    public boolean processNextPending() {
        Optional<JobExecution> pending = executionRepository.findFirstQueuedForUpdate();
        if (pending.isEmpty()) {
            return false;
        }
        processClaimed(pending.get());
        return true;
    }

    @Transactional
    public void process(java.util.UUID executionId) {
        Optional<JobExecution> pending = executionRepository.findByIdForUpdate(executionId);
        if (pending.isEmpty() || pending.get().getStatus() != ExecutionStatus.QUEUED) {
            return;
        }
        processClaimed(pending.get());
    }

    private void processClaimed(JobExecution execution) {
        JobAttempt attempt = execution.getLatestAttempt();
        try {
            attempt.transitionTo(ExecutionStatus.RUNNING, Instant.now());
            execution.updateStatus(ExecutionStatus.RUNNING);
            executionRepository.saveAndFlush(execution);

            executionHandler.execute(execution);

            attempt.transitionTo(ExecutionStatus.SUCCEEDED, Instant.now());
            execution.updateStatus(ExecutionStatus.SUCCEEDED);
        } catch (Exception exception) {
            LOGGER.error("Execution handler failed for execution {}", execution.getId(), exception);
            attempt.recordError("EXECUTION_FAILED", "Execution failed", diagnostic(exception));
            attempt.transitionTo(ExecutionStatus.FAILED, Instant.now());
            execution.updateStatus(ExecutionStatus.FAILED);
        }
        executionRepository.saveAndFlush(execution);
    }

    private String diagnostic(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getName() + (message == null ? "" : ": " + message);
    }
}
