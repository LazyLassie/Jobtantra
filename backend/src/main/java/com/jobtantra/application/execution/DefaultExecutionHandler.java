package com.jobtantra.application.execution;

import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.domain.model.TaskStatus;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.Task;
import org.springframework.stereotype.Component;

@Component
public class DefaultExecutionHandler implements ExecutionHandler {

@Override
public void execute(JobExecution execution) {
    Job job = execution.getJob();

    if (job == null) {
        throw new IllegalStateException("Execution has no associated job");
    }

    for (Task task : job.getTasks()) {
        if (task.getStatus() != TaskStatus.ACTIVE) {
            continue;
        }

        if ("NO_OP".equalsIgnoreCase(task.getTaskType())) {
            continue;
        }

        throw new IllegalArgumentException(
                "Unsupported task type: " + task.getTaskType()
        );
    }
}
}
