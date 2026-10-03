package com.jobtantra.application.schedule;

import com.jobtantra.application.execution.ExecutionService;
import com.jobtantra.application.schedule.dto.ScheduleRequest;
import com.jobtantra.application.schedule.dto.ScheduleResponse;
import com.jobtantra.common.exception.ResourceNotFoundException;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobSchedule;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.domain.model.ScheduleType;
import com.jobtantra.infrastructure.persistence.repository.JobRepository;
import com.jobtantra.infrastructure.persistence.repository.JobScheduleRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

@Service
public class ScheduleService {

    private static final Instant ONE_TIME_COMPLETE = Instant.parse("9999-12-31T00:00:00Z");

    private final JobRepository jobRepository;
    private final JobScheduleRepository scheduleRepository;
    private final ExecutionService executionService;

        public ScheduleService(JobRepository jobRepository, JobScheduleRepository scheduleRepository,
            ExecutionService executionService) {
        this.jobRepository = jobRepository;
        this.scheduleRepository = scheduleRepository;
        this.executionService = executionService;
    }

    @Transactional
    public ScheduleResponse upsert(UUID jobId, ScheduleRequest request) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        ScheduleDefinition definition = definition(request);
        JobSchedule schedule = scheduleRepository.findByJob_Id(jobId)
                .orElseGet(() -> new JobSchedule(job, definition.type(), definition.cronExpression(),
                        definition.oneTimeAt(), definition.nextRunAt()));
        if (schedule.getId() != null) {
            schedule.update(definition.type(), definition.cronExpression(), definition.oneTimeAt(), definition.nextRunAt());
        }
        return toResponse(scheduleRepository.saveAndFlush(schedule));
    }

    @Transactional
    public ScheduleResponse get(UUID jobId) {
        return toResponse(scheduleRepository.findByJob_Id(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found for job: " + jobId)));
    }

    @Transactional
    public void remove(UUID jobId) {
        JobSchedule schedule = scheduleRepository.findByJob_Id(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found for job: " + jobId));
        scheduleRepository.delete(schedule);
    }

    @Transactional
    public void processDueSchedules(Instant now) {
        for (JobSchedule schedule : scheduleRepository.findDueForUpdate(now, JobStatus.ACTIVE)) {
            Instant occurrence = schedule.getNextRunAt();
            executionService.create(schedule.getJobId(), idempotencyKey(schedule, occurrence));
            schedule.markScheduled(occurrence, nextRunAt(schedule, occurrence));
            scheduleRepository.saveAndFlush(schedule);
        }
    }

    private ScheduleDefinition definition(ScheduleRequest request) {
        if (request.type() == ScheduleType.ONE_TIME) {
            if (request.oneTimeAt() == null || request.cronExpression() != null) {
                throw new InvalidScheduleException("ONE_TIME schedules require oneTimeAt and no cronExpression");
            }
            return new ScheduleDefinition(request.type(), null, request.oneTimeAt(), request.oneTimeAt());
        }
        if (request.cronExpression() == null || request.cronExpression().isBlank() || request.oneTimeAt() != null) {
            throw new InvalidScheduleException("CRON schedules require cronExpression and no oneTimeAt");
        }
        CronExpression cron;
        try {
            cron = CronExpression.parse(request.cronExpression());
        } catch (IllegalArgumentException exception) {
            throw new InvalidScheduleException("Invalid cronExpression");
        }
        Instant next = cron.next(Instant.now().atZone(ZoneOffset.UTC)).toInstant();
        return new ScheduleDefinition(request.type(), request.cronExpression(), null, next);
    }

    private Instant nextRunAt(JobSchedule schedule, Instant occurrence) {
        if (schedule.getScheduleType() == ScheduleType.ONE_TIME) {
            return ONE_TIME_COMPLETE;
        }
        return CronExpression.parse(schedule.getCronExpression()).next(occurrence.atZone(ZoneOffset.UTC)).toInstant();
    }

    private String idempotencyKey(JobSchedule schedule, Instant occurrence) {
        return "schedule:" + schedule.getId() + ":" + occurrence.toString();
    }

    private ScheduleResponse toResponse(JobSchedule schedule) {
        return new ScheduleResponse(schedule.getId(), schedule.getJobId(), schedule.getScheduleType(),
                schedule.getCronExpression(), schedule.getOneTimeAt(), schedule.getNextRunAt(), schedule.getLastScheduledAt());
    }

    private record ScheduleDefinition(ScheduleType type, String cronExpression, Instant oneTimeAt, Instant nextRunAt) { }
}
