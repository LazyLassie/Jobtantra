package com.jobtantra.web.execution;

import com.jobtantra.application.execution.ExecutionService;
import com.jobtantra.application.execution.ExecutionCreationResult;
import com.jobtantra.application.job.dto.JobExecutionResponse;
import com.jobtantra.common.api.ApiResponse;
import com.jobtantra.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;

@RestController
@Tag(name = "Executions", description = "Create and manage logical job executions")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/api/v1/jobs/{jobId}/executions")
    @PreAuthorize("@jobAuthorization.canAccessJob(#jobId, authentication)")
    @Operation(summary = "Create a queued execution", description = "Creates one logical execution and its first queued attempt. Requires Idempotency-Key.")
    public ResponseEntity<ApiResponse<JobExecutionResponse>> create(@P("jobId") @PathVariable UUID jobId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ExecutionCreationResult result = executionService.create(jobId, idempotencyKey);
        if (!result.created()) {
            return ResponseEntity.ok(ApiResponse.of(result.response()));
        }
        return ResponseEntity.created(URI.create("/api/v1/executions/" + result.response().id()))
            .body(ApiResponse.of(result.response()));
    }

    @GetMapping("/api/v1/executions/{executionId}")
    @PreAuthorize("@jobAuthorization.canAccessExecution(#executionId, authentication)")
    @Operation(summary = "Get an execution")
    public ResponseEntity<ApiResponse<JobExecutionResponse>> get(@P("executionId") @PathVariable UUID executionId) {
        return ResponseEntity.ok(ApiResponse.of(executionService.get(executionId)));
    }

    @PostMapping("/api/v1/executions/{executionId}/cancel")
    @PreAuthorize("@jobAuthorization.canAccessExecution(#executionId, authentication)")
    @Operation(summary = "Cancel an execution")
    public ResponseEntity<ApiResponse<JobExecutionResponse>> cancel(@P("executionId") @PathVariable UUID executionId) {
        return ResponseEntity.ok(ApiResponse.of(executionService.cancel(executionId)));
    }

    @PostMapping("/api/v1/executions/{executionId}/retry")
    @PreAuthorize("@jobAuthorization.canAccessExecution(#executionId, authentication)")
    @Operation(summary = "Retry a failed execution")
    public ResponseEntity<ApiResponse<JobExecutionResponse>> retry(@P("executionId") @PathVariable UUID executionId) {
        return ResponseEntity.ok(ApiResponse.of(executionService.retry(executionId)));
    }

    @PostMapping("/{executionId}/ai-analysis")
    public ResponseEntity<String> analyzeWithAi(@PathVariable UUID executionId) {
    JobExecutionResponse execution = executionService.get(executionId);
    String error = execution.errorMessage();
    String apiKey = System.getenv("ANTHROPIC_API_KEY");

    if (apiKey == null || apiKey.isBlank()) {
        return ResponseEntity.internalServerError()
                .body("ANTHROPIC_API_KEY is not configured");
    }

    String requestBody = """
        {
          "model": "claude-haiku-4-5-20251001",
          "max_tokens": 300,
          "messages": [
            {
              "role": "user",
              "content": "Analyze this job execution error. Give the likely root cause and one recommended fix:\\n%s"
            }
          ]
        }
        """.formatted(error.replace("\"", "\\\""));

    try {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        return ResponseEntity
                .status(response.statusCode())
                .body(response.body());

    } catch (Exception e) {
        return ResponseEntity.internalServerError()
                .body("AI analysis failed");
    }
   }

}
