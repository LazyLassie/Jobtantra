package com.jobtantra.application.auth.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserProfile user) {
}