package com.jobtantra.application.auth.dto;

import com.jobtantra.domain.model.UserRole;

public record UserProfile(String username, UserRole role) {
}