package com.jobtantra.web.task;

import com.jobtantra.application.task.TaskService;
import com.jobtantra.application.task.dto.TaskConfigurationUpdateRequest;
import com.jobtantra.application.task.dto.TaskCreateRequest;
import com.jobtantra.application.task.dto.TaskResponse;
import com.jobtantra.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/tasks")
@Tag(name = "Tasks", description = "Manage task definitions for a job")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @PreAuthorize("@jobAuthorization.canAccessJob(#jobId, authentication)")
    @Operation(summary = "Create a task for a draft job")
    public ResponseEntity<ApiResponse<TaskResponse>> create(@P("jobId") @PathVariable UUID jobId,
            @Valid @RequestBody TaskCreateRequest request) {
        TaskResponse response = taskService.create(jobId, request);
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + jobId + "/tasks/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @GetMapping
    @PreAuthorize("@jobAuthorization.canAccessJob(#jobId, authentication)")
    @Operation(summary = "List tasks for a job")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> list(@P("jobId") @PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.of(taskService.list(jobId)));
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("@jobAuthorization.canAccessJob(#jobId, authentication)")
    @Operation(summary = "Get a task")
    public ResponseEntity<ApiResponse<TaskResponse>> get(@P("jobId") @PathVariable UUID jobId, @PathVariable UUID taskId) {
        return ResponseEntity.ok(ApiResponse.of(taskService.get(jobId, taskId)));
    }

    @PatchMapping("/{taskId}")
    @PreAuthorize("@jobAuthorization.canAccessJob(#jobId, authentication)")
    @Operation(summary = "Update task configuration for a draft job")
    public ResponseEntity<ApiResponse<TaskResponse>> updateConfiguration(@P("jobId") @PathVariable UUID jobId,
            @PathVariable UUID taskId, @Valid @RequestBody TaskConfigurationUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(taskService.updateConfiguration(jobId, taskId, request)));
    }
}