package com.jobtantra.security;

import com.jobtantra.application.auth.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final AuthService authService;
    private final String username;
    private final String password;

    public BootstrapAdminInitializer(AuthService authService,
            @Value("${jobtantra.auth.bootstrap-admin.username:}") String username,
            @Value("${jobtantra.auth.bootstrap-admin.password:}") String password) {
        this.authService = authService;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() != password.isBlank()) {
            throw new IllegalStateException("Both bootstrap admin username and password must be configured");
        }
        if (!username.isBlank()) {
            authService.provisionBootstrapAdmin(username, password);
        }
    }
}