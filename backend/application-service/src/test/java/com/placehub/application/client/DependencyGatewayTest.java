package com.placehub.application.client;

import com.placehub.application.config.FeignConfig;
import com.placehub.common.error.ApiException;
import com.placehub.common.error.DependencyUnavailableException;
import feign.FeignException;
import feign.Request;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DependencyGatewayTest {

    static final Request REQUEST = Request.create(Request.HttpMethod.GET, "/internal/x", Map.of(), null,
            StandardCharsets.UTF_8, null);

    UserClient users = mock(UserClient.class);
    JobClient jobs = mock(JobClient.class);
    CircuitBreakerRegistry registry = new FeignConfig().placeHubCircuitBreakers();
    DependencyGateway gateway = new DependencyGateway(users, jobs, registry);
    UUID id = UUID.randomUUID();

    @Test
    void notFoundIsPropagatedAs404AndDoesNotTripTheBreaker() {
        when(jobs.summary(any())).thenThrow(new FeignException.NotFound("nf", REQUEST, null, null));
        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(() -> gateway.jobSummary(id)).isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
        assertThat(registry.circuitBreaker("job-service").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void infrastructureFailuresBecome503AndOpenTheCircuit() {
        when(users.academicProfile(any())).thenThrow(new RuntimeException("Connection refused"));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> gateway.academicProfile(id)).isInstanceOf(DependencyUnavailableException.class)
                    .hasMessage(DependencyGateway.UNAVAILABLE);
        }
        assertThat(registry.circuitBreaker("user-service").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        // While open, calls are short-circuited (still 503) without reaching the client.
        assertThatThrownBy(() -> gateway.academicProfile(id)).isInstanceOf(DependencyUnavailableException.class);
        verify(users, times(5)).academicProfile(any());
    }

    @Test
    void serverErrorsAreRecordedAs503() {
        when(jobs.validateEligibility(any(), any()))
                .thenThrow(new FeignException.ServiceUnavailable("down", REQUEST, null, null));
        assertThatThrownBy(() -> gateway.validateEligibility(id,
                new ClientDtos.EligibilityCheck("CSE", 2027, BigDecimal.TEN, 0)))
                .isInstanceOf(DependencyUnavailableException.class);
    }
}
