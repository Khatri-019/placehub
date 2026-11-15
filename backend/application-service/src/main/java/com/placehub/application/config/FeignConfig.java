package com.placehub.application.config;

import com.placehub.common.security.InternalTokenFilter;
import com.placehub.common.security.PlaceHubSecurityProperties;
import com.placehub.common.web.CorrelationIdFilter;
import feign.RequestInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import feign.FeignException;
import org.slf4j.MDC;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableFeignClients(basePackages = "com.placehub.application.client")
public class FeignConfig {

    /** Attaches the internal service token and forwards the correlation id on every Feign call. */
    @Bean
    public RequestInterceptor internalHeadersInterceptor(PlaceHubSecurityProperties props) {
        return template -> {
            template.header(InternalTokenFilter.HEADER, props.internalToken());
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null) {
                template.header(CorrelationIdFilter.HEADER, correlationId);
            }
        };
    }

    /**
     * Circuit breakers for user-service and job-service (sliding window 10, 50% failures, open 10s). Client errors
     * (4xx, e.g. job not found) are business outcomes and are not recorded as failures.
     */
    @Bean
    public CircuitBreakerRegistry placeHubCircuitBreakers() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordException(FeignConfig::isInfrastructureFailure)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    static boolean isInfrastructureFailure(Throwable t) {
        if (t instanceof FeignException fe) {
            return fe.status() < 400 || fe.status() >= 500;
        }
        return true;
    }
}
