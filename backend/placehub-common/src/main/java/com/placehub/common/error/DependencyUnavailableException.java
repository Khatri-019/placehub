package com.placehub.common.error;

import org.springframework.http.HttpStatus;

/** A downstream dependency (service, broker) could not be reached; mapped to 503. */
public class DependencyUnavailableException extends ApiException {

    public DependencyUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
