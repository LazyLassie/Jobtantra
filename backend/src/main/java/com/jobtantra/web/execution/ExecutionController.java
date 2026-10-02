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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Executions", description = "Create and manage logical job executions")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/api/v1/jobs/{jobId}/executions")
    @Operation(summary = "Create a queued execution", description = "Creates one logical execution and its first queued attempt. Requires Idempotency-Key.")
    public ResponseEntity<ApiResponse<JobExecutionResponse>> create(@PathVariable UUID jobId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ExecutionCreationResult result = executionService.create(jobId, idempotencyKey);
        if (!result.created()) {
            return ResponseEntity.ok(ApiResponse.of(result.response()));
        }
        return ResponseEntity.created(URI.create("/api/v1/executions/" + result.response().id()))
            .body(ApiResponse.of(result.response()));
    }

    @GetMapping("/api/v1/executions/{executionId}")
    @Operation(summary = "Get an execution")
    public ResponseEntity<ApiResponse<JobExecutionResponse>> get(@PathVariable UUID executionId) {
        return ResponseEntity.ok(ApiResponse.of(executionService.get(executionId)));
    }

    @PostMapping("/api/v1/executions/{executionId}/cancel")
    @Operation(summary = "Cancel an execution")
    public ResponseEntity<ApiResponse<JobExecutionResponse>> cancel(@PathVariable UUID executionId) {
        return ResponseEntity.ok(ApiResponse.of(executionService.cancel(executionId)));
    }

    @PostMapping("/api/v1/executions/{executionId}/retry")
    @Operation(summary = "Retry a failed execution")
    public ResponseEntity<ApiResponse<JobExecutionResponse>> retry(@PathVariable UUID executionId) {
        return ResponseEntity.ok(ApiResponse.of(executionService.retry(executionId)));
    }

}
