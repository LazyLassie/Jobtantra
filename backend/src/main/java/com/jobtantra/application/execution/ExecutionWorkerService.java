package com.jobtantra.application.execution;

import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExecutionWorkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionWorkerService.class);
    private final ExecutionClaimService claimService;
    private final ExecutionHandler executionHandler;

    public ExecutionWorkerService(ExecutionClaimService claimService, ExecutionHandler executionHandler) {
        this.claimService = claimService;
        this.executionHandler = executionHandler;
    }

    public boolean processNextPending() {
        Optional<ExecutionClaimService.ClaimedExecution> pending = claimService.claimNext(Instant.now());
        if (pending.isEmpty()) {
            return false;
        }
        ExecutionClaimService.ClaimedExecution claim = pending.get();
        Exception failure = null;
        boolean interrupted = false;
        try {
            executionHandler.execute(claim.execution());
        } catch (Exception exception) {
            LOGGER.error("Execution handler failed for execution {}", claim.execution().getId(), exception);
            failure = exception;
            interrupted = exception instanceof InterruptedException;
        }
        claimService.complete(claim, failure, Instant.now());
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return true;
    }
}
