package com.jobtantra.infrastructure.persistence.repository;

import com.jobtantra.domain.model.Worker;
import com.jobtantra.domain.model.WorkerStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepository extends JpaRepository<Worker, UUID> {

    Optional<Worker> findByWorkerIdentifier(String workerIdentifier);

    List<Worker> findByStatusOrderByLastHeartbeatAsc(WorkerStatus status);
}
