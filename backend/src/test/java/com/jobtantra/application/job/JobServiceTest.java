package com.jobtantra.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.infrastructure.persistence.repository.JobExecutionRepository;
import com.jobtantra.infrastructure.persistence.repository.JobRepository;
import com.jobtantra.infrastructure.persistence.repository.TaskRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecutionRepository jobExecutionRepository;

    @Mock
    private TaskRepository taskRepository;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, jobExecutionRepository, taskRepository);
    }

    @Test
    void activatesDraftJob() {
        Job job = job(JobStatus.DRAFT);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        assertThat(jobService.activate(JOB_ID).status()).isEqualTo(JobStatus.ACTIVE);
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void rejectsActiveToActiveTransition() {
        Job job = job(JobStatus.ACTIVE);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.activate(JOB_ID))
                .isInstanceOf(JobStateException.class)
                .hasMessage("Invalid job status transition from ACTIVE to ACTIVE");
    }

    @Test
    void missingJobReturnsNotFound() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.activate(JOB_ID))
                .isInstanceOf(com.jobtantra.common.exception.ResourceNotFoundException.class)
                .hasMessage("Job not found: " + JOB_ID);
    }

    @Test
    void cancelsActiveJob() {
        Job job = job(JobStatus.ACTIVE);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        assertThat(jobService.cancel(JOB_ID).status()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void rejectsCancellationOfDraftJob() {
        Job job = job(JobStatus.DRAFT);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.cancel(JOB_ID))
                .isInstanceOf(JobStateException.class)
                .hasMessage("Invalid job status transition from DRAFT to CANCELLED");
    }

    private static final UUID JOB_ID = UUID.randomUUID();

    private Job job(JobStatus status) {
        Job job = new Job("daily-import", null, "scheduler", 10, 300, RetryPolicy.defaults(), Map.of());
        if (status != JobStatus.DRAFT) {
            job.transitionTo(status == JobStatus.ACTIVE ? JobStatus.ACTIVE : status);
        }
        return job;
    }
}
