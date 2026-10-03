package com.jobtantra.infrastructure.persistence.repository;

import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.JobExecution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobExecutionRepository extends JpaRepository<JobExecution, UUID> {

    List<JobExecution> findByJob_IdOrderByCreatedAtDesc(UUID jobId);

    Page<JobExecution> findByJob_Id(UUID jobId, Pageable pageable);

    Page<JobExecution> findByJob_IdAndStatus(UUID jobId, com.jobtantra.domain.model.ExecutionStatus status, Pageable pageable);

    Optional<JobExecution> findByJob_IdAndIdempotencyKey(UUID jobId, String idempotencyKey);

    @Query(value = "select id from job_executions where status = 'QUEUED' and available_at <= :now "
            + "order by available_at asc, created_at asc limit 1 for update skip locked", nativeQuery = true)
    Optional<UUID> findNextQueuedIdForUpdate(@Param("now") java.time.Instant now);

    @Query(value = "select id from job_executions where status = 'RUNNING' and lease_expires_at <= :now "
            + "order by lease_expires_at asc limit 1 for update skip locked", nativeQuery = true)
    Optional<UUID> findExpiredRunningIdForUpdate(@Param("now") java.time.Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from JobExecution execution where execution.id = :id")
    Optional<JobExecution> findByIdForUpdate(@Param("id") UUID id);

    long countByJob_Id(UUID jobId);

    List<JobExecution> findByStatusOrderByCreatedAtAsc(ExecutionStatus status);
}
