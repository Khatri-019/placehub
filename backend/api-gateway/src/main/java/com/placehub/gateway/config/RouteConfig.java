package com.placehub.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.util.unit.DataSize;

/** Authoritative route table (spec section 3.4 + 20). Each public prefix maps to exactly one service. */
@Configuration
public class RouteConfig {

    private static final String DEDUPE_HEADERS = "Access-Control-Allow-Origin Access-Control-Allow-Credentials";
    /** Default request body limit (spec 20.2). Only the resume upload route is raised. */
    static final DataSize DEFAULT_MAX_BODY = DataSize.ofMegabytes(1);
    /** 2 MB file plus the multipart envelope. */
    static final DataSize RESUME_UPLOAD_MAX_BODY = DataSize.ofKilobytes(2200);

    @Bean
    public RouteLocator placeHubRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service-resume-upload", r -> r
                        .order(-1)
                        .path("/api/users/me/resume").and().method(HttpMethod.POST)
                        .filters(f -> withLimit(f, RESUME_UPLOAD_MAX_BODY))
                        .uri("lb://user-service"))
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
                                "/api/admin/applications", "/api/admin/applications/**",
                                "/api/admin/policy", "/api/admin/policy/**",
                                "/api/admin/placements", "/api/admin/placements/**",
                                "/api/admin/students/**",
                                "/api/admin/consents", "/api/admin/consents/**")
                        .filters(RouteConfig::common)
                        .uri("lb://application-service"))
                .route("notification-service", r -> r
                        .path("/api/notifications", "/api/notifications/**")
                        .filters(RouteConfig::common)
                        .uri("lb://notification-service"))
                .build();
    }

    private static GatewayFilterSpec common(GatewayFilterSpec f) {
        return withLimit(f, DEFAULT_MAX_BODY);
    }

    private static GatewayFilterSpec withLimit(GatewayFilterSpec f, DataSize maxBody) {
        return f.dedupeResponseHeader(DEDUPE_HEADERS, "RETAIN_FIRST").setRequestSize(maxBody);
    }
}
