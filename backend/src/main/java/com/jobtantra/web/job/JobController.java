package com.jobtantra.web.job;

import com.jobtantra.application.job.JobService;
import com.jobtantra.application.job.dto.JobCreateRequest;
import com.jobtantra.application.job.dto.JobExecutionResponse;
import com.jobtantra.application.job.dto.JobResponse;
import com.jobtantra.application.job.dto.JobUpdateRequest;
import com.jobtantra.common.api.ApiResponse;
import com.jobtantra.common.dto.PageResponse;
import com.jobtantra.domain.model.JobStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Manage job definitions and execution history")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @Operation(summary = "Create a job")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Job created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = com.jobtantra.common.api.ApiError.class)))
    })
    public ResponseEntity<ApiResponse<JobResponse>> create(@Valid @RequestBody JobCreateRequest request) {
        JobResponse response = jobService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + response.id())).body(ApiResponse.of(response));
    }

    @GetMapping
    @Operation(summary = "List jobs", description = "Returns a paginated job list, optionally filtered by lifecycle status.")
    public ResponseEntity<ApiResponse<PageResponse<JobResponse>>> list(
            @Parameter(description = "Filter by job lifecycle status", in = ParameterIn.QUERY)
            @RequestParam(required = false) JobStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(jobService.list(status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a job")
    public ResponseEntity<ApiResponse<JobResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(jobService.get(id)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update editable job metadata")
    public ResponseEntity<ApiResponse<JobResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody JobUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(jobService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft job")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        jobService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a job")
    public ResponseEntity<ApiResponse<JobResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(jobService.cancel(id)));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a draft job", description = "Transitions a DRAFT job to ACTIVE.")
    public ResponseEntity<ApiResponse<JobResponse>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(jobService.activate(id)));
    }

    @GetMapping("/{id}/executions")
    @Operation(summary = "Get job execution history")
    public ResponseEntity<ApiResponse<List<JobExecutionResponse>>> executions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(jobService.executions(id)));
    }
}
