package com.jobtantra.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workers", indexes = {
        @Index(name = "idx_workers_status", columnList = "status"),
        @Index(name = "idx_workers_last_heartbeat", columnList = "last_heartbeat")
})
public class Worker extends AuditableEntity {

    @NotBlank
    @Size(max = 255)
    @Column(name = "worker_identifier", nullable = false, unique = true, length = 255)
    private String workerIdentifier;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkerStatus status = WorkerStatus.REGISTERED;

    @NotNull
    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @NotNull
    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    protected Worker() {
    }

    public Worker(String workerIdentifier, Map<String, Object> metadata) {
        this.workerIdentifier = workerIdentifier;
        this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        this.registeredAt = Instant.now();
        this.lastHeartbeat = registeredAt;
    }

    public String getWorkerIdentifier() {
        return workerIdentifier;
    }

    public WorkerStatus getStatus() {
        return status;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
