package com.jobtantra.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.domain.model.RetryPolicy;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExecutionRepositoryTest {

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
    private JobExecutionRepository executionRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void persistsLogicalExecutionAndFirstAttempt() {
        Job job = new Job("daily", null, "owner", 1, 60, RetryPolicy.defaults(), Map.of());
        job.transitionTo(JobStatus.ACTIVE);
        jobRepository.saveAndFlush(job);

        JobExecution execution = new JobExecution(job, "request-1");
        execution.addAttempt();
        executionRepository.saveAndFlush(execution);

        var loaded = executionRepository.findByJob_IdAndIdempotencyKey(job.getId(), "request-1");
        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(loaded.orElseThrow().getAttempts()).hasSize(1);
    }

    @Test
    void selectsExpiredRunningExecutionForLeaseRecovery() {
        Job job = new Job("expired-claim", null, "owner", 1, 60, RetryPolicy.defaults(), Map.of());
        job.transitionTo(JobStatus.ACTIVE);
        jobRepository.saveAndFlush(job);
        JobExecution execution = new JobExecution(job, "expired-claim");
        execution.addAttempt().transitionTo(ExecutionStatus.RUNNING, Instant.now().minusSeconds(30));
        execution.claim(UUID.randomUUID(), Instant.now().minusSeconds(1));
        executionRepository.saveAndFlush(execution);

        assertThat(executionRepository.findExpiredRunningIdForUpdate(Instant.now().plusSeconds(1)))
                .contains(execution.getId());
    }

    @Test
    void validRunningLeaseIsNotAvailableForClaimOrRecovery() {
        Job job = new Job("valid-claim", null, "owner", 1, 60, RetryPolicy.defaults(), Map.of());
        job.transitionTo(JobStatus.ACTIVE);
        jobRepository.saveAndFlush(job);
        Instant now = Instant.now();
        JobExecution execution = new JobExecution(job, "valid-claim");
        execution.addAttempt().transitionTo(ExecutionStatus.RUNNING, now);
        execution.claim(UUID.randomUUID(), now.plusSeconds(60));
        executionRepository.saveAndFlush(execution);

        assertThat(executionRepository.findNextQueuedIdForUpdate(now))
            .isNotEqualTo(Optional.of(execution.getId()));
        assertThat(executionRepository.findExpiredRunningIdForUpdate(now))
            .isNotEqualTo(Optional.of(execution.getId()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void skipsExecutionAlreadyLockedByAnotherConsumer() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID executionId = transaction.execute(status -> {
            Job job = new Job("claim-" + UUID.randomUUID(), null, "owner", 1, 60,
                    RetryPolicy.defaults(), Map.of());
            job.transitionTo(JobStatus.ACTIVE);
            jobRepository.saveAndFlush(job);
            JobExecution execution = new JobExecution(job, "claim-" + UUID.randomUUID());
            execution.addAttempt();
            return executionRepository.saveAndFlush(execution).getId();
        });

        CountDownLatch firstConsumerLockedRow = new CountDownLatch(1);
        CountDownLatch releaseFirstConsumer = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Optional<UUID>> firstClaim = executor.submit(() -> transaction.execute(status -> {
                Optional<UUID> selected = executionRepository.findNextQueuedIdForUpdate(Instant.now().plusSeconds(5));
                firstConsumerLockedRow.countDown();
                try {
                    if (!releaseFirstConsumer.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release claim transaction");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return selected;
            }));

            assertThat(firstConsumerLockedRow.await(5, TimeUnit.SECONDS)).isTrue();
            Optional<UUID> secondClaim = transaction.execute(status ->
                    executionRepository.findNextQueuedIdForUpdate(Instant.now().plusSeconds(5)));

            assertThat(secondClaim).isEmpty();
            releaseFirstConsumer.countDown();
            assertThat(firstClaim.get(5, TimeUnit.SECONDS)).contains(executionId);
        } finally {
            releaseFirstConsumer.countDown();
            executor.shutdownNow();
        }
    }
}
