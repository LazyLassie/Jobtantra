package com.jobtantra.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.core.env.Environment;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ObservabilityEndpointTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mockMvc;
        @Autowired private Environment environment;

    @Test
    @WithMockUser(username = "observability-owner")
    void exposesMetricsAndReportsJobCreationCounterWithoutSensitiveActuatorEndpoints() throws Exception {
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,metrics");
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"observability-job","description":"metric check","createdBy":"ignored",
                                 "priority":1,"timeoutSeconds":60,
                                 "retryPolicy":{"maxRetries":0,"initialBackoffSeconds":0,
                                   "maxBackoffSeconds":0,"backoffMultiplier":1.0},"configuration":{}}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/actuator/metrics/jobtantra.jobs.created"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("jobtantra.jobs.created"))
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));

    }
}