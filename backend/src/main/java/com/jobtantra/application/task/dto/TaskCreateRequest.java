package com.jobtantra.application.task.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record TaskCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String taskType,
        @Min(0) int sequenceOrder,
        Map<String, Object> configuration) {
}