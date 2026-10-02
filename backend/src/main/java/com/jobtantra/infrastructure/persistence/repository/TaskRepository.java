package com.jobtantra.infrastructure.persistence.repository;

import com.jobtantra.domain.model.Task;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByJob_IdOrderBySequenceOrderAsc(UUID jobId);

    long countByJob_Id(UUID jobId);
}
