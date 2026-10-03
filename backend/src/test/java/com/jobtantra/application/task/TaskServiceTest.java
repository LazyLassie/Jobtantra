package com.jobtantra.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.jobtantra.application.job.JobStateException;
import com.jobtantra.application.task.dto.TaskConfigurationUpdateRequest;
import com.jobtantra.application.task.dto.TaskCreateRequest;
import com.jobtantra.domain.model.Job;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.domain.model.RetryPolicy;
import com.jobtantra.domain.model.Task;
import com.jobtantra.infrastructure.persistence.repository.JobRepository;
import com.jobtantra.infrastructure.persistence.repository.TaskRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

    @Mock private JobRepository jobRepository;
    @Mock private TaskRepository taskRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(jobRepository, taskRepository);
    }

    @Test
    void createsNoOpTask() {
        Job job = job(JobStatus.DRAFT);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = taskService.create(JOB_ID,
                new TaskCreateRequest("noop", "NO_OP", 0, Map.of()));

        assertThat(response.name()).isEqualTo("noop");
        assertThat(response.taskType()).isEqualTo("NO_OP");
        assertThat(response.configuration()).isEmpty();
    }

    @Test
    void createsHttpTaskWithSupportedConfiguration() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.DRAFT)));
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Map<String, Object> configuration = Map.of(
                "url", "https://example.test/hook",
                "method", "post",
                "body", "payload");

        var response = taskService.create(JOB_ID,
                new TaskCreateRequest("webhook", "http", 1, configuration));

        assertThat(response.taskType()).isEqualTo("HTTP");
        assertThat(response.configuration()).containsEntry("method", "POST")
                .containsEntry("url", "https://example.test/hook")
                .containsEntry("body", "payload");
    }

    @Test
    void rejectsInvalidHttpConfiguration() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.DRAFT)));

        assertThatThrownBy(() -> taskService.create(JOB_ID,
                new TaskCreateRequest("webhook", "HTTP", 1, Map.of("url", "file:///tmp/data"))))
                .isInstanceOf(InvalidTaskRequestException.class)
                .hasMessage("HTTP tasks require an absolute HTTP or HTTPS URL");
        assertThatThrownBy(() -> taskService.create(JOB_ID,
            new TaskCreateRequest("webhook", "HTTP", 1,
                Map.of("url", "https://example.test/hook", "body", "payload"))))
            .isInstanceOf(InvalidTaskRequestException.class)
            .hasMessage("HTTP task body is only supported with POST");
    }

    @Test
    void listsAndGetsTasksWithinTheJob() {
        Job job = job(JobStatus.DRAFT);
        Task task = new Task(job, "noop", "NO_OP", 0, Map.of());
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(taskRepository.findByJob_IdOrderBySequenceOrderAsc(JOB_ID)).thenReturn(List.of(task));
        when(taskRepository.findByIdAndJob_Id(TASK_ID, JOB_ID)).thenReturn(Optional.of(task));

        assertThat(taskService.list(JOB_ID)).extracting("name").containsExactly("noop");
        assertThat(taskService.get(JOB_ID, TASK_ID).taskType()).isEqualTo("NO_OP");
    }

    @Test
    void updatesConfigurationForDraftJob() {
        Job job = job(JobStatus.DRAFT);
        Task task = new Task(job, "webhook", "HTTP", 1, Map.of("url", "https://old.test"));
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(taskRepository.findByIdAndJob_Id(TASK_ID, JOB_ID)).thenReturn(Optional.of(task));
        when(taskRepository.saveAndFlush(task)).thenReturn(task);

        var response = taskService.updateConfiguration(JOB_ID, TASK_ID,
                new TaskConfigurationUpdateRequest(Map.of("url", "https://new.test/hook", "method", "GET")));

        assertThat(response.configuration()).containsEntry("url", "https://new.test/hook");
        assertThat(task.getName()).isEqualTo("webhook");
        assertThat(task.getTaskType()).isEqualTo("HTTP");
    }

    @Test
    void rejectsTaskModificationForActiveJob() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.ACTIVE)));

        assertThatThrownBy(() -> taskService.create(JOB_ID,
                new TaskCreateRequest("noop", "NO_OP", 0, Map.of())))
                .isInstanceOf(JobStateException.class)
                .hasMessage("Tasks can only be changed while the job is DRAFT");
    }

    private Job job(JobStatus status) {
        Job job = new Job("task-test", null, "owner", 1, 60, RetryPolicy.defaults(), Map.of());
        if (status != JobStatus.DRAFT) {
            job.transitionTo(status);
        }
        return job;
    }
}