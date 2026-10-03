package com.jobtantra.application.execution;

import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.TaskStatus;
import com.jobtantra.domain.model.Task;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DefaultExecutionHandler implements ExecutionHandler {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public void execute(JobExecution execution) throws Exception {
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

            if ("HTTP".equalsIgnoreCase(task.getTaskType())) {
                executeHttp(job, task.getConfiguration());
                continue;
            }

            throw new IllegalArgumentException("Unsupported task type: " + task.getTaskType());
        }
    }

    private void executeHttp(Job job, Map<String, Object> configuration) throws Exception {
        Object urlValue = configuration.get("url");
        Object methodValue = configuration.get("method");
        Object bodyValue = configuration.get("body");
        if (!(urlValue instanceof String url) || url.isBlank()) {
            throw new IllegalArgumentException("HTTP task requires a URL");
        }
        if (bodyValue != null && !(bodyValue instanceof String)) {
            throw new IllegalArgumentException("HTTP task body must be a string");
        }

        String method = methodValue == null ? "GET" : methodValue.toString().toUpperCase();
        if (!"GET".equals(method) && !"POST".equals(method)) {
            throw new IllegalArgumentException("HTTP task method must be GET or POST");
        }
        HttpRequest.BodyPublisher body = bodyValue == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString((String) bodyValue);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(job.getTimeoutSeconds()))
                .method(method, body)
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP task returned status " + response.statusCode());
        }
    }
}
