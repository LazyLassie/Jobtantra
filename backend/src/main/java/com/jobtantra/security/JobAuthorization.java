package com.jobtantra.security;

import com.jobtantra.infrastructure.persistence.repository.JobExecutionRepository;
import com.jobtantra.infrastructure.persistence.repository.JobRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("jobAuthorization")
public class JobAuthorization {

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;

    public JobAuthorization(JobRepository jobRepository, JobExecutionRepository executionRepository) {
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
    }

    public boolean canAccessJob(UUID jobId, Authentication authentication) {
        return isAdmin(authentication)
                || jobRepository.existsByIdAndCreatedBy(jobId, authentication.getName());
    }

    public boolean canAccessExecution(UUID executionId, Authentication authentication) {
        return isAdmin(authentication)
                || executionRepository.existsByIdAndJob_CreatedBy(executionId, authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}