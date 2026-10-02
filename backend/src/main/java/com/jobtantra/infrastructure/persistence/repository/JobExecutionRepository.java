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

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select execution from JobExecution execution where execution.status = com.jobtantra.domain.model.ExecutionStatus.QUEUED "
            + "order by execution.createdAt asc")
        Optional<JobExecution> findFirstQueuedForUpdate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from JobExecution execution where execution.id = :id")
    Optional<JobExecution> findByIdForUpdate(@Param("id") UUID id);

    long countByJob_Id(UUID jobId);

    List<JobExecution> findByStatusOrderByCreatedAtAsc(ExecutionStatus status);
}
