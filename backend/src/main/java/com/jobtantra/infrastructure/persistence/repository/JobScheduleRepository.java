package com.jobtantra.infrastructure.persistence.repository;

import com.jobtantra.domain.model.JobSchedule;
import com.jobtantra.domain.model.JobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobScheduleRepository extends JpaRepository<JobSchedule, UUID> {

    Optional<JobSchedule> findByJob_Id(UUID jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select schedule from JobSchedule schedule join fetch schedule.job job "
            + "where schedule.nextRunAt <= :now and job.status = :status")
    List<JobSchedule> findDueForUpdate(@Param("now") Instant now, @Param("status") JobStatus status);
}
