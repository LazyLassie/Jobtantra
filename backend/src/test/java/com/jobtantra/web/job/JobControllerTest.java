package com.jobtantra.web.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtantra.application.job.JobService;
import com.jobtantra.application.job.JobStateException;
import com.jobtantra.application.job.dto.JobCreateRequest;
import com.jobtantra.application.job.dto.JobExecutionResponse;
import com.jobtantra.application.job.dto.JobResponse;
import com.jobtantra.application.job.dto.JobUpdateRequest;
import com.jobtantra.application.job.dto.RetryPolicyRequest;
import com.jobtantra.application.job.dto.RetryPolicyResponse;
import com.jobtantra.common.dto.PageResponse;
import com.jobtantra.common.exception.GlobalExceptionHandler;
import com.jobtantra.common.exception.ResourceNotFoundException;
import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.domain.model.JobStatus;
import com.jobtantra.security.SecurityConfig;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

@WebMvcTest(JobController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@WithMockUser
class JobControllerTest {

    private static final UUID JOB_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobService jobService;

    @Test
    void createsJob() throws Exception {
        when(jobService.create(any())).thenReturn(jobResponse(JobStatus.DRAFT));

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"createdBy\":\"owner\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void getsJob() throws Exception {
        when(jobService.get(JOB_ID)).thenReturn(jobResponse(JobStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/jobs/{id}", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("daily-import"));
    }

    @Test
    void listsJobs() throws Exception {
        when(jobService.list(eq(JobStatus.ACTIVE), any())).thenReturn(
                new PageResponse<>(List.of(jobResponse(JobStatus.ACTIVE)), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/jobs").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void authenticatedUserCanListJobs() throws Exception {
        when(jobService.list(eq(null), any())).thenReturn(
                new PageResponse<>(List.of(jobResponse(JobStatus.DRAFT)), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"));
    }

    @Test
    void updatesJob() throws Exception {
        when(jobService.update(eq(JOB_ID), any())).thenReturn(jobResponse(JobStatus.DRAFT));

        mockMvc.perform(patch("/api/v1/jobs/{id}", JOB_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JobUpdateRequest("renamed", null, 5, 300L, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("daily-import"));
    }

    @Test
    void deletesDraftJob() throws Exception {
        doNothing().when(jobService).delete(JOB_ID);

        mockMvc.perform(delete("/api/v1/jobs/{id}", JOB_ID))
                .andExpect(status().isNoContent());

        verify(jobService).delete(JOB_ID);
    }

    @Test
    void returnsConflictForInvalidDeleteOrTransition() throws Exception {
        doThrow(new JobStateException("Only DRAFT jobs can be deleted")).when(jobService).delete(JOB_ID);

        mockMvc.perform(delete("/api/v1/jobs/{id}", JOB_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_JOB_STATE"));
    }

    @Test
    void returnsNotFoundForMissingJob() throws Exception {
        when(jobService.get(JOB_ID)).thenThrow(new ResourceNotFoundException("Job not found"));

        mockMvc.perform(get("/api/v1/jobs/{id}", JOB_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void cancelsJob() throws Exception {
        when(jobService.cancel(JOB_ID)).thenReturn(jobResponse(JobStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/jobs/{id}/cancel", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

        @Test
        void activatesDraftJob() throws Exception {
                when(jobService.activate(JOB_ID)).thenReturn(jobResponse(JobStatus.ACTIVE));

                mockMvc.perform(post("/api/v1/jobs/{id}/activate", JOB_ID))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }

    @Test
    void returnsExecutionHistory() throws Exception {
        when(jobService.executions(JOB_ID)).thenReturn(List.of(new JobExecutionResponse(
                UUID.randomUUID(), JOB_ID, UUID.randomUUID(), ExecutionStatus.SUCCEEDED, 1, null, Instant.now(), Instant.now(), null, null,
                Instant.now(), Instant.now())));

        mockMvc.perform(get("/api/v1/jobs/{id}/executions", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SUCCEEDED"));
    }

    private JobCreateRequest createRequest() {
        return new JobCreateRequest("daily-import", "Import data", "owner", 10, 300L,
                new RetryPolicyRequest(3, 30L, 3_600L, BigDecimal.valueOf(2)), Map.of("source", "s3"));
    }

    private JobResponse jobResponse(JobStatus status) {
        return new JobResponse(JOB_ID, "daily-import", "Import data", status, Instant.now(), Instant.now(), "owner", 10,
                300, new RetryPolicyResponse(3, 30, 3_600, BigDecimal.valueOf(2)), Map.of("source", "s3"));
    }
}
