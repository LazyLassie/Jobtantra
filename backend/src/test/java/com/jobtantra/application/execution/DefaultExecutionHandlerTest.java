package com.jobtantra.application.execution;

import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.domain.model.Task;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultExecutionHandlerTest {

    @Test
    void executesNoOpTaskSuccessfully() {
        Job job = new Job(
                "local",
                "No-op job",
                "handler-test",
                1,
                60,
                RetryPolicy.defaults(),
                Map.of()
        );

        job.getTasks().add(new Task(job, "noop-task", "NO_OP", 1, Map.of()));

        JobExecution execution = new JobExecution(job, "handler-test-key");

        DefaultExecutionHandler handler = new DefaultExecutionHandler();

        assertDoesNotThrow(() -> handler.execute(execution));
    }

    @Test
    void rejectsUnsupportedActiveTaskType() {
        Job job = new Job(
                "local",
                "Unsupported job",
                "handler-test",
                1,
                60,
                RetryPolicy.defaults(),
                Map.of()
        );

        job.getTasks().add(new Task(job, "unsupported-task", "UNKNOWN", 1, Map.of()));

        JobExecution execution = new JobExecution(job, "handler-test-key");

        DefaultExecutionHandler handler = new DefaultExecutionHandler();

        assertThrows(
                IllegalArgumentException.class,
                () -> handler.execute(execution)
        );
    }
}
