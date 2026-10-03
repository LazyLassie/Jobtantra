package com.jobtantra.application.auth;

import com.jobtantra.application.auth.dto.LoginRequest;
import com.jobtantra.application.auth.dto.LoginResponse;
import com.jobtantra.application.auth.dto.ProvisionUserRequest;
import com.jobtantra.application.auth.dto.RegisterRequest;
import com.jobtantra.application.auth.dto.UserProfile;
import com.jobtantra.domain.model.UserAccount;
import com.jobtantra.domain.model.UserRole;
import com.jobtantra.infrastructure.persistence.repository.UserAccountRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long tokenTtlSeconds;

    public AuthService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
            @Value("${jobtantra.auth.token-ttl-seconds:3600}") long tokenTtlSeconds) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    @Transactional
    public UserProfile register(RegisterRequest request) {
        return createUser(request.username(), request.password(), UserRole.USER);
    }

    @Transactional
    public UserProfile provision(ProvisionUserRequest request) {
        return createUser(request.username(), request.password(), request.role());
    }

    @Transactional
    public void provisionBootstrapAdmin(String username, String password) {
        String normalizedUsername = normalize(username);
        var existing = userRepository.findByUsername(normalizedUsername);
        if (existing.isPresent()) {
            if (existing.get().getRole() != UserRole.ADMIN) {
                throw new IllegalStateException("Configured bootstrap admin username is already assigned to a USER");
            }
            return;
        }
        validatePassword(password);
        saveUser(normalizedUsername, password, UserRole.ADMIN);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccount user = userRepository.findByUsername(normalize(request.username()))
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("jobtantra")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(tokenTtlSeconds))
                .subject(user.getUsername())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", tokenTtlSeconds,
                new UserProfile(user.getUsername(), user.getRole()));
    }

    private UserProfile createUser(String username, String password, UserRole role) {
        validatePassword(password);
        String normalizedUsername = normalize(username);
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new UsernameAlreadyExistsException(normalizedUsername);
        }
        return saveUser(normalizedUsername, password, role);
    }

    private UserProfile saveUser(String username, String password, UserRole role) {
        try {
            UserAccount user = userRepository.saveAndFlush(
                    new UserAccount(username, passwordEncoder.encode(password), role));
            return new UserProfile(user.getUsername(), user.getRole());
        } catch (DataIntegrityViolationException exception) {
            throw new UsernameAlreadyExistsException(username);
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidAccountRequestException("Password must be at least 12 characters and no more than 72 UTF-8 bytes");
        }
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}