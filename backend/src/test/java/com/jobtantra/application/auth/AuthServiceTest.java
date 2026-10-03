package com.jobtantra.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.jobtantra.application.auth.dto.LoginRequest;
import com.jobtantra.application.auth.dto.RegisterRequest;
import com.jobtantra.domain.model.UserAccount;
import com.jobtantra.domain.model.UserRole;
import com.jobtantra.infrastructure.persistence.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserAccountRepository userRepository;
    @Mock private JwtEncoder jwtEncoder;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, passwordEncoder, jwtEncoder, 3600);
    }

    @Test
    void registrationStoresBcryptHashAndReturnsOnlyUserProfile() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.saveAndFlush(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var profile = authService.register(new RegisterRequest("Alice", "correct-horse-battery"));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).saveAndFlush(captor.capture());
        UserAccount stored = captor.getValue();
        assertThat(stored.getUsername()).isEqualTo("alice");
        assertThat(stored.getPasswordHash()).isNotEqualTo("correct-horse-battery");
        assertThat(passwordEncoder.matches("correct-horse-battery", stored.getPasswordHash())).isTrue();
        assertThat(profile.username()).isEqualTo("alice");
        assertThat(profile.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void correctPasswordIssuesJwtAndWrongPasswordDoesNot() {
        String passwordHash = passwordEncoder.encode("correct-horse-battery");
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(new UserAccount("alice", passwordHash, UserRole.USER)));
        Instant issuedAt = Instant.now();
        Jwt token = Jwt.withTokenValue("signed.jwt.token")
                .header("alg", "HS256")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .subject("alice")
                .claim("roles", List.of("USER"))
                .build();
        when(jwtEncoder.encode(any())).thenReturn(token);

        var response = authService.login(new LoginRequest("Alice", "correct-horse-battery"));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.user().role()).isEqualTo(UserRole.USER);
        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwtEncoder).encode(any());
        verifyNoMoreInteractions(jwtEncoder);
    }
}