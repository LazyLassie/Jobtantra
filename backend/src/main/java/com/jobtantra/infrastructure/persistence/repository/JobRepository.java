package com.jobtantra.infrastructure.persistence.repository;

import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByStatusOrderByPriorityDescCreatedAtAsc(JobStatus status);

    org.springframework.data.domain.Page<Job> findByStatus(JobStatus status, org.springframework.data.domain.Pageable pageable);

    List<Job> findByCreatedByOrderByCreatedAtDesc(String createdBy);

        org.springframework.data.domain.Page<Job> findByCreatedBy(String createdBy, org.springframework.data.domain.Pageable pageable);

        org.springframework.data.domain.Page<Job> findByCreatedByAndStatus(String createdBy, JobStatus status,
            org.springframework.data.domain.Pageable pageable);

        boolean existsByIdAndCreatedBy(UUID id, String createdBy);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from Job job where job.id = :id")
    java.util.Optional<Job> findByIdForUpdate(@Param("id") UUID id);
}
