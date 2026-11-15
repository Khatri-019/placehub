package com.placehub.gateway.filter;

import org.springframework.http.HttpHeaders;

/** Test bridge to the package-private header sanitizer. */
public final class EdgeFilterAccess {

    private EdgeFilterAccess() {
    }

    public static void sanitize(HttpHeaders headers, String correlationId) {
        EdgeFilter.sanitize(headers, correlationId);
    }
}
