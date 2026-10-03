package com.jobtantra.web.auth;

import com.jobtantra.application.auth.AuthService;
import com.jobtantra.application.auth.dto.LoginRequest;
import com.jobtantra.application.auth.dto.LoginResponse;
import com.jobtantra.application.auth.dto.ProvisionUserRequest;
import com.jobtantra.application.auth.dto.RegisterRequest;
import com.jobtantra.application.auth.dto.UserProfile;
import com.jobtantra.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a USER account")
    public ResponseEntity<ApiResponse<UserProfile>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.of(authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive a bearer token")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.of(authService.login(request)));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Provision an account (ADMIN only)")
    public ResponseEntity<ApiResponse<UserProfile>> provision(@Valid @RequestBody ProvisionUserRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.of(authService.provision(request)));
    }
}