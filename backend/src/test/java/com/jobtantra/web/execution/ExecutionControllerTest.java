package com.jobtantra.web.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtantra.application.execution.ExecutionService;
import com.jobtantra.application.execution.ExecutionCreationResult;
import com.jobtantra.application.job.dto.JobExecutionResponse;
import com.jobtantra.common.exception.GlobalExceptionHandler;
import com.jobtantra.domain.model.ExecutionStatus;
import com.jobtantra.security.SecurityConfig;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

@WebMvcTest(ExecutionController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@WithMockUser
class ExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExecutionService executionService;

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();

    @Test
    void createsExecutionWithIdempotencyKey() throws Exception {
        when(executionService.create(JOB_ID, "request-1"))
            .thenReturn(new ExecutionCreationResult(response(ExecutionStatus.QUEUED, 1), true));

        mockMvc.perform(post("/api/v1/jobs/{jobId}/executions", JOB_ID)
                        .header("Idempotency-Key", "request-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.attemptNumber").value(1));
    }

    @Test
    void retrievesExecution() throws Exception {
        when(executionService.get(EXECUTION_ID)).thenReturn(response(ExecutionStatus.QUEUED, 1));

        mockMvc.perform(get("/api/v1/executions/{executionId}", EXECUTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void cancelsExecution() throws Exception {
        when(executionService.cancel(EXECUTION_ID)).thenReturn(response(ExecutionStatus.CANCELLED, 1));

        mockMvc.perform(post("/api/v1/executions/{executionId}/cancel", EXECUTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void retriesExecution() throws Exception {
        when(executionService.retry(EXECUTION_ID)).thenReturn(response(ExecutionStatus.QUEUED, 2));

        mockMvc.perform(post("/api/v1/executions/{executionId}/retry", EXECUTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.attemptNumber").value(2));
    }

    private JobExecutionResponse response(ExecutionStatus status, int attempt) {
        return new JobExecutionResponse(EXECUTION_ID, JOB_ID, UUID.randomUUID(), status, attempt, null, null, null, null, null,
                Instant.now(), Instant.now());
    }
}
