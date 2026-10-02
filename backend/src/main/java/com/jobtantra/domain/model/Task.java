package com.jobtantra.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_tasks_job_order", columnList = "job_id, sequence_order"),
        @Index(name = "idx_tasks_status", columnList = "status")
})
public class Task extends AuditableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @NotBlank
    @Size(max = 100)
    @Column(name = "task_type", nullable = false, length = 100)
    private String taskType;

    @Min(0)
    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status = TaskStatus.ACTIVE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configuration = new HashMap<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "task_dependencies",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "depends_on_task_id"))
    private Set<Task> dependencies = new HashSet<>();

    protected Task() {
    }

    public Task(Job job, String name, String taskType, int sequenceOrder, Map<String, Object> configuration) {
        this.job = job;
        this.name = name;
        this.taskType = taskType;
        this.sequenceOrder = sequenceOrder;
        this.configuration = configuration == null ? new HashMap<>() : new HashMap<>(configuration);
    }

    public Job getJob() {
        return job;
    }

    public String getName() {
        return name;
    }

    public String getTaskType() {
        return taskType;
    }

    public int getSequenceOrder() {
        return sequenceOrder;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public Set<Task> getDependencies() {
        return dependencies;
    }
}
