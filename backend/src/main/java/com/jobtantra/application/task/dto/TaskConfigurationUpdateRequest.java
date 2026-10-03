package com.jobtantra.application.task.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record TaskConfigurationUpdateRequest(@NotNull Map<String, Object> configuration) {
}