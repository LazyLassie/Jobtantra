package com.jobtantra.application.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jobtantra.application.execution.ExecutionCreationResult;
import com.jobtantra.application.execution.ExecutionService;
import com.jobtantra.application.execution.ExecutionWorkerService;
import com.jobtantra.application.job.dto.JobExecutionResponse;
import com.jobtantra.application.schedule.dto.ScheduleRequest;
import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobSchedule;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.domain.model.ScheduleType;
import com.jobtantra.infrastructure.persistence.repository.JobRepository;
import com.jobtantra.infrastructure.persistence.repository.JobScheduleRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobScheduleRepository scheduleRepository;
    @Mock private ExecutionService executionService;
    @Mock private ExecutionWorkerService executionWorkerService;

    private ScheduleService service;
    private static final UUID JOB_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ScheduleService(jobRepository, scheduleRepository, executionService, executionWorkerService);
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
    void dueScheduleCreatesOneExecutionAndAdvances() {
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
        verify(executionWorkerService).process(executionId);
        verify(scheduleRepository).saveAndFlush(schedule);
    }

    private Job activeJob() {
        Job job = new Job("daily", null, "owner", 1, 60, RetryPolicy.defaults(), Map.of());
        job.transitionTo(JobStatus.ACTIVE);
        return job;
    }
}
