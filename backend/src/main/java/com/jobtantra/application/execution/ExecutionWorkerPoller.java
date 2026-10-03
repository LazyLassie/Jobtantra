package com.jobtantra.application.execution;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExecutionWorkerPoller {

    private final ExecutionWorkerService workerService;

    public ExecutionWorkerPoller(ExecutionWorkerService workerService) {
        this.workerService = workerService;
    }

    @Scheduled(fixedDelayString = "${jobtantra.worker.fixed-delay-ms:500}")
    public void poll() {
        workerService.processNextPending();
    }
}