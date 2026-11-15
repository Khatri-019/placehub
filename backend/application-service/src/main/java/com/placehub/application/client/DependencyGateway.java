package com.placehub.application.client;

import com.placehub.common.error.ApiException;
import com.placehub.common.error.DependencyUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Wraps the Feign clients with Resilience4j circuit breakers (synchronous, so MDC/correlation id is preserved).
 * 404 from a dependency becomes a 404 here; anything else (timeouts, 5xx, open circuit, auth misconfiguration)
 * becomes 503 "Eligibility could not be verified".
 */
@Component
public class DependencyGateway {

    public static final String UNAVAILABLE = "Eligibility could not be verified. Try again shortly.";
    private static final Logger log = LoggerFactory.getLogger(DependencyGateway.class);

    private final UserClient userClient;
    private final JobClient jobClient;
    private final CircuitBreaker userBreaker;
    private final CircuitBreaker jobBreaker;

    public DependencyGateway(UserClient userClient, JobClient jobClient, CircuitBreakerRegistry registry) {
        this.userClient = userClient;
        this.jobClient = jobClient;
        this.userBreaker = registry.circuitBreaker("user-service");
        this.jobBreaker = registry.circuitBreaker("job-service");
    }

    public ClientDtos.AcademicProfile academicProfile(UUID studentId) {
        return call(userBreaker, () -> userClient.academicProfile(studentId), "Student profile not found");
    }

    /** Marks the resume version as captured by an application (it can then never be deleted). */
    public void referenceResume(UUID studentId, UUID resumeId) {
        call(userBreaker, () -> {
            userClient.referenceResume(studentId, resumeId);
            return null;
        }, "Resume not found");
    }

    public ResponseEntity<byte[]> resumeFile(UUID studentId, UUID resumeId) {
        return call(userBreaker, () -> userClient.resumeFile(studentId, resumeId), "Resume not found");
    }

    public ClientDtos.EligibilityResult validateEligibility(UUID jobId, ClientDtos.EligibilityCheck check) {
        return call(jobBreaker, () -> jobClient.validateEligibility(jobId, check), "Job not found");
    }

    public ClientDtos.JobSummary jobSummary(UUID jobId) {
        return call(jobBreaker, () -> jobClient.summary(jobId), "Job not found");
    }

    private <T> T call(CircuitBreaker breaker, Supplier<T> supplier, String notFoundMessage) {
        try {
            return breaker.executeSupplier(supplier);
        } catch (FeignException.NotFound e) {
            throw ApiException.notFound(notFoundMessage);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Dependency {} unavailable: {}", breaker.getName(), e.toString());
            throw new DependencyUnavailableException(UNAVAILABLE);
        }
    }
}
