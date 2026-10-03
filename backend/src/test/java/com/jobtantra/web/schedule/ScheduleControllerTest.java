package com.jobtantra.web.schedule;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtantra.application.schedule.ScheduleService;
import com.jobtantra.application.schedule.dto.ScheduleRequest;
import com.jobtantra.application.schedule.dto.ScheduleResponse;
import com.jobtantra.common.exception.GlobalExceptionHandler;
import com.jobtantra.domain.model.ScheduleType;
import com.jobtantra.security.SecurityConfig;
import com.jobtantra.security.JobAuthorization;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.core.Authentication;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import org.springframework.test.context.TestPropertySource;

@WebMvcTest(ScheduleController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jobtantra.auth.jwt-secret=security-tests-jwt-secret-at-least-32-bytes")
@WithMockUser
class ScheduleControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ScheduleService scheduleService;
    @MockBean(name = "jobAuthorization") private JobAuthorization jobAuthorization;

    private static final UUID JOB_ID = UUID.randomUUID();

    @BeforeEach
    void allowOwnedResources() {
        lenient().when(jobAuthorization.canAccessJob(any(UUID.class), any(Authentication.class))).thenReturn(true);
    }

    @Test
    void upsertsSchedule() throws Exception {
        ScheduleRequest request = new ScheduleRequest(ScheduleType.ONE_TIME, null,
                Instant.parse("2026-10-01T12:00:00Z"));
        when(scheduleService.upsert(eq(JOB_ID), eq(request))).thenReturn(response());

        mockMvc.perform(put("/api/v1/jobs/{jobId}/schedule", JOB_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("ONE_TIME"));
    }

    @Test
    void getsAndRemovesSchedule() throws Exception {
        when(scheduleService.get(JOB_ID)).thenReturn(response());
        doNothing().when(scheduleService).remove(JOB_ID);

        mockMvc.perform(get("/api/v1/jobs/{jobId}/schedule", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(JOB_ID.toString()));
        mockMvc.perform(delete("/api/v1/jobs/{jobId}/schedule", JOB_ID))
                .andExpect(status().isNoContent());
    }

    private ScheduleResponse response() {
        Instant time = Instant.parse("2026-10-01T12:00:00Z");
        return new ScheduleResponse(UUID.randomUUID(), JOB_ID, ScheduleType.ONE_TIME, null, time, time, null);
    }
}
