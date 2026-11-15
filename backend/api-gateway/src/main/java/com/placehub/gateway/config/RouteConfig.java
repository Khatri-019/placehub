package com.placehub.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Authoritative route table (spec section 3.4). Each public prefix maps to exactly one service. */
@Configuration
public class RouteConfig {

    private static final String DEDUPE_HEADERS = "Access-Control-Allow-Origin Access-Control-Allow-Credentials";

    @Bean
    public RouteLocator placeHubRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service", r -> r
                        .path("/api/users/**", "/api/auth/**", "/api/admin/users", "/api/admin/users/**")
                        .filters(RouteConfig::common)
                        .uri("lb://user-service"))
                .route("job-service", r -> r
                        .path("/api/companies", "/api/companies/**", "/api/jobs", "/api/jobs/**",
                                "/api/recruiter/jobs", "/api/recruiter/jobs/**",
                                "/api/admin/jobs", "/api/admin/jobs/**",
                                "/api/admin/companies", "/api/admin/companies/**")
                        .filters(RouteConfig::common)
                        .uri("lb://job-service"))
                .route("application-service", r -> r
                        .path("/api/applications", "/api/applications/**",
                                "/api/admin/applications", "/api/admin/applications/**")
                        .filters(RouteConfig::common)
                        .uri("lb://application-service"))
                .route("notification-service", r -> r
                        .path("/api/notifications", "/api/notifications/**")
                        .filters(RouteConfig::common)
                        .uri("lb://notification-service"))
                .build();
    }

    private static GatewayFilterSpec common(GatewayFilterSpec f) {
        return f.dedupeResponseHeader(DEDUPE_HEADERS, "RETAIN_FIRST");
    }
}
