package com.jobtantra.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobSchedule;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.domain.model.ScheduleType;
import java.time.Instant;
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
class JobScheduleRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private JobRepository jobRepository;
    @Autowired private JobScheduleRepository scheduleRepository;

    @Test
    void persistsOneTimeScheduleAndFindsItByJob() {
        Job job = new Job("daily", null, "owner", 1, 60, RetryPolicy.defaults(), Map.of());
        job.transitionTo(JobStatus.ACTIVE);
        jobRepository.saveAndFlush(job);
        Instant time = Instant.parse("2026-10-01T12:00:00Z");
        scheduleRepository.saveAndFlush(new JobSchedule(job, ScheduleType.ONE_TIME, null, time, time));

        assertThat(scheduleRepository.findByJob_Id(job.getId())).isPresent();
    }
}
