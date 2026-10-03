package com.jobtantra.application.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtantra.application.execution.ExecutionCreationResult;
import com.jobtantra.application.execution.ExecutionClaimService;
import com.jobtantra.application.execution.ExecutionHandler;
import com.jobtantra.application.execution.ExecutionService;
import com.jobtantra.application.execution.ExecutionWorkerService;
import com.jobtantra.application.job.dto.JobExecutionResponse;
import com.jobtantra.application.schedule.dto.ScheduleRequest;
import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobExecution;
import com.jobtantra.domain.model.JobSchedule;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.domain.model.ScheduleType;
import com.jobtantra.infrastructure.persistence.repository.JobRepository;
import com.jobtantra.infrastructure.persistence.repository.JobExecutionRepository;
import com.jobtantra.infrastructure.persistence.repository.JobAttemptRepository;
import com.jobtantra.infrastructure.persistence.repository.JobScheduleRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobScheduleRepository scheduleRepository;
    @Mock private ExecutionService executionService;
    @Mock private JobExecutionRepository executionRepository;
    @Mock private JobAttemptRepository attemptRepository;
    @Mock private ExecutionHandler executionHandler;

    private ScheduleService service;
    private static final UUID JOB_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ScheduleService(jobRepository, scheduleRepository, executionService);
    }

    @Test
    void createsOneTimeScheduleInUtc() {
        Job job = activeJob();
        Instant oneTimeAt = Instant.parse("2026-10-01T12:00:00Z");
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(scheduleRepository.findByJob_Id(JOB_ID)).thenReturn(Optional.empty());
        when(scheduleRepository.saveAndFlush(any(JobSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleRequest request = new ScheduleRequest(ScheduleType.ONE_TIME, null, oneTimeAt);
        var response = service.upsert(JOB_ID, request);

        assertThat(response.type()).isEqualTo(ScheduleType.ONE_TIME);
        assertThat(response.nextRunAt()).isEqualTo(oneTimeAt);
    }

    @Test
    void dueScheduleQueuesOneExecutionAndAdvances() {
        Job job = activeJob();
        Instant occurrence = Instant.parse("2026-10-01T12:00:00Z");
        JobSchedule schedule = new JobSchedule(job, ScheduleType.ONE_TIME, null, occurrence, occurrence);
        when(scheduleRepository.findDueForUpdate(any(), org.mockito.ArgumentMatchers.eq(JobStatus.ACTIVE)))
                .thenReturn(java.util.List.of(schedule));
        UUID executionId = UUID.randomUUID();
        when(executionService.create(any(), any())).thenReturn(new ExecutionCreationResult(
            new JobExecutionResponse(executionId, JOB_ID, UUID.randomUUID(), ExecutionStatus.QUEUED, 1,
                null, null, null, null, null, Instant.now(), Instant.now()), true));

        service.processDueSchedules(Instant.parse("2026-10-01T12:01:00Z"));

        verify(executionService).create(any(), org.mockito.ArgumentMatchers.eq("schedule:null:2026-10-01T12:00:00Z"));
        verify(scheduleRepository).saveAndFlush(schedule);
    }

    @Test
    void scheduledExecutionIsProcessedLaterByTheDatabaseWorker() throws Exception {
        Job job = activeJob();
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        Instant occurrence = Instant.parse("2026-10-01T12:00:00Z");
        JobSchedule schedule = new JobSchedule(job, ScheduleType.ONE_TIME, null, occurrence, occurrence);
        ReflectionTestUtils.setField(schedule, "id", SCHEDULE_ID);
        when(scheduleRepository.findDueForUpdate(any(), eq(JobStatus.ACTIVE)))
                .thenReturn(java.util.List.of(schedule));
        String idempotencyKey = "schedule:" + SCHEDULE_ID + ":" + occurrence;
        when(jobRepository.findByIdForUpdate(JOB_ID)).thenReturn(Optional.of(job));
        when(executionRepository.findByJob_IdAndIdempotencyKey(JOB_ID, idempotencyKey))
                .thenReturn(Optional.empty());
        AtomicReference<JobExecution> persistedExecution = new AtomicReference<>();
        when(executionRepository.saveAndFlush(any(JobExecution.class))).thenAnswer(invocation -> {
            JobExecution saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", EXECUTION_ID);
            persistedExecution.set(saved);
            return saved;
        });
        when(executionRepository.findExpiredRunningIdForUpdate(any())).thenReturn(Optional.empty());
        when(executionRepository.findNextQueuedIdForUpdate(any())).thenReturn(Optional.of(EXECUTION_ID));
        when(executionRepository.findById(EXECUTION_ID))
                .thenAnswer(invocation -> Optional.of(persistedExecution.get()));
        when(executionRepository.findByIdForUpdate(EXECUTION_ID))
                .thenAnswer(invocation -> Optional.of(persistedExecution.get()));
        ExecutionService realExecutionService = new ExecutionService(jobRepository, executionRepository, attemptRepository,
            new SimpleMeterRegistry());
        ScheduleService scheduleService = new ScheduleService(jobRepository, scheduleRepository, realExecutionService);

        scheduleService.processDueSchedules(occurrence.plusSeconds(1));

        JobExecution execution = persistedExecution.get();
        assertThat(execution).isNotNull();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        org.mockito.Mockito.verifyNoInteractions(executionHandler);
        ExecutionWorkerService worker = new ExecutionWorkerService(
            new ExecutionClaimService(executionRepository, 0, new SimpleMeterRegistry()), executionHandler);
        assertThat(worker.processNextPending()).isTrue();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        org.mockito.Mockito.verify(executionHandler).execute(execution);
        verify(executionRepository).findByJob_IdAndIdempotencyKey(JOB_ID, idempotencyKey);
    }

    @Test
    void duplicateDueOccurrenceReusesStableIdempotencyKey() {
        Instant occurrence = Instant.parse("2026-10-01T12:00:00Z");
        UUID scheduleId = UUID.randomUUID();
        JobSchedule schedule = mock(JobSchedule.class);
        when(schedule.getId()).thenReturn(scheduleId);
        when(schedule.getJobId()).thenReturn(JOB_ID);
        when(schedule.getScheduleType()).thenReturn(ScheduleType.ONE_TIME);
        when(schedule.getNextRunAt()).thenReturn(occurrence);
        when(scheduleRepository.findDueForUpdate(any(), eq(JobStatus.ACTIVE)))
                .thenReturn(java.util.List.of(schedule));
        UUID executionId = UUID.randomUUID();
        when(executionService.create(any(), any())).thenReturn(new ExecutionCreationResult(
                new JobExecutionResponse(executionId, JOB_ID, UUID.randomUUID(), ExecutionStatus.QUEUED, 1,
                        null, null, null, null, null, Instant.now(), Instant.now()), false));

        service.processDueSchedules(occurrence.plusSeconds(1));
        service.processDueSchedules(occurrence.plusSeconds(2));

        verify(executionService, times(2)).create(JOB_ID, "schedule:" + scheduleId + ":" + occurrence);
    }

    private Job activeJob() {
        Job job = new Job("daily", null, "owner", 1, 60, RetryPolicy.defaults(), Map.of());
        job.transitionTo(JobStatus.ACTIVE);
        return job;
    }

    private static final UUID EXECUTION_ID = UUID.randomUUID();
    private static final UUID SCHEDULE_ID = UUID.randomUUID();
}
