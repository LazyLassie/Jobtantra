package com.jobtantra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jobtantra.infrastructure.persistence.repository.JobExecutionRepository;
import com.jobtantra.infrastructure.persistence.repository.JobRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class JobAuthorizationTest {

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();

    @Mock private JobRepository jobRepository;
    @Mock private JobExecutionRepository executionRepository;

    private JobAuthorization jobAuthorization;

    @BeforeEach
    void setUp() {
        jobAuthorization = new JobAuthorization(jobRepository, executionRepository);
    }

    @Test
    void userCanAccessOnlyJobsOwnedByTheirUsername() {
        var user = new UsernamePasswordAuthenticationToken("alice", "",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(jobRepository.existsByIdAndCreatedBy(JOB_ID, "alice")).thenReturn(true);

        assertThat(jobAuthorization.canAccessJob(JOB_ID, user)).isTrue();
        verify(jobRepository).existsByIdAndCreatedBy(JOB_ID, "alice");
    }

    @Test
    void userCannotAccessAJobOwnedBySomeoneElse() {
        var user = new UsernamePasswordAuthenticationToken("alice", "",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(jobRepository.existsByIdAndCreatedBy(JOB_ID, "alice")).thenReturn(false);

        assertThat(jobAuthorization.canAccessJob(JOB_ID, user)).isFalse();
    }

    @Test
    void adminCanAccessJobsAndExecutionsWithoutOwnerLookup() {
        var admin = new UsernamePasswordAuthenticationToken("root", "",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(jobAuthorization.canAccessJob(JOB_ID, admin)).isTrue();
        assertThat(jobAuthorization.canAccessExecution(EXECUTION_ID, admin)).isTrue();
        verifyNoInteractions(jobRepository, executionRepository);
    }
}