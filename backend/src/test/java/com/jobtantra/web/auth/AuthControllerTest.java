package com.jobtantra.web.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtantra.application.auth.AuthService;
import com.jobtantra.application.auth.InvalidCredentialsException;
import com.jobtantra.application.auth.dto.LoginResponse;
import com.jobtantra.application.auth.dto.ProvisionUserRequest;
import com.jobtantra.application.auth.dto.UserProfile;
import com.jobtantra.common.exception.GlobalExceptionHandler;
import com.jobtantra.domain.model.UserRole;
import com.jobtantra.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import java.time.Instant;
import java.util.List;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jobtantra.auth.jwt-secret=security-tests-jwt-secret-at-least-32-bytes")
class AuthControllerTest {

        @Autowired private MockMvc mockMvc;
        @Autowired private JwtEncoder jwtEncoder;
    @MockBean private AuthService authService;

    @Test
    void authenticatesAndReturnsTokenWithoutPasswordHash() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("signed.jwt.token", "Bearer", 3600,
                new UserProfile("alice", UserRole.USER)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"correct-horse-battery"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.data.user.username").value("alice"))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    @Test
    void rejectsInvalidCredentialsWithStandardUnauthorizedError() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void registersAsUserWithoutReturningPasswordHash() throws Exception {
        when(authService.register(any())).thenReturn(new UserProfile("alice", UserRole.USER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"correct-horse-battery"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void adminCanProvisionAccounts() throws Exception {
        when(authService.provision(any(ProvisionUserRequest.class)))
                .thenReturn(new UserProfile("bob", UserRole.USER));

        mockMvc.perform(post("/api/v1/auth/users").header("Authorization", "Bearer " + token("root", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","password":"another-secure-password","role":"USER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("bob"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        verify(authService).provision(any(ProvisionUserRequest.class));
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotProvisionAccounts() throws Exception {
        mockMvc.perform(post("/api/v1/auth/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","password":"another-secure-password","role":"ADMIN"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private String token(String username, String role) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("jobtantra")
                .subject(username)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(600))
                .claim("roles", List.of(role))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}