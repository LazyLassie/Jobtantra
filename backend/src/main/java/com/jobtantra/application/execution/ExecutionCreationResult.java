package com.jobtantra.application.execution;

import com.jobtantra.application.job.dto.JobExecutionResponse;

public record ExecutionCreationResult(JobExecutionResponse response, boolean created) {
}
