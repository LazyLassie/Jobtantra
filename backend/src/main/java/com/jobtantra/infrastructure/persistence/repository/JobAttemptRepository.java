package com.jobtantra.infrastructure.persistence.repository;

import com.jobtantra.domain.model.JobAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface JobAttemptRepository extends JpaRepository<JobAttempt, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<JobAttempt> findTopByExecution_IdOrderByAttemptNumberDesc(UUID executionId);
}