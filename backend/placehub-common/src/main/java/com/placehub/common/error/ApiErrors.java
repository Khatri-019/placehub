package com.placehub.common.error;

import com.placehub.common.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/** Factory for {@link ApiError} bodies. */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static ApiError of(HttpStatus status, String message, String path) {
        return of(status, message, path, null);
    }

    public static ApiError of(HttpStatus status, String message, String path, Map<String, Object> details) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path,
                MDC.get(CorrelationIdFilter.MDC_KEY), details == null || details.isEmpty() ? null : details);
    }
}
