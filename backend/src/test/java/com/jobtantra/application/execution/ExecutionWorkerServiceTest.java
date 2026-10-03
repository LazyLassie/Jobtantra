package com.jobtantra.application.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.domain.model.Task;
import com.jobtantra.infrastructure.persistence.repository.JobExecutionRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
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
    private HttpServer httpServer;

    @BeforeEach
    void setUp() {
        worker = new ExecutionWorkerService(executionRepository, executionHandler);
    }

    @AfterEach
    void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
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

    @Test
    void workerExecutesHttpTaskAndMarksExecutionSucceeded() throws IOException {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        String url = startHttpServer("/run", 204, requestMethod, requestBody);
        JobExecution execution = queuedHttpExecution(url, "POST", "payload");
        when(executionRepository.findByIdForUpdate(execution.getId())).thenReturn(Optional.of(execution));
        worker = new ExecutionWorkerService(executionRepository, new DefaultExecutionHandler());

        worker.process(execution.getId());

        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(requestBody.get()).isEqualTo("payload");
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(execution.getLatestAttempt().getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
    }

    @Test
    void unsuccessfulHttpTaskProducesSanitizedFailure() throws IOException {
        String url = startHttpServer("/fail", 503, new AtomicReference<>(), new AtomicReference<>());
        JobExecution execution = queuedHttpExecution(url, "GET", null);
        when(executionRepository.findByIdForUpdate(execution.getId())).thenReturn(Optional.of(execution));
        worker = new ExecutionWorkerService(executionRepository, new DefaultExecutionHandler());

        worker.process(execution.getId());

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getLatestAttempt().getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getLatestAttempt().getErrorCode()).isEqualTo("EXECUTION_FAILED");
        assertThat(execution.getLatestAttempt().getErrorMessage()).isEqualTo("Execution failed");
        assertThat(execution.getLatestAttempt().getFailureDetail()).contains("status 503");
    }

    private String startHttpServer(String path, int responseStatus, AtomicReference<String> requestMethod,
            AtomicReference<String> requestBody) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext(path, exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.close();
        });
        httpServer.start();
        return "http://127.0.0.1:" + httpServer.getAddress().getPort() + path;
    }

    private JobExecution queuedHttpExecution(String url, String method, String body) {
        Job job = new Job("http", null, "worker-test", 1, 60, RetryPolicy.defaults(), Map.of());
        Map<String, Object> configuration = new java.util.HashMap<>();
        configuration.put("url", url);
        configuration.put("method", method);
        if (body != null) {
            configuration.put("body", body);
        }
        job.getTasks().add(new Task(job, "http-task", "HTTP", 1, configuration));
        JobExecution execution = new JobExecution(job, "worker-http-key");
        execution.addAttempt();
        return execution;
    }

    private JobExecution queuedExecution() {
        Job job = new Job("local", null, "worker-test", 1, 60, RetryPolicy.defaults(), Map.of());
        JobExecution execution = new JobExecution(job, "worker-test-key");
        execution.addAttempt();
        return execution;
    }
}
