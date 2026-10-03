package com.jobtantra.application.auth.dto;

import com.jobtantra.domain.model.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProvisionUserRequest(
        @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotNull UserRole role) {
}