package com.jobtantra.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.domain.model.Task;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void persistsJobAndOrderedTasksThroughFlywaySchema() {
        Job job = jobRepository.saveAndFlush(
                new Job("daily-import", "Import source data", "scheduler", 20, 600, RetryPolicy.defaults(), Map.of("source", "s3")));
        taskRepository.saveAndFlush(new Task(job, "download", "HTTP", 0, Map.of("uri", "https://example.test/data")));
        taskRepository.saveAndFlush(new Task(job, "validate", "VALIDATION", 1, Map.of()));

        assertThat(jobRepository.findByStatusOrderByPriorityDescCreatedAtAsc(JobStatus.DRAFT))
                .extracting(Job::getName)
                .containsExactly("daily-import");
        assertThat(taskRepository.findByJob_IdOrderBySequenceOrderAsc(job.getId()))
                .extracting(Task::getName)
                .containsExactly("download", "validate");
    }
}
