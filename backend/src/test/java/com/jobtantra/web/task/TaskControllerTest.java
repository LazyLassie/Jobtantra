package com.jobtantra.web.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtantra.application.task.InvalidTaskRequestException;
import com.jobtantra.application.task.TaskService;
import com.jobtantra.application.task.dto.TaskConfigurationUpdateRequest;
import com.jobtantra.application.task.dto.TaskCreateRequest;
import com.jobtantra.application.task.dto.TaskResponse;
import com.jobtantra.common.exception.GlobalExceptionHandler;
import com.jobtantra.domain.model.TaskStatus;
import com.jobtantra.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@WithMockUser
class TaskControllerTest {

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private TaskService taskService;

    @Test
    void createsHttpTask() throws Exception {
        when(taskService.create(eq(JOB_ID), any(TaskCreateRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/jobs/{jobId}/tasks", JOB_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"webhook","taskType":"HTTP","sequenceOrder":0,
                                 "configuration":{"url":"https://example.test/hook","method":"POST","body":"payload"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.taskType").value("HTTP"))
                .andExpect(jsonPath("$.data.configuration.url").value("https://example.test/hook"));
    }

    @Test
    void listsGetsAndUpdatesTaskConfiguration() throws Exception {
        when(taskService.list(JOB_ID)).thenReturn(List.of(response()));
        when(taskService.get(JOB_ID, TASK_ID)).thenReturn(response());
        when(taskService.updateConfiguration(eq(JOB_ID), eq(TASK_ID), any(TaskConfigurationUpdateRequest.class)))
                .thenReturn(response());

        mockMvc.perform(get("/api/v1/jobs/{jobId}/tasks", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(TASK_ID.toString()));
        mockMvc.perform(get("/api/v1/jobs/{jobId}/tasks/{taskId}", JOB_ID, TASK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("webhook"));
        mockMvc.perform(patch("/api/v1/jobs/{jobId}/tasks/{taskId}", JOB_ID, TASK_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskConfigurationUpdateRequest(
                                Map.of("url", "https://example.test/hook")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskType").value("HTTP"));
    }

    @Test
    void returnsStandardApiErrorForInvalidTaskConfiguration() throws Exception {
        when(taskService.create(eq(JOB_ID), any(TaskCreateRequest.class)))
                .thenThrow(new InvalidTaskRequestException("HTTP task method must be GET or POST"));

        mockMvc.perform(post("/api/v1/jobs/{jobId}/tasks", JOB_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"webhook","taskType":"HTTP","sequenceOrder":0,
                                 "configuration":{"url":"https://example.test/hook","method":"DELETE"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TASK_REQUEST"))
                .andExpect(jsonPath("$.message").value("HTTP task method must be GET or POST"));
    }

    private TaskResponse response() {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        return new TaskResponse(TASK_ID, JOB_ID, "webhook", "HTTP", 0, TaskStatus.ACTIVE,
                Map.of("url", "https://example.test/hook", "method", "POST", "body", "payload"), now, now);
    }
}