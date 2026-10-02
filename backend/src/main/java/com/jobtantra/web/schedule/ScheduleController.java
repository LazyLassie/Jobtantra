package com.jobtantra.web.schedule;

import com.jobtantra.application.schedule.ScheduleService;
import com.jobtantra.application.schedule.dto.ScheduleRequest;
import com.jobtantra.application.schedule.dto.ScheduleResponse;
import com.jobtantra.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/schedule")
@Tag(name = "Schedules", description = "Configure one-time and cron job schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PutMapping
    @Operation(summary = "Create or replace a job schedule")
    public ResponseEntity<ApiResponse<ScheduleResponse>> upsert(@PathVariable UUID jobId,
            @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.of(scheduleService.upsert(jobId, request)));
    }

    @GetMapping
    @Operation(summary = "Get a job schedule")
    public ResponseEntity<ApiResponse<ScheduleResponse>> get(@PathVariable UUID jobId) {
        return ResponseEntity.ok(ApiResponse.of(scheduleService.get(jobId)));
    }

    @DeleteMapping
    @Operation(summary = "Remove a job schedule")
    public ResponseEntity<Void> remove(@PathVariable UUID jobId) {
        scheduleService.remove(jobId);
        return ResponseEntity.noContent().build();
    }
}
