package com.jobtantra.application.execution;

import com.jobtantra.domain.model.JobExecution;

@FunctionalInterface
public interface ExecutionHandler {

    void execute(JobExecution execution) throws Exception;
}
