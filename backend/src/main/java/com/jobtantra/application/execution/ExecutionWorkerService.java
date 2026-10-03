package com.jobtantra.application.execution;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ExecutionWorkerService {

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
