package com.eesara.drive.common;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        boolean success,
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors
) { }
