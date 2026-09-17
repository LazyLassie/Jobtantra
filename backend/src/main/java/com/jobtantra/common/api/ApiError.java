package com.jobtantra.common.api;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        String code,
        String message,
        String path,
        String correlationId,
        Instant timestamp,
        Map<String, String> fieldErrors) {
}
